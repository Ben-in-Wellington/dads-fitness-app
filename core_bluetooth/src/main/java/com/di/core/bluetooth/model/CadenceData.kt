// CadenceData.kt

package com.di.core.bluetooth.model

data class CadenceData(
    val cadenceRpm: Int,
    val crankRevolutions: Int, // Changed to Int to reflect 16-bit UINT from BLE sensor
    val lastCrankEventTime: Int, // Changed to Int to reflect 16-bit UINT from BLE sensor
    val batteryLevel: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Connecting : BleConnectionState()
    object Connected : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

data class DeviceInfo(
    val manufacturerName: String? = null,
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val firmwareRevision: String? = null,
    val batteryLevel: Int? = null
)