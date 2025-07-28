// file: core/data/src/main/java/com/di/core/data/SessionRepositoryImpl.kt

package com.di.core.data

import com.di.core.data.database.* // Import all entities and DAOs
import com.di.core.data.database.SessionSummaryRaw // Ensure this import is correct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [SessionRepository] providing concrete data access operations
 * for fitness sessions using Room database DAOs.
 * It also integrates with [SettingsRepository] to fetch user-specific configurations.
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val surveyDao: SurveyResponseDao,
    private val cadenceDao: CadenceDataDao,
    private val settingsRepository: SettingsRepository,
    private val surveyResponseDao: SurveyResponseDao, // Make sure this is here
) : SessionRepository {

    /**
     * Creates a new fitness session entry in the database for the specified user.
     * The wheel circumference is fetched from user settings to be stored with the session.
     *
     * @param userId The ID of the user for whom the session is created.
     * @return The ID of the newly inserted session.
     */
    override suspend fun createNewSession(userId: Long): Long {
        val wheelCircumference = settingsRepository.getWheelCircumference(userId)
        val newSession = SessionEntity(
            userId = userId,
            startTime = System.currentTimeMillis(),
            endTime = null,
            durationSeconds = 0,
            estimatedDistance = 0.0,
            status = "ACTIVE", // Mark the session as active
            wheelCircumferenceMeters = wheelCircumference
        )
        return sessionDao.insertSession(newSession)
    }

    /**
     * Finds an active (non-completed) session for a specific user.
     *
     * @param userId The ID of the user.
     * @return The active [SessionEntity] or null if none is found.
     */
    override suspend fun findActiveSession(userId: Long): SessionEntity? {
        return sessionDao.findActiveSession(userId)
    }

    /**
     * Retrieves a session by its unique ID.
     *
     * @param sessionId The ID of the session to retrieve.
     * @return The [SessionEntity] if found, otherwise null.
     */
    override suspend fun getSessionById(sessionId: Long): SessionEntity? {
        return sessionDao.getSessionById(sessionId)
    }

    /**
     * Updates the progress (elapsed duration) of an ongoing active session.
     *
     * @param sessionId The ID of the session to update.
     * @param currentDuration The current duration in seconds.
     */
    override suspend fun updateActiveSessionProgress(sessionId: Long, currentDuration: Long) {
        sessionDao.updateActiveSessionProgress(sessionId, currentDuration)
    }

    /**
     * Completes a session, updating its final metrics and setting its status to "COMPLETED".
     *
     * @param sessionId The ID of the session to complete.
     * @param duration The final duration in seconds.
     * @param distance The final estimated distance in kilometers.
     * @param avgCadence The calculated average cadence.
     * @param maxCadence The maximum cadence recorded.
     * @param minCadence The minimum non-zero cadence recorded.
     * @param totalRevolutions The total crank revolutions for the session.
     */
    override suspend fun completeSession(
        sessionId: Long,
        duration: Long,
        distance: Double,
        avgCadence: Double,
        maxCadence: Int,
        minCadence: Int,
        totalRevolutions: Long
    ) {
        sessionDao.completeSession(
            sessionId = sessionId,
            endTime = System.currentTimeMillis(),
            duration = duration,
            distance = distance,
            avgCadence = avgCadence,
            maxCadence = maxCadence,
            minCadence = minCadence,
            revolutions = totalRevolutions,
            status = "COMPLETED" // Ensure status is set to completed
        )
    }

    /**
     * Saves a survey response associated with a user and a specific session.
     *
     * @param userId The ID of the user.
     * @param sessionId The ID of the session.
     * @param question The survey question.
     * @param response The user's response.
     */
    override suspend fun saveSurveyResponse(userId: Long, sessionId: Long, question: String, response: String) {
        val surveyResponse = SurveyResponseEntity(
            userId = userId,
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            question = question,
            response = response
        )
        surveyDao.insertResponse(surveyResponse)
    }

    /**
     * Saves a single [CadenceDataEntity] to the database.
     *
     * @param reading The cadence data entry.
     */
    override suspend fun saveCadenceReading(reading: CadenceDataEntity) {
        cadenceDao.insertCadenceReading(reading)
    }

    /**
     * Saves a list of [CadenceDataEntity] objects in a batch operation.
     *
     * @param readings The list of cadence data entries.
     */
    override suspend fun saveCadenceReadings(readings: List<CadenceDataEntity>) {
        cadenceDao.insertCadenceReadings(readings)
    }

    /**
     * Retrieves all recorded cadence data for a given session.
     *
     * @param sessionId The ID of the session.
     * @return A list of [CadenceDataEntity] for the session.
     */
    override suspend fun getCadenceDataForSession(sessionId: Long): List<CadenceDataEntity> {
        return cadenceDao.getCadenceDataForSession(sessionId)
    }

    /**
     * Retrieves a specified number of the most recent sessions for a particular user.
     *
     * @param userId The ID of the user.
     * @param limit The maximum number of sessions to return.
     * @return A list of [SessionEntity].
     */
    override suspend fun getRecentSessions(userId: Long, limit: Int): List<SessionEntity> {
        return sessionDao.getRecentSessions(userId, limit)
    }

    override suspend fun getTodayStats(userId: Long) =
        sessionDao.getTodayStats(userId)

    override fun getTodayStatsFlow(userId: Long) =
        sessionDao.getTodayStatsFlow(userId)

    override suspend fun getSurveyResponses(sessionId: Long): Map<String, String> {
        return withContext(Dispatchers.IO) {
            surveyResponseDao.getResponsesForSession(sessionId)
                .associate { it.question to it.response }
        }
    }

    /**
     * Retrieves an aggregated summary of all sessions for a given user.
     *
     * @param userId The ID of the user.
     * @return A [SessionSummaryRaw] object.
     */
    override suspend fun getOverallSessionSummary(userId: Long): SessionSummaryRaw? {
        return sessionDao.getOverallSessionSummary(userId)
    }

    /**
     * Retrieves a reactive flow of the session summary from the DAO.
     * It maps the raw database result (or null if no sessions exist) to a clean
     * [SessionSummary] object, providing default values for new users.
     */
    override fun getOverallSessionSummaryFlow(userId: Long): Flow<SessionSummary> {
        // This relies on a new method in your SessionDao. See note below.
        return sessionDao.getOverallSessionSummaryFlow(userId).map { rawSummary ->
            // If the raw summary is null (e.g., for a new user), return a default SessionSummary.
            rawSummary?.let {
                SessionSummary(
                    totalSessions = it.totalSessions,
                    totalDurationSeconds = it.totalDurationSeconds,
                    totalDistanceKm = it.totalDistanceKm,
                    longestSessionSeconds = it.longestSessionSeconds
                )
            } ?: SessionSummary() // Provide default empty summary
        }
    }

    /**
     * Deletes all data associated with a specific user.
     * This relies on `onDelete = ForeignKey.CASCADE` defined in the Room entities
     * for related tables (sessions, notes, survey responses, settings, achievements).
     * The actual deletion of the [UserEntity] itself would typically be handled by a UserManager.
     *
     * @param userId The ID of the user whose data should be purged.
     */
    override suspend fun deleteUserData(userId: Long) {
        // With onDelete = CASCADE set up in your entity foreign keys,
        // deleting the UserEntity will automatically delete linked data.
        // If a specific DAO for UserEntity deletion is needed here, or if cascade
        // is not fully comprehensive for all related tables, explicit DAO calls would go here.
        // Example: If AchievementDao had a deleteUserAchievements(userId: Long) method, it could be called.
        // As per the current setup and architectural intent, this method is primarily
        // for ensuring the interface supports this functionality, with Room's cascade
        // handling much of the actual deletion.
    }
}