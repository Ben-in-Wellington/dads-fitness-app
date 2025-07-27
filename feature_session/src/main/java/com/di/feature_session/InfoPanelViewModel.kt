package com.di.feature_session

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.data.AchievementRepository
import com.di.core.data.SessionRepository
import com.di.core.data.SessionSummary
import com.di.core.data.SettingsRepository
import com.di.core.data.UserManager
import com.di.core.data.database.TodayStats
import com.di.feature_session.ui.Achievement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Data class representing the overall statistics for a user.
 *
 * @property totalSessions The total number of completed workout sessions.
 * @property totalDuration The total duration of all sessions, formatted as a string (e.g., "10h 30m").
 * @property totalDistance The total distance covered in all sessions, formatted as a string (e.g., "150.5 km").
 * @property longestSession The duration of the longest single session, formatted as a string.
 */
data class OverallStats(
    val totalSessions: Int = 0,
    val totalDuration: String = "0m",
    val totalDistance: String = "0.0 km",
    val longestSession: String = "0m"
)

/**
 * ViewModel for the Info Panel, which displays user information, overall statistics,
 * and unlocked achievements.
 *
 * This ViewModel follows a reactive approach, deriving its UI state directly from
 * changes in the active user. When the active user switches, all displayed data
 * automatically updates to reflect the new user's information.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InfoPanelViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val achievementRepository: AchievementRepository,
    private val sessionRepository: SessionRepository,
    private val userManager: UserManager
) : ViewModel() {

    // A private flow that represents the current user's ID, or null if no user is active.
    private val currentUserIdFlow: Flow<Long?> = userManager.activeUser.map { it?.id }

    /**
     * A [StateFlow] that emits the active user's name.
     * It uses `flatMapLatest` to react to user changes: if the user ID is not null, it fetches
     * the name from settings; otherwise, it emits an empty string.
     */
    val userName: StateFlow<String> = userManager.activeUser.flatMapLatest { user ->
        if (user == null) {
            flowOf("")
        } else {
            settingsRepository
                .getSettingFlow(user.id, "personal_name")
                .map { settingValue ->
                    // fall back to the column in users table
                    (settingValue ?: "").ifBlank { user.name }
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    /**
     * A [StateFlow] that emits the user's overall workout statistics.
     * It reacts to user changes, fetching the session summary for the active user
     * and mapping it to the [OverallStats] data class. If no user is active, it emits default stats.
     */
    val overallStats: StateFlow<OverallStats> = currentUserIdFlow.flatMapLatest { userId ->
        if (userId != null) {
            sessionRepository.getOverallSessionSummaryFlow(userId).map { summary ->
                mapSummaryToOverallStats(summary)
            }
        } else {
            flowOf(OverallStats()) // Emit default stats if no user is active
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OverallStats()
    )

    /**
     * A [StateFlow] that emits the list of achievements unlocked by the active user.
     * It reacts to user changes, fetching the unlocked achievements and mapping the DTOs
     * to the UI-friendly [Achievement] model. If no user is active, it emits an empty list.
     */
    val unlockedAchievements: StateFlow<List<Achievement>> = currentUserIdFlow.flatMapLatest { userId ->
        if (userId != null) {
            achievementRepository.getUnlockedAchievements(userId).map { list ->
                // Map the list of DTOs to a list of UI models
                list.map { dto ->
                    Achievement(
                        id = dto.id,
                        name = dto.name,
                        description = dto.description,
                        icon = getIconFromName(dto.iconName),
                        unlockedDate = dto.unlockedAt?.let { formatDate(it) }
                    )
                }
            }
        } else {
            flowOf(emptyList()) // Emit an empty list if no user is active
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * Maps a [SessionSummary] data object from the repository to an [OverallStats] UI model.
     */
    private fun mapSummaryToOverallStats(summary: SessionSummary): OverallStats {
        return OverallStats(
            totalSessions = summary.totalSessions,
            totalDuration = formatDuration(summary.totalDurationSeconds),
            totalDistance = "%.1f km".format(summary.totalDistanceKm),
            longestSession = formatDuration(summary.longestSessionSeconds)
        )
    }

    /**
     * Converts an icon name string (from the database) to its corresponding [ImageVector].
     * Provides a default icon if the name is not recognized.
     *
     * @param iconName The name of the icon (e.g., "DirectionsBike").
     * @return The matching [ImageVector].
     */
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
            else -> Icons.Default.Star // Default icon
        }
    }

    /**
     * Formats a Unix timestamp into a user-friendly date string (e.g., "Jul 26, 2025").
     *
     * @param timestamp The timestamp in milliseconds.
     * @return The formatted date string.
     */
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Formats a duration in seconds into a human-readable string (e.g., "1h 30m" or "45m 15s").
     *
     * @param totalSeconds The duration in seconds.
     * @return The formatted duration string.
     */
    private fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0m"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val remainingSeconds = totalSeconds % 60

        return when {
            hours > 0 -> {
                // Round minutes to nearest whole number if seconds are more than 30
                val roundedMinutes = if (remainingSeconds >= 30) minutes + 1 else minutes
                "%dh %02dm".format(hours, roundedMinutes)
            }
            minutes > 0 -> "%dm".format(minutes)
            else -> "%ds".format(totalSeconds)
        }
    }

    val todayStats: StateFlow<TodayStats?> =
        currentUserIdFlow.flatMapLatest { uid ->
            uid?.let { sessionRepository.getTodayStatsFlow(it) } ?: flowOf(null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}