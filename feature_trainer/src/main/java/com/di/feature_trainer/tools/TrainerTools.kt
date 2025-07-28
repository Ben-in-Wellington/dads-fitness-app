// TrainerTools.kt

package com.di.feature_trainer.tools

import android.content.Context
import android.content.Intent
import com.di.core.data.SessionRepository
import com.di.core.data.UserManager
import com.di.core.data.database.SessionEntity
import com.di.core.data.database.TrainerNoteDao
import com.di.core.data.database.TrainerNoteEntity
import com.di.feature_trainer.data.models.* // Ensure all necessary data models for Live API tool communication are imported
import dagger.hilt.android.qualifiers.ApplicationContext // For safely injecting application context
import kotlinx.coroutines.flow.first // Used to collect the first emitted value from a Flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat // For date and time formatting
import java.util.* // For Locale and Date objects
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt // For rounding Double to Int

/**
 * A singleton class responsible for executing various "tools" (functions)
 * requested by the Gemini Live API.
 *
 * This class acts as an intermediary, translating AI's function call requests
 * into specific actions within the Android application's domain, such as
 * accessing user data, saving notes, or interacting with system intents (like sending emails).
 *
 * It receives [ToolCall] objects from the API, dispatches them to the appropriate
 * internal handler function, and constructs a [ToolResponse] to send back to the API with the results.
 *
 * @param sessionRepository Repository for accessing user workout session data.
 * @param trainerNoteDao DAO for interacting with trainer-specific notes in the local database.
 * @param userManager Manager for accessing the currently active user's information.
 * @param context Application context, safely injected using Hilt's @ApplicationContext,
 *                necessary for launching intents (e.g., email client).
 */
@Singleton
class TrainerTools @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val trainerNoteDao: TrainerNoteDao,
    private val userManager: UserManager,
    @ApplicationContext private val context: Context
) {
    // Date formatters for consistent output in tool responses and email bodies.
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    /**
     * Executes one or more function calls requested by the Gemini Live API.
     * This is the main entry point for the AI's tool requests.
     *
     * It iterates through the list of function calls within the [toolCall] object,
     * dispatches each call to its corresponding handler function based on `call.name`,
     * and collects their responses into a list of [FunctionResponse] objects.
     *
     * @param toolCall The [ToolCall] object containing one or more function calls
     *                 from the Gemini Live API.
     * @return A [ToolResponse] containing a list of [FunctionResponse] objects,
     *         each corresponding to an executed function call.
     */
    suspend fun executeTool(toolCall: ToolCall): ToolResponse {
        // Process each function call requested by the model.
        val functionResponses = toolCall.functionCalls.map { call ->
            when (call.name) {
                "lookup_session_history" -> lookupSessionHistory(call)
                "add_trainer_note" -> addTrainerNote(call)
                "get_trainer_notes" -> getTrainerNotes(call)
                "start_workout_session" -> startWorkoutSession(call)  // Add this
                else -> errorResponse(call.id, "Unknown function: ${call.name}")
            }
        }
        return ToolResponse(functionResponses = functionResponses)
    }

    /**
     * Handles the "lookup_session_history" function call.
     * Retrieves the user's most recent cycling session statistics from the database.
     *
     * @param call The specific [FunctionCall] for "lookup_session_history", potentially
     *             containing a 'limit' argument to specify the number of sessions to retrieve.
     * @return A [FunctionResponse] with session data in JSON format, or an error if
     *         no active user is found.
     */
    private suspend fun lookupSessionHistory(call: FunctionCall): FunctionResponse {
        // Ensure an active user is available to fetch session data.
        val userId = userManager.activeUser.first()?.id
            ?: return errorResponse(call.id, "No active user found to lookup session history.")

        // Parse the 'limit' argument from the function call arguments, defaulting to 5 sessions.
        val limit = (call.args?.get("limit") as? JsonPrimitive)?.content?.toIntOrNull() ?: 5
        val sessions = sessionRepository.getRecentSessions(userId, limit = limit)

        // Build a JSON array containing summary data for each retrieved session.
        val sessionData = buildJsonArray {
            sessions.forEach { session ->
                add(buildJsonObject {
                    put("date", JsonPrimitive(formatDate(session.startTime)))
                    put("time", JsonPrimitive(formatTime(session.startTime)))
                    put("duration_minutes", JsonPrimitive(session.durationSeconds / 60))
                    put("distance_km", JsonPrimitive("%.2f".format(session.estimatedDistance)))
                    put("avg_cadence", JsonPrimitive(session.averageCadence.roundToInt()))
                    put("max_cadence", JsonPrimitive(session.maxCadence))
                    put("status", JsonPrimitive(session.status)) // Include session status
                })
            }
        }

        // Return a successful FunctionResponse with the session data and total count.
        return FunctionResponse(
            id = call.id,
            name = call.name,
            response = mapOf(
                "sessions" to sessionData, // The actual session data as a JSON array
                "total_count" to JsonPrimitive(sessions.size) // Total number of sessions returned
            )
        )
    }

    /**
     * Handles the "add_trainer_note" function call.
     * Saves a textual note provided by the AI (or explicitly from the conversation)
     * into the local database for the current user.
     *
     * @param call The specific [FunctionCall] for "add_trainer_note", containing
     *             the 'note_text' argument.
     * @return A [FunctionResponse] indicating success or failure of saving the note.
     */
    private suspend fun addTrainerNote(call: FunctionCall): FunctionResponse {
        // Ensure an active user is available to associate the note with.
        val userId = userManager.activeUser.first()?.id
            ?: return errorResponse(call.id, "No active user found to add trainer note.")

        // Extract the 'note_text' parameter from the function call arguments.
        val noteText = (call.args?.get("note_text") as? JsonPrimitive)?.content
            ?: return errorResponse(call.id, "Missing 'note_text' parameter for adding a note. Please provide the content of the note.")

        return try {
            // Create a new TrainerNoteEntity and insert it into the database.
            val note = TrainerNoteEntity(
                userId = userId,
                timestamp = System.currentTimeMillis(),
                note = noteText
            )
            trainerNoteDao.insertNote(note)
            // Return a success response.
            FunctionResponse(
                id = call.id,
                name = call.name,
                response = mapOf("result" to JsonPrimitive("Note saved successfully"))
            )
        } catch (e: Exception) {
            // Return an error response if saving fails.
            errorResponse(call.id, "Failed to save note: ${e.message}")
        }
    }

    /**
     * Handles the "get_trainer_notes" function call.
     * Retrieves all previously saved trainer notes for the current user from the database.
     *
     * @param call The specific [FunctionCall] for "get_trainer_notes". This function does not
     *             typically require arguments but accepts the [FunctionCall] object for consistency.
     * @return A [FunctionResponse] with a list of notes in JSON format, or an error if
     *         no active user is found or retrieval fails.
     */
    private suspend fun getTrainerNotes(call: FunctionCall): FunctionResponse {
        val userId = userManager.activeUser.first()?.id
            ?: return errorResponse(call.id, "No active user found to retrieve trainer notes.")

        return try {
            val allNotes = trainerNoteDao.getAllNotes(userId).first()

            val notesToReturn = when {
                allNotes.size <= 20 -> allNotes
                else -> {
                    val firstFive = allNotes.take(5)
                    val recentFifteen = allNotes.takeLast(15)
                    (firstFive + recentFifteen).distinctBy { it.id }
                }
            }

            val notesData = buildJsonArray {
                notesToReturn.forEach { note ->
                    add(buildJsonObject {
                        put("date", JsonPrimitive(formatDate(note.timestamp)))
                        put("time", JsonPrimitive(formatTime(note.timestamp)))
                        put("note", JsonPrimitive(note.note))
                        put("is_early_note", JsonPrimitive(allNotes.indexOf(note) < 5))
                    })
                }
            }

            FunctionResponse(
                id = call.id,
                name = call.name,
                response = mapOf(
                    "notes" to notesData,
                    "displayed_count" to JsonPrimitive(notesToReturn.size),
                    "total_count" to JsonPrimitive(allNotes.size),
                    "note" to JsonPrimitive(
                        if (allNotes.size > 20)
                            "Showing first 5 notes plus 15 most recent. Total notes: ${allNotes.size}"
                        else
                            "Showing all notes"
                    )
                )
            )
        } catch (e: Exception) {
            errorResponse(call.id, "Failed to retrieve notes: ${e.message}")
        }
    }

    private suspend fun startWorkoutSession(call: FunctionCall): FunctionResponse {
        val readinessConfirmed = (call.args?.get("readiness_confirmed") as? JsonPrimitive)
            ?.content?.toBooleanStrictOrNull() ?: false

        if (!readinessConfirmed) {
            return errorResponse(
                call.id,
                "Cannot start session without countdown and readiness confirmation"
            )
        }

        return FunctionResponse(
            id = call.id,
            name = call.name,
            response = mapOf(
                "result" to JsonPrimitive("start_session_requested"),
                "message" to JsonPrimitive("Starting workout session...")
            )
        )
    }

    /**
     * Handles the "send_progress_email" function call.
     * Prepares an email with a summary of the user's recent fitness sessions
     * and opens the device's default email client for the user to review and send it.
     *
     * @param call The specific [FunctionCall] for "send_progress_email". This function does not
     *             typically require arguments.
     * @return A [FunctionResponse] indicating whether the email app was successfully opened,
     *         or an error if prerequisites are not met (e.g., no active user, no sessions found).
     */
    private suspend fun sendProgressEmail(call: FunctionCall): FunctionResponse {
        // Ensure an active user is available.
        val userId = userManager.activeUser.first()?.id
            ?: return errorResponse(call.id, "No active user found to send progress email.")

        val user = userManager.activeUser.first()
            ?: return errorResponse(call.id, "No active user data found for email generation.")

        // Get recent sessions (e.g., last 7 days) to include in the report.
        val sessions = sessionRepository.getRecentSessions(userId, limit = 7)
        if (sessions.isEmpty()) {
            return errorResponse(call.id, "No sessions found to generate a progress report. Please complete some workouts first.")
        }

        val latestSession = sessions.first() // Use the most recent session for detailed overview.

        val subject = "Fitness Progress Report - ${user.name}"
        val body = buildEmailBody(user.name, sessions, latestSession)

        // Create an ACTION_SEND email intent.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822" // Standard MIME type for email.
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            // FLAG_ACTIVITY_NEW_TASK is crucial when launching an activity from a non-Activity context
            // (like a Hilt singleton `TrainerTools` class).
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            // Use createChooser to allow the user to select their preferred email app.
            // FLAG_ACTIVITY_NEW_TASK is also applied to the chooser intent.
            val chooserIntent = Intent.createChooser(intent, "Send progress report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
            // Return a success response indicating the email app was launched.
            FunctionResponse(
                id = call.id,
                name = call.name,
                response = mapOf("result" to JsonPrimitive("Email app opened successfully for sending progress report."))
            )
        } catch (e: Exception) {
            // Return an error response if launching the email app fails.
            errorResponse(call.id, "Failed to open email app: ${e.message}. Please ensure an email app is installed.")
        }
    }

    /**
     * Constructs the body of the fitness progress email.
     * This report summarizes recent session data and the latest session details.
     *
     * @param userName The name of the user for whom the report is generated.
     * @param sessions A list of recent [SessionEntity] objects to summarize total progress.
     * @param latestSession The most recent [SessionEntity] for a detailed snapshot.
     * @return A formatted string suitable for an email body.
     */
    private fun buildEmailBody(userName: String, sessions: List<SessionEntity>, latestSession: SessionEntity): String {
        val totalDistance = sessions.sumOf { it.estimatedDistance }
        val totalTime = sessions.sumOf { it.durationSeconds }
        val avgCadence = sessions.map { it.averageCadence }.average()

        return buildString {
            appendLine("Fitness Progress Report for $userName")
            appendLine("Generated on: ${formatDate(System.currentTimeMillis())}")
            appendLine()
            appendLine("--- LATEST SESSION (${formatDate(latestSession.startTime)}) ---")
            appendLine("- Duration: ${latestSession.durationSeconds / 60} minutes")
            appendLine("- Distance: ${"%.2f".format(latestSession.estimatedDistance)} km")
            appendLine("- Average Cadence: ${latestSession.averageCadence.roundToInt()} rpm")
            appendLine("- Max Cadence: ${latestSession.maxCadence} rpm")
            appendLine()
            appendLine("--- WEEKLY SUMMARY (Last ${sessions.size} sessions) ---")
            appendLine("- Total Sessions: ${sessions.size}")
            appendLine("- Total Time: ${totalTime / 60} minutes")
            appendLine("- Total Distance: ${"%.2f".format(totalDistance)} km")
            appendLine("- Average Cadence: ${avgCadence.roundToInt()} rpm")
            appendLine()
            appendLine("Keep up the great work, $userName! Your dedication is making a difference.")
            appendLine("\nBest regards,")
            appendLine("Your Fitness Assistant")
        }
    }

    /**
     * Creates a standardized [FunctionResponse] for an error scenario.
     * This helps in consistently reporting tool execution failures back to the AI model,
     * allowing it to understand why a tool call failed and potentially adjust its behavior.
     *
     * @param id The ID of the function call that failed. This ID is crucial for the AI
     *           to link the error response back to the specific tool call it made.
     * @param message A descriptive error message explaining what went wrong.
     * @return A [FunctionResponse] object indicating an error, with a specific "error" field.
     */
    private fun errorResponse(id: String, message: String): FunctionResponse {
        return FunctionResponse(
            id = id,
            name = "", // The 'name' field can be empty or set to the function name for error responses.
            response = mapOf("error" to JsonPrimitive(message)) // Use JsonPrimitive for string value
        )
    }

    /**
     * Formats a given timestamp (in milliseconds) into a "MMM dd, yyyy" date string.
     *
     * @param timestamp The timestamp in milliseconds.
     * @return A formatted date string.
     */
    private fun formatDate(timestamp: Long): String {
        return dateFormatter.format(Date(timestamp))
    }

    /**
     * Formats a given timestamp (in milliseconds) into a "h:mm a" time string.
     *
     * @param timestamp The timestamp in milliseconds.
     * @return A formatted time string.
     */
    private fun formatTime(timestamp: Long): String {
        return timeFormatter.format(Date(timestamp))
    }
}