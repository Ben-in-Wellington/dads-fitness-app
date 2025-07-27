package com.di.feature_session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.di.core.bluetooth.CadenceRepository
import com.di.core.bluetooth.model.BleConnectionState
import com.di.core.bluetooth.model.BleDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val cadenceRepository: CadenceRepository
) : ViewModel() {

    companion object {
        private const val TAG = "BluetoothViewModel"
    }

    val availableDevices: StateFlow<List<BleDevice>> = cadenceRepository.availableDevices
    val connectionState: StateFlow<BleConnectionState> = cadenceRepository.connectionState

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startScanning() {
        Log.d(TAG, "Starting scan from ViewModel")
        viewModelScope.launch {
            _isScanning.value = true
            try {
                cadenceRepository.startScanning().collect { devices ->
                    Log.d(TAG, "Received ${devices.size} devices from repository")
                    devices.forEach { device ->
                        Log.d(TAG, "Device: ${device.name} (${device.address})")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
            } finally {
                _isScanning.value = false
                Log.d(TAG, "Scan completed")
            }
        }
    }

    fun connectToDevice(deviceAddress: String) {
        Log.d(TAG, "Connecting to device: $deviceAddress")
        cadenceRepository.connectToDevice(deviceAddress)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting from device")
        cadenceRepository.disconnect()
    }
}