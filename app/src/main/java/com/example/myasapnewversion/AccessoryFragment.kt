package com.example.myasapnewversion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AccessoryFragment : Fragment() {

    private val logTag = "AccessoryFragment"
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var adapter: BleDeviceAdapter
    private val scanResults = mutableListOf<BleDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_DURATION = 15000L // 15 secondes
    private val SCAN_INTERVAL = 5000L // 5 secondes
    private val REQUEST_CODE_PERMISSIONS = 1001
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_accessory, container, false)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_devices)
            ?: throw IllegalStateException("Le layout fragment_accessory.xml doit contenir un RecyclerView avec l'id @+id/recycler_devices")

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        adapter = BleDeviceAdapter(scanResults) { device ->
            val intent = Intent(requireContext(), AccessoryDetailActivity::class.java)
            intent.putExtra("device_mac", device.mac)
            intent.putExtra("device_name", device.baseName)
            startActivity(intent)
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val manager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        return view
    }

    override fun onResume() {
        super.onResume()
        scanResults.clear()
        adapter.notifyDataSetChanged()
        startScanLoop()
        requireContext().registerReceiver(batteryReceiver, IntentFilter("BATTERY_UPDATE"))
    }

    override fun onPause() {
        super.onPause()
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
        requireContext().unregisterReceiver(batteryReceiver)
    }

    private fun startScanLoop() {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }

        bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
        log("🟢 Démarrage du scan BLE")

        handler.postDelayed({
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
            log("⏹️ Fin du scan BLE")
            handler.postDelayed({ startScanLoop() }, SCAN_INTERVAL)
        }, SCAN_DURATION)
    }

    private fun hasPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(requireActivity(), permissions, REQUEST_CODE_PERMISSIONS)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val mac = device.address
            val rssi = result.rssi
            val rawName = device.name ?: ""

            // Récupère le nom personnalisé si existant
            val customName = prefs.getString("${mac}_name", null)
            val isItagOrTY = rawName.contains("itag", ignoreCase = true) || rawName.contains("ty", ignoreCase = true)
            val isRenamed = customName != null

            if (!isItagOrTY && !isRenamed) {
                return
            }

            val displayName = customName ?: if (rawName.isNotBlank()) rawName else "Appareil inconnu"
            val autoConnect = prefs.getBoolean("${mac}_auto", false)
            val batteryLevel = DeviceStorage.getBatteryLevel(requireContext(), mac)

            val index = scanResults.indexOfFirst { it.mac == mac }
            val newDevice = BleDevice(
                name = displayName,
                rssi = rssi,
                mac = mac,
                isAutoConnected = autoConnect,
                isConnected = false,
                baseName = rawName,
                batteryLevel = if (batteryLevel >= 0) batteryLevel else null
            )
            if (index >= 0) {
                scanResults[index] = newDevice
                adapter.notifyItemChanged(index)
            } else {
                scanResults.add(newDevice)
                adapter.notifyItemInserted(scanResults.size - 1)
            }
            log("📡 Détecté $mac ($displayName)")
        }
    }

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // À compléter si tu veux mettre à jour le niveau de batterie en live
        }
    }

    private fun log(msg: String) {
        Log.d(logTag, "[${timestamp()}] $msg")
    }

    private fun timestamp(): String {
        return java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
    }
}