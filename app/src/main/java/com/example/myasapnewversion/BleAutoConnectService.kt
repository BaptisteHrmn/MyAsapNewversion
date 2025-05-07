package com.example.myasapnewversion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BleAutoConnectService : Service() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private val SCAN_DURATION = 10_000L
    private val SCAN_INTERVAL = 15_000L

    companion object {
        private const val NOTIF_CHANNEL_ID = "ble_service_channel"
        private const val NOTIF_ID = 1
        private const val TAG = "BLE_SERVICE"
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        private val BATTERY_CHAR_UUID    = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Initialisation Bluetooth
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        scanner = bluetoothAdapter.bluetoothLeScanner

        // Passe le service en avant-plan (notification)
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("MyAsap BLE Service")
            .setContentText("Scan des accessoires en cours…")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
        startForeground(NOTIF_ID, notification)

        // Démarre la boucle de scan
        startBleScan()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Service BLE MyAsap",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(chan)
        }
    }

    private fun startBleScan() {
        scanner?.startScan(scanCallback)
        log("🟢 Démarrage du scan BLE")
        handler.postDelayed({
            scanner?.stopScan(scanCallback)
            log("⏹️ Fin du scan BLE")
            handler.postDelayed({ startBleScan() }, SCAN_INTERVAL)
        }, SCAN_DURATION)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val mac = device.address ?: return

            // On ne gère QUE les appareils marqués auto=true
            val auto = prefs.getBoolean("${mac}_auto", false)
            if (!auto) return

            log("ℹ️ Auto-connexion demandée pour $mac")
            device.connectGatt(this@BleAutoConnectService, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val mac = gatt.device.address
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("✅ Connecté à $mac")
                    prefs.edit().putBoolean("${mac}_connected", true).apply()
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("❌ Déconnecté de $mac")
                    prefs.edit().putBoolean("${mac}_connected", false).apply()
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val battService = gatt.getService(BATTERY_SERVICE_UUID)
            val battChar    = battService?.getCharacteristic(BATTERY_CHAR_UUID)
            if (battChar != null) {
                gatt.readCharacteristic(battChar)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == BATTERY_CHAR_UUID) {
                val batteryLevel = characteristic.value.first().toInt() and 0xFF
                val mac = gatt.device.address
                log("🔋 Niveau batterie de $mac = $batteryLevel%")
                prefs.edit().putInt("battery_${mac}", batteryLevel).apply()
                // On se déconnecte après lecture
                gatt.disconnect()
            }
        }
    }

    private fun log(msg: String) {
        Log.d(TAG, "[${timestamp()}] $msg")
    }

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    override fun onDestroy() {
        super.onDestroy()
        scanner?.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}