package com.example.myasapnewversion

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class BleAutoConnectService : Service() {

    companion object {
        private const val CHANNEL_ID     = "ble_auto_connect"
        private const val NOTIF_ID       = 1
        private const val TAG            = "BLE_SERVICE"
        private const val SCAN_DURATION  = 5_000L
        private const val SCAN_INTERVAL  = 15_000L
    }

    private lateinit var scanner: BluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyAsap BLE Service")
            .setContentText("Recherche et connexion BLE active")
            .setSmallIcon(R.drawable.ic_connected)
            .build()
        startForeground(NOTIF_ID, notification)

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        scanner = manager.adapter.bluetoothLeScanner

        scheduleScan()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE Auto-Connect",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scheduleScan() {
        handler.post(object : Runnable {
            override fun run() {
                val ok = ContextCompat.checkSelfPermission(
                    this@BleAutoConnectService,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

                if (ok) {
                    scanner.startScan(scanCallback)
                    log("🟢 Démarrage du scan BLE auto-connect")
                    handler.postDelayed({
                        scanner.stopScan(scanCallback)
                        log("⏹️ Fin du scan BLE auto-connect")
                        handler.postDelayed(this, SCAN_INTERVAL)
                    }, SCAN_DURATION)
                } else {
                    log("❌ Permission BLUETOOTH_SCAN manquante")
                    handler.postDelayed(this, SCAN_INTERVAL)
                }
            }
        })
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            log("Résultat auto-connect: ${result.device.address} RSSI=${result.rssi}")
            // TODO : implémenter la logique de connexion automatique
        }
    }

    private fun log(msg: String) {
        Log.d(TAG, "[${timestamp()}] $msg")
    }

    private fun timestamp(): String =
        java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scanner.stopScan(scanCallback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}