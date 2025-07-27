// file: feature_session/src/main/java/com/di/feature_session/CalibrationViewModel.kt

package com.di.feature_session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.bluetooth.CadenceRepository
import com.di.core.data.SettingsRepository
import com.di.core.data.UserManager // Import UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first // For .first()
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cadenceRepository: CadenceRepository,
    private val userManager: UserManager // Inject UserManager
) : ViewModel() {

    private val _wheelCircumference = MutableStateFlow(2.1)
    val wheelCircumference: StateFlow<Double> = _wheelCircumference.asStateFlow()

    private val _isTestMode = MutableStateFlow(false)
    val isTestMode: StateFlow<Boolean> = _isTestMode.asStateFlow()

    private val _testCadence = MutableStateFlow(0)
    val testCadence: StateFlow<Int> = _testCadence.asStateFlow()

    private val _testSpeed = MutableStateFlow(0.0)
    val testSpeed: StateFlow<Double> = _testSpeed.asStateFlow()

    private var currentUserId: Long? = null // To store the active user's ID

    init {
        // Observe active user changes and reload circumference
        viewModelScope.launch {
            userManager.activeUser.collect { activeUser ->
                currentUserId = activeUser?.id
                // Only load if a user is active
                if (currentUserId != null) {
                    loadCurrentCircumference(currentUserId!!)
                } else {
                    // Reset if no user is active (shouldn't happen with UserManager setup)
                    _wheelCircumference.value = 2.1
                }
            }
        }
        observeCadenceData()
    }

    // --- FIX: Now takes userId ---
    private suspend fun loadCurrentCircumference(userId: Long) {
        _wheelCircumference.value = settingsRepository.getWheelCircumference(userId) // Pass userId
    }

    private fun observeCadenceData() {
        viewModelScope.launch {
            cadenceRepository.cadenceData
                .filterNotNull()
                .collect { data ->
                    if (_isTestMode.value) {
                        _testCadence.value = data.cadenceRpm
                        // Calculate speed based on current wheel circumference
                        val speedMs = (data.cadenceRpm / 60.0) * _wheelCircumference.value
                        _testSpeed.value = speedMs * 3.6 // Convert to km/h
                    }
                }
        }
    }

    // --- FIX: Now takes userId, which is implicit from currentUserId ---
    fun setWheelCircumference(circumference: Double) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch // Ensure userId is available
            settingsRepository.setWheelCircumference(userId, circumference) // Pass userId
            _wheelCircumference.value = circumference
        }
    }

    fun toggleTestMode() {
        _isTestMode.value = !_isTestMode.value
        if (!_isTestMode.value) {
            _testCadence.value = 0
            _testSpeed.value = 0.0
        }
    }
}