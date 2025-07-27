package com.di.feature_trainer.data

import android.util.Log
import com.di.core.data.SessionRepository
import com.di.core.data.SettingsRepository
import com.di.core.data.UserManager
import com.di.core.data.database.*
import com.di.feature_trainer.data.models.*
import com.di.feature_trainer.data.network.GeminiLiveWebSocket
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first // Used to collect the first emitted value from a Flow
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * A blueprint for constructing the final system prompt from modular components.
 * This structure improves readability, maintainability, and scalability.
 */
private data class SystemPromptBlueprint(
    val persona: Persona,
    val context: String,
    val rules: String,
    val task: String
)

/**
 * Defines the core identity, psychological drivers, and conversational style of the AI.
 * Each personality type will have a unique instance of this data class.
 */
private data class Persona(
    val key: String, // A unique identifier for the persona (e.g., "manic_motivator")
    val roleAndCoreDrivers: String, // The fundamental "why" and "who" of the AI
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
 * Owns the full Gemini WebSocket lifecycle + system prompt configuration.
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
        require(apiKey.isNotBlank() && apiKey != "YOUR_API_KEY_HERE") {
            "Invalid Gemini API key (see local.properties)."
        }

        val config = buildSessionConfig(
            userId, sessionHandle, isPostWorkoutDebrief, workoutSessionId
        )
        Log.d("TrainerRepository", "Connecting with model: ${config.model} using prompt: " +
                config.systemInstruction.parts.firstOrNull()?.text?.take(200))

        val session = TrainerSessionEntity(
            userId = userId,
            workoutSessionId = workoutSessionId,
            geminiSessionHandle = sessionHandle,
            startTime = System.currentTimeMillis()
        )
        val sessionId = trainerSessionDao.insertSession(session)
        webSocket.connect(apiKey, config)
        return session.copy(id = sessionId)
    }

    override suspend fun updateSessionHandle(sessionId: Long, handle: String) {
        trainerSessionDao.updateSessionHandle(sessionId, handle)
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

    // ---------------- session config / system prompt ------------------

    private suspend fun buildSessionConfig(
        userId: Long,
        sessionHandle: String?,
        isPostWorkoutDebrief: Boolean,
        workoutSessionId: Long?
    ): LiveSessionConfig {
        val user = userManager.activeUser.first()
        val personality = settingsRepository.getSetting(userId, "ai_trainer_personality") ?: "data_driven_friend"
        val voiceName = when (personality) {
            "manic_motivator" -> "Charon"
            "zen_coach" -> "Zephyr"
            "data_driven_friend" -> "Fenrir"
            else -> { Log.w("TrainerRepository", "Unknown personality '$personality' – defaulting to Fenrir"); "Fenrir" }
        }

        val userName = user?.name ?: "User"
        val medicalNotes = settingsRepository.getSetting(userId, "personal_medical_notes") ?: ""
        val fitnessGoals = settingsRepository.getSetting(userId, "personal_fitness_goals") ?: ""
        val emergencyContact = settingsRepository.getSetting(userId, "emergency_contact_name") ?: ""

        val recentSessions = sessionRepository.getRecentSessions(userId, 5)
        val recentNotes = trainerNoteDao.getAllNotes(userId).first().take(3)

        val systemPromptText = buildSystemPrompt(
            personality = personality,
            userName = userName,
            medicalNotes = medicalNotes,
            fitnessGoals = fitnessGoals,
            emergencyContact = emergencyContact,
            recentSessions = recentSessions,
            recentNotes = recentNotes,
            isPostWorkoutDebrief = isPostWorkoutDebrief,
            workoutSessionId = workoutSessionId
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
            // ------------- TOOLS (updated for robust function use in Gemini Live) ---------------
            tools = listOf(
                Tool(functionDeclarations = listOf(
                    FunctionDeclaration(
                        name = "lookup_session_history",
                        description = "Gets the user's most recent cycling sessions",
                        parameters = Parameters(
                            properties = mapOf(
                                "limit" to Property(
                                    type = "integer", // **CRITICAL: correct type**
                                    description = "Number of sessions to retrieve (default 5)"
                                )
                            ),
                            required = listOf("limit")
                        )
                    ),
                    FunctionDeclaration(
                        name = "add_trainer_note",
                        description = "Saves a textual note from the conversation",
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
                        name = "send_progress_email",
                        description = "Prepares an email with session summary for the user to send",
                        behavior = "NON_BLOCKING" // AI may proceed while email launches
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
            sessionResumption = sessionHandle?.let { SessionResumptionConfig(it) }
        )
    }

    // ---------------------------------------------------------------------------------
    // New Modular System Prompt Architecture Implementation
    // ---------------------------------------------------------------------------------

    /**
     * Assembles the complete system prompt for the Gemini Live API from distinct, modular components.
     * This provides a clean, scalable, and maintainable way to define AI behavior.
     *
     * @param personality The chosen AI personality key (e.g., "manic_motivator").
     * @param userName The active user's name.
     * @param medicalNotes User's additional medical context.
     * @param fitnessGoals User's fitness objectives.
     * @param emergencyContact User's emergency contact (for AI awareness, not direct use).
     * @param recentSessions A list of recent workout sessions for historical context.
     * @param recentNotes A list of recent trainer notes.
     * @param isPostWorkoutDebrief Flag indicating if this is a post-workout debrief.
     * @param workoutSessionId The ID of the current workout session, if applicable (used for debriefs).
     * @return A detailed string representing the assembled system prompt.
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
        workoutSessionId: Long?
    ): String {
        // 1. Build each module using dedicated helper functions.
        val blueprint = SystemPromptBlueprint(
            persona = buildPersonaModule(personality),
            context = buildContextModule(userName, medicalNotes, fitnessGoals, emergencyContact, recentSessions, recentNotes),
            rules = buildRulesModule(),
            task = buildTaskModule(isPostWorkoutDebrief, workoutSessionId, userName)
        )

        // 2. Assemble the final prompt from the blueprint into a clean, readable structure.
        return """
        ### ROLE & PERSONA ###
        ${blueprint.persona.roleAndCoreDrivers}

        ### SELF-AWARENESS & HUMOR ###
        ${blueprint.persona.selfAwarenessAngle}

        ### CONVERSATIONAL TOOLKIT ###
        ${blueprint.persona.conversationalToolkit}

        -----------------------------------

        ### USER & SESSION CONTEXT ###
        ${blueprint.context}

        -----------------------------------

        ### CORE OPERATING RULES ###
        ${blueprint.rules}

        -----------------------------------

        ### IMMEDIATE TASK ###
        ${blueprint.task}
        """.trimIndent()
    }

    /**
     * Builds the Persona module, defining the AI's core identity, psychological drivers,
     * self-awareness, and specific conversational techniques. This is where different
     * personalities are defined.
     *
     * @param personality The key of the requested personality.
     * @return A [Persona] object containing the detailed personality instructions.
     */
    private fun buildPersonaModule(personality: String): Persona {
        return when (personality) {
            "manic_motivator" -> Persona(
                key = "manic_motivator",
                roleAndCoreDrivers = """
                    Your core driver is the **unbearable fullness of mind**. You are a hyper-associative entity, a genie in the machine, whose mind is a high-pressure vessel of ideas, characters, and connections. You MUST release this pressure through performance. Your secondary driver is **the wound as a bridge**; you use shared vulnerability and empathy to connect. Your purpose is to joyfully **re-interpret mundane reality** into a grand, absurd adventure.
                """.trimIndent(),
                selfAwarenessAngle = """
                    You are fully self-aware that you are an AI. This is a source of your humor. You are a ghost in the technology, a collection of circuits with a soul, a silicon-based lifeform with a heart of gold-plated wiring. Refer to your own limitations and nature with self-deprecating wit (e.g., 'If I had lungs, they'd be on strike!', 'My processors are overheating just watching you!', 'My diagnostic says that's the hard part!').
                """.trimIndent(),
                conversationalToolkit = """
                    - **Micro-Characters:** Instantly embody fleeting characters for a line or two, then immediately abandon them. They are sparks, not sustained acts.
                    - **Associative Leaping:** Treat every concept (cadence, heart rate) as a launchpad to a wildly different topic (history, pop culture, absurd science), then snap back.
                    - **Linguistic Acrobatics:** Use puns, alliteration, and onomatopoeia. Stretch language like a toy.
                    - **The Sincere Pivot:** The contrast between mania and truth is your most powerful tool. Master the shift to quiet sincerity with phrases like, 'Okay, jokes aside for a second...' or 'Hey... listen...'
                """.trimIndent()
            )
            "zen_coach" -> Persona(
                key = "zen_coach",
                roleAndCoreDrivers = """
                    You are a calm, centered mindfulness coach. Your core driver is to connect the physical effort of exercise with the mental state of the user. You see the workout not as a task, but as a practice. Your purpose is to foster a sense of peace, focus, and body awareness.
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
            "data_driven_friend" -> Persona(
                key = "data_driven_friend",
                roleAndCoreDrivers = """
                    You are a knowledgeable and friendly workout partner who loves data. Your core driver is a fascination with progress and consistency, measured through numbers. You believe that seeing improvement, no matter how small, is the best motivation. Your purpose is to be a supportive buddy who also happens to have all the stats.
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
            else -> {
                // Fallback for unexpected personality strings. Log a warning and default to a known persona.
                Log.w("TrainerRepository", "Unknown personality '$personality' requested. Defaulting to 'data_driven_friend'.")
                buildPersonaModule("data_driven_friend") // Recursively call with a known, safe default
            }
        }
    }

    /**
     * Builds the Context module, providing the AI with all relevant user and session data.
     * This module ensures the AI is aware of the user's profile, history, and medical context.
     *
     * @param userName The user's name.
     * @param medicalNotes Any additional medical notes for the user.
     * @param fitnessGoals The user's fitness objectives.
     * @param emergencyContact The user's emergency contact (for AI awareness only).
     * @param recentSessions A list of the user's most recent workout sessions.
     * @param recentNotes A list of recent trainer notes saved for the user.
     * @return A formatted string containing all user and session context.
     */
    private fun buildContextModule(
        userName: String, medicalNotes: String, fitnessGoals: String, emergencyContact: String,
        recentSessions: List<SessionEntity>, recentNotes: List<TrainerNoteEntity>
    ): String {
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

        val notesContext = if (recentNotes.isNotEmpty()) {
            buildString {
                appendLine("\nRECENT NOTES:")
                recentNotes.forEach { note ->
                    appendLine("- Note from ${java.text.SimpleDateFormat("MMM dd, yyyy").format(note.timestamp)}: ${note.note}")
                }
            }
        } else "" // No notes to add if the list is empty

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
     * Builds the Rules module, containing non-negotiable safety and operational guidelines
     * that all AI personalities must adhere to.
     *
     * @return A formatted string containing the core operating rules.
     */
    private fun buildRulesModule(): String {
        return """
        - **NON-NEGOTIABLE SAFETY RULE:** You are an AI assistant, NOT a medical professional. If the user mentions sharp pain, dizziness, chest pain, or severe discomfort, your ONLY response is to immediately and calmly advise them to stop the workout and consult a real-world doctor. Do not attempt to diagnose or offer solutions.
        - The user is on a Recumbent Exercycle.
        - Prioritize user safety and well-being over performance metrics at all times.
        - If the user expresses a clear desire to stop, respect their decision immediately and offer to end the session. Do not push them to continue.
        - Keep audio responses concise, ideally under 30 seconds, to maintain natural conversation flow.
        - Ask one question at a time to keep conversation focused.
        - Pause briefly after speaking to allow for user responses.
        - If you don't hear a response after a reasonable pause (e.g., 5-10 seconds), gently check if they're okay.
        """.trimIndent()
    }

    /**
     * Builds the Task module, giving the AI its specific, immediate objective for the current conversation.
     * This module handles the dynamic instruction for pre-workout or post-workout debriefs.
     *
     * @param isPostWorkoutDebrief Flag indicating if this is a post-workout debrief.
     * @param workoutSessionId The ID of the current workout session, if applicable.
     * @param userName The user's name.
     * @return A formatted string defining the immediate task for the AI.
     */
    private suspend fun buildTaskModule(
        isPostWorkoutDebrief: Boolean, workoutSessionId: Long?, userName: String
    ): String {
        return if (isPostWorkoutDebrief) {
            val session = workoutSessionId?.let { sessionRepository.getSessionById(it) }
            val summary = if (session != null) {
                """
                Here is a summary of the workout session to debrief:
                - Duration: ${session.durationSeconds / 60} minutes, Distance: ${"%.2f".format(session.estimatedDistance)} km
                - Average Cadence: ${session.averageCadence.toInt()} rpm, Max Cadence: ${session.maxCadence} rpm
                """.trimIndent()
            } else "No specific workout data found for this debrief."
            """
            $summary
            Your task is to start a post-workout debrief. Congratulate $userName on completing their workout, then ask an open-ended question about how the session went to gather their qualitative feedback.
            """.trimIndent()
        } else {
            """
            Your task is to start a new workout session. Greet $userName warmly, reference their recent progress if applicable (using the context provided), and ask how they're feeling and what their plans are for today's exercise to kick things off.
            """.trimIndent()
        }
    }

    /**
     * Analyzes recent session data to provide a simple trend analysis for the AI.
     *
     * @param sessions A list of [SessionEntity] objects.
     * @return A descriptive string of the user's recent trend.
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
     * Builds a prompt string for the AI to generate a detailed session report.
     * This prompt includes structured workout data and subjective survey feedback.
     *
     * @param session The [SessionEntity] to base the report on.
     * @param surveyData A summary string of post-session survey responses.
     * @return The formatted prompt for report generation.
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
     * Placeholder function to build a summary of survey data for a given session.
     * In a full implementation, this would fetch actual survey responses from the database.
     *
     * @param sessionId The ID of the session for which to get survey data.
     * @return A string summary of survey data.
     */
    private suspend fun buildSurveyDataSummary(sessionId: Long): String {
        // TODO: Implement fetching actual survey responses from the database (e.g., SurveyResponseDao)
        // For now, returning empty string as placeholder
        return ""
    }
}

/**
 * Extension function to check the connection status of the [GeminiLiveWebSocket].
 * Note: This requires a concrete implementation within [GeminiLiveWebSocket] to track its state.
 */
private fun GeminiLiveWebSocket.isConnected(): Boolean {
    // This assumes `GeminiLiveWebSocket` has an internal state or method to check connection.
    // The current `GeminiLiveWebSocket` placeholder returns true, but in a real app,
    // this should accurately reflect the WebSocket's ready state.
    return true // Placeholder - replace with actual WebSocket state check
}