package com.example.myasapnewversion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class BleAutoConnectService : Service() {

    companion object {
        const val ACTION_CONNECT    = "com.example.myasapnewversion.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.myasapnewversion.ACTION_DISCONNECT"
        const val EXTRA_DEVICE_MAC  = "device_mac"
        private const val TAG        = "BleAutoConnectSvc"
        private const val CHANNEL_ID = "ble_auto_connect_channel"
    }

    private var bluetoothGatt: BluetoothGatt? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (status == GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "✅ Connecté au GATT server")
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "⛔ Déconnecté du GATT server")
                        stopSelf()
                    }
                    else -> {
                        // Autres états ignorés
                    }
                }
            } else {
                Log.e(TAG, "⚠️ Erreur connexion GATT (status=$status)")
                gatt.close()
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Service de connexion BLE",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyAsap – Service BLE")
            .setContentText("Service de connexion automatique en cours")
            .setSmallIcon(R.drawable.ic_auto_connect)
            .build()
        startForeground(1, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            intent.getStringExtra(EXTRA_DEVICE_MAC)?.let { mac ->
                val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = btManager.adapter.getRemoteDevice(mac)
                when (action) {
                    ACTION_CONNECT -> {
                        Log.d(TAG, "Démarrage connexion GATT pour $mac")
                        bluetoothGatt = device.connectGatt(this, false, gattCallback)
                    }
                    ACTION_DISCONNECT -> {
                        Log.d(TAG, "Démarrage déconnexion GATT pour $mac")
                        bluetoothGatt?.disconnect()
                    }
                    else -> {
                        Log.w(TAG, "Action inconnue reçue : $action")
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bluetoothGatt?.close()
        super.onDestroy()
    }
}