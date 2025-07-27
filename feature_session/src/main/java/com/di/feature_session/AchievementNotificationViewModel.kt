// file: feature_session/src/main/java/com/di/feature_session/AchievementNotificationViewModel.kt

package com.di.feature_session

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.data.AchievementNotificationService // <<< Import AchievementNotificationService interface
import com.di.core.data.database.UnlockedAchievement // <<< IMPORT UnlockedAchievement (from core:data)
import com.di.feature_session.ui.Achievement // This is your UI-specific Achievement data class
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat // <<< IMPORT SimpleDateFormat for date formatting
import java.util.Date // <<< IMPORT Date for date formatting
import java.util.Locale // <<< IMPORT Locale for date formatting
import javax.inject.Inject

@HiltViewModel
class AchievementNotificationViewModel @Inject constructor(
    private val notificationService: AchievementNotificationService // Injected interface
) : ViewModel() {

    private val _currentAchievement = MutableStateFlow<Achievement?>(null)
    val currentAchievement: StateFlow<Achievement?> = _currentAchievement.asStateFlow()

    private val achievementQueue = mutableListOf<Achievement>()

    init {
        viewModelScope.launch {
            // --- FIX: Now collecting UnlockedAchievement ---
            notificationService.newAchievements.collect { unlockedAchievement ->
                // --- FIX: Map the UnlockedAchievement DTO to your UI's Achievement model ---
                val achievement = Achievement(
                    id = unlockedAchievement.id,
                    name = unlockedAchievement.name,
                    description = unlockedAchievement.description,
                    icon = getIconFromName(unlockedAchievement.iconName),
                    // Crucially, format the unlockedAt timestamp for the UI
                    unlockedDate = unlockedAchievement.unlockedAt?.let { timestamp ->
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
                    }
                )

                achievementQueue.add(achievement)
                showNextAchievement()
            }
        }
    }

    private fun showNextAchievement() {
        if (_currentAchievement.value == null && achievementQueue.isNotEmpty()) {
            _currentAchievement.value = achievementQueue.removeAt(0)
        }
    }

    fun dismissCurrent() {
        _currentAchievement.value = null
        showNextAchievement()
    }

    private fun getIconFromName(iconName: String): ImageVector {
        return when (iconName) {
            "DirectionsBike" -> Icons.Default.DirectionsBike
            "CalendarToday" -> Icons.Default.CalendarToday
            "Timer" -> Icons.Default.Timer
            "EmojiEvents" -> Icons.Default.EmojiEvents
            "Explore" -> Icons.Default.Explore
            "Landscape" -> Icons.Default.Landscape
            "TrendingUp" -> Icons.Default.TrendingUp
            "MilitaryTech" -> Icons.Default.MilitaryTech
            "Speed" -> Icons.Default.Speed
            "LooksOne" -> Icons.Default.LooksOne
            else -> Icons.Default.Star
        }
    }
}