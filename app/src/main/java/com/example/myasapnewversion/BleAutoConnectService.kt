package com.example.myasapnewversion

import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.*

class BleAutoConnectService : Service() {

    companion object {
        private const val TAG = "BLE_SERVICE"
        private val BATTERY_SERVICE_UUID  = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
        private const val SCAN_PERIOD: Long = 10_000L
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())

    // Adresses à auto-connecter (charge-les depuis tes préférences ou ta base)
    private val autoConnectAddresses = mutableSetOf<String>()

    // Pour éviter les doubles connexions simultanées
    private val connecting    = mutableSetOf<String>()
    private val connectedGatts = mutableMapOf<String, BluetoothGatt>()

    // Stockage du niveau de batterie lu
    private val batteryLevels = mutableMapOf<String, Int>()

    // ——— Callback de scan BLE ———
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val addr   = device.address
            val name   = device.name ?: result.scanRecord?.deviceName ?: "?"
            val isAuto = autoConnectAddresses.contains(addr)
            val isConn = connectedGatts.containsKey(addr)
            val batt   = batteryLevels[addr]?.toString() ?: "?"

            Log.d(TAG, "📡 Détecté $addr ($name), auto=$isAuto, conn=$isConn, batt=$batt%")

            if (isAuto && !isConn && !connecting.contains(addr)) {
                connecting.add(addr)
                Log.d(TAG, "🔌 Tentative de connexion à $addr")
                device.connectGatt(this@BleAutoConnectService, false, gattCallback)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "❌ Scan échoué : code $errorCode")
        }
    }

    // ——— Callback GATT ———
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val addr = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "✅ Connecté à $addr")
                connectedGatts[addr] = gatt
                gatt.discoverServices()
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "❌ Déconnecté de $addr")
                connectedGatts.remove(addr)
                batteryLevels.remove(addr)
                connecting.remove(addr)
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.getService(BATTERY_SERVICE_UUID)
                    ?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                    ?.let { characteristic ->
                        gatt.readCharacteristic(characteristic)
                    }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS &&
                characteristic.uuid == BATTERY_LEVEL_CHAR_UUID
            ) {
                val level = characteristic.getIntValue(
                    BluetoothGattCharacteristic.FORMAT_UINT8, 0
                ) ?: 0
                val addr = gatt.device.address
                Log.d(TAG, "🔋 Batterie de $addr : $level%")
                batteryLevels[addr] = level
                // Ici tu peux notifier ton UI (Broadcast, LiveData, etc.)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Initialisation Bluetooth
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter     = manager.adapter
        bluetoothLeScanner   = bluetoothAdapter?.bluetoothLeScanner

        // Charge ici tes adresses auto-connect (SharedPrefs ou base)
        // Exemple : autoConnectAddresses.add("C0:00:00:02:7D:8E")

        startScanLoop()
    }

    private fun startScanLoop() {
        handler.post {
            bluetoothLeScanner?.startScan(scanCallback)
            Log.d(TAG, "🟢 Démarrage du scan BLE")
            handler.postDelayed({
                bluetoothLeScanner?.stopScan(scanCallback)
                Log.d(TAG, "⏹️ Fin du scan BLE")
                startScanLoop()
            }, SCAN_PERIOD)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothLeScanner?.stopScan(scanCallback)
        connectedGatts.values.forEach { it.close() }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent): IBinder? = null
}