// file: feature_session/src/main/java/com/di/feature_session/PersonalInfoViewModel.kt

package com.di.feature_session

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.data.SettingsRepository
import com.di.core.data.UserManager // IMPORT THIS
import com.di.feature_session.model.PersonalInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class PersonalInfoViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val userManager: UserManager // Inject UserManager
) : ViewModel() {

    private val _personalInfo = MutableStateFlow(PersonalInfo())
    val personalInfo: StateFlow<PersonalInfo> = _personalInfo.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _hasChanges = MutableStateFlow(false)
    val hasChanges: StateFlow<Boolean> = _hasChanges.asStateFlow()

    // NEW: Expose whether the current user can be deleted (i.e., if there's more than 1 user)
    val canDeleteUser: StateFlow<Boolean> = userManager.allUsers
        .map { it.size > 1 } // True if there's more than one user
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keep active for 5 seconds after collection stops
            initialValue = false
        )

    private var originalInfo = PersonalInfo()
    private var currentUserId: Long? = null // Store the ID of the currently loaded user

    init {
        // Collect active user changes and reload personal info accordingly
        viewModelScope.launch {
            userManager.activeUser.collect { activeUserEntity ->
                if (activeUserEntity != null) {
                    currentUserId = activeUserEntity.id
                    loadPersonalInfo(activeUserEntity.id)
                } else {
                    // This scenario should ideally not happen if UserManager ensures an active user always exists.
                    // But as a fallback, clear the info and set hasChanges to false.
                    _personalInfo.value = PersonalInfo()
                    originalInfo = PersonalInfo()
                    _hasChanges.value = false
                    currentUserId = null
                }
            }
        }
    }

    /**
     * Loads personal information for a specific user.
     * @param userId The ID of the user whose information to load.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun loadPersonalInfo(userId: Long) {
        try {
            // All settingsRepository calls now require a userId
            val info = PersonalInfo(
                name = settingsRepository.getSetting(userId, "personal_name") ?: "",
                age = settingsRepository.getSetting(userId, "personal_age")?.toIntOrNull(),
                weight = settingsRepository.getSetting(userId, "personal_weight")?.toFloatOrNull(),
                dateOfBirth = settingsRepository.getSetting(userId, "personal_dob")?.let {
                    try {
                        LocalDate.parse(it)
                    } catch (e: Exception) {
                        null
                    }
                },
                medicalNotes = settingsRepository.getSetting(userId, "personal_medical_notes") ?: "",
                fitnessGoals = settingsRepository.getSetting(userId, "personal_fitness_goals") ?: "",
                emergencyContactName = settingsRepository.getSetting(userId, "emergency_contact_name") ?: "",
                emergencyContactPhone = settingsRepository.getSetting(userId, "emergency_contact_phone") ?: "",
                emergencyContactEmail = settingsRepository.getSetting(userId, "emergency_contact_email") ?: ""
            )
            _personalInfo.value = info
            originalInfo = info
            _hasChanges.value = false // Reset changes after loading new user's data
        } catch (e: Exception) {
            e.printStackTrace()
            // Log or handle the error gracefully, maybe display a default empty profile
            _personalInfo.value = PersonalInfo()
            originalInfo = PersonalInfo()
            _hasChanges.value = false
        }
    }

    fun updateName(name: String) {
        _personalInfo.value = _personalInfo.value.copy(name = name)
        checkForChanges()
    }

    fun updateAge(age: Int?) {
        _personalInfo.value = _personalInfo.value.copy(age = age)
        checkForChanges()
    }

    fun updateWeight(weight: Float?) {
        _personalInfo.value = _personalInfo.value.copy(weight = weight)
        checkForChanges()
    }

    fun updateDateOfBirth(date: LocalDate) {
        _personalInfo.value = _personalInfo.value.copy(dateOfBirth = date)
        checkForChanges()
    }

    fun updateMedicalNotes(notes: String) {
        _personalInfo.value = _personalInfo.value.copy(medicalNotes = notes)
        checkForChanges()
    }

    fun updateFitnessGoals(goals: String) {
        _personalInfo.value = _personalInfo.value.copy(fitnessGoals = goals)
        checkForChanges()
    }

    fun updateEmergencyContactName(name: String) {
        _personalInfo.value = _personalInfo.value.copy(emergencyContactName = name)
        checkForChanges()
    }

    fun updateEmergencyContactPhone(phone: String) {
        _personalInfo.value = _personalInfo.value.copy(emergencyContactPhone = phone)
        checkForChanges()
    }

    fun updateEmergencyContactEmail(email: String) {
        _personalInfo.value = _personalInfo.value.copy(emergencyContactEmail = email)
        checkForChanges()
    }

    private fun checkForChanges() {
        _hasChanges.value = _personalInfo.value != originalInfo
    }

    /**
     * Saves the current personal information for the active user.
     */
    fun savePersonalInfo() {
        viewModelScope.launch {
            _isSaving.value = true
            // Ensure we have an active user ID to save against
            val userId = currentUserId ?: run {
                // If there's no currentUserId, we can't save. This should ideally not happen
                // if UserManager ensures an active user, but good for robustness.
                _isSaving.value = false
                return@launch
            }

            try {
                val info = _personalInfo.value

                // All settingsRepository.setSetting calls now require a userId
                settingsRepository.setSetting(userId, "personal_name", info.name)

                settingsRepository.setSetting(userId, "personal_age", info.age?.toString() ?: "")
                settingsRepository.setSetting(userId, "personal_weight", info.weight?.toString() ?: "")
                settingsRepository.setSetting(userId, "personal_dob", info.dateOfBirth?.toString() ?: "")

                settingsRepository.setSetting(userId, "personal_medical_notes", info.medicalNotes)
                settingsRepository.setSetting(userId, "personal_fitness_goals", info.fitnessGoals)
                settingsRepository.setSetting(userId, "emergency_contact_name", info.emergencyContactName)
                settingsRepository.setSetting(userId, "emergency_contact_phone", info.emergencyContactPhone)
                settingsRepository.setSetting(userId, "emergency_contact_email", info.emergencyContactEmail)

                originalInfo = info // Update original info to current state
                _hasChanges.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                // Log or show error message
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Deletes the currently active user and switches to another user (or creates a default).
     */
    fun deleteCurrentUser() {
        viewModelScope.launch {
            val userIdToDelete = currentUserId ?: return@launch
            userManager.deleteUser(userIdToDelete)
            // UserManager will handle switching to a new user or creating a default one.
            // The activeUser flow will then trigger a reload of personal info.
        }
    }
}