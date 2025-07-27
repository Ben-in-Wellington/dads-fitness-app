// file: core/data/src/main/java/com/di/core/data/database/Entities.kt
package com.di.core.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


/**
 * Represents a user profile in the database.
 * The primary key `id` is auto-generated. `isActive` tracks the currently selected user.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    var isActive: Boolean = false // This will be managed by the UserManager
)

/**
 * Represents a single fitness session.
 * Sessions are linked to a [UserEntity] via `userId`.
 * `onDelete = ForeignKey.CASCADE` ensures that deleting a user also deletes their sessions.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["userId"])] // Index for efficient user-specific queries
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long, // Foreign key to link to the active user
    val startTime: Long,
    val endTime: Long?,
    val durationSeconds: Long,
    val estimatedDistance: Double,
    val status: String, // "ACTIVE", "COMPLETED", "CANCELED"
    val averageCadence: Double = 0.0,
    val maxCadence: Int = 0,
    val minCadence: Int = 0,
    val totalRevolutions: Long = 0,
    val wheelCircumferenceMeters: Double = 2.1
)

/**
 * Stores textual notes made by the AI trainer (or manually) for a specific user.
 * Linked to a [UserEntity] via `userId`.
 * `onDelete = ForeignKey.CASCADE` ensures notes are deleted when the user is deleted.
 */
@Entity(
    tableName = "trainer_notes",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["userId"])] // Index for efficient user-specific queries
)
data class TrainerNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long, // Foreign key to link to the active user
    val timestamp: Long,
    val note: String
)

/**
 * Stores responses to post-session survey questions.
 * Linked to both a [UserEntity] and a [SessionEntity].
 * `onDelete = ForeignKey.CASCADE` ensures responses are deleted with user or session.
 */
@Entity(
    tableName = "survey_responses",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["userId"]), Index(value = ["sessionId"])] // Indices for efficient queries
)
data class SurveyResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long, // Foreign key to link to the active user
    val sessionId: Long,
    val timestamp: Long,
    val question: String,
    val response: String
)

/**
 * Stores raw cadence data readings collected during a session.
 * Linked to a [SessionEntity] via `sessionId`.
 * `onDelete = ForeignKey.CASCADE` ensures cadence data is deleted when its session is deleted.
 */
@Entity(
    tableName = "cadence_data",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sessionId"])] // Index for efficient session-specific queries
)
data class CadenceDataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val cadenceRpm: Int,
    val crankRevolutions: Long,
    val instantaneousSpeed: Double,
    val batteryLevel: Int? = null
)

/**
 * Stores user-specific settings (e.g., wheel circumference, AI trainer preferences).
 * Uses a composite primary key (`userId`, `key`) for unique setting identification per user.
 * Linked to a [UserEntity] via `userId`.
 * `onDelete = ForeignKey.CASCADE` ensures settings are deleted with the user.
 */
@Entity(
    tableName = "user_settings",
    primaryKeys = ["userId", "key"], // Composite primary key
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["userId"])] // Index for efficient user-specific settings retrieval
)
data class UserSettingsEntity(
    val userId: Long, // Foreign key to link to the active user
    val key: String,
    val value: String
)

/**
 * Defines the static metadata for an achievement (e.g., "First 30-minute session!").
 * This table is global and not user-specific.
 */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String, // Unique string ID for the achievement (e.g., "FIRST_30_MIN_SESSION")
    val name: String,
    val description: String,
    val iconName: String, // Resource name or identifier for the icon
    val category: String = "general" // E.g., "distance", "consistency"
)

/**
 * Represents an achievement that a specific user has unlocked.
 * This is a "join table" linking a user to an achievement.
 * `onDelete = ForeignKey.CASCADE` ensures achievements are cleaned up when a user is deleted.
 */
@Entity(
    tableName = "user_achievements",
    primaryKeys = ["userId", "achievementId"], // Composite primary key
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AchievementEntity::class, parentColumns = ["id"], childColumns = ["achievementId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["achievementId"]) // Index for efficient lookup of unlocked achievements
    ]
)
data class UserAchievementEntity(
    val userId: Long,
    val achievementId: String,
    val unlockedAt: Long // Timestamp when the achievement was unlocked
)

/**
 * Represents a trainer conversation session with the AI, linking to a workout session if applicable.
 * This entity stores metadata about the conversation itself, including the Gemini session handle for resumption.
 * `onDelete = ForeignKey.CASCADE` added to `userId` to ensure proper cleanup.
 */
@Entity(
    tableName = "trainer_sessions",
    foreignKeys = [
        // Link to a workout session (optional, no cascade on delete for workout session)
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["workoutSessionId"], onDelete = ForeignKey.SET_NULL),
        // Link to the user, with cascade delete
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE) // ADDED CASCADE
    ],
    indices = [Index(value = ["userId"]), Index(value = ["workoutSessionId"])] // Indices for efficient queries
)
data class TrainerSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutSessionId: Long?, // Links to workout session if active (nullable for general chats)
    val userId: Long,
    val geminiSessionHandle: String?, // For conversation resumption with Gemini API
    val startTime: Long,
    val endTime: Long? = null,
    val transcript: String = "", // Stores a JSON array of transcript entries for session history
    val isActive: Boolean = true // True if the conversation is ongoing
)

/**
 * Stores reports generated by the AI trainer, linked to a specific session and user.
 * `onDelete = ForeignKey.CASCADE` ensures reports are deleted with the session or user.
 */
@Entity(
    tableName = "trainer_reports",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"]), Index(value = ["userId"])] // Indices for efficient queries
)
data class TrainerReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val userId: Long,
    val reportContent: String, // The full text content of the generated report
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Data Transfer Object (DTO) for raw session summary data retrieved from the database.
 * This class directly maps to the columns returned by the aggregation query in [SessionDao].
 * It includes the essential statistics needed for the InfoPanelViewModel.
 */
data class SessionSummaryRaw(
    val totalSessions: Int,
    val totalDurationSeconds: Long,
    val totalDistanceKm: Double,
    val longestSessionSeconds: Long // Added this field
)