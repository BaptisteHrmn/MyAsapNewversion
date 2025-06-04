package com.example.myasapnewversion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*
import kotlin.collections.HashMap

class BleAutoConnectService : Service() {

    private val binder = LocalBinder()
    private val logTag = "BleAutoConnectService"
    private val TRACE_TAG = "BLE_TRACE"
    private val BATTERY_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    // UUIDs pour iTAG
    private val ITAG_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val ITAG_CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    // UUIDs pour TY
    private val TY_SERVICE_UUID = UUID.fromString("0000a201-0000-1000-8000-00805f9b34fb")
    private val TY_CHAR_UUID = UUID.fromString("0000a202-0000-1000-8000-00805f9b34fb")

    private val scanResults = HashMap<String, BleDevice>()
    private val gattMap = HashMap<String, BluetoothGatt>()
    private var isScanning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "ble_channel")
            .setContentTitle("Connexion Bluetooth")
            .setContentText("Recherche et connexion aux accessoires...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        startForeground(1, notification)
        startBleScan()
    }

    override fun onDestroy() {
        stopBleScan()
        gattMap.values.forEach { it.close() }
        gattMap.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class LocalBinder : Binder() {
        fun getService(): BleAutoConnectService = this@BleAutoConnectService
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ble_channel",
                "Connexion Bluetooth",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // --- BLE SCAN ---

    private fun startBleScan() {
        if (isScanning) return
        isScanning = true
        val scanner = BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
        Log.d(logTag, "🔍 Scan BLE démarré")
    }

    private fun stopBleScan() {
        if (!isScanning) return
        isScanning = false
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(scanCallback)
        Log.d(logTag, "🛑 Scan BLE arrêté")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                handleDeviceFound(device)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { result ->
                result.device?.let { handleDeviceFound(it) }
            }
        }
    }

    private fun handleDeviceFound(device: BluetoothDevice) {
        val mac = device.address
        val originalName = device.name ?: ""
        Log.d("BLE_SCAN_DEBUG", "Détecté : $mac - '$originalName'")

        val associatedMacs = DeviceStorage.getAssociatedMacs(applicationContext)
        val customName = DeviceStorage.getCustomName(applicationContext, mac)
        val isAssociated = associatedMacs.contains(mac)
        val isItagOrTY = originalName.contains("itag", ignoreCase = true) ||
                originalName.contains("ty", ignoreCase = true)

        // Ajoute à la liste si associé ou si nom contient itag/ty
        if (isAssociated || isItagOrTY) {
            val existingDevices = DeviceStorage.loadDevices(applicationContext)
            val existing = existingDevices.find { it.mac == mac }
            val displayName = customName ?: existing?.name ?: if (originalName.isNotBlank()) originalName else mac
            val batteryLevel = DeviceStorage.getBatteryLevel(applicationContext, mac)
            val autoConnected = existing?.isAutoConnected ?: isAssociated

            val newDevice = BleDevice(
                name = displayName,
                mac = mac,
                batteryLevel = batteryLevel,
                isAutoConnected = autoConnected
            )

            scanResults[mac] = newDevice

            // Fusionne la liste
            val mergedList = (scanResults.values + existingDevices)
                .groupBy { it.mac }
                .map { entry ->
                    entry.value.reduce { acc, device ->
                        BleDevice(
                            name = if (device.name.isNotBlank() && device.name != device.mac) device.name else acc.name,
                            mac = device.mac,
                            batteryLevel = device.batteryLevel ?: acc.batteryLevel,
                            isAutoConnected = device.isAutoConnected || acc.isAutoConnected
                        )
                    }
                }

            DeviceStorage.saveDevices(applicationContext, mergedList)
            sendBroadcast(Intent("BLE_LIST_UPDATE"))

            // Connexion auto si associé (même si nom vide)
            if (autoConnected && !gattMap.containsKey(mac)) {
                gattMap[mac]?.close()
                gattMap.remove(mac)
                device.connectGatt(applicationContext, true, gattCallback)
            }
        }
    }

    // --- GATT CALLBACK ---

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TRACE_TAG, "onConnectionStateChange: mac=${gatt.device.address}, status=$status, newState=$newState")
            val mac = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(logTag, "Connecté à $mac")
                gattMap[mac] = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(logTag, "Déconnecté de $mac")
                gattMap.remove(mac)
                gatt.close()
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(logTag, "Erreur GATT $status pour $mac, fermeture")
                gattMap.remove(mac)
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TRACE_TAG, "onServicesDiscovered: mac=${gatt.device.address}, status=$status")
            for (service in gatt.services) {
                Log.i("BLE_SERVICES", "Service UUID: ${service.uuid} (${getServiceName(service.uuid)})")
                for (char in service.characteristics) {
                    Log.i(
                        "BLE_SERVICES",
                        "  └─ Char UUID: ${char.uuid} (${getCharacteristicName(char.uuid)}) | props: 0x${char.properties.toString(16)} | perms: 0x${char.permissions.toString(16)}"
                    )
                }
            }
            // Abonnement notifications bouton iTAG
            val itagService = gatt.getService(ITAG_SERVICE_UUID)
            val itagChar = itagService?.getCharacteristic(ITAG_CHAR_UUID)
            if (itagChar != null && itagChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                gatt.setCharacteristicNotification(itagChar, true)
                val descriptor = itagChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
                Log.d(TRACE_TAG, "Abonnement notifications iTAG (FFE1) sur ${gatt.device.address}")
            }
            // Abonnement notifications bouton TY
            val tyService = gatt.getService(TY_SERVICE_UUID)
            val tyChar = tyService?.getCharacteristic(TY_CHAR_UUID)
            if (tyChar != null && tyChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                gatt.setCharacteristicNotification(tyChar, true)
                val descriptor = tyChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
                Log.d(TRACE_TAG, "Abonnement notifications TY (A202) sur ${gatt.device.address}")
            }
            // Batterie
            var batteryRead = false
            gatt.services.forEach { service ->
                service.characteristics.forEach { characteristic ->
                    if (characteristic.uuid == BATTERY_UUID) {
                        gatt.readCharacteristic(characteristic)
                        batteryRead = true
                    }
                }
            }
            if (!batteryRead) {
                gatt.services.forEach { service ->
                    service.characteristics.forEach { characteristic ->
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 &&
                            characteristic.value == null &&
                            characteristic.uuid != BATTERY_UUID &&
                            characteristic.permissions and BluetoothGattCharacteristic.PERMISSION_READ != 0
                        ) {
                            gatt.readCharacteristic(characteristic)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TRACE_TAG, "onCharacteristicRead: mac=${gatt.device.address}, uuid=${characteristic.uuid}, value=${characteristic.value?.joinToString()}, status=$status")
            Log.i("BLE_READ", "Read from ${characteristic.uuid} (${getCharacteristicName(characteristic.uuid)}): ${characteristic.value?.joinToString { String.format("%02X", it) }}")
            val mac = gatt.device.address
            var batteryLevel: Int? = null
            if (characteristic.uuid == BATTERY_UUID) {
                batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
            } else if (characteristic.value != null && characteristic.value.size == 1) {
                val possible = characteristic.value[0].toInt() and 0xFF
                if (possible in 0..100) {
                    batteryLevel = possible
                }
            }
            if (batteryLevel != null) {
                DeviceStorage.saveBatteryLevel(applicationContext, mac, batteryLevel)
                sendBroadcast(Intent("BATTERY_UPDATE").apply {
                    putExtra("address", mac)
                    putExtra("battery", batteryLevel)
                })
                val existingDevices = DeviceStorage.loadDevices(applicationContext).toMutableList()
                val idx = existingDevices.indexOfFirst { it.mac == mac }
                if (idx >= 0) {
                    val updated = existingDevices[idx].copy(batteryLevel = batteryLevel)
                    existingDevices[idx] = updated
                    DeviceStorage.saveDevices(applicationContext, existingDevices)
                    sendBroadcast(Intent("BLE_LIST_UPDATE"))
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            Log.d(TRACE_TAG, "onCharacteristicWrite: mac=${gatt.device.address}, uuid=${characteristic.uuid}, value=${characteristic.value?.joinToString()}, status=$status")
            Log.i("BLE_WRITE", "Write to ${characteristic.uuid} (${getCharacteristicName(characteristic.uuid)}): ${characteristic.value?.joinToString { String.format("%02X", it) }}")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d(TRACE_TAG, "onCharacteristicChanged: mac=${gatt.device.address}, uuid=${characteristic.uuid}, value=${characteristic.value?.joinToString()}")
            Log.i("BLE_NOTIFY", "Notification from ${characteristic.uuid} (${getCharacteristicName(characteristic.uuid)}): ${characteristic.value?.joinToString { String.format("%02X", it) }}")
            if (characteristic.uuid == TY_CHAR_UUID) {
                val value = characteristic.value
                if (value != null && value.isNotEmpty()) {
                    Log.i("TY_EVENT", "Bouton TY pressé sur ${gatt.device.address} (valeur: ${value[0].toInt()})")
                    sendBroadcast(Intent("TY_BUTTON_PRESSED").apply {
                        putExtra("address", gatt.device.address)
                        putExtra("value", value[0].toInt())
                    })
                }
            }
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TRACE_TAG, "onDescriptorRead: mac=${gatt.device.address}, uuid=${descriptor.uuid}, value=${descriptor.value?.joinToString()}, status=$status")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TRACE_TAG, "onDescriptorWrite: mac=${gatt.device.address}, uuid=${descriptor.uuid}, value=${descriptor.value?.joinToString()}, status=$status")
        }
    }

    // --- Pour faire sonner un appareil iTAG ou TY ---
    fun ringDevice(mac: String) {
        Log.d("BLE_DEBUG", "ringDevice appelé pour $mac")
        val gatt = gattMap[mac] ?: return

        // iTAG : essayer d'abord Immediate Alert, puis FFE1 si dispo
        val immediateAlertService = gatt.getService(UUID.fromString("00001802-0000-1000-8000-00805f9b34fb"))
        val alertLevelChar = immediateAlertService?.getCharacteristic(UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb"))
        if (alertLevelChar != null) {
            alertLevelChar.value = byteArrayOf(0x02)
            gatt.writeCharacteristic(alertLevelChar)
            return
        }

        // Sinon, méthode propriétaire iTAG
        val itagService = gatt.getService(ITAG_SERVICE_UUID)
        val itagChar = itagService?.getCharacteristic(ITAG_CHAR_UUID)
        if (itagChar != null) {
            itagChar.value = byteArrayOf(0x01)
            gatt.writeCharacteristic(itagChar)
            return
        }

        // Pour TY
        val tyService = gatt.getService(TY_SERVICE_UUID)
        val tyChar = tyService?.getCharacteristic(TY_CHAR_UUID)
        if (tyChar != null) {
            tyChar.value = byteArrayOf(0x01)
            gatt.writeCharacteristic(tyChar)
            // Pour certains TY, il faut tester 0x02 si 0x01 ne fonctionne pas
            // tyChar.value = byteArrayOf(0x02)
            // gatt.writeCharacteristic(tyChar)
            return
        }
    }

    // --- Pour arrêter le bip d'un appareil iTAG ou TY ---
    fun stopRingDevice(mac: String) {
        Log.d("BLE_DEBUG", "stopRingDevice appelé pour $mac")
        val gatt = gattMap[mac] ?: return

        // iTAG : Immediate Alert à 0x00 (Stop)
        val immediateAlertService = gatt.getService(UUID.fromString("00001802-0000-1000-8000-00805f9b34fb"))
        val alertLevelChar = immediateAlertService?.getCharacteristic(UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb"))
        if (alertLevelChar != null) {
            alertLevelChar.value = byteArrayOf(0x00)
            gatt.writeCharacteristic(alertLevelChar)
            return
        }

        // Sinon, méthode propriétaire iTAG
        val itagService = gatt.getService(ITAG_SERVICE_UUID)
        val itagChar = itagService?.getCharacteristic(ITAG_CHAR_UUID)
        if (itagChar != null) {
            itagChar.value = byteArrayOf(0x00)
            gatt.writeCharacteristic(itagChar)
            return
        }

        // Pour TY
        val tyService = gatt.getService(TY_SERVICE_UUID)
        val tyChar = tyService?.getCharacteristic(TY_CHAR_UUID)
        if (tyChar != null) {
            tyChar.value = byteArrayOf(0x00)
            gatt.writeCharacteristic(tyChar)
            return
        }
    }

    private fun getServiceName(uuid: UUID): String = when (uuid.toString().lowercase()) {
        "0000180f-0000-1000-8000-00805f9b34fb" -> "Battery Service"
        "00001802-0000-1000-8000-00805f9b34fb" -> "Immediate Alert"
        "0000ffe0-0000-1000-8000-00805f9b34fb" -> "iTAG Service"
        "0000a201-0000-1000-8000-00805f9b34fb" -> "TY Service"
        else -> "Unknown"
    }

    private fun getCharacteristicName(uuid: UUID): String = when (uuid.toString().lowercase()) {
        "00002a19-0000-1000-8000-00805f9b34fb" -> "Battery Level"
        "00002a06-0000-1000-8000-00805f9b34fb" -> "Alert Level"
        "0000ffe1-0000-1000-8000-00805f9b34fb" -> "iTAG Notify/Write"
        "0000a202-0000-1000-8000-00805f9b34fb" -> "TY Notify/Write"
        else -> "Unknown"
    }
}