// CadenceSensorManager.kt

package com.di.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.di.core.bluetooth.model.BleConnectionState
import com.di.core.bluetooth.model.BleDevice
import com.di.core.bluetooth.model.CadenceData
import com.di.core.bluetooth.model.DeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class CadenceSensorManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Create the coroutine scope internally - DO NOT inject it
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null

    private val handler = Handler(Looper.getMainLooper())

    // State flows
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _cadenceData = MutableStateFlow<CadenceData?>(null) // Use StateFlow, initialize with null
    val cadenceData: StateFlow<CadenceData?> = _cadenceData.asStateFlow() // Expose as StateFlow

    private val _availableDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val availableDevices: StateFlow<List<BleDevice>> = _availableDevices.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    // Cadence calculation variables (now Int for 16-bit unsigned handling)
    private var lastCrankRevolutions: Int = 0
    private var lastCrankEventTime: Int = 0
    private var isFirstReading = true

    // Auto-reconnection variables
    private var targetDeviceAddress: String? = null
    private var autoReconnectJob: Job? = null
    private var reconnectAttempts = 0

    private val gattOperationQueue: Queue<() -> Unit> = ConcurrentLinkedQueue()
    private var isGattOperationInProgress = false

    companion object {
        private const val TAG = "CadenceSensorManager"
        private const val SCAN_TIMEOUT_MS = 10000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_DELAY_MS = 3000L
        private const val BATTERY_CHECK_INTERVAL_MS = 300000L // 5 minutes
    }

    fun startScanning(): Flow<List<BleDevice>> = callbackFlow {
        if (!bluetoothAdapter.isEnabled) {
            _connectionState.value = BleConnectionState.Error("Bluetooth is disabled")
            close()
            return@callbackFlow
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        val foundDevices = mutableMapOf<String, BleDevice>()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name ?: "Unknown"

                // Filter for cycling sensors using CSC service UUID and common names
                if (result.scanRecord?.serviceUuids?.any {
                        it.uuid == BleConstants.CyclingSpeedAndCadence.SERVICE_UUID
                    } == true ||
                    deviceName.contains("WAHOO", ignoreCase = true) ||
                    deviceName.contains("RPM", ignoreCase = true) ||
                    deviceName.contains("CADENCE", ignoreCase = true) ||
                    deviceName.contains("SPEED", ignoreCase = true)
                ) {
                    foundDevices[device.address] = BleDevice(
                        name = deviceName,
                        address = device.address,
                        rssi = result.rssi
                    )
                    _availableDevices.value = foundDevices.values.toList()
                    trySend(foundDevices.values.toList())
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
                _connectionState.value = BleConnectionState.Error("Scan failed: $errorCode")
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.CyclingSpeedAndCadence.SERVICE_UUID))
                .build()
        )

        scanner?.startScan(scanFilters, scanSettings, scanCallback)

        // Stop scanning after timeout
        handler.postDelayed({
            scanner?.stopScan(scanCallback)
            channel.close()
        }, SCAN_TIMEOUT_MS)

        awaitClose {
            scanner?.stopScan(scanCallback)
        }
    }

    fun connectToDevice(deviceAddress: String, autoReconnect: Boolean = true) {
        targetDeviceAddress = if (autoReconnect) deviceAddress else null
        reconnectAttempts = 0
        connectToDeviceInternal(deviceAddress)
    }

    private fun connectToDeviceInternal(deviceAddress: String) {
        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            _connectionState.value = BleConnectionState.Connecting

            bluetoothGatt?.let {
                it.disconnect()
                it.close()
            }

            // Connect with autoConnect = false for faster initial connection
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device: ${e.message}", e)
            _connectionState.value = BleConnectionState.Error("Failed to connect: ${e.message}")
            scheduleReconnect()
        }
    }

    fun disconnect() {
        targetDeviceAddress = null
        autoReconnectJob?.cancel()

        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.Disconnected
        _batteryLevel.value = null
        _deviceInfo.value = null
        isFirstReading = true
    }

    private fun scheduleReconnect() {
        if (targetDeviceAddress == null || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                Log.w(TAG, "Max reconnect attempts reached for $targetDeviceAddress. Stopping auto-reconnect.")
                _connectionState.value = BleConnectionState.Error("Failed to reconnect after multiple attempts.")
                targetDeviceAddress = null // Stop trying to reconnect
            }
            return
        }

        autoReconnectJob?.cancel()
        autoReconnectJob = coroutineScope.launch {
            Log.i(TAG, "Attempting reconnect ${reconnectAttempts + 1}/${MAX_RECONNECT_ATTEMPTS} for $targetDeviceAddress")
            delay(RECONNECT_DELAY_MS * (reconnectAttempts + 1)) // Exponential backoff
            reconnectAttempts++
            targetDeviceAddress?.let { connectToDeviceInternal(it) }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server")
                    reconnectAttempts = 0
                    _connectionState.value = BleConnectionState.Connected
                    gattOperationQueue.clear() // Clear any old operations
                    isGattOperationInProgress = false
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server (status: $status)")
                    disconnect() // Use the disconnect function to clean up
                    scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered. Queuing GATT operations.")
                // Enqueue all desired operations. They will be executed sequentially.
                setupCadenceNotifications(gatt)
                readDeviceInformation(gatt)
                readBatteryLevel(gatt)
                // Kick off the first operation from the queue
                executeNextGattOperation()
            } else {
                Log.e(TAG, "onServicesDiscovered failed with status: $status")
                disconnect()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // This is now clean and only handles incoming data
            when (characteristic.uuid) {
                BleConstants.CyclingSpeedAndCadence.MEASUREMENT_CHARACTERISTIC_UUID -> parseCadenceData(characteristic.value)
                BleConstants.Battery.LEVEL_CHARACTERISTIC_UUID -> parseBatteryLevel(characteristic.value)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Read successful for ${characteristic.uuid}")
                when (characteristic.uuid) {
                    BleConstants.Battery.LEVEL_CHARACTERISTIC_UUID -> parseBatteryLevel(characteristic.value)
                    BleConstants.DeviceInformation.MANUFACTURER_NAME_CHARACTERISTIC_UUID -> updateDeviceInfo { it.copy(manufacturerName = characteristic.getStringValue(0)) }
                    BleConstants.DeviceInformation.MODEL_NUMBER_CHARACTERISTIC_UUID -> updateDeviceInfo { it.copy(modelNumber = characteristic.getStringValue(0)) }
                    BleConstants.DeviceInformation.SERIAL_NUMBER_CHARACTERISTIC_UUID -> updateDeviceInfo { it.copy(serialNumber = characteristic.getStringValue(0)) }
                    BleConstants.DeviceInformation.FIRMWARE_REVISION_CHARACTERISTIC_UUID -> updateDeviceInfo { it.copy(firmwareRevision = characteristic.getStringValue(0)) }
                }
            } else {
                Log.w(TAG, "Read failed for ${characteristic.uuid}, status: $status")
            }
            // Operation finished, execute next one
            isGattOperationInProgress = false
            executeNextGattOperation()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Descriptor write successful for ${descriptor.uuid}")
                if (descriptor.uuid == BleConstants.Descriptors.CCC_DESCRIPTOR_UUID && descriptor.characteristic.uuid == BleConstants.CyclingSpeedAndCadence.MEASUREMENT_CHARACTERISTIC_UUID) {
                    Log.i(TAG, ">>> CSC Measurement Notifications ENABLED <<<")
                }
            } else {
                Log.e(TAG, "Descriptor write failed for ${descriptor.uuid}, status: $status")
            }
            // Operation finished, execute next one
            isGattOperationInProgress = false
            executeNextGattOperation()
        }
    }

    // --- GATT Operation Queue Management ---
    private fun queueGattOperation(operation: () -> Unit) {
        gattOperationQueue.add(operation)
    }

    private fun executeNextGattOperation() {
        if (isGattOperationInProgress || gattOperationQueue.isEmpty()) {
            return
        }
        isGattOperationInProgress = true
        val operation = gattOperationQueue.poll()
        operation?.invoke()
    }
    // --- End Queue Management ---

    private fun setupCadenceNotifications(gatt: BluetoothGatt) {
        val characteristic = gatt.getService(BleConstants.CyclingSpeedAndCadence.SERVICE_UUID)
            ?.getCharacteristic(BleConstants.CyclingSpeedAndCadence.MEASUREMENT_CHARACTERISTIC_UUID)
            ?: run { Log.e(TAG, "CSC Measurement characteristic not found"); return }

        // Queue enabling notifications
        queueGattOperation {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(BleConstants.Descriptors.CCC_DESCRIPTOR_UUID)
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                Log.i(TAG, "Queued: Writing CSC descriptor")
                gatt.writeDescriptor(it)
            } ?: run {
                Log.e(TAG, "CCC Descriptor not found for CSC Measurement")
                isGattOperationInProgress = false
                executeNextGattOperation()
            }
        }
    }

    private fun readDeviceInformation(gatt: BluetoothGatt) {
        val service = gatt.getService(BleConstants.DeviceInformation.SERVICE_UUID) ?: return
        val characteristics = listOf(
            BleConstants.DeviceInformation.MANUFACTURER_NAME_CHARACTERISTIC_UUID,
            BleConstants.DeviceInformation.MODEL_NUMBER_CHARACTERISTIC_UUID,
            BleConstants.DeviceInformation.SERIAL_NUMBER_CHARACTERISTIC_UUID,
            BleConstants.DeviceInformation.FIRMWARE_REVISION_CHARACTERISTIC_UUID
        )
        characteristics.forEach { uuid ->
            service.getCharacteristic(uuid)?.let { characteristic ->
                queueGattOperation {
                    Log.i(TAG, "Queued: Reading characteristic ${characteristic.uuid}")
                    gatt.readCharacteristic(characteristic)
                }
            }
        }
    }

    private fun readBatteryLevel(gatt: BluetoothGatt) {
        val characteristic = gatt.getService(BleConstants.Battery.SERVICE_UUID)
            ?.getCharacteristic(BleConstants.Battery.LEVEL_CHARACTERISTIC_UUID)
            ?: return

        // Queue reading the battery level
        queueGattOperation {
            Log.i(TAG, "Queued: Reading battery level")
            gatt.readCharacteristic(characteristic)
        }
        // Also queue enabling notifications for it
        queueGattOperation {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(BleConstants.Descriptors.CCC_DESCRIPTOR_UUID)
            descriptor?.let {
                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                Log.i(TAG, "Queued: Writing Battery descriptor")
                gatt.writeDescriptor(it)
            } ?: run {
                Log.e(TAG, "CCC Descriptor not found for Battery Level")
                isGattOperationInProgress = false
                executeNextGattOperation()
            }
        }
    }

    private fun parseCadenceData(data: ByteArray) {
        if (data.isEmpty()) {
            Log.w(TAG, "Received empty cadence data.")
            return
        }
        Log.d(TAG, "Parsing cadence data: ${data.toHexString()}") // Add this log to see raw data

        val flags = data[0].toInt() and 0xFF // Ensure unsigned
        var offset = 1

        if (flags and 0x01 != 0) {
            offset += 6
        }

        if (flags and 0x02 != 0 && data.size >= offset + 4) {
            val crankRevolutions = (data[offset + 1].toInt() and 0xFF shl 8) or
                    (data[offset].toInt() and 0xFF)

            val crankEventTime = (data[offset + 3].toInt() and 0xFF shl 8) or
                    (data[offset + 2].toInt() and 0xFF)

            if (!isFirstReading) {
                val cadenceRpm = calculateCadence(
                    currentRevolutions = crankRevolutions,
                    currentTime = crankEventTime,
                    previousRevolutions = lastCrankRevolutions,
                    previousTime = lastCrankEventTime
                )

                val currentBatteryLevel = _batteryLevel.value

                val cadenceData = CadenceData(
                    cadenceRpm = cadenceRpm,
                    crankRevolutions = crankRevolutions,
                    lastCrankEventTime = crankEventTime,
                    batteryLevel = currentBatteryLevel
                )

                _cadenceData.value = cadenceData
                Log.i(TAG, "Updated CadenceData StateFlow: RPM = $cadenceRpm")

                if (_cadenceData.tryEmit(cadenceData)) {
                    Log.i(TAG, "Emitted CadenceData: RPM = $cadenceRpm. (Subscriber is active)")
                } else {
                    Log.w(TAG, "Failed to emit CadenceData: RPM = $cadenceRpm. (No active subscriber)")
                }
            } else {
                isFirstReading = false
                Log.i(TAG, "First cadence reading received. Subsequent readings will be processed.")
            }

            lastCrankRevolutions = crankRevolutions
            lastCrankEventTime = crankEventTime
        } else {
            Log.w(TAG, "Crank data not present or insufficient data in packet. Flags: $flags, Data size: ${data.size}")
        }
    }

    // Helper to view raw byte data in logs
    private fun ByteArray.toHexString(): String = joinToString(separator = " ") { eachByte -> "%02x".format(eachByte) }

    private fun parseBatteryLevel(data: ByteArray) {
        if (data.isNotEmpty()) {
            val batteryLevel = data[0].toInt() and 0xFF
            _batteryLevel.value = batteryLevel
            Log.i(TAG, "Battery level updated: $batteryLevel%")
            updateDeviceInfo { it.copy(batteryLevel = batteryLevel) }
        }
    }


    private fun calculateCadence(
        currentRevolutions: Int, // 16-bit unsigned
        currentTime: Int,       // 16-bit unsigned (1/1024s)
        previousRevolutions: Int,
        previousTime: Int
    ): Int {
        // Handle 16-bit unsigned integer rollover for revolutions
        val revDiff = (currentRevolutions - previousRevolutions) and 0xFFFF

        // Handle 16-bit unsigned integer rollover for time (already implemented)
        var timeDiff = (currentTime - previousTime)
        if (timeDiff < 0) {
            timeDiff += 65536 // Add max value for 16-bit unsigned int
        }

        if (revDiff == 0 || timeDiff == 0) {
            return 0 // No change in revolutions or no time elapsed
        }

        // Time is in 1/1024 second units
        val timeInSeconds = timeDiff / 1024.0

        return if (timeInSeconds > 0) {
            (revDiff * 60.0 / timeInSeconds).toInt()
        } else {
            0
        }
    }

    private fun updateDeviceInfo(update: (DeviceInfo) -> DeviceInfo) {
        _deviceInfo.value = update(_deviceInfo.value ?: DeviceInfo())
    }

    /**
     * Clean up resources when the manager is no longer needed.
     * This should be called when the application is being destroyed.
     */
    fun cleanup() {
        disconnect()
        coroutineScope.cancel()
    }
}