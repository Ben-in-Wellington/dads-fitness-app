package com.di.core.data

import com.di.core.data.database.CadenceDataEntity
import com.di.core.data.database.SessionEntity
import com.di.core.data.database.SessionSummaryRaw
import com.di.core.data.database.TodayStats
import kotlinx.coroutines.flow.Flow

/**
 * A clean data class representing the overall session summary for the UI layer (e.g., InfoPanelViewModel).
 * This provides a stable model for the UI, decoupled from the raw database query result.
 * It includes default values for scenarios where no data exists (e.g., a new user).
 */
data class SessionSummary(
    val totalSessions: Int = 0,
    val totalDurationSeconds: Long = 0L,
    val totalDistanceKm: Double = 0.0,
    val longestSessionSeconds: Long = 0L
)

/**
 * Defines the contract for data operations related to fitness sessions.
 * This repository manages session creation, updates, completion, and retrieval,
 * as well as associated cadence data and survey responses.
 * All user-specific data access methods require a userId for proper data isolation.
 */
interface SessionRepository {

    // The erroneous 'abstract val it: Any' has been removed.

    /**
     * Creates a new fitness session entry in the database.
     * The new session is linked to a specific user.
     *
     * @param userId The ID of the user for whom the session is created.
     * @return The ID of the newly created session.
     */
    suspend fun createNewSession(userId: Long): Long

    /**
     * Finds an active (uncompleted) session for a given user.
     *
     * @param userId The ID of the user whose active session is being sought.
     * @return The [SessionEntity] if an active session is found, otherwise null.
     */
    suspend fun findActiveSession(userId: Long): SessionEntity?

    /**
     * Retrieves a specific session by its unique ID.
     * This is crucial for accessing detailed session data, e.g., for AI debriefs or reports.
     *
     * @param sessionId The unique ID of the session to retrieve.
     * @return The [SessionEntity] corresponding to the ID, or null if not found.
     */
    suspend fun getSessionById(sessionId: Long): SessionEntity?

    /**
     * Updates the duration of an active session while it is in progress.
     *
     * @param sessionId The ID of the session to update.
     * @param currentDuration The current elapsed time in seconds.
     */
    suspend fun updateActiveSessionProgress(sessionId: Long, currentDuration: Long)

    /**
     * Marks a session as completed and records its final statistics.
     *
     * @param sessionId The ID of the session to complete.
     * @param duration The total duration of the session in seconds.
     * @param distance The estimated distance covered in kilometers.
     * @param avgCadence The average cadence (RPM) during the session.
     * @param maxCadence The maximum cadence (RPM) reached during the session.
     * @param minCadence The minimum non-zero cadence (RPM) during the session.
     * @param totalRevolutions The total crank revolutions recorded.
     */
    suspend fun completeSession(
        sessionId: Long,
        duration: Long,
        distance: Double,
        avgCadence: Double,
        maxCadence: Int,
        minCadence: Int,
        totalRevolutions: Long
    )

    /**
     * Saves a user's response to a post-session survey question.
     *
     * @param userId The ID of the user providing the response.
     * @param sessionId The ID of the session related to the survey.
     * @param question The survey question.
     * @param response The user's response to the question.
     */
    suspend fun saveSurveyResponse(userId: Long, sessionId: Long, question: String, response: String)

    /**
     * Saves a single cadence data reading.
     *
     * @param reading The [CadenceDataEntity] to save.
     */
    suspend fun saveCadenceReading(reading: CadenceDataEntity)

    /**
     * Saves a list of cadence data readings in a batch.
     *
     * @param readings The list of [CadenceDataEntity] to save.
     */
    suspend fun saveCadenceReadings(readings: List<CadenceDataEntity>)

    /**
     * Retrieves all cadence data recordings for a specific session.
     *
     * @param sessionId The ID of the session to retrieve cadence data for.
     * @return A list of [CadenceDataEntity] for the given session.
     */
    suspend fun getCadenceDataForSession(sessionId: Long): List<CadenceDataEntity>

    /**
     * Retrieves a limited number of most recent sessions for a given user.
     *
     * @param userId The ID of the user.
     * @param limit The maximum number of recent sessions to retrieve.
     * @return A list of [SessionEntity] objects.
     */
    suspend fun getRecentSessions(userId: Long, limit: Int): List<SessionEntity>

    /**
     * Retrieves a raw summary of all sessions for a specific user.
     *
     * @param userId The ID of the user.
     * @return A [SessionSummaryRaw] object containing aggregated session data.
     */
    suspend fun getOverallSessionSummary(userId: Long): SessionSummaryRaw?

    suspend fun getTodayStats(userId: Long): TodayStats?
    fun getTodayStatsFlow(userId: Long): Flow<TodayStats?>

    /**
     * Deletes all data associated with a specific user.
     * This typically triggers cascade deletes for sessions, notes, settings, etc.
     *
     * @param userId The ID of the user whose data should be deleted.
     */
    suspend fun deleteUserData(userId: Long)

    /**
     * Gets a reactive Flow of the overall session summary for a user.
     * This will automatically emit new summary data whenever the user's session history changes.
     *
     * @param userId The ID of the user.
     * @return A Flow emitting [SessionSummary] objects.
     */
    fun getOverallSessionSummaryFlow(userId: Long): Flow<SessionSummary>
}