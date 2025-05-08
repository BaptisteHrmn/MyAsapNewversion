package com.example.myasapnewversion

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.content.Intent
import android.os.IBinder
import android.util.Log

class BleAutoConnectService : Service() {

    companion object {
        private const val TAG = "BLE_SERVICE"

        // Actions pour connecter/déconnecter un appareil via Broadcast
        const val ACTION_CONNECT    = "com.example.myasapnewversion.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.myasapnewversion.ACTION_DISCONNECT"

        // Clé pour passer l'adresse MAC de l'appareil
        const val EXTRA_DEVICE_MAC  = "com.example.myasapnewversion.EXTRA_DEVICE_MAC"
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null

    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        scanner = bluetoothAdapter.bluetoothLeScanner
        Log.d(TAG, "Service créé, scan prêt")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val mac = intent.getStringExtra(EXTRA_DEVICE_MAC)
                mac?.let { connectToDevice(it) }
            }
            ACTION_DISCONNECT -> {
                val mac = intent.getStringExtra(EXTRA_DEVICE_MAC)
                mac?.let { disconnectDevice(it) }
            }
            // ... autres actions existantes
        }
        return START_STICKY
    }

    private fun connectToDevice(mac: String) {
        Log.d(TAG, "📡 Connexion à $mac")
        // ... implémentation existante
    }

    private fun disconnectDevice(mac: String) {
        Log.d(TAG, "⏹️ Déconnexion de $mac")
        // ... implémentation de la déconnexion
    }

    override fun onBind(intent: Intent?): IBinder? = null
}