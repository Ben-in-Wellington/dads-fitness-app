// file: core/data/src/main/java/com/di/core/data/UserManager.kt
package com.di.core.data

import com.di.core.data.database.UserDao
import com.di.core.data.database.UserEntity
import com.di.core.data.database.UserSettingsDao
import com.di.core.data.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the state of all users in the application.
 * This is the single source of truth for who is logged in, and provides
 * functions to add, switch, and delete users.
 */
@Singleton
class UserManager @Inject constructor(
    private val userDao: UserDao,
    private val settingsDao: UserSettingsDao,

    /* 1 ────────────────────────────────────────────────
       inject the qualified dispatcher instead of relying
       on a default value                                               */
    @IoDispatcher private val io: CoroutineDispatcher
) {
    // A private scope to run database checks off the main thread.
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * A flow that emits the currently active user. ViewModels can collect this
     * to react to user switches.
     */
    val activeUser: Flow<UserEntity?> = userDao.getActiveUser()

    /**
     * A flow that emits the list of all users in the database.
     */
    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    init {
        // This block runs when the app first starts.
        // It checks if any user exists. If not, it creates a default "Dad" profile.
        scope.launch {
            if (userDao.getUserCount() == 0) {
                val defaultUser = UserEntity(name = "Dad", isActive = true)
                userDao.insertUser(defaultUser)
            }
        }
    }

    /**
     * Creates a new user profile and immediately sets them as the active user.
     */

    suspend fun addNewUser(rawName: String) = withContext(io) {
        val name = rawName.trim().ifBlank { "Unnamed" }
        val newId = userDao.insertUser(UserEntity(name = name))
        // Activate immediately
        userDao.switchActiveUser(newId)
        // Persist the name also as a setting so the UI can read it
        settingsDao.setSetting(newId, "personal_name", name)
    }

    /**
     * Sets a new user as the active one.
     * This will cause the activeUser flow to emit a new value.
     */
    suspend fun setActiveUser(id: Long) = withContext(io) {
        userDao.switchActiveUser(id)
    }

    /**
     * Deletes a user and all their associated data (due to CASCADE on delete).
     * It then ensures another user is set as active if available.
     */
    suspend fun deleteUser(userId: Long) {
        // --- FIX: Get the UserEntity object before deleting ---
        val userToDelete = userDao.getUserById(userId) ?: return // If user not found, do nothing

        // Important: Check if the user being deleted is the active one.
        val wasActive = activeUser.first()?.id == userId

        userDao.deleteUser(userToDelete) // --- FIX: Pass the UserEntity object ---

        if (wasActive) {
            // If the deleted user was active, we must assign a new active user.
            // Wait for the delete operation to propagate and then get the updated list
            // A small delay or ensuring the Flow is up-to-date might be needed in real apps,
            // but for Room, the transaction should be fast enough.
            val remainingUsers = allUsers.first() // Get the updated list of users.
            if (remainingUsers.isNotEmpty()) {
                // Make the first remaining user the new active user.
                setActiveUser(remainingUsers.first().id)
            } else {
                // If no users are left after deletion, create a new default one
                val defaultUser = UserEntity(name = "Dad", isActive = true)
                userDao.insertUser(defaultUser)
            }
        }
    }
}