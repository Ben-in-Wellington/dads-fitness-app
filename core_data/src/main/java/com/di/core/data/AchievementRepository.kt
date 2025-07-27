// file: core/data/src/main/java/com/di/core/data/AchievementRepository.kt
package com.di.core.data

import com.di.core.data.database.*
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for managing user achievements.
 */
interface AchievementRepository {

    /** Flow of all achievements unlocked by [userId]. */
    fun getUnlockedAchievements(userId: Long): Flow<List<UnlockedAchievement>>

    /** Evaluate all rules for [sessionId] and unlock where appropriate. */
    suspend fun checkAndUnlockAchievements(userId: Long, sessionId: Long)
}

@Singleton
class AchievementRepositoryImpl @Inject constructor(
    private val achievementDao: AchievementDao,
    private val sessionDao: SessionDao,
    private val userDao: UserDao,
    private val notificationService: AchievementNotificationService
) : AchievementRepository {

    /* ------------------------------------------------------------------ */
    /*  Public API                                                        */
    /* ------------------------------------------------------------------ */

    override fun getUnlockedAchievements(userId: Long): Flow<List<UnlockedAchievement>> =
        achievementDao.getUnlockedAchievements(userId)

    override suspend fun checkAndUnlockAchievements(userId: Long, sessionId: Long) {
        // ---------- sanity ----------
        val user = userDao.getUserById(userId) ?: return

        // ---------- make sure definitions exist (idempotent) ----------
        ensureDefaultDefinitions()

        // ---------- data we need ----------
        val allSessions: List<SessionEntity> =
            sessionDao.getRecentSessions(userId, Int.MAX_VALUE)

        val currentSession = allSessions.firstOrNull { it.id == sessionId } ?: return

        val sessionsLast7Days =
            allSessions.filter { (it.endTime ?: 0) > System.currentTimeMillis() - MILLIS_7_DAYS }

        val totalDistanceKm = allSessions.sumOf { it.estimatedDistance }

        /* ------------------------------------------------------------------
           1. SINGLE-SESSION ACHIEVEMENTS
        ------------------------------------------------------------------- */

        if (allSessions.size == 1)
            unlockIfNew(userId, "first_ride")

        when (currentSession.durationSeconds) {
            in MIN_15_SEC..Long.MAX_VALUE -> unlockIfNew(userId, "15_minute_milestone")
        }
        if (currentSession.durationSeconds >= MIN_30_SEC)
            unlockIfNew(userId, "30_minute_milestone")
        if (currentSession.durationSeconds >= MIN_45_SEC)
            unlockIfNew(userId, "45_minute_milestone")
        if (currentSession.durationSeconds >= MIN_60_SEC)
            unlockIfNew(userId, "60_minute_milestone")

        // Average speed
        if (currentSession.durationSeconds > 0) {
            val avgKmh =
                (currentSession.estimatedDistance / currentSession.durationSeconds) * 3600.0
            if (avgKmh >= 20.0) unlockIfNew(userId, "speed_demon")
        }

        // Average cadence
        currentSession.averageCadence.let { avg ->
            if (avg >= 60) unlockIfNew(userId, "cadence_60")
            if (avg >= 80) unlockIfNew(userId, "cadence_80")
        }

        // Personal best
        val prevBest =
            allSessions.filter { it.id != sessionId }.maxOfOrNull { it.durationSeconds } ?: 0
        if (prevBest > 0 && currentSession.durationSeconds > prevBest)
            unlockIfNew(userId, "personal_best")

        /* ------------------------------------------------------------------
           2. CONSISTENCY / STREAKS
        ------------------------------------------------------------------- */

        if (sessionsLast7Days.size >= 3)
            unlockIfNew(userId, "consistent_cyclist")
        if (sessionsLast7Days.size >= 7)
            unlockIfNew(userId, "week_warrior")

        // consecutive-day streaks
        val dayIndices = allSessions.map { it.startTime / MILLIS_DAY }.distinct().sorted()
        fun hasStreak(len: Int): Boolean =
            dayIndices.windowed(len).any { w ->
                w.zipWithNext().all { (a, b) -> b == a + 1 }
            }
        if (hasStreak(3)) unlockIfNew(userId, "three_day_streak")
        if (hasStreak(5)) unlockIfNew(userId, "five_day_streak")

        /* ------------------------------------------------------------------
           3. AGGREGATE TOTALS
        ------------------------------------------------------------------- */

        if (totalDistanceKm >= 5)  unlockIfNew(userId, "5km_total")
        if (totalDistanceKm >= 10) unlockIfNew(userId, "10km_total")
        if (totalDistanceKm >= 25) unlockIfNew(userId, "25km_total")
        if (totalDistanceKm >= 50) unlockIfNew(userId, "50km_total")

        val totalSessions = allSessions.size
        if (totalSessions >= 10)  unlockIfNew(userId, "ten_sessions_total")
        if (totalSessions >= 20)  unlockIfNew(userId, "twenty_sessions_total")
        if (totalSessions >= 100) unlockIfNew(userId, "century_club")
    }

    /* ------------------------------------------------------------------ */
    /*  Internal helpers                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Insert (or replace) all default achievement definitions.
     * Runs very quickly on each call; Room replaces only when the row
     * differs, so this is safe to call at every app start.
     */
    private suspend fun ensureDefaultDefinitions() {
        achievementDao.insertAchievements(DEFAULT_DEFS)
    }

    /** Try to unlock [achievementId] and trigger notification if new. */
    private suspend fun unlockIfNew(userId: Long, achievementId: String) {
        val result = achievementDao.unlockAchievement(
            UserAchievementEntity(
                userId = userId,
                achievementId = achievementId,
                unlockedAt = System.currentTimeMillis()
            )
        )
        if (result == -1L) return            // already had it

        val def = achievementDao.getAchievementById(achievementId) ?: return
        notificationService.notifyAchievementUnlocked(
            UnlockedAchievement(
                id          = def.id,
                name        = def.name,
                description = def.description,
                iconName    = def.iconName,
                unlockedAt  = System.currentTimeMillis()
            )
        )
    }

    /* ------------------------------------------------------------------ */
    /*  Constants & default achievement list                              */
    /* ------------------------------------------------------------------ */

    companion object {

        /* ---- time constants ---- */
        private val MILLIS_DAY    = TimeUnit.DAYS.toMillis(1)
        private val MILLIS_7_DAYS = TimeUnit.DAYS.toMillis(7)

        private const val MIN_15_SEC = 15 * 60L
        private const val MIN_30_SEC = 30 * 60L
        private const val MIN_45_SEC = 45 * 60L
        private const val MIN_60_SEC = 60 * 60L

        /* ---- default definitions ---- */
        val DEFAULT_DEFS: List<AchievementEntity> = listOf(
            /* FIRST BATCH (original) */
            AchievementEntity("first_ride",            "First Ride",            "Complete your first cycling session",         "DirectionsBike", "milestone"),
            AchievementEntity("consistent_cyclist",    "Consistent Cyclist",    "Complete 3 sessions in 7 days",               "CalendarToday",  "consistency"),
            AchievementEntity("15_minute_milestone",   "15-Minute Champion",    "Ride for 15 minutes in one session",          "Timer",          "duration"),
            AchievementEntity("30_minute_milestone",   "30-Minute Hero",        "Ride for 30 minutes in one session",          "Timer",          "duration"),
            AchievementEntity("5km_total",             "5 km Explorer",         "Accumulate 5 kilometres",                     "Explore",        "distance"),
            AchievementEntity("10km_total",            "10 km Adventurer",      "Accumulate 10 kilometres",                    "Landscape",      "distance"),
            AchievementEntity("personal_best",         "Personal Best",         "Beat your longest session time",              "TrendingUp",     "milestone"),
            AchievementEntity("week_warrior",          "Week Warrior",          "Ride 7 times in 7 days",                      "MilitaryTech",   "consistency"),
            AchievementEntity("speed_demon",           "Speed Demon",           "Average 20 km/h in a session",                "Speed",          "performance"),
            AchievementEntity("century_club",          "Century Club",          "Complete 100 total sessions",                 "LooksOne",       "milestone"),

            /* NEW DISTANCE */
            AchievementEntity("25km_total",            "25 km Traveller",       "Accumulate 25 kilometres",                    "Explore",        "distance"),
            AchievementEntity("50km_total",            "50 km Voyager",         "Accumulate 50 kilometres",                    "Explore",        "distance"),

            /* NEW DURATION */
            AchievementEntity("45_minute_milestone",   "45-Minute Pro",         "Ride for 45 minutes in one session",          "Timer",          "duration"),
            AchievementEntity("60_minute_milestone",   "1-Hour Power",          "Ride for 60 minutes in one session",          "Timer",          "duration"),

            /* CADENCE */
            AchievementEntity("cadence_60",            "RPM Rookie",            "Hold ≥ 60 rpm average cadence",               "RotateRight",    "performance"),
            AchievementEntity("cadence_80",            "RPM Ace",               "Hold ≥ 80 rpm average cadence",               "RotateRight",    "performance"),

            /* STREAKS & CONSISTENCY */
            AchievementEntity("three_day_streak",      "3-Day Streak",          "Ride 3 consecutive days",                     "CalendarToday",  "consistency"),
            AchievementEntity("five_day_streak",       "5-Day Streak",          "Ride 5 consecutive days",                     "CalendarToday",  "consistency"),

            /* SESSION COUNTS */
            AchievementEntity("ten_sessions_total",    "Double-Digit Rider",    "Complete 10 total sessions",                  "LooksTwo",       "milestone"),
            AchievementEntity("twenty_sessions_total", "Twenty Strong",         "Complete 20 total sessions",                  "Looks3",         "milestone")
        )
    }
}