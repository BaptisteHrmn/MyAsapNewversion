package com.example.myasapnewversion

import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.*

class BleAutoConnectService : Service() {

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val scanner: BluetoothLeScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            connectToDevice(device)
        }
    }

    private val gattCallbacks = mutableMapOf<String, BluetoothGattCallback>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startScanning()
        return START_STICKY
    }

    private fun startScanning() {
        val scanFilter = ScanFilter.Builder().build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BleService", "Connected to ${device.address}")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BleService", "Disconnected from ${device.address}")
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
                    val batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    Log.d("BleService", "Battery level of ${device.address}: $batteryLevel%")
                    // Mettre à jour l'objet BleDevice ici
                }
            }
        }

        gattCallbacks[device.address] = callback
        device.connectGatt(this, false, callback)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.stopScan(scanCallback)
        gattCallbacks.values.forEach { callback ->
            // Fermer les connexions GATT si nécessaire
        }
        gattCallbacks.clear()
    }
}