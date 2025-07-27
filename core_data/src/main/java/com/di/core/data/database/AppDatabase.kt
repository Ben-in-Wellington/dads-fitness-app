// AppDatabase.kt
package com.di.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        SessionEntity::class,
        TrainerNoteEntity::class,
        SurveyResponseEntity::class,
        CadenceDataEntity::class,
        UserSettingsEntity::class,
        AchievementEntity::class,
        UserAchievementEntity::class,
        TrainerSessionEntity::class,
        TrainerReportEntity::class
    ],
    version = 6, // INCREMENT VERSION due to new entities
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun trainerNoteDao(): TrainerNoteDao
    abstract fun surveyResponseDao(): SurveyResponseDao
    abstract fun cadenceDataDao(): CadenceDataDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun achievementDao(): AchievementDao
    abstract fun trainerSessionDao(): TrainerSessionDao
    abstract fun trainerReportDao(): TrainerReportDao
}