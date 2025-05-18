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
    private val TY_CHAR_UUID = UUID.fromString("0000a202-0000-1000-8000-00805f9b34fb") // À confirmer si besoin

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

        val isItagOrTY = originalName.contains("itag", ignoreCase = true) ||
                originalName.contains("TY", ignoreCase = true)
        val isAssociated = associatedMacs.contains(mac)

        if (isItagOrTY || isAssociated) {
            // Charger les infos existantes
            val existingDevices = DeviceStorage.loadDevices(applicationContext)
            val existing = existingDevices.find { it.mac == mac }

            val displayName = customName ?: existing?.name ?: originalName
            val batteryLevel = DeviceStorage.getBatteryLevel(applicationContext, mac)
            val autoConnected = existing?.isAutoConnected ?: isAssociated

            val newDevice = BleDevice(
                name = displayName,
                mac = mac,
                batteryLevel = batteryLevel,
                isAutoConnected = autoConnected
            )

            scanResults[mac] = newDevice

            // Fusionner toute la liste :
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
                device.connectGatt(applicationContext, true, gattCallback)
            }
        }
    }

    // --- GATT CALLBACK ---

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
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
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gatt.services.forEach { service ->
                Log.d("BLE_UUID_DEBUG", "Service: ${service.uuid}")
                service.characteristics.forEach { characteristic ->
                    Log.d("BLE_UUID_DEBUG", "  Characteristic: ${characteristic.uuid}")
                    // Batterie standard
                    if (characteristic.uuid == BATTERY_UUID) {
                        gatt.readCharacteristic(characteristic)
                    }
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == BATTERY_UUID) {
                val batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                DeviceStorage.saveBatteryLevel(applicationContext, gatt.device.address, batteryLevel)
                sendBroadcast(Intent("BATTERY_UPDATE").apply {
                    putExtra("address", gatt.device.address)
                    putExtra("battery", batteryLevel)
                })

                // Met à jour la liste persistée
                val mac = gatt.device.address
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

        // Pour TY, à compléter après logs spécifiques TY
    }
}