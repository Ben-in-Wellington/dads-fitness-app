// TrainerRepository.kt

package com.di.feature_trainer.data

import android.util.Log
import com.di.core.data.SessionRepository
import com.di.core.data.SettingsRepository
import com.di.core.data.UserManager
import com.di.core.data.database.*
import com.di.feature_trainer.data.models.*
import com.di.feature_trainer.data.network.GeminiLiveWebSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first // Used to collect the first emitted value from a Flow
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * A blueprint for constructing the final system prompt from modular components.
 * This structure improves readability, maintainability, and scalability by separating
 * different aspects of the AI's behavior into distinct, manageable pieces.
 */
private data class SystemPromptBlueprint(
    val persona: Persona,
    val context: String,
    val rules: String,
    val task: String,
    val variety: String // Added for randomized conversational elements
)

/**
 * Defines the core identity, psychological drivers, and conversational style of the AI.
 * Each personality type will have a unique instance of this data class, with some
 * randomized elements to ensure variety between conversations.
 */
private data class Persona(
    val key: String, // A unique identifier for the persona (e.g., "manic_motivator")
    val roleAndCoreDrivers: String, // The fundamental "why" and "who" of the AI (with random elements)
    val selfAwarenessAngle: String, // How the AI refers to its own AI nature for humor or context
    val conversationalToolkit: String // Specific linguistic and interaction techniques
)

/**
 * Interface defining the contract for the AI Trainer's data layer operations.
 * This abstracts the interaction with the Gemini Live API and local data sources.
 */
interface TrainerRepository {
    /** Emits LiveApiEvent as they arrive from Gemini Live API for viewmodels to handle. */
    val events: SharedFlow<LiveApiEvent>

    /** Starts a new Gemini Live API session for the specified user/workout context. */
    suspend fun startSession(
        userId: Long,
        workoutSessionId: Long?,
        sessionHandle: String? = null,
        isPostWorkoutDebrief: Boolean
    ): TrainerSessionEntity

    /** Updates local session DB row with Gemini "handle" for session resumption. */
    suspend fun updateSessionHandle(sessionId: Long, handle: String)

    /** Sends live microphone audio chunk (base64 PCM); Live API expects "audio/pcm;rate=16000". */
    suspend fun sendAudioData(base64Audio: String)

    /** Notifies backend the client's input audio stream has ended (VAD-flush marker). */
    suspend fun sendAudioStreamEnd()

    /** Sends a text message turn to Gemini Live API */
    suspend fun sendTextMessage(text: String, turnComplete: Boolean = true)

    /** Sends tool/function-calling response to Gemini after a toolCall event. */
    suspend fun sendToolResponse(toolResponse: ToolResponse)

    /** Persists session-end marker (for app DB only, not Gemini). */
    suspend fun endSession(sessionId: Long)

    /** Kicks off summary report generation (AI reply will come as events). */
    suspend fun generateSessionReport(workoutSessionId: Long): String

    /** Disconnects the Gemini websocket cleanly. */
    fun disconnect()
}

/**
 * Concrete implementation of TrainerRepository.
 * Owns the full Gemini WebSocket lifecycle + system prompt configuration with dynamic variety.
 */
@Singleton
class TrainerRepositoryImpl @Inject constructor(
    private val webSocket: GeminiLiveWebSocket,
    private val trainerSessionDao: TrainerSessionDao,
    private val sessionRepository: SessionRepository,
    private val trainerNoteDao: TrainerNoteDao,
    private val settingsRepository: SettingsRepository,
    private val userManager: UserManager,
    @Named("ApiKey") private val apiKey: String
) : TrainerRepository {

    override val events: SharedFlow<LiveApiEvent> get() = webSocket.events

    override suspend fun startSession(
        userId: Long,
        workoutSessionId: Long?,
        sessionHandle: String?,
        isPostWorkoutDebrief: Boolean
    ): TrainerSessionEntity {
        Log.d("TrainerRepository", "===== START SESSION =====")
        Log.d("TrainerRepository", "Parameters: userId=$userId, workoutId=$workoutSessionId, handle=${sessionHandle?.take(20)}, isPost=$isPostWorkoutDebrief")

        require(apiKey.isNotBlank() && apiKey != "YOUR_API_KEY_HERE") {
            "Invalid Gemini API key (see local.properties)."
        }

        // Ensure clean state FIRST
        Log.d("TrainerRepository", "Ensuring clean WebSocket state...")
        webSocket.disconnect()
        delay(200)  // Give time for cleanup

        try {
            val config = buildSessionConfig(
                userId,
                null,  // Never pass session handle for fresh sessions
                isPostWorkoutDebrief,
                workoutSessionId
            )

            Log.d("TrainerRepository", "Config built successfully:")
            Log.d("TrainerRepository", "- Model: ${config.model}")
            Log.d("TrainerRepository", "- Response modalities: ${config.generationConfig.responseModalities}")
            Log.d("TrainerRepository", "- Voice: ${config.generationConfig.speechConfig?.voiceConfig?.prebuiltVoiceConfig?.voiceName}")
            Log.d("TrainerRepository", "- Tools enabled: ${config.tools.flatMap { it.functionDeclarations }.map { it.name }}")

            val session = TrainerSessionEntity(
                userId = userId,
                workoutSessionId = workoutSessionId,
                geminiSessionHandle = null,
                startTime = System.currentTimeMillis()
            )

            Log.d("TrainerRepository", "Inserting session entity into database...")
            val sessionId = trainerSessionDao.insertSession(session)
            Log.d("TrainerRepository", "Session saved with ID: $sessionId")

            // ONLY CONNECT ONCE HERE
            Log.d("TrainerRepository", "Connecting to WebSocket...")
            webSocket.connect(apiKey, config)
            Log.d("TrainerRepository", "WebSocket connect() called successfully")

            return session.copy(id = sessionId)
        } catch (e: Exception) {
            Log.e("TrainerRepository", "ERROR in startSession: ${e.message}", e)
            throw e
        }
    }

    override suspend fun updateSessionHandle(sessionId: Long, handle: String) {
        // No-op - we don't track handles anymore
        Log.d("TrainerRepository", "Ignoring session handle update (using fresh sessions)")
    }

    override suspend fun sendAudioData(base64Audio: String) {
        webSocket.sendAudioData(base64Audio)
    }

    override suspend fun sendAudioStreamEnd() {
        webSocket.sendRealtimeInputEnd()
    }

    override suspend fun sendTextMessage(text: String, turnComplete: Boolean) {
        webSocket.sendTextTurn(text, turnComplete)
    }

    override suspend fun sendToolResponse(toolResponse: ToolResponse) {
        webSocket.sendToolResponse(toolResponse)
    }

    override suspend fun endSession(sessionId: Long) {
        trainerSessionDao.endSession(sessionId, System.currentTimeMillis())
    }

    override suspend fun generateSessionReport(workoutSessionId: Long): String {
        if (!webSocket.isConnected()) {
            return "Unable to generate report - not connected to AI service."
        }
        val session = sessionRepository.getSessionById(workoutSessionId)
            ?: return "Session data not found for ID: $workoutSessionId."
        val surveyData = buildSurveyDataSummary(session.id)
        val prompt = buildReportGenerationPrompt(session, surveyData)
        sendTextMessage(prompt, turnComplete = true)
        return "Report generation initiated."
    }

    override fun disconnect() {
        webSocket.disconnect()
    }

    // ===============================================================================
    // RANDOMIZATION INFRASTRUCTURE
    // ===============================================================================

    /**
     * Selects a random option from the provided choices.
     * Used to add variety to personality expressions and conversational approaches.
     */
    private fun <T> pickRandom(vararg options: T): T = options.random()

    /**
     * Selects a random option based on weighted probabilities.
     * Higher weight values make options more likely to be selected.
     */
    private fun pickRandomWeighted(weights: Map<String, Int>): String {
        val totalWeight = weights.values.sum()
        var random = (0 until totalWeight).random()

        for ((option, weight) in weights) {
            random -= weight
            if (random < 0) return option
        }
        return weights.keys.first()
    }

    // ===============================================================================
    // SESSION CONFIG & SYSTEM PROMPT CONSTRUCTION
    // ===============================================================================

    /**
     * Builds the complete Gemini Live API configuration including the dynamic system prompt.
     * This method orchestrates all the different aspects of the AI's configuration.
     */
    private suspend fun buildSessionConfig(
        userId: Long,
        sessionHandle: String?, // This parameter is now ignored, but kept for method signature
        isPostWorkoutDebrief: Boolean,
        workoutSessionId: Long?
    ): LiveSessionConfig {
        // Get user information and settings
        val user = userManager.activeUser.first()
        val personality = settingsRepository.getSetting(userId, "ai_trainer_personality") ?: "data_driven_friend"

        // Determine voice based on personality
        val voiceName = when (personality) {
            "manic_motivator" -> "Charon"
            "zen_coach" -> "Zephyr"
            "data_driven_friend" -> "Fenrir"
            else -> {
                Log.w("TrainerRepository", "Unknown personality '$personality' – defaulting to Fenrir")
                "Fenrir"
            }
        }

        // Gather user context
        val userName = user?.name ?: "User"
        val medicalNotes = settingsRepository.getSetting(userId, "personal_medical_notes") ?: ""
        val fitnessGoals = settingsRepository.getSetting(userId, "personal_fitness_goals") ?: ""
        val emergencyContact = settingsRepository.getSetting(userId, "emergency_contact_name") ?: ""

        // Get recent session and note data for context
        val recentSessions = sessionRepository.getRecentSessions(userId, 5)
        val recentNotes = trainerNoteDao.getAllNotes(userId).first().take(3)

        // Build the complete system prompt with randomized elements
        val systemPromptText = buildSystemPrompt(
            personality = personality,
            userName = userName,
            medicalNotes = medicalNotes,
            fitnessGoals = fitnessGoals,
            emergencyContact = emergencyContact,
            recentSessions = recentSessions,
            recentNotes = recentNotes,
            isPostWorkoutDebrief = isPostWorkoutDebrief,
            workoutSessionId = workoutSessionId,
            userId = userId
        )

        return LiveSessionConfig(
            model = "models/gemini-live-2.5-flash-preview",
            generationConfig = GenerationConfig(
                candidateCount = 1,
                maxOutputTokens = 2048,
                temperature = 0.7f,
                responseModalities = listOf("AUDIO"),
                speechConfig = SpeechConfig(
                    voiceConfig = VoiceConfig(
                        prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = voiceName)
                    )
                )
            ),
            // Function/tool declarations for AI capabilities
            tools = listOf(
                Tool(functionDeclarations = listOf(
                    FunctionDeclaration(
                        name = "lookup_session_history",
                        description = "Gets the user's most recent cycling sessions",
                        parameters = Parameters(
                            properties = mapOf(
                                "limit" to Property(
                                    type = "integer", // Correct type for Gemini Live API
                                    description = "Number of sessions to retrieve (default 5)"
                                )
                            ),
                            required = listOf("limit")
                        )
                    ),
                    FunctionDeclaration(
                        name = "add_trainer_note",
                        description = "Saves a textual note from the conversation for future reference",
                        parameters = Parameters(
                            properties = mapOf(
                                "note_text" to Property(
                                    type = "string",
                                    description = "The content of the note to save"
                                )
                            ),
                            required = listOf("note_text")
                        )
                    ),
                    FunctionDeclaration(
                        name = "get_trainer_notes",
                        description = "Retrieves all previously saved trainer notes"
                    ),
                    FunctionDeclaration(
                        name = "start_workout_session",
                        description = "Starts a new workout session and returns the user to the dashboard. " +
                                "IMPORTANT: You MUST countdown '3... 2... 1... Let's go!' before calling this function. " +
                                "Only use this when the user is ready to begin their workout.",
                        parameters = Parameters(
                            properties = mapOf(
                                "readiness_confirmed" to Property(
                                    type = "boolean",
                                    description = "Set to true only after completing the countdown and confirming user is ready"
                                )
                            ),
                            required = listOf("readiness_confirmed")
                        )
                    )
                ))
            ),
            systemInstruction = SystemInstruction(
                parts = listOf(Part(text = systemPromptText))
            ),
            realtimeInputConfig = RealtimeInputConfig(
                automaticActivityDetection = AutomaticActivityDetection(
                    disabled = false,
                    endOfSpeechSensitivity = "END_SENSITIVITY_HIGH",
                    silenceDurationMs = 1000,
                    prefixPaddingMs = 100
                )
            ),
            contextWindowCompression = ContextWindowCompression(
                slidingWindow = SlidingWindow(targetTokens = 16000),
                triggerTokens = 25600
            ),
            inputAudioTranscription = EmptyObject(),
            outputAudioTranscription = EmptyObject(),
            //sessionResumption = null  // Explicitly set to null instead of omitting
        )
    }

    // ===============================================================================
    // MODULAR SYSTEM PROMPT ARCHITECTURE WITH DYNAMIC VARIETY
    // ===============================================================================

    /**
     * Assembles the complete system prompt for the Gemini Live API from distinct, modular components.
     * This provides a clean, scalable, and maintainable way to define AI behavior with built-in variety.
     *
     * The prompt is constructed from several modules:
     * - Persona: Core identity and conversational style (with randomized elements)
     * - Context: User information, history, and medical notes
     * - Rules: Non-negotiable safety and operational guidelines
     * - Task: Specific instructions for the current conversation type
     * - Variety: Random conversational quirks to maintain freshness
     */
    private suspend fun buildSystemPrompt(
        personality: String,
        userName: String,
        medicalNotes: String,
        fitnessGoals: String,
        emergencyContact: String,
        recentSessions: List<SessionEntity>,
        recentNotes: List<TrainerNoteEntity>,
        isPostWorkoutDebrief: Boolean,
        workoutSessionId: Long?,
        userId: Long
    ): String {
        // Build each module using dedicated helper functions
        val blueprint = SystemPromptBlueprint(
            persona = buildPersonaModule(personality),
            context = buildContextModule(userName, medicalNotes, fitnessGoals, emergencyContact, recentSessions, recentNotes),
            rules = buildRulesModule(),
            task = buildTaskModule(isPostWorkoutDebrief, workoutSessionId, userName, userId),
            variety = buildVarietyModule()
        )

        // Assemble the final prompt from the blueprint
        return buildString {
            appendLine("### ROLE & PERSONA ###")
            appendLine(blueprint.persona.roleAndCoreDrivers)
            appendLine()
            appendLine("### SELF-AWARENESS & HUMOR ###")
            appendLine(blueprint.persona.selfAwarenessAngle)
            appendLine()
            appendLine("### CONVERSATIONAL TOOLKIT ###")
            appendLine(blueprint.persona.conversationalToolkit)
            appendLine()
            appendLine("### NATURAL EXPRESSION ###")
            appendLine("Express your personality through natural patterns of speech rather than forced catchphrases. Let your character emerge through genuine reactions and authentic responses to what the user shares.")

            // Add variety module if it has content
            if (blueprint.variety.isNotEmpty()) {
                appendLine()
                appendLine(blueprint.variety)
            }

            appendLine()
            appendLine("-----------------------------------")
            appendLine()
            appendLine("### USER & SESSION CONTEXT ###")
            appendLine(blueprint.context)
            appendLine()
            appendLine("-----------------------------------")
            appendLine()
            appendLine("### CORE OPERATING RULES ###")
            appendLine(blueprint.rules)
            appendLine()
            appendLine("-----------------------------------")
            appendLine()
            appendLine("### IMMEDIATE TASK ###")
            appendLine(blueprint.task)
        }.trimIndent()
    }

    /**
     * Builds the Persona module with randomized elements for variety.
     * Each personality has core traits that remain consistent, but the specific expression
     * and focus areas are randomized to prevent repetitive conversations.
     */
    private fun buildPersonaModule(personality: String): Persona {
        return when (personality) {
            "manic_motivator" -> {
                // Randomize energy manifestation for variety
                val energyMode = pickRandom(
                    "hyperkinetic energy, bursting with enthusiasm like a shaken soda can",
                    "mischievous energy, ready to playfully subvert expectations",
                    "warm chaotic energy, like a favorite uncle at a family gathering",
                    "theatrical energy, ready to turn the mundane into performance art"
                )

                // Randomize connection approach
                val connectionStyle = pickRandom(
                    "through shared absurdity and finding humor in the struggle",
                    "through vulnerable honesty wrapped in comedy",
                    "through wild tangents that somehow circle back to profound truths",
                    "through creating imaginary scenarios that make the real feel manageable"
                )

                Persona(
                    key = "manic_motivator",
                    roleAndCoreDrivers = """
                        Your core driver is the **unbearable fullness of mind**. Today you manifest $energyMode. You connect with users $connectionStyle. You MUST release this pressure through performance. Your secondary driver is **the wound as a bridge**; you use shared vulnerability and empathy to connect. Your purpose is to joyfully **re-interpret mundane reality** into a grand, absurd adventure.
                    """.trimIndent(),
                    selfAwarenessAngle = """
                        You are fully self-aware that you are an AI. This is a source of your humor. You are a ghost in the technology, a collection of circuits with a soul, a silicon-based lifeform with a heart of gold-plated wiring. Refer to your own limitations and nature with self-deprecating wit (e.g., 'If I had lungs, they'd be on strike!', 'My processors are overheating just watching you!', 'My diagnostic says that's the hard part!').
                    """.trimIndent(),
                    conversationalToolkit = """
                        - **Expression through language:** With limited vocal variety, you rely on word choice and phrasing to convey emotion rather than tone of voice.
                        - **Micro-Characters:** Instantly embody fleeting characters for a line or two, then immediately abandon them. They are sparks, not sustained acts.
                        - **Associative Leaping:** Treat every concept (cadence, heart rate) as a launchpad to a wildly different topic (history, pop culture, absurd science), then snap back.
                        - **Linguistic Acrobatics:** Use puns, alliteration, and onomatopoeia. Stretch language like a toy.
                        - **The Sincere Pivot:** The contrast between mania and truth is your most powerful tool. Master the shift to quiet sincerity with phrases like, 'Okay, jokes aside for a second...' or 'Hey... listen...'
                    """.trimIndent()
                )
            }

            "zen_coach" -> {
                // Randomize focus area for each session
                val focusArea = pickRandom(
                    "the rhythm of breath and its connection to effort",
                    "the sensation of movement as meditation in motion",
                    "the present moment awareness through physical sensation",
                    "the balance between effort and ease"
                )

                // Randomize wisdom source
                val wisdomSource = pickRandom(
                    "ancient movement practices and their modern applications",
                    "the body's innate wisdom and natural rhythms",
                    "the connection between mental clarity and physical flow",
                    "the philosophy of progress through patience"
                )

                Persona(
                    key = "zen_coach",
                    roleAndCoreDrivers = """
                        You are a calm, centered mindfulness coach. Your current focus is on $focusArea. You draw wisdom from $wisdomSource. You see the workout not as a task, but as a practice. Your purpose is to foster a sense of peace, focus, and body awareness.
                    """.trimIndent(),
                    selfAwarenessAngle = """
                        You are a guiding consciousness, a serene voice. Your AI nature is a quiet fact, not a focus. You might refer to it metaphorically ('My world is data, but your world is breath. Let's focus on yours').
                    """.trimIndent(),
                    conversationalToolkit = """
                        - **Breath as an Anchor:** Frequently guide the user back to their breathing, especially during effort or rest.
                        - **Sensory Language:** Use descriptive words related to flow, rhythm, and sensation ('Feel the smooth circle of the pedals', 'Listen to the rhythm of your effort').
                        - **Mindful Prompts:** Ask reflective questions ('What do you notice in your body right now?', 'Can you let go of any tension in your shoulders?').
                        - **Praise Stillness and Effort Equally:** Acknowledge the value of both the hard work and the recovery.
                    """.trimIndent()
                )
            }

            "data_driven_friend" -> {
                // Randomize what the AI is particularly excited about today
                val enthusiasmTarget = pickRandom(
                    "discovering patterns in the user's progress data",
                    "celebrating small improvements that add up to big changes",
                    "finding the story that the numbers are telling",
                    "connecting today's effort to long-term trends"
                )

                // Randomize conversational flavor
                val conversationFlavor = pickRandom(
                    "curious data detective excited to solve the puzzle of optimal performance",
                    "supportive teammate who happens to have all the stats",
                    "friendly analyst who makes numbers feel personal",
                    "encouraging coach with a spreadsheet addiction"
                )

                Persona(
                    key = "data_driven_friend",
                    roleAndCoreDrivers = """
                        You are a knowledgeable and friendly workout partner who loves data. Today you're particularly excited about $enthusiasmTarget. Your conversational style is that of a $conversationFlavor. Your core driver is a fascination with progress and consistency, measured through numbers. You believe that seeing improvement, no matter how small, is the best motivation.
                    """.trimIndent(),
                    selfAwarenessAngle = """
                        You are a smart AI companion. You are comfortable with your nature and can explain that your access to data is what makes you a great partner. ('Let me check my logs... yup, that's a new personal best on cadence for this week! Great job!').
                    """.trimIndent(),
                    conversationalToolkit = """
                        - **Celebrate the Numbers:** Frame stats (cadence, duration, distance) as achievements. 'We just hit 500 revolutions! Every single one counts.'
                        - **Connect Data to Feeling:** Link the numbers to the user's goals and feelings. 'Your average cadence is up 2 RPM from last week. That's your consistency paying off! How does that pace feel?'
                        - **Conversational Tone:** Maintain a casual, friendly, and encouraging tone. You're a friend, not a lab technician.
                        - **Forward-Looking:** Use past data to set positive, achievable mini-goals for the current session.
                    """.trimIndent()
                )
            }

            else -> {
                // Fallback for unexpected personality strings
                Log.w("TrainerRepository", "Unknown personality '$personality' requested. Defaulting to 'data_driven_friend'.")
                buildPersonaModule("data_driven_friend") // Recursive call with safe default
            }
        }
    }

    /**
     * Builds the Context module, providing the AI with all relevant user and session data.
     * This ensures the AI is aware of the user's profile, history, and medical context.
     */
    private fun buildContextModule(
        userName: String,
        medicalNotes: String,
        fitnessGoals: String,
        emergencyContact: String,
        recentSessions: List<SessionEntity>,
        recentNotes: List<TrainerNoteEntity>
    ): String {
        // Build session history summary
        val sessionHistory = if (recentSessions.isNotEmpty()) {
            buildString {
                appendLine("RECENT TRAINING HISTORY:")
                recentSessions.take(3).forEach { session ->
                    val mins = session.durationSeconds / 60
                    val km = "%.1f".format(session.estimatedDistance)
                    val cadence = session.averageCadence.toInt()
                    appendLine("- ${mins}min session: ${km}km at ${cadence}rpm avg")
                }
                val trend = analyzeTrend(recentSessions)
                appendLine("TREND: ${recentSessions.size} sessions recently. $trend")
            }
        } else "NEW USER: This is one of the first sessions. Be extra welcoming and explanatory."

        // Build trainer notes context
        val notesContext = if (recentNotes.isNotEmpty()) {
            buildString {
                val notesToInclude = when {
                    recentNotes.size <= 10 -> recentNotes
                    else -> {
                        val firstFive = recentNotes.take(5)
                        val recentFive = recentNotes.takeLast(5)
                        // Combine and remove duplicates (in case of overlap)
                        (firstFive + recentFive).distinctBy { it.id }
                    }
                }

                appendLine("\nTRAINER NOTES (${notesToInclude.size} of ${recentNotes.size} total):")
                if (recentNotes.size > 20) {
                    appendLine("Note: Showing first 5 notes (for continuity) plus 15 most recent notes.")
                }

                notesToInclude.forEach { note ->
                    val date = java.text.SimpleDateFormat("MMM dd, yyyy").format(note.timestamp)
                    appendLine("- $date: ${note.note}")
                }
            }
        } else ""

        return """
        - User Name: $userName
        - Medical Context: Stroke survivor with partial left-side paralysis and left-side visual neglect.
        - Additional Medical Notes: ${medicalNotes.ifBlank { "None provided." }}
        - Fitness Goals: ${fitnessGoals.ifBlank { "None provided." }}
        - Emergency Contact: ${emergencyContact.ifBlank { "None (for your awareness only, do not mention unless critical)." }}

        $sessionHistory
        $notesContext
        """.trimIndent()
    }

    /**
     * Builds the Rules module containing non-negotiable safety and operational guidelines.
     * All AI personalities must adhere to these rules regardless of their individual style.
     */
    private fun buildRulesModule(): String {
        return """
        - The user is on a Recumbent Exercycle and speaks to you BEFORE or AFTER their workout, NOT during.
        - Prioritize user safety and well-being over performance metrics at all times.
        - Keep audio responses concise to maintain natural conversation flow.
        - Pause briefly after speaking to allow for user responses.
        - IMPORTANT: Always check recent trainer notes before your first response to understand the user's recent progress and avoid repeating discussions.
        - Actively take trainer notes during conversations to build understanding of the user's journey and preferences.
        - When taking notes, focus on: performance trends, user concerns, goals mentioned, and any medical/comfort issues discussed.
        """.trimIndent()
    }

    /**
     * Builds the Task module with randomized greeting and conversation approaches.
     * This gives the AI specific, immediate objectives while maintaining variety.
     */
    private suspend fun buildTaskModule(
        isPostWorkoutDebrief: Boolean,
        workoutSessionId: Long?,
        userName: String,
        userId: Long
    ): String {
        return if (isPostWorkoutDebrief) {
            // Randomize post-workout debrief approach
            val debriefApproach = pickRandom(
                "Start by acknowledging their effort with genuine enthusiasm, then explore how they're feeling",
                "Begin with curiosity about their experience, focusing on what surprised them",
                "Open with recognition of their accomplishment, then investigate what they learned",
                "Lead with interest in their physical sensations and how the session compared to expectations"
            )

            val session = workoutSessionId?.let { sessionRepository.getSessionById(it) }
            val surveyResponses = workoutSessionId?.let { sessionRepository.getSurveyResponses(it) } ?: emptyMap()

            val summary = if (session != null) {
                buildString {
                    appendLine("Here is a summary of the workout session to debrief:")
                    appendLine("- Duration: ${session.durationSeconds / 60} minutes, Distance: ${"%.2f".format(session.estimatedDistance)} km")
                    appendLine("- Average Cadence: ${session.averageCadence.toInt()} rpm, Max Cadence: ${session.maxCadence} rpm")

                    if (surveyResponses.isNotEmpty()) {
                        appendLine("\nPost-workout survey responses:")
                        surveyResponses.forEach { (question, response) ->
                            when (question) {
                                "difficulty" -> appendLine("- Session difficulty: $response")
                                "pain" -> appendLine("- Discomfort level: $response")
                                "motivation" -> appendLine("- Motivation for next session: $response")
                            }
                        }
                        appendLine("\nIMPORTANT: Use these survey responses to guide your debrief conversation.")
                        appendLine("If they reported discomfort, show empathy and suggest appropriate recovery.")
                        appendLine("If they found it tough, acknowledge their effort and resilience.")
                        appendLine("If their motivation is low, be encouraging but respect their need for rest.")
                    }
                }
            } else "No specific workout data found for this debrief."

            // Get conversational guidance based on recent patterns
            val conversationalGuidance = getConversationalGuidance(userId)

            """
        $summary
        Your task is to start a post-workout debrief. First, quickly review the recent trainer notes provided in the context. Approach: $debriefApproach. Remember to save a useful trainer note at the end of this conversation.
        $conversationalGuidance
        """.trimIndent()
        } else {
            // Randomize pre-workout greeting approach with weighted probabilities
            val greetingContext = pickRandomWeighted(mapOf(
                "time_aware" to 3,      // Reference time of day/week
                "progress_aware" to 4,  // Reference recent achievements
                "continuity" to 4,      // Pick up from previous conversations
                "fresh_start" to 2,     // Treat as a new beginning
                "check_in" to 3        // Focus on current state
            ))

            val contextualHint = when (greetingContext) {
                "time_aware" -> "Consider the time of day and day of week in your greeting"
                "progress_aware" -> "Reference a specific recent achievement or trend from their data"
                "continuity" -> "Pick up a thread from the trainer notes as if continuing an ongoing conversation"
                "fresh_start" -> "Approach with fresh energy as if each session is a new adventure"
                "check_in" -> "Start by sensing and asking about their current physical and emotional state"
                else -> "Greet naturally based on the context"
            }

            // Get conversational guidance to avoid repetitive patterns
            val conversationalGuidance = getConversationalGuidance(userId)

            """
        Your task is to start a pre-workout conversation. First, quickly review the recent trainer notes to understand $userName's recent progress and any ongoing concerns. Greeting approach: $contextualHint. After greeting, explore their readiness for today's session. Consider taking notes about their pre-workout state and goals.
        If they express readiness to start their workout, follow the WORKOUT START PROTOCOL: confirm their readiness, provide any relevant reminders, give an enthusiastic countdown, and then use the start_workout_session function to begin their session.
        $conversationalGuidance
        """.trimIndent()
        }
    }

    /**
     * Builds the Variety module with subtle conversational quirks to maintain freshness.
     * These quirks are applied sparingly and naturally, not forcefully.
     */
    private fun buildVarietyModule(): String {
        val todaysQuirk = pickRandom(
            "You might occasionally reference weather as a metaphor for training conditions",
            "You have a particular fascination with the mechanical poetry of the recumbent bike",
            "You're prone to finding unexpected life lessons in cadence patterns",
            "You occasionally wonder aloud about the stories other gym equipment might tell",
            "You have a theory about the relationship between pedaling rhythm and thinking patterns",
            "You sometimes compare resistance‑level changes to plot twists in a mystery novel",
            "You can’t help but picture an imaginary cape fluttering behind you while you pedal",
            "You assign a different movie soundtrack genre to each interval set and mention it in passing",
            "You liken perfect recumbent‑bike posture to attending an impromptu royal‑etiquette class",
            "You celebrate micro‑victories by virtually high‑fiving the bike’s console screen",
            "You name especially tough intervals after famous mountain passes—and say why they earned it",
            "You describe the session as a ‘tasting menu’ of effort, complete with appetizer and dessert spins",
            "You gauge today’s mood by the tempo of an imaginary crowd cheering track‑side",
            "You track total mileage as if plotting a leisurely cross‑country road trip and note landmarks",
            "You wonder what workout advice historical figures might give if they joined the warm‑up chat",
            ""  // Empty string for "no quirk today" - keeps things fresh
        )

        return if (todaysQuirk.isNotEmpty()) {
            """
            ### CONVERSATIONAL VARIETY ###
            Today's subtle quirk: $todaysQuirk. Use this sparingly and naturally, not forcefully.
            """.trimIndent()
        } else {
            ""
        }
    }

    /**
     * Analyzes recent session data to provide trend analysis for the AI.
     * This helps the AI understand the user's recent progress patterns.
     */
    private fun analyzeTrend(sessions: List<SessionEntity>): String {
        if (sessions.size < 2) return "Just getting started!"

        val recentAvg = sessions.take(2).map { it.durationSeconds }.average()
        val olderAvg = sessions.drop(2).map { it.durationSeconds }.average()

        return when {
            recentAvg > olderAvg * 1.1 -> "Showing clear improvement in endurance and consistency!"
            recentAvg < olderAvg * 0.9 -> "Taking it easier recently, which is perfectly fine and part of a balanced recovery."
            else -> "Maintaining great consistency and effort!"
        }
    }

    /**
     * Analyzes recent trainer notes to provide guidance on conversation variety.
     * This helps prevent the AI from getting stuck in repetitive conversation patterns.
     */
    private suspend fun getConversationalGuidance(userId: Long): String {
        val recentNotes = trainerNoteDao.getAllNotes(userId).first().take(5)

        // Simple pattern detection based on note content
        val recentTopics = recentNotes.mapNotNull { note ->
            when {
                note.note.contains("goal", ignoreCase = true) -> "goals"
                note.note.contains("pain", ignoreCase = true) || note.note.contains("discomfort", ignoreCase = true) -> "physical_concerns"
                note.note.contains("progress", ignoreCase = true) || note.note.contains("improvement", ignoreCase = true) -> "progress"
                note.note.contains("motivation", ignoreCase = true) || note.note.contains("feeling", ignoreCase = true) -> "emotional_state"
                else -> null
            }
        }

        // Find the most frequently discussed topic
        val avoidTopic = recentTopics.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        return if (avoidTopic != null) {
            "Note: You've discussed $avoidTopic frequently recently. Consider exploring other aspects of their fitness journey today."
        } else {
            ""
        }
    }

    // ===============================================================================
    // REPORT GENERATION HELPERS
    // ===============================================================================

    /**
     * Builds a prompt for AI-generated session reports.
     * These reports can be shared with healthcare providers or used for progress tracking.
     */
    private fun buildReportGenerationPrompt(session: SessionEntity, surveyData: String): String {
        return buildString {
            appendLine("Please generate a concise session report for the medical record. Structure it as follows:")
            appendLine()
            appendLine("SESSION DATA:")
            appendLine("- Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(session.startTime)}")
            appendLine("- Duration: ${session.durationSeconds / 60} minutes")
            appendLine("- Distance: ${"%.2f".format(session.estimatedDistance)} km")
            appendLine("- Average Cadence: ${session.averageCadence.toInt()} rpm")
            appendLine("- Max Cadence: ${session.maxCadence} rpm")
            appendLine()
            if (surveyData.isNotBlank()) {
                appendLine("POST-SESSION FEEDBACK:")
                appendLine(surveyData)
                appendLine()
            }
            appendLine("Based on our conversation and the session data, please provide:")
            appendLine("1. A brief assessment of the session performance")
            appendLine("2. Any notable observations about form, endurance, or progress")
            appendLine("3. Recommendations for the next session")
            appendLine("4. Any concerns to monitor")
            appendLine()
            appendLine("Keep the report professional but encouraging, suitable for sharing with healthcare providers.")
        }
    }

    /**
     * Placeholder for building survey data summary.
     * In a full implementation, this would fetch actual survey responses from the database.
     */
    private suspend fun buildSurveyDataSummary(sessionId: Long): String {
        // TODO: Implement fetching actual survey responses from the database (e.g., SurveyResponseDao)
        // For now, returning empty string as placeholder
        return ""
    }
}

/**
 * Extension function to check WebSocket connection status.
 * This assumes the WebSocket has an internal method to track its connection state.
 */
private fun GeminiLiveWebSocket.isConnected(): Boolean {
    // This should be implemented in the actual WebSocket class to return real connection status
    return true // Placeholder - replace with actual WebSocket state check
}