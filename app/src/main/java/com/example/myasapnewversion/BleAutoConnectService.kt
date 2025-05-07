package com.example.myasapnewversion

import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.preference.PreferenceManager
import java.util.*

class BleAutoConnectService : Service() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val gattMap = mutableMapOf<String, BluetoothGatt>()

    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        autoConnectStoredDevices()
    }

    private fun autoConnectStoredDevices() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val macs = prefs.all.keys.filter {
            it.endsWith("_auto") && prefs.getBoolean(it, false)
        }.mapNotNull {
            val mac = it.removeSuffix("_auto")
            if (BluetoothAdapter.checkBluetoothAddress(mac)) mac else null
        }

        for (mac in macs) {
            val device = bluetoothAdapter.getRemoteDevice(mac)
            log("🔄 Tentative de connexion à $mac")
            device.connectGatt(this, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val mac = gatt.device.address
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@BleAutoConnectService)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gattMap[mac] = gatt
                prefs.edit().putBoolean("${mac}_connected", true).apply()
                log("✅ Connecté à $mac")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gattMap.remove(mac)
                prefs.edit().putBoolean("${mac}_connected", false).apply()
                log("❌ Déconnecté de $mac")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
            val batteryLevelChar = batteryService?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
            if (batteryLevelChar != null) {
                gatt.readCharacteristic(batteryLevelChar)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == BATTERY_LEVEL_CHAR_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                val level = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                val mac = gatt.device.address
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@BleAutoConnectService)
                prefs.edit().putInt("battery_${mac}", level).apply()
                log("🔋 Batterie de $mac : $level%")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun log(msg: String) {
        Log.d("BLE_SERVICE", "[${TimeUtil.timestamp()}] $msg")
    }
}