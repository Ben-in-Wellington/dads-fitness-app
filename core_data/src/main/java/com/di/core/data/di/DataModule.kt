package com.di.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.di.core.data.AchievementNotificationService // Interface
import com.di.core.data.AchievementNotificationServiceImpl // Implementation
import com.di.core.data.AchievementRepository
import com.di.core.data.AchievementRepositoryImpl
import com.di.core.data.SessionRepository
import com.di.core.data.SessionRepositoryImpl
import com.di.core.data.SettingsRepository
import com.di.core.data.SettingsRepositoryImpl
import com.di.core.data.database.* // Import all entities and DAOs
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// --- Module to provide database instance and DAOs ---
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // --- Existing Migrations ---
    // MIGRATION_2_3: Adds cadence fields, cadence_data table, and user_settings table
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add cadence fields to sessions table (already existing)
            database.execSQL("ALTER TABLE sessions ADD COLUMN averageCadence REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE sessions ADD COLUMN maxCadence INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE sessions ADD COLUMN minCadence INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE sessions ADD COLUMN totalRevolutions INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE sessions ADD COLUMN wheelCircumferenceMeters REAL NOT NULL DEFAULT 2.1")

            // Create cadence_data table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cadence_data (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    cadenceRpm INTEGER NOT NULL,
                    crankRevolutions INTEGER NOT NULL,
                    instantaneousSpeed REAL NOT NULL,
                    batteryLevel INTEGER,
                    FOREIGN KEY(sessionId) REFERENCES sessions(id) ON DELETE CASCADE
                )
            """
            )
            // --- FIX IS HERE: Add the missing index for cadence_data table ---
            database.execSQL("CREATE INDEX `index_cadence_data_sessionId` ON `cadence_data` (`sessionId`)")


            // Create user_settings table (already existing)
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_settings (
                    `key` TEXT PRIMARY KEY NOT NULL,
                    value TEXT NOT NULL
                )
            """
            )
            // Insert default settings (already existing)
            database.execSQL(
                """
                INSERT OR IGNORE INTO user_settings (`key`, value) VALUES
                ('wheel_circumference', '2.1'),
                ('preferred_units', 'metric'),
                ('last_connected_device_address', ''),
                ('last_connected_device_name', ''),
                ('auto_reconnect', 'true')
            """
            )
        }
    }

    // MIGRATION_3_4: Adds achievements table
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS achievements (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    iconName TEXT NOT NULL,
                    unlockedAt INTEGER,
                    isUnlocked INTEGER NOT NULL DEFAULT 0,
                    category TEXT NOT NULL DEFAULT 'general'
                )
            """
            )
            // Achievements definitions are inserted via a callback or in the code later.
            // For migrations, we usually define the schema, not the initial data.
        }
    }

    // --- NEW MIGRATION_4_5: Introducing Multi-User Schema (Corrected) ---
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. Create 'users' table and a default user
            database.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, isActive INTEGER NOT NULL DEFAULT 0)")
            database.execSQL("INSERT INTO users (name, isActive) VALUES ('Dad', 1)")
            val dadUserId = database.query("SELECT id FROM users WHERE name = 'Dad' LIMIT 1").use {
                if (it.moveToFirst()) it.getLong(0) else 1L
            }

            // 2. Migrate 'sessions' table
            database.execSQL("ALTER TABLE sessions RENAME TO old_sessions")
            database.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, startTime INTEGER NOT NULL, endTime INTEGER, durationSeconds INTEGER NOT NULL, estimatedDistance REAL NOT NULL, status TEXT NOT NULL, averageCadence REAL NOT NULL DEFAULT 0.0, maxCadence INTEGER NOT NULL DEFAULT 0, minCadence INTEGER NOT NULL DEFAULT 0, totalRevolutions INTEGER NOT NULL DEFAULT 0, wheelCircumferenceMeters REAL NOT NULL DEFAULT 2.1, FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE)")
            database.execSQL("CREATE INDEX `index_sessions_userId` ON `sessions` (`userId`)")
            database.execSQL("INSERT INTO sessions (id, userId, startTime, endTime, durationSeconds, estimatedDistance, status, averageCadence, maxCadence, minCadence, totalRevolutions, wheelCircumferenceMeters) SELECT id, $dadUserId, startTime, endTime, durationSeconds, estimatedDistance, status, averageCadence, maxCadence, minCadence, totalRevolutions, wheelCircumferenceMeters FROM old_sessions")
            database.execSQL("DROP TABLE old_sessions")
            // REMOVE THE DUPLICATE INSERT STATEMENT

            // 3. Recreate 'trainer_notes' table with userId FK and Index
            database.execSQL("ALTER TABLE trainer_notes RENAME TO old_trainer_notes")
            database.execSQL(
                """
            CREATE TABLE trainer_notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                userId INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                note TEXT NOT NULL,
                FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
            )
        """
            )
            database.execSQL("CREATE INDEX `index_trainer_notes_userId` ON `trainer_notes` (`userId`)")
            database.execSQL(
                """
            INSERT INTO trainer_notes (id, userId, timestamp, note)
            SELECT id, $dadUserId, timestamp, note FROM old_trainer_notes
        """
            )
            database.execSQL("DROP TABLE old_trainer_notes")

            // 4. Recreate 'survey_responses' table with userId FK and Index
            database.execSQL("ALTER TABLE survey_responses RENAME TO old_survey_responses")
            database.execSQL(
                """
            CREATE TABLE survey_responses (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                userId INTEGER NOT NULL,
                sessionId INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                question TEXT NOT NULL,
                response TEXT NOT NULL,
                FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(sessionId) REFERENCES sessions(id) ON DELETE CASCADE
            )
        """
            )
            database.execSQL("CREATE INDEX `index_survey_responses_userId` ON `survey_responses` (`userId`)")
            database.execSQL("CREATE INDEX `index_survey_responses_sessionId` ON `survey_responses` (`sessionId`)")
            database.execSQL(
                """
            INSERT INTO survey_responses (id, userId, sessionId, timestamp, question, response)
            SELECT id, $dadUserId, sessionId, timestamp, question, response FROM old_survey_responses
        """
            )
            database.execSQL("DROP TABLE old_survey_responses")

            // 5. Recreate 'user_settings' table with composite PK and Index
            database.execSQL("ALTER TABLE user_settings RENAME TO old_user_settings")
            database.execSQL(
                """
            CREATE TABLE user_settings (
                userId INTEGER NOT NULL,
                `key` TEXT NOT NULL,
                value TEXT NOT NULL,
                PRIMARY KEY(userId, `key`),
                FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
            )
        """
            )
            database.execSQL("CREATE INDEX `index_user_settings_userId` ON `user_settings` (`userId`)")
            database.execSQL(
                """
            INSERT INTO user_settings (userId, `key`, value)
            SELECT $dadUserId, `key`, value FROM old_user_settings
        """
            )
            database.execSQL("DROP TABLE old_user_settings")

            // 6. Store old achievement data BEFORE dropping the table
            val oldAchievements = mutableListOf<Pair<String, Long?>>()
            database.query("SELECT id, unlockedAt FROM achievements WHERE isUnlocked = 1")
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val unlockedAt = if (cursor.isNull(1)) null else cursor.getLong(1)
                        oldAchievements.add(id to unlockedAt)
                    }
                }

            // 7. Recreate the 'achievements' table to hold only definitions
            database.execSQL("DROP TABLE achievements")
            database.execSQL("CREATE TABLE achievements (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, iconName TEXT NOT NULL, category TEXT NOT NULL DEFAULT 'general')")

            // 8. Insert achievement definitions FIRST
            database.execSQL(
                """
            INSERT INTO achievements (id, name, description, iconName, category) VALUES
            ('first_ride', 'First Ride', 'Complete your first cycling session', 'DirectionsBike', 'milestone'),
            ('consistent_cyclist', 'Consistent Cyclist', 'Complete 3 sessions in 7 days', 'CalendarToday', 'consistency'),
            ('15_minute_milestone', '15 Minute Champion', 'Complete a 15-minute session', 'Timer', 'milestone'),
            ('30_minute_milestone', '30 Minute Hero', 'Complete a 30-minute session', 'EmojiEvents', 'milestone'),
            ('5km_total', '5km Explorer', 'Cycle a total of 5 kilometers', 'Explore', 'fitness'),
            ('10km_total', '10km Adventurer', 'Cycle a total of 10 kilometers', 'Landscape', 'fitness'),
            ('personal_best', 'Personal Best', 'Beat your longest session time', 'TrendingUp', 'fitness'),
            ('week_warrior', 'Week Warrior', 'Complete 7 sessions in 7 days', 'MilitaryTech', 'consistency'),
            ('speed_demon', 'Speed Demon', 'Reach 20 km/h average speed', 'Speed', 'fitness'),
            ('century_club', 'Century Club', 'Complete 100 total sessions', 'LooksOne', 'milestone')
        """
            )

            // 9. Create user_achievements table
            database.execSQL("CREATE TABLE user_achievements (userId INTEGER NOT NULL, achievementId TEXT NOT NULL, unlockedAt INTEGER NOT NULL, PRIMARY KEY(userId, achievementId), FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE, FOREIGN KEY(achievementId) REFERENCES achievements(id) ON DELETE CASCADE)")

            // 10. Migrate unlocked achievements using the stored data
            oldAchievements.forEach { (achievementId, unlockedAt) ->
                val timestamp = unlockedAt ?: System.currentTimeMillis()
                database.execSQL("INSERT INTO user_achievements (userId, achievementId, unlockedAt) VALUES ($dadUserId, '$achievementId', $timestamp)")
            }
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create trainer_sessions table
            database.execSQL("""
            CREATE TABLE IF NOT EXISTS trainer_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workoutSessionId INTEGER,
                userId INTEGER NOT NULL,
                geminiSessionHandle TEXT,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                transcript TEXT NOT NULL DEFAULT '',
                isActive INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(workoutSessionId) REFERENCES sessions(id) ON DELETE CASCADE,
                FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
            )
        """)

            // Create indices for trainer_sessions
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trainer_sessions_workoutSessionId ON trainer_sessions(workoutSessionId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trainer_sessions_userId ON trainer_sessions(userId)")

            // Create trainer_reports table
            database.execSQL("""
            CREATE TABLE IF NOT EXISTS trainer_reports (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId INTEGER NOT NULL,
                userId INTEGER NOT NULL,
                reportContent TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY(sessionId) REFERENCES sessions(id) ON DELETE CASCADE,
                FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
            )
        """)

            // Create indices for trainer_reports
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trainer_reports_sessionId ON trainer_reports(sessionId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trainer_reports_userId ON trainer_reports(userId)")
        }
    }


    // --- Database Callback for initial data (like achievements definitions) ---
    // This runs only when the database is created for the *first time* (not on migration)
    private class InitialDataCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Insert default achievement definitions if they don't exist (handles first install)
            db.execSQL(
                """
                INSERT OR IGNORE INTO achievements (id, name, description, iconName, category) VALUES
                ('first_ride', 'First Ride', 'Complete your first cycling session', 'DirectionsBike', 'milestone'),
                ('consistent_cyclist', 'Consistent Cyclist', 'Complete 3 sessions in 7 days', 'CalendarToday', 'consistency'),
                ('15_minute_milestone', '15 Minute Champion', 'Complete a 15-minute session', 'Timer', 'milestone'),
                ('30_minute_milestone', '30 Minute Hero', 'Complete a 30-minute session', 'EmojiEvents', 'milestone'),
                ('5km_total', '5km Explorer', 'Cycle a total of 5 kilometers', 'Explore', 'fitness'),
                ('10km_total', '10km Adventurer', 'Cycle a total of 10 kilometers', 'Landscape', 'fitness'),
                ('personal_best', 'Personal Best', 'Beat your longest session time', 'TrendingUp', 'fitness'),
                ('week_warrior', 'Week Warrior', 'Complete 7 sessions in 7 days', 'MilitaryTech', 'consistency'),
                ('speed_demon', 'Speed Demon', 'Reach 20 km/h average speed', 'Speed', 'fitness'),
                ('century_club', 'Century Club', 'Complete 100 total sessions', 'LooksOne', 'milestone')
            """
            )
        }
    }

    // --- Provides the Room Database instance to Hilt ---
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "dads_fitness_db"
        )
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6) // Add MIGRATION_5_6
            .addCallback(InitialDataCallback())
            .build()
    }

    // --- Providing DAOs to Hilt ---
    // Hilt will automatically find these and provide them when requested.
    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao() // NEW

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideTrainerNoteDao(db: AppDatabase): TrainerNoteDao = db.trainerNoteDao()

    @Provides
    fun provideSurveyResponseDao(db: AppDatabase): SurveyResponseDao = db.surveyResponseDao()

    @Provides
    fun provideCadenceDataDao(db: AppDatabase): CadenceDataDao = db.cadenceDataDao()

    @Provides
    fun provideUserSettingsDao(db: AppDatabase): UserSettingsDao = db.userSettingsDao()

    @Provides
    fun provideAchievementDao(db: AppDatabase): AchievementDao = db.achievementDao()

    @Provides
    fun provideTrainerSessionDao(db: AppDatabase): TrainerSessionDao = db.trainerSessionDao()

    @Provides
    fun provideTrainerReportDao(db: AppDatabase): TrainerReportDao = db.trainerReportDao()

}

// --- Module to bind interfaces to their implementations (for Hilt) ---
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // Binds AchievementNotificationService interface to its implementation
    @Binds
    @Singleton
    abstract fun bindAchievementNotificationService(
        impl: AchievementNotificationServiceImpl
    ): AchievementNotificationService // <<< NEW BINDING

    // Binds SessionRepository interface to its implementation
    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        sessionRepositoryImpl: SessionRepositoryImpl
    ): SessionRepository

    // Binds SettingsRepository interface to its implementation
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    // Binds AchievementRepository interface to its implementation
    @Binds
    @Singleton
    abstract fun bindAchievementRepository(
        achievementRepositoryImpl: AchievementRepositoryImpl
    ): AchievementRepository
}