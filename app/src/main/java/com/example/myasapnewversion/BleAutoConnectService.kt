package com.example.myasapnewversion

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.example.myasapnewversion.TimeUtil
import java.util.UUID

@SuppressLint("MissingPermission")
class BleAutoConnectService : Service() {

    companion object {
        private const val CHANNEL_ID = "BleAutoConnectServiceChannel"
        private const val NOTIF_ID = 1
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val gattMap = mutableMapOf<String, BluetoothGatt>()
    private val BATTERY_SERVICE_UUID =
        UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_CHAR_UUID =
        UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    override fun onCreate() {
        super.onCreate()
        // Création du NotificationChannel (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "Service de connexion BLE",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintient le service BLE en tâche de fond"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(chan)
        }
        // Initialisation de l’adaptateur Bluetooth
        bluetoothAdapter =
            (getSystemService(BluetoothManager::class.java)).adapter
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notification et passage en foreground
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyAsapNewversion")
            .setContentText("Service BLE en cours")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
        startForeground(NOTIF_ID, notification)

        // Démarre l’auto-connexion
        autoConnectStoredDevices()
        return START_STICKY
    }

    private fun autoConnectStoredDevices() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.all.keys
            .filter { it.endsWith("_auto") && prefs.getBoolean(it, false) }
            .mapNotNull { key ->
                key.removeSuffix("_auto")
                    .takeIf { BluetoothAdapter.checkBluetoothAddress(it) }
            }
            .forEach { mac ->
                log(" Tentative de connexion à $mac")
                bluetoothAdapter
                    .getRemoteDevice(mac)
                    .connectGatt(this, false, gattCallback)
            }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val mac = gatt.device.address
            // Met à jour le flag connecté
            PreferenceManager.getDefaultSharedPreferences(this@BleAutoConnectService)
                .edit {
                    putBoolean("${mac}_connected", newState == BluetoothProfile.STATE_CONNECTED)
                }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("✅ Connecté à $mac")
                gattMap[mac] = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("❌ Déconnecté de $mac")
                gatt.close()
                gattMap.remove(mac)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.getService(BATTERY_SERVICE_UUID)
                    ?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                    ?.let { gatt.readCharacteristic(it) }
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID &&
                status == BluetoothGatt.GATT_SUCCESS
            ) {
                val level = characteristic.getIntValue(
                    BluetoothGattCharacteristic.FORMAT_UINT8,
                    0
                )
                val mac = gatt.device.address
                PreferenceManager.getDefaultSharedPreferences(this@BleAutoConnectService)
                    .edit {
                        putInt("battery_$mac", level)
                    }
                log(" Batterie de $mac : $level%")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun log(msg: String) {
        Log.d("BLE_SERVICE", "[${TimeUtil.timestamp()}] $msg")
    }
}