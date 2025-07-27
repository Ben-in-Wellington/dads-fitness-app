// file: feature_session/src/main/java/com/di/feature_session/SessionViewModel.kt

package com.di.feature_session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.bluetooth.CadenceRepository
import com.di.core.bluetooth.model.BleConnectionState
import com.di.core.bluetooth.model.CadenceData
import com.di.core.data.AchievementRepository
import com.di.core.data.SessionRepository
import com.di.core.data.SettingsRepository
import com.di.core.data.UserManager // Import UserManager
import com.di.core.data.database.CadenceDataEntity
import com.di.core.data.database.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class SessionUiState(
    val isActive: Boolean = false,
    val sessionId: Long? = null,
    val elapsedTimeSeconds: Long = 0L,
    val estimatedDistance: Double = 0.0,
    val currentSpeed: Double = 0.0,
    val currentCadence: Int = 0,
    val averageCadence: Double = 0.0,
    val displayTime: String = "00:00",
    val bleConnectionState: BleConnectionState = BleConnectionState.Disconnected
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val cadenceRepository: CadenceRepository,
    private val settingsRepository: SettingsRepository,
    private val achievementRepository: AchievementRepository,
    private val userManager: UserManager // Inject UserManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private val _navigationEvent = Channel<NavigationEvent>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private val cadenceReadings = mutableListOf<Int>()
    private var _lastProcessedCrankRevolutions: Int? = null
    private var _totalSessionCrankRevolutions: Long = 0L
    private var _wheelCircumferenceMeters: Double = 2.1 // Cached wheel circumference

    private var currentUserId: Long? = null // Store the active user's ID

    companion object {
        private const val UPDATE_INTERVAL_SECONDS = 30L
    }

    init {
        // Observe BLE connection state
        viewModelScope.launch {
            cadenceRepository.connectionState.collect { state ->
                _uiState.update { it.copy(bleConnectionState = state) }
            }
        }

        // Observe cadence data
        viewModelScope.launch {
            cadenceRepository.cadenceData
                .filterNotNull()
                .collect { data ->
                    handleCadenceData(data)
                }
        }

        // --- FIX: Observe active user changes and load user-specific settings/sessions ---
        viewModelScope.launch {
            userManager.activeUser.collect { activeUser ->
                currentUserId = activeUser?.id
                if (currentUserId != null) {
                    // Load initial wheel circumference setting for the new user
                    _wheelCircumferenceMeters = settingsRepository.getWheelCircumference(currentUserId!!)

                    // Check for active session for the new user on startup
                    sessionRepository.findActiveSession(currentUserId!!)?.let { activeSession ->
                        resumeSession(activeSession)
                    }
                } else {
                    // Reset UI or handle no user scenario
                    _uiState.value = SessionUiState()
                    _wheelCircumferenceMeters = 2.1 // Default
                }
            }
        }
    }

    fun startSession() {
        viewModelScope.launch {
            val userId = currentUserId ?: run { /* Log error or show message */ return@launch }

            // Fetch the latest wheel circumference setting when starting a new session
            _wheelCircumferenceMeters = settingsRepository.getWheelCircumference(userId) // Pass userId

            val newSessionId = sessionRepository.createNewSession(userId) // Pass userId
            cadenceReadings.clear()
            _lastProcessedCrankRevolutions = null
            _totalSessionCrankRevolutions = 0L

            _uiState.update {
                it.copy(
                    isActive = true,
                    sessionId = newSessionId,
                    elapsedTimeSeconds = 0L,
                    displayTime = "00:00",
                    estimatedDistance = 0.0,
                    currentSpeed = 0.0,
                    currentCadence = 0,
                    averageCadence = 0.0
                )
            }
            startTimer()
        }
    }

    fun stopSession() {
        timerJob?.cancel()
        viewModelScope.launch {
            val finalState = _uiState.value
            val userId = currentUserId ?: run { /* Log error */ return@launch }

            if (finalState.sessionId != null) {
                sessionRepository.completeSession(
                    sessionId = finalState.sessionId,
                    duration = finalState.elapsedTimeSeconds,
                    distance = finalState.estimatedDistance,
                    avgCadence = finalState.averageCadence,
                    maxCadence = cadenceReadings.maxOrNull() ?: 0,
                    minCadence = cadenceReadings.filter { it > 0 }.minOrNull() ?: 0,
                    totalRevolutions = _totalSessionCrankRevolutions
                )

                // --- FIX: Pass userId to checkAndUnlockAchievements ---
                achievementRepository.checkAndUnlockAchievements(userId, finalState.sessionId)

                _navigationEvent.send(NavigationEvent.NavigateToSurvey(finalState.sessionId))
            }
            _uiState.value = SessionUiState(bleConnectionState = _uiState.value.bleConnectionState)
        }
    }

    private fun handleCadenceData(data: CadenceData) {
        if (!_uiState.value.isActive) return

        cadenceReadings.add(data.cadenceRpm)

        val wheelCircumference = _wheelCircumferenceMeters
        val speedMs = (data.cadenceRpm / 60.0) * wheelCircumference
        val speedKmh = speedMs * 3.6

        if (_lastProcessedCrankRevolutions != null) {
            var deltaRevolutions = data.crankRevolutions - _lastProcessedCrankRevolutions!!
            if (deltaRevolutions < 0) {
                deltaRevolutions += 65536
            }
            _totalSessionCrankRevolutions += deltaRevolutions
        }
        _lastProcessedCrankRevolutions = data.crankRevolutions

        val distanceKm = (_totalSessionCrankRevolutions * wheelCircumference) / 1000.0

        val avgCadence = if (cadenceReadings.isNotEmpty()) {
            cadenceReadings.average()
        } else 0.0

        _uiState.update {
            it.copy(
                currentCadence = data.cadenceRpm,
                currentSpeed = speedKmh,
                averageCadence = avgCadence,
                estimatedDistance = distanceKm
            )
        }

        viewModelScope.launch {
            val currentSessionId = _uiState.value.sessionId
            if (currentSessionId != null) {
                val cadenceEntity = CadenceDataEntity(
                    sessionId = currentSessionId,
                    timestamp = System.currentTimeMillis(),
                    cadenceRpm = data.cadenceRpm,
                    crankRevolutions = data.crankRevolutions.toLong(),
                    instantaneousSpeed = speedKmh,
                    batteryLevel = data.batteryLevel
                )
                sessionRepository.saveCadenceReading(cadenceEntity)
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        val sessionState = _uiState.value
        val initialTime = sessionState.elapsedTimeSeconds

        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis() - (initialTime * 1000)
            var lastUpdateTime = initialTime

            while (isActive) {
                val newElapsedTime = (System.currentTimeMillis() - startTime) / 1000

                _uiState.update {
                    it.copy(
                        elapsedTimeSeconds = newElapsedTime,
                        displayTime = formatTime(newElapsedTime)
                    )
                }

                if (newElapsedTime > 0 && newElapsedTime - lastUpdateTime >= UPDATE_INTERVAL_SECONDS) {
                    try {
                        sessionRepository.updateActiveSessionProgress(
                            sessionId = _uiState.value.sessionId!!,
                            currentDuration = newElapsedTime
                        )
                        lastUpdateTime = newElapsedTime
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                delay(1000L)
            }
        }
    }

    private suspend fun resumeSession(session: SessionEntity) {
        _wheelCircumferenceMeters = session.wheelCircumferenceMeters

        val existingCadenceData = sessionRepository.getCadenceDataForSession(session.id)
        val lastCadenceReading = existingCadenceData.lastOrNull()

        _lastProcessedCrankRevolutions = lastCadenceReading?.crankRevolutions?.toInt()
        _totalSessionCrankRevolutions = session.totalRevolutions

        val restoredCadenceRpmList = existingCadenceData.map { it.cadenceRpm }.toMutableList()
        cadenceReadings.clear()
        cadenceReadings.addAll(restoredCadenceRpmList)

        val restoredAverageCadence = if (cadenceReadings.isNotEmpty()) restoredCadenceRpmList.average() else 0.0

        _uiState.update {
            it.copy(
                isActive = true,
                sessionId = session.id,
                elapsedTimeSeconds = session.durationSeconds,
                estimatedDistance = session.estimatedDistance,
                displayTime = formatTime(session.durationSeconds),
                currentCadence = lastCadenceReading?.cadenceRpm ?: 0,
                currentSpeed = lastCadenceReading?.instantaneousSpeed ?: 0.0,
                averageCadence = restoredAverageCadence
            )
        }
        startTimer()
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60

        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, remainingSeconds)
        } else {
            "%02d:%02d".format(minutes, remainingSeconds)
        }
    }
}

sealed class NavigationEvent {
    data class NavigateToSurvey(val sessionId: Long) : NavigationEvent()
}