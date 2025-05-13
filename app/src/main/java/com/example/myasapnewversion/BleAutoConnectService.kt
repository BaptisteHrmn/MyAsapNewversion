package com.example.myasapnewversion

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class BleAutoConnectService : Service() {

    private val TAG = "BLE_SERVICE"

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private val handler = Handler()

    private val connectedDevices = mutableMapOf<String, BluetoothGatt>()
    private val autoConnectAddresses = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        scanner = bluetoothAdapter.bluetoothLeScanner

        autoConnectAddresses.clear()
        DeviceStorage.loadDevices(this).forEach {
            if (it.isAutoConnected) autoConnectAddresses.add(it.mac)
        }

        log("🔁 Service BLE créé")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, createNotification())
        log("▶️ Service BLE lancé")

        startPeriodicScan()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ble_channel",
                "Connexion BLE automatique",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "ble_channel")
            .setContentTitle("Bluetooth actif")
            .setContentText("Connexion automatique aux accessoires")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    private fun startPeriodicScan() {
        scanRunnable.run()
    }

    private val scanRunnable = object : Runnable {
        override fun run() {
            scanner?.startScan(scanCallback)
            log("🟢 Démarrage du scan BLE")
            handler.postDelayed({
                scanner?.stopScan(scanCallback)
                log("⏹️ Fin du scan BLE")
                handler.postDelayed(this, 5000)
            }, 5000)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "?"
            val mac = device.address
            val rssi = result.rssi

            val isAuto = autoConnectAddresses.contains(mac)
            val isConn = connectedDevices.containsKey(mac)
            val batt = "?"

            log("📡 Détecté $mac ($name), auto=$isAuto, conn=$isConn, batt=$batt%")

            if (isAuto && !isConn) {
                log("🔌 Tentative de connexion à $mac ($name)")
                device.connectGatt(this@BleAutoConnectService, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val mac = gatt.device.address
            val name = gatt.device.name ?: "?"
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("✅ Connecté à $mac ($name)")
                connectedDevices[mac] = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("❌ Déconnecté de $mac ($name)")
                connectedDevices.remove(mac)
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val batteryService = gatt.getService(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"))
            val batteryLevelChar = batteryService?.getCharacteristic(UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb"))
            batteryLevelChar?.let {
                gatt.readCharacteristic(it)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")) {
                val battery = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                val mac = gatt.device.address
                log("🔋 Batterie de $mac : $battery%")
                // TODO : enregistrer niveau batterie si besoin
            }
        }
    }

    private fun log(message: String) {
        val timestamp = TimeUtil.timestamp()
        Log.d(TAG, "[$timestamp] $message")
    }
}