// Daos.kt

package com.di.core.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for [CadenceDataEntity].
 * Provides methods for inserting, retrieving, and deleting cycling cadence data.
 */
@Dao
interface CadenceDataDao {
    /**
     * Inserts a single cadence data reading into the database.
     * @param reading The [CadenceDataEntity] to insert.
     */
    @Insert
    suspend fun insertCadenceReading(reading: CadenceDataEntity)

    /**
     * Inserts multiple cadence data readings into the database in a batch.
     * @param readings A list of [CadenceDataEntity] to insert.
     */
    @Insert
    suspend fun insertCadenceReadings(readings: List<CadenceDataEntity>)

    /**
     * Retrieves all cadence data recordings for a specific session, ordered by timestamp.
     * @param sessionId The ID of the session.
     * @return A list of [CadenceDataEntity] for the given session.
     */
    @Query("SELECT * FROM cadence_data WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun getCadenceDataForSession(sessionId: Long): List<CadenceDataEntity>

    /**
     * Deletes all cadence data entries associated with a specific session.
     * This is typically handled by cascade delete if defined in the SessionEntity foreign key.
     * @param sessionId The ID of the session whose cadence data should be deleted.
     */
    @Query("DELETE FROM cadence_data WHERE sessionId = :sessionId")
    suspend fun deleteCadenceDataForSession(sessionId: Long)

    /**
     * Retrieves aggregated cadence statistics (average, max, min) for a given session.
     * Excludes zero RPM readings from min and average calculations to reflect active cycling.
     * @param sessionId The ID of the session.
     * @return A [CadenceStats] object containing the calculated statistics, or null if no data.
     */
    @Query("""
        SELECT AVG(cadenceRpm) as avgCadence, MAX(cadenceRpm) as maxCadence, MIN(cadenceRpm) as minCadence
        FROM cadence_data
        WHERE sessionId = :sessionId AND cadenceRpm > 0
    """)
    suspend fun getCadenceStatsForSession(sessionId: Long): CadenceStats?
}

/**
 * Data class to hold the result of aggregated cadence statistics queries.
 */
data class CadenceStats(
    val avgCadence: Double?,
    val maxCadence: Int?,
    val minCadence: Int?
)

/**
 * Data Access Object (DAO) for [UserEntity].
 * Manages operations related to user profiles, including active user management.
 */
@Dao
interface UserDao {
    /**
     * Inserts a new user or replaces an existing one if a conflict occurs (based on primary key).
     * @param user The [UserEntity] to insert/update.
     * @return The ID of the inserted or replaced user.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    /**
     * Retrieves the currently active user, if any.
     * @return A [Flow] emitting the active [UserEntity] or null.
     */
    @Query("SELECT * FROM users WHERE isActive = 1 LIMIT 1")
    fun getActiveUser(): Flow<UserEntity?>

    /**
     * Retrieves all registered users, ordered by name.
     * @return A [Flow] emitting a list of all [UserEntity] objects.
     */
    @Query("SELECT * FROM users ORDER BY name")
    fun getAllUsers(): Flow<List<UserEntity>>

    /**
     * Performs a transaction to switch the active user:
     * 1. Sets all existing users to inactive.
     * 2. Sets the specified user as active.
     * @param newUserId The ID of the user to make active.
     */
    @Transaction
    suspend fun switchActiveUser(newUserId: Long) {
        setAllUsersInactive()
        setUserActive(newUserId)
    }

    /**
     * Sets the `isActive` flag to `false` for all users in the database.
     */
    @Query("UPDATE users SET isActive = 0")
    suspend fun setAllUsersInactive()

    /**
     * Sets the `isActive` flag to `true` for a specific user.
     * @param userId The ID of the user to activate.
     */
    @Query("UPDATE users SET isActive = 1 WHERE id = :userId")
    suspend fun setUserActive(userId: Long)

    /**
     * Retrieves the total count of users in the database.
     * @return The number of users.
     */
    @Query("SELECT COUNT(id) FROM users")
    suspend fun getUserCount(): Int

    /**
     * Retrieves a [UserEntity] by its unique ID.
     * @param userId The ID of the user to retrieve.
     * @return The [UserEntity] if found, otherwise null.
     */
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): UserEntity?

    /**
     * Deletes a specific user from the database.
     * Due to `onDelete = CASCADE` in related entities, this will also delete
     * all associated sessions, notes, survey responses, settings, and user achievements.
     * @param user The [UserEntity] to delete.
     */
    @Delete
    suspend fun deleteUser(user: UserEntity)
}

/** Aggregated statistics for “today”. */
data class TodayStats(
    val sessions: Int,
    val distanceKm: Double
)

/**
 * Data Access Object (DAO) for [SessionEntity].
 * Manages operations related to user fitness sessions.
 */
@Dao
interface SessionDao {
    /**
     * Inserts a new fitness session into the database.
     * @param session The [SessionEntity] to insert.
     * @return The ID of the newly inserted session.
     */
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    /**
     * Completes an active session by updating its end time, duration, distance,
     * cadence statistics, total revolutions, and status.
     */
    @Query("""
        UPDATE sessions
        SET endTime = :endTime,
            durationSeconds = :duration,
            estimatedDistance = :distance,
            averageCadence = :avgCadence,
            maxCadence = :maxCadence,
            minCadence = :minCadence,
            totalRevolutions = :revolutions,
            status = :status
        WHERE id = :sessionId
    """)
    suspend fun completeSession(
        sessionId: Long,
        endTime: Long,
        duration: Long,
        distance: Double,
        avgCadence: Double,
        maxCadence: Int,
        minCadence: Int,
        revolutions: Long,
        status: String
    )

    /**
     * Updates the duration of an active session while it is in progress.
     */
    @Query("UPDATE sessions SET durationSeconds = :currentDuration WHERE id = :sessionId")
    suspend fun updateActiveSessionProgress(sessionId: Long, currentDuration: Long)

    /**
     * Finds an active (status 'ACTIVE') session for a specific user.
     */
    @Query("SELECT * FROM sessions WHERE status = 'ACTIVE' AND userId = :userId LIMIT 1")
    suspend fun findActiveSession(userId: Long): SessionEntity?

    /**
     * Retrieves a specific session by its unique ID.
     */
    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): SessionEntity?

    /**
     * Retrieves the latest completed session for a specific user.
     */
    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND userId = :userId ORDER BY endTime DESC LIMIT 1")
    suspend fun getLatestCompletedSession(userId: Long): SessionEntity?

    /**
     * Retrieves a specified number of the most recent completed sessions for a given user.
     */
    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' AND userId = :userId ORDER BY endTime DESC LIMIT :limit")
    suspend fun getRecentSessions(userId: Long, limit: Int): List<SessionEntity>

    /**
     * Retrieves a one-time aggregated summary of all completed sessions for a specific user.
     */
    @Query("""
        SELECT
            COUNT(id) AS totalSessions,
            COALESCE(SUM(durationSeconds), 0) AS totalDurationSeconds,
            COALESCE(SUM(estimatedDistance), 0.0) AS totalDistanceKm,
            COALESCE(MAX(durationSeconds), 0) AS longestSessionSeconds
        FROM sessions WHERE status = 'COMPLETED' AND userId = :userId
    """)
    suspend fun getOverallSessionSummary(userId: Long): SessionSummaryRaw // Renamed from getOverallSessionSummaryRaw for clarity

    /**
     * Retrieves a reactive [Flow] of an aggregated summary of all completed sessions for a specific user.
     * Room will automatically emit a new summary whenever the user's session data changes.
     *
     * @param userId The ID of the user.
     * @return A Flow emitting a nullable [SessionSummaryRaw] object. It's nullable in case the user has no sessions.
     */
    @Query("""
        SELECT
            COUNT(id) AS totalSessions,
            COALESCE(SUM(durationSeconds), 0) AS totalDurationSeconds,
            COALESCE(SUM(estimatedDistance), 0.0) AS totalDistanceKm,
            COALESCE(MAX(durationSeconds), 0) AS longestSessionSeconds
        FROM sessions WHERE status = 'COMPLETED' AND userId = :userId
    """)
    fun getOverallSessionSummaryFlow(userId: Long): Flow<SessionSummaryRaw?>

    /* ---------- TODAY ------------- */

    /** one-shot result */
    @Query(
        """
        SELECT  COUNT(id)                       AS sessions,
                COALESCE(SUM(estimatedDistance),0.0) AS distanceKm
        FROM    sessions
        WHERE   status = 'COMPLETED'
          AND   userId = :userId
          AND   date(startTime/1000,'unixepoch','localtime')
                = date('now','localtime')
        """
    )
    suspend fun getTodayStats(userId: Long): TodayStats?

    /** reactive result that updates automatically */
    @Query(
        """
        SELECT  COUNT(id)                       AS sessions,
                COALESCE(SUM(estimatedDistance),0.0) AS distanceKm
        FROM    sessions
        WHERE   status = 'COMPLETED'
          AND   userId = :userId
          AND   date(startTime/1000,'unixepoch','localtime')
                = date('now','localtime')
        """
    )
    fun getTodayStatsFlow(userId: Long): Flow<TodayStats?>

    /**
     * Deletes all sessions associated with a specific user.
     */
    @Query("DELETE FROM sessions WHERE userId = :userId")
    suspend fun deleteSessionsForUser(userId: Long)
}

/**
 * Data Access Object (DAO) for [UserSettingsEntity].
 * Manages user-specific key-value settings.
 */
@Dao
interface UserSettingsDao {
    /**
     * Inserts or replaces a user-specific setting.
     */
    @Query("INSERT OR REPLACE INTO user_settings (userId, `key`, value) VALUES (:userId, :key, :value)")
    suspend fun setSetting(userId: Long, key: String, value: String)

    /**
     * Retrieves a setting value for a specific user and key.
     */
    @Query("SELECT value FROM user_settings WHERE userId = :userId AND `key` = :key")
    suspend fun getSetting(userId: Long, key: String): String?

    /**
     * Retrieves a reactive [Flow] of a setting's value for a specific user and key.
     * Room will automatically emit a new value whenever this specific setting changes.
     *
     * @param userId The ID of the user.
     * @param key The key of the setting to observe.
     * @return A Flow emitting the setting's value as a nullable String.
     */
    @Query("SELECT value FROM user_settings WHERE userId = :userId AND `key` = :key")
    fun getSettingFlow(userId: Long, key: String): Flow<String?>

    /**
     * Deletes all settings for a specific user.
     */
    @Query("DELETE FROM user_settings WHERE userId = :userId")
    suspend fun deleteUserSettingsForUser(userId: Long)
}


/**
 * Data Access Object (DAO) for [TrainerNoteEntity].
 * Manages trainer-specific notes for users.
 */
@Dao
interface TrainerNoteDao {
    /**
     * Inserts a new trainer note.
     * @param note The [TrainerNoteEntity] to insert.
     */
    @Insert
    suspend fun insertNote(note: TrainerNoteEntity)

    /**
     * Retrieves all trainer notes for a specific user, ordered by timestamp in descending order.
     * @param userId The ID of the user.
     * @return A [Flow] emitting a list of [TrainerNoteEntity] objects.
     */
    @Query("SELECT * FROM trainer_notes WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllNotes(userId: Long): Flow<List<TrainerNoteEntity>>

    /**
     * Deletes all trainer notes associated with a specific user.
     * @param userId The ID of the user whose notes should be deleted.
     */
    @Query("DELETE FROM trainer_notes WHERE userId = :userId")
    suspend fun deleteTrainerNotesForUser(userId: Long)
}

/**
 * Data Access Object (DAO) for [SurveyResponseEntity].
 * Manages user responses to post-session surveys.
 */
@Dao
interface SurveyResponseDao {
    /**
     * Inserts a new survey response.
     * @param response The [SurveyResponseEntity] to insert.
     */
    @Insert
    suspend fun insertResponse(response: SurveyResponseEntity)

    /**
     * Retrieves all survey responses for a specific session.
     * @param sessionId The ID of the session.
     * @return A list of [SurveyResponseEntity] for the given session.
     */
    @Query("SELECT * FROM survey_responses WHERE sessionId = :sessionId")
    suspend fun getResponsesForSession(sessionId: Long): List<SurveyResponseEntity>

    /**
     * Deletes all survey responses associated with a specific user.
     * @param userId The ID of the user whose survey responses should be deleted.
     */
    @Query("DELETE FROM survey_responses WHERE userId = :userId")
    suspend fun deleteSurveyResponsesForUser(userId: Long)
}

/**
 * Data Access Object (DAO) for [AchievementEntity] and [UserAchievementEntity].
 * Manages static achievement definitions and user-specific unlocked achievements.
 */
@Dao
interface AchievementDao {
    /**
     * Retrieves all achievements unlocked by a specific user, including their details
     * from the `achievements` table and the `unlockedAt` timestamp.
     * @param userId The ID of the user.
     * @return A [Flow] emitting a list of [UnlockedAchievement] data classes.
     */
    @Query("""
        SELECT a.id, a.name, a.description, a.iconName, ua.unlockedAt
        FROM achievements a JOIN user_achievements ua ON a.id = ua.achievementId
        WHERE ua.userId = :userId
        ORDER BY ua.unlockedAt DESC
    """)
    fun getUnlockedAchievements(userId: Long): Flow<List<UnlockedAchievement>>

    /**
     * Inserts a [UserAchievementEntity] to mark an achievement as unlocked for a user.
     * Uses `OnConflictStrategy.IGNORE` to prevent re-inserting if already unlocked.
     * @param userAchievement The [UserAchievementEntity] to insert.
     * @return The row ID of the inserted item, or -1 if the conflict strategy ignored the insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlockAchievement(userAchievement: UserAchievementEntity): Long

    /**
     * Retrieves all static achievement definitions directly from the `achievements` table.
     * @return A list of all [AchievementEntity] objects.
     */
    @Query("SELECT * FROM achievements")
    suspend fun getAllAchievementsDirect(): List<AchievementEntity>

    /**
     * Inserts or replaces a list of static achievement definitions.
     * Useful for pre-populating or updating achievement data.
     * @param achievements A list of [AchievementEntity] to insert/replace.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    /**
     * Gets the total count of static achievement definitions.
     * @return The number of achievement definitions.
     */
    @Query("SELECT COUNT(*) FROM achievements")
    suspend fun getAchievementCount(): Int

    /**
     * Deletes all user-specific achievement records for a given user.
     * @param userId The ID of the user whose achievements should be deleted.
     */
    @Query("DELETE FROM user_achievements WHERE userId = :userId")
    suspend fun deleteUserAchievementsForUser(userId: Long)

    @Query("SELECT * FROM achievements WHERE id = :id LIMIT 1")
    suspend fun getAchievementById(id: String): AchievementEntity?
}

/**
 * Data Access Object (DAO) for [TrainerSessionEntity].
 * Manages the records of conversations with the AI trainer.
 */
@Dao
interface TrainerSessionDao {
    /**
     * Inserts a new trainer conversation session into the database.
     * @param session The [TrainerSessionEntity] to insert.
     * @return The ID of the newly inserted trainer session.
     */
    @Insert
    suspend fun insertSession(session: TrainerSessionEntity): Long

    /**
     * Updates the Gemini session handle for an existing trainer session.
     * This handle is used to resume conversations with the Gemini Live API.
     * @param sessionId The ID of the trainer session to update.
     * @param handle The new Gemini session handle.
     */
    @Query("UPDATE trainer_sessions SET geminiSessionHandle = :handle WHERE id = :sessionId")
    suspend fun updateSessionHandle(sessionId: Long, handle: String)

    /**
     * Retrieves an active trainer session linked to a specific workout session.
     * @param workoutSessionId The ID of the workout session.
     * @return The active [TrainerSessionEntity] or null.
     */
    @Query("SELECT * FROM trainer_sessions WHERE workoutSessionId = :workoutSessionId AND isActive = 1")
    suspend fun getActiveTrainerSession(workoutSessionId: Long): TrainerSessionEntity?

    /**
     * Ends a trainer session by setting its `isActive` status to false and recording the end time.
     * @param sessionId The ID of the trainer session to end.
     * @param endTime The timestamp when the session ended.
     */
    @Query("UPDATE trainer_sessions SET isActive = 0, endTime = :endTime WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endTime: Long)
}

/**
 * Data Access Object (DAO) for [TrainerReportEntity].
 * Manages reports generated by the AI trainer.
 */
@Dao
interface TrainerReportDao {
    /**
     * Inserts a new trainer report into the database.
     * @param report The [TrainerReportEntity] to insert.
     * @return The ID of the newly inserted report.
     */
    @Insert
    suspend fun insertReport(report: TrainerReportEntity): Long

    /**
     * Retrieves a limited number of the most recent trainer reports for a specific user.
     * @param userId The ID of the user.
     * @param limit The maximum number of reports to retrieve.
     * @return A list of [TrainerReportEntity] objects.
     */
    @Query("SELECT * FROM trainer_reports WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentReports(userId: Long, limit: Int): List<TrainerReportEntity>

    /**
     * Retrieves a specific trainer report linked to a workout session ID.
     * @param sessionId The ID of the workout session.
     * @return The [TrainerReportEntity] for the session, or null.
     */
    @Query("SELECT * FROM trainer_reports WHERE sessionId = :sessionId")
    suspend fun getReportForSession(sessionId: Long): TrainerReportEntity?

    /**
     * Deletes all trainer reports associated with a specific user.
     * @param userId The ID of the user whose reports should be deleted.
     */
    @Query("DELETE FROM trainer_reports WHERE userId = :userId")
    suspend fun deleteTrainerReportsForUser(userId: Long)
}

/**
 * Data class representing an unlocked achievement with its details and unlock timestamp.
 * Used for displaying user-specific achievements.
 * (This class remains unchanged as it's a projection from a JOIN query).
 */
data class UnlockedAchievement(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val unlockedAt: Long?
)
