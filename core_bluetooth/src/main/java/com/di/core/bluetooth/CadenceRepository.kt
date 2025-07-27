// CadenceRepository.kt

package com.di.core.bluetooth

import com.di.core.bluetooth.model.BleConnectionState
import com.di.core.bluetooth.model.BleDevice
import com.di.core.bluetooth.model.CadenceData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface CadenceRepository {
    val connectionState: StateFlow<BleConnectionState>
    val cadenceData: StateFlow<CadenceData?>
    val availableDevices: StateFlow<List<BleDevice>>

    fun startScanning(): Flow<List<BleDevice>>
    fun connectToDevice(deviceAddress: String)
    fun disconnect()
}

@Singleton
class CadenceRepositoryImpl @Inject constructor(
    private val cadenceSensorManager: CadenceSensorManager
) : CadenceRepository {

    override val connectionState: StateFlow<BleConnectionState> =
        cadenceSensorManager.connectionState

    override val cadenceData: StateFlow<CadenceData?> = cadenceSensorManager.cadenceData

    override val availableDevices: StateFlow<List<BleDevice>> =
        cadenceSensorManager.availableDevices

    override fun startScanning(): Flow<List<BleDevice>> =
        cadenceSensorManager.startScanning()

    override fun connectToDevice(deviceAddress: String) {
        cadenceSensorManager.connectToDevice(deviceAddress)
    }

    override fun disconnect() {
        cadenceSensorManager.disconnect()
    }
}