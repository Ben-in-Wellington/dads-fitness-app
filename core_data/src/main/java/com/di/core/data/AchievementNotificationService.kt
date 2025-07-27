// file: core/data/src/main/java/com/di/core/data/AchievementNotificationService.kt

package com.di.core.data

import com.di.core.data.database.UnlockedAchievement // <<< IMPORT UnlockedAchievement
import kotlinx.coroutines.flow.Flow

// This defines the contract for the notification service
interface AchievementNotificationService {
    // Method to notify about an unlocked achievement
    fun notifyAchievementUnlocked(achievement: UnlockedAchievement) // <<< Uses UnlockedAchievement

    // Flow to observe for new achievement notifications
    val newAchievements: Flow<UnlockedAchievement> // <<< Emits UnlockedAchievement
}