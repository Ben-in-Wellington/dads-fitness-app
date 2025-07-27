// file: core/data/src/main/java/com/di/core/data/SettingsRepositoryImpl.kt
package com.di.core.data

import com.di.core.data.database.UserSettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDao: UserSettingsDao
) : SettingsRepository {

    companion object {
        private const val WHEEL_CIRCUMFERENCE_KEY = "wheel_circumference"
        private const val LAST_DEVICE_ADDRESS_KEY = "last_connected_device_address"
        private const val LAST_DEVICE_NAME_KEY = "last_connected_device_name"
        private const val AUTO_RECONNECT_KEY = "auto_reconnect"
        private const val PREFERRED_UNITS_KEY = "preferred_units"

        private const val DEFAULT_WHEEL_CIRCUMFERENCE = 2.1 // meters
    }

    override suspend fun getWheelCircumference(userId: Long): Double {
        return settingsDao.getSetting(userId, WHEEL_CIRCUMFERENCE_KEY)?.toDoubleOrNull()
            ?: DEFAULT_WHEEL_CIRCUMFERENCE
    }

    override suspend fun setWheelCircumference(userId: Long, circumference: Double) {
        settingsDao.setSetting(userId, WHEEL_CIRCUMFERENCE_KEY, circumference.toString())
    }

    override suspend fun getLastConnectedDevice(userId: Long): Pair<String, String>? {
        val address = settingsDao.getSetting(userId, LAST_DEVICE_ADDRESS_KEY)
        val name = settingsDao.getSetting(userId, LAST_DEVICE_NAME_KEY)

        return if (!address.isNullOrBlank() && !name.isNullOrBlank()) {
            Pair(address, name)
        } else {
            null
        }
    }

    override suspend fun setLastConnectedDevice(userId: Long, address: String, name: String) {
        settingsDao.setSetting(userId, LAST_DEVICE_ADDRESS_KEY, address)
        settingsDao.setSetting(userId, LAST_DEVICE_NAME_KEY, name)
    }

    override suspend fun getAutoReconnect(userId: Long): Boolean {
        return settingsDao.getSetting(userId, AUTO_RECONNECT_KEY)?.toBooleanStrictOrNull() ?: true
    }

    override suspend fun setAutoReconnect(userId: Long, enabled: Boolean) {
        settingsDao.setSetting(userId, AUTO_RECONNECT_KEY, enabled.toString())
    }

    override suspend fun getPreferredUnits(userId: Long): String {
        return settingsDao.getSetting(userId, PREFERRED_UNITS_KEY) ?: "metric"
    }

    override suspend fun setPreferredUnits(userId: Long, units: String) {
        settingsDao.setSetting(userId, PREFERRED_UNITS_KEY, units)
    }

    override suspend fun getSetting(userId: Long, key: String): String? {
        return settingsDao.getSetting(userId, key)
    }

    override suspend fun setSetting(userId: Long, key: String, value: String) {
        settingsDao.setSetting(userId, key, value)
    }

    /**
     * Implements the reactive method to get a setting's value.
     * This simply delegates the call to the DAO, which returns a Flow that
     * Room automatically keeps updated.
     */
    override fun getSettingFlow(userId: Long, key: String): Flow<String?> {
        return settingsDao.getSettingFlow(userId, key)
    }
}