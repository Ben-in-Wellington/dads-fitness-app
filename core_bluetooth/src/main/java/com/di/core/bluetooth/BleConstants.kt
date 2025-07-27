// BleConstants.kt (UPDATED)
package com.di.core.bluetooth

import java.util.UUID

object BleConstants {

    // Standard Bluetooth GATT Descriptors
    object Descriptors {
        // Client Characteristic Configuration Descriptor (for enabling notifications/indications)
        val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // Standard Cycling Speed and Cadence Service (CSC)
    object CyclingSpeedAndCadence {
        val SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        val MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
        val SENSOR_LOCATION_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a5d-0000-1000-8000-00805f9b34fb")
        // Note: The CCC_DESCRIPTOR_UUID is a standard GATT descriptor, defined in BleConstants.Descriptors.
        // It applies to any characteristic that supports notifications (like CSC Measurement).
    }

    // Standard Battery Service
    object Battery {
        val SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        val LEVEL_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    }

    // Standard Device Information Service (for device details)
    object DeviceInformation {
        val SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")
        val SERIAL_NUMBER_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REVISION_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb")
    }

    // Wahoo specific service (Optional: Not directly used for standard CSC data, but kept for reference)
    object Wahoo {
        val SERVICE_UUID: UUID = UUID.fromString("a026ee0c-0a7d-4ab3-97fa-f1500f9feb8b")
    }
}