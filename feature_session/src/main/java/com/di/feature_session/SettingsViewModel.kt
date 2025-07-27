package com.di.feature_session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.data.SettingsRepository
import com.di.core.data.UserManager // Import the UserManager
import com.di.feature_session.UserProfile
import com.di.core.data.database.UserEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val userManager: UserManager // Inject the UserManager
) : ViewModel() {

    // --- State for user-specific settings ---
    private val _wheelCircumference = MutableStateFlow(2.1) // Default value
    val wheelCircumference: StateFlow<Double> = _wheelCircumference.asStateFlow()

    private val _lastConnectedDevice = MutableStateFlow<Pair<String, String>?>(null)
    val lastConnectedDevice: StateFlow<Pair<String, String>?> = _lastConnectedDevice.asStateFlow()

    private val _autoReconnect = MutableStateFlow(true)
    val autoReconnect: StateFlow<Boolean> = _autoReconnect.asStateFlow()

    // --- State for user management, driven by the UserManager ---

    // Expose the active user to the UI, converting from UserEntity to UserProfile
    val activeUser: StateFlow<UserProfile?> = userManager.activeUser
        .map { it?.toUserProfile() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Expose the list of all users to the UI
    val allUsers: StateFlow<List<UserProfile>> = userManager.allUsers
        .map { userList -> userList.map { it.toUserProfile() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // This is the key: we reactively collect the active user.
        // Whenever the user changes, we reload their specific settings.
        viewModelScope.launch {
            userManager.activeUser.collect { activeUserEntity ->
                if (activeUserEntity != null) {
                    loadUserSettings(activeUserEntity.id)
                } else {
                    // Handle the case where there is no active user (e.g., first app start)
                    resetSettingsToDefaults()
                }
            }
        }
    }

    /**
     * Loads settings for a specific user ID.
     */
    private suspend fun loadUserSettings(userId: Long) {
        // Calls to repository now need the userId
        _wheelCircumference.value = settingsRepository.getWheelCircumference(userId)
        _lastConnectedDevice.value = settingsRepository.getLastConnectedDevice(userId)
        _autoReconnect.value = settingsRepository.getAutoReconnect(userId)
    }

    /**
     * Resets settings to their default values when no user is active.
     */
    private fun resetSettingsToDefaults() {
        _wheelCircumference.value = 2.1
        _lastConnectedDevice.value = null
        _autoReconnect.value = true
    }

    /**
     * Sets the auto-reconnect preference for the currently active user.
     */
    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            // Get the current user's ID before saving
            val userId = activeUser.value?.id ?: return@launch
            settingsRepository.setAutoReconnect(userId, enabled)
            _autoReconnect.value = enabled
        }
    }

    // --- User Management Functions ---

    /**
     * Changes the active user in the application.
     */
    fun setActiveUser(userId: Long) {
        viewModelScope.launch {
            userManager.setActiveUser(userId)
        }
    }

    /**
     * Adds a new user profile to the database.
     */
    fun addNewUser(name: String) {
        viewModelScope.launch {
            userManager.addNewUser(name)
        }
    }

    /**
     * Helper function to map the database entity to a UI-friendly data class.
     */
    private fun UserEntity.toUserProfile(): UserProfile {
        return UserProfile(
            id = this.id,
            name = this.name,
            isActive = this.isActive
        )
    }
}