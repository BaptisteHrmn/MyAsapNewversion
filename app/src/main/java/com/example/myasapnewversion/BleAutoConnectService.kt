package com.example.myasapnewversion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class BleAutoConnectService : Service() {

    private val binder = LocalBinder()
    private val BATTERY_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    // UUID générique pour la caractéristique d'action, à remplacer dès que tu as le bon
    private val ACTION_UUID: UUID? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "ble_channel")
            .setContentTitle("Connexion Bluetooth")
            .setContentText("Recherche et connexion aux accessoires...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Icône système, toujours présente
            .build()
        startForeground(1, notification)
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

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BleService", "Connecté à ${gatt.device.address}")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BleService", "Déconnecté de ${gatt.device.address}")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d("BleService", "Services découverts pour ${gatt.device.address}")
            for (service in gatt.services) {
                Log.d("BleService", "Service UUID: ${service.uuid}")
                for (characteristic in service.characteristics) {
                    Log.d("BleService", "  ↳ Caractéristique UUID: ${characteristic.uuid}")
                }
            }
            // Lecture batterie si présente
            gatt.services.forEach { service ->
                service.characteristics.forEach { characteristic ->
                    if (characteristic.uuid == BATTERY_UUID) {
                        gatt.readCharacteristic(characteristic)
                    }
                    // Abonnement à la caractéristique d'action si connue
                    if (ACTION_UUID != null && characteristic.uuid == ACTION_UUID) {
                        gatt.setCharacteristicNotification(characteristic, true)
                        characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let { desc ->
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(desc)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == BATTERY_UUID) {
                val batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                Log.d("BleService", "Niveau batterie ${gatt.device.address} : $batteryLevel%")
                DeviceStorage.saveBatteryLevel(applicationContext, gatt.device.address, batteryLevel)
                sendBroadcast(Intent("BATTERY_UPDATE").apply {
                    putExtra("address", gatt.device.address)
                    putExtra("battery", batteryLevel)
                })
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d("BleService", "Notification reçue : UUID=${characteristic.uuid}, valeur=${characteristic.value?.joinToString()}")
            if (ACTION_UUID != null && characteristic.uuid == ACTION_UUID) {
                sendBroadcast(Intent("ACTION_SIGNAL").apply {
                    putExtra("address", gatt.device.address)
                    putExtra("action", characteristic.value)
                })
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class LocalBinder : Binder() {
        fun getService(): BleAutoConnectService = this@BleAutoConnectService
    }

    // Ajoute ici ta logique de scan/connexion automatique selon ton existant
}