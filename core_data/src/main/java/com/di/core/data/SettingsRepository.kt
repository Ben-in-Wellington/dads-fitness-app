package com.di.core.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    // Existing methods, now with userId parameter
    suspend fun getWheelCircumference(userId: Long): Double // ADD userId
    suspend fun setWheelCircumference(userId: Long, circumference: Double) // ADD userId

    suspend fun getLastConnectedDevice(userId: Long): Pair<String, String>? // ADD userId
    suspend fun setLastConnectedDevice(userId: Long, address: String, name: String) // ADD userId

    suspend fun getAutoReconnect(userId: Long): Boolean // ADD userId
    suspend fun setAutoReconnect(userId: Long, enabled: Boolean) // ADD userId

    suspend fun getPreferredUnits(userId: Long): String // ADD userId
    suspend fun setPreferredUnits(userId: Long, units: String) // ADD userId

    // Generic settings methods for extensibility
    suspend fun getSetting(userId: Long, key: String): String? // ADD userId
    suspend fun setSetting(userId: Long, key: String, value: String) // ADD userId

    fun getSettingFlow(userId: Long, key: String): Flow<String?>
    // REMOVED for now: If you need a method to get ALL settings for a user,
    // you'd need a specific DAO query for it (e.g., getAllSettingsForUser(userId: Long)).
    // fun getAllSettings(): Flow<Map<String, String>>
}