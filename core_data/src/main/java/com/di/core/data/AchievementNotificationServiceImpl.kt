// file: core/data/src/main/java/com/di/core/data/AchievementNotificationServiceImpl.kt

package com.di.core.data

import com.di.core.data.database.UnlockedAchievement // <<< IMPORT UnlockedAchievement
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
// This is the actual implementation of the interface
class AchievementNotificationServiceImpl @Inject constructor() : AchievementNotificationService {

    // Internal MutableSharedFlow to emit events
    private val _newAchievements = MutableSharedFlow<UnlockedAchievement>( // <<< Uses UnlockedAchievement
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Publicly exposed SharedFlow
    override val newAchievements: SharedFlow<UnlockedAchievement> = _newAchievements

    // Implementation of the notification method
    override fun notifyAchievementUnlocked(achievement: UnlockedAchievement) { // <<< Accepts UnlockedAchievement
        _newAchievements.tryEmit(achievement) // Uses tryEmit for non-suspending call
    }
}