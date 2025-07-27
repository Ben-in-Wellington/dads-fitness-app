package com.di.feature_trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.data.SettingsRepository
import com.di.core.data.UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing the AI Trainer settings UI and persisting user preferences.
 * Handles the loading and saving of various AI trainer configurations.
 */
@HiltViewModel
class AITrainerSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val userManager: UserManager
) : ViewModel() {

    // MutableStateFlows to hold the current UI state of each setting.
    // Initial default values are set here.
    private val _trainerPersonality = MutableStateFlow("data_driven_friend") // Changed default
    val trainerPersonality: StateFlow<String> = _trainerPersonality.asStateFlow()

    private val _motivationLevel = MutableStateFlow(0.5f)
    val motivationLevel: StateFlow<Float> = _motivationLevel.asStateFlow()

    private val _voiceSpeed = MutableStateFlow(1.0f)
    val voiceSpeed: StateFlow<Float> = _voiceSpeed.asStateFlow()

    private val _autoGreetings = MutableStateFlow(true)
    val autoGreetings: StateFlow<Boolean> = _autoGreetings.asStateFlow()

    private val _progressReminders = MutableStateFlow(true)
    val progressReminders: StateFlow<Boolean> = _progressReminders.asStateFlow()

    // Store the current user's ID to use in settings calls, ensuring settings are user-specific.
    private var currentUserId: Long? = null

    init {
        // Observe the active user. When the user changes, reload settings for that user.
        viewModelScope.launch {
            userManager.activeUser.collect { activeUserEntity ->
                if (activeUserEntity != null) {
                    currentUserId = activeUserEntity.id
                    loadSettings(activeUserEntity.id)
                } else {
                    // If no user is active (e.g., during app startup before user creation),
                    // reset settings to their default UI state and clear the user ID.
                    resetToDefaults()
                    currentUserId = null
                }
            }
        }
    }

    /**
     * Loads settings for a specific user ID from the [SettingsRepository].
     * Defaults are applied if a setting is not found.
     *
     * @param userId The ID of the user whose settings are to be loaded.
     */
    private suspend fun loadSettings(userId: Long) {
        _trainerPersonality.value = settingsRepository.getSetting(userId, "ai_trainer_personality") ?: "data_driven_friend" // Changed default
        _motivationLevel.value = settingsRepository.getSetting(userId, "ai_trainer_motivation")?.toFloatOrNull() ?: 0.5f
        _voiceSpeed.value = settingsRepository.getSetting(userId, "ai_trainer_voice_speed")?.toFloatOrNull() ?: 1.0f
        _autoGreetings.value = settingsRepository.getSetting(userId, "ai_trainer_auto_greetings")?.toBooleanStrictOrNull() ?: true
        _progressReminders.value = settingsRepository.getSetting(userId, "ai_trainer_progress_reminders")?.toBooleanStrictOrNull() ?: true
    }

    /**
     * Resets the UI state of all settings to their default values.
     * Used when no active user is found.
     */
    private fun resetToDefaults() {
        _trainerPersonality.value = "data_driven_friend" // Changed default
        _motivationLevel.value = 0.5f
        _voiceSpeed.value = 1.0f
        _autoGreetings.value = true
        _progressReminders.value = true
    }

    /**
     * Sets the chosen AI trainer personality and persists it to the [SettingsRepository].
     *
     * @param personality The string key of the selected personality (e.g., "manic_motivator").
     */
    fun setTrainerPersonality(personality: String) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch // Ensure we have a userId to save settings
            settingsRepository.setSetting(userId, "ai_trainer_personality", personality)
            _trainerPersonality.value = personality // Update UI state
        }
    }

    /**
     * Sets the motivation level and persists it.
     *
     * @param level The motivation level (0.0f to 1.0f).
     */
    fun setMotivationLevel(level: Float) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            settingsRepository.setSetting(userId, "ai_trainer_motivation", level.toString())
            _motivationLevel.value = level
        }
    }

    /**
     * Sets the voice speaking speed and persists it.
     *
     * @param speed The voice speed (e.g., 0.5f for half speed, 1.5f for 1.5x speed).
     */
    fun setVoiceSpeed(speed: Float) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            settingsRepository.setSetting(userId, "ai_trainer_voice_speed", speed.toString())
            _voiceSpeed.value = speed
        }
    }

    /**
     * Sets whether auto greetings are enabled and persists the setting.
     *
     * @param enabled True to enable, false to disable.
     */
    fun setAutoGreetings(enabled: Boolean) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            settingsRepository.setSetting(userId, "ai_trainer_auto_greetings", enabled.toString())
            _autoGreetings.value = enabled
        }
    }

    /**
     * Sets whether progress reminders are enabled and persists the setting.
     *
     * @param enabled True to enable, false to disable.
     */
    fun setProgressReminders(enabled: Boolean) {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            settingsRepository.setSetting(userId, "ai_trainer_progress_reminders", enabled.toString())
            _progressReminders.value = enabled
        }
    }
}