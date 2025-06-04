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
    private val BATTERY_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    // UUIDs pour iTAG
    private val ITAG_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val ITAG_CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    // UUIDs pour TY
    private val TY_SERVICE_UUID = UUID.fromString("0000a201-0000-1000-8000-00805f9b34fb")
    private val TY_CHAR_UUID = UUID.fromString("0000a202-0000-1000-8000-00805f9b34fb") // Confirmé par tes logs

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
        val associatedMacs = DeviceStorage.getAssociatedMacs(applicationContext)
        val customName = DeviceStorage.getCustomName(applicationContext, mac)

        val isAssociated = associatedMacs.contains(mac)
        val isItagOrTY = originalName.contains("itag", ignoreCase = true) ||
                originalName.contains("ty", ignoreCase = true)

        // Afficher seulement si déjà associé OU si nom contient itag/ty
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

            // Fusionner toute la liste
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

            // Connexion auto si associé
            if (autoConnected && !gattMap.containsKey(mac)) {
                // Ferme une ancienne connexion si elle existe
                gattMap[mac]?.close()
                gattMap.remove(mac)
                device.connectGatt(applicationContext, true, gattCallback)
            }
        }
    }

    // --- GATT CALLBACK ---

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val mac = gatt.device.address
            Log.d("BLE_DEBUG", "onConnectionStateChange $mac, status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(logTag, "Connecté à $mac")
                gattMap[mac] = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(logTag, "Déconnecté de $mac")
                gattMap.remove(mac)
                gatt.close()
            }
            // Ferme la connexion si erreur
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(logTag, "Erreur GATT $status pour $mac, fermeture")
                gattMap.remove(mac)
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("BLE_BATTERY_DEBUG", "onServicesDiscovered for ${gatt.device.address}, status=$status")
            var batteryRead = false
            gatt.services.forEach { service ->
                Log.d("BLE_UUID_DEBUG", "Service: ${service.uuid}")
                service.characteristics.forEach { characteristic ->
                    Log.d("BLE_UUID_DEBUG", "  Characteristic: ${characteristic.uuid}")
                    // Batterie standard
                    if (characteristic.uuid == BATTERY_UUID) {
                        gatt.readCharacteristic(characteristic)
                        batteryRead = true
                    }
                }
            }
            // Si aucune caractéristique batterie standard trouvée, tenter de lire toute caractéristique UINT8
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
            Log.d("BLE_BATTERY_DEBUG", "onCharacteristicRead: uuid=${characteristic.uuid}, value=${characteristic.value?.joinToString()}, status=$status")
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

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            // Pas utilisé pour iTAG/TY
        }
    }

    // --- Pour faire sonner un appareil iTAG ou TY ---
    fun ringDevice(mac: String) {
        Log.d("BLE_DEBUG", "ringDevice appelé pour $mac")
        val gatt = gattMap[mac] ?: return
        val device = scanResults[mac]
        val name = device?.name?.lowercase() ?: ""

        // iTAG : essayer d'abord Immediate Alert, puis FFE1 si dispo
        val immediateAlertService = gatt.getService(UUID.fromString("00001802-0000-1000-8000-00805f9b34fb"))
        val alertLevelChar = immediateAlertService?.getCharacteristic(UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb"))
        if (alertLevelChar != null) {
            alertLevelChar.value = byteArrayOf(0x02) // 0x02 = High Alert (sonnerie)
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
            tyChar.value = byteArrayOf(0x01) // 0x01 ou 0x02 selon le comportement, à tester
            gatt.writeCharacteristic(tyChar)
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
            alertLevelChar.value = byteArrayOf(0x00) // 0x00 = No Alert (arrêt)
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
}