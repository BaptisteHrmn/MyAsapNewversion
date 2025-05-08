package com.example.myasapnewversion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid              // ← import ajouté
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AccessoryFragment : Fragment() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scanResults = mutableListOf<BleDevice>()
    private lateinit var adapter: BleDeviceAdapter

    private val SCAN_DURATION = 10_000L
    private val REQUEST_CODE_PERMISSIONS = 101

    // UUID standard du service Batterie
    private val BATTERY_SERVICE_UUID =
        ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ): View = inflater.inflate(R.layout.fragment_accessory, container, false)

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RecyclerView & Adaptateur
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_devices)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = BleDeviceAdapter(scanResults) { device ->
            Log.d("UI_ACTION", "[FRAG_CLICK] Détail ${device.mac}")
            val intent = android.content.Intent(requireContext(), AccessoryDetailActivity::class.java)
            intent.putExtra("device_mac", device.mac)
            intent.putExtra("device_name", device.name)
            startActivity(intent)
        }
        recycler.adapter = adapter

        // Init Bluetooth
        val manager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        scanner = bluetoothAdapter.bluetoothLeScanner

        // Charge mémoires
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        injectStoredDevices(prefs)

        // Lance le scan continu
        startBleScan()
    }

    private fun injectStoredDevices(prefs: SharedPreferences) {
        val devices = prefs.all.keys
            .filter { it.endsWith("_name") }
            .mapNotNull { key ->
                val mac = key.removeSuffix("_name")
                if (!BluetoothAdapter.checkBluetoothAddress(mac)) return@mapNotNull null
                val name = prefs.getString(key, null) ?: return@mapNotNull null
                val auto = prefs.getBoolean("${mac}_auto", false)
                val connected = prefs.getBoolean("${mac}_connected", false)
                val battRaw = prefs.getInt("battery_$mac", -1)
                val batt = battRaw.takeIf { it >= 0 }
                BleDevice(name, -100, mac, auto, connected, name, batt)
            }
        scanResults.clear()
        scanResults.addAll(devices)
        adapter.notifyDataSetChanged()
    }

    private fun startBleScan() {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(null, settings, scanCallback)
        log("🟢 Démarrage du scan BLE")
        handler.postDelayed({
            scanner?.stopScan(scanCallback)
            log("⏹️ Fin du scan BLE")
            handler.postDelayed({ startBleScan() }, 0)
        }, SCAN_DURATION)
    }

    private fun hasPermissions(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(requireActivity(), perms, REQUEST_CODE_PERMISSIONS)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { handleResult(it) }
        }

        private fun handleResult(result: ScanResult) {
            val dev = result.device ?: return
            val mac = dev.address
            val raw = result.scanRecord?.deviceName ?: dev.name
            if (raw.isNullOrBlank()) return
            val trimmed = raw.trim().takeIf { it.length >= 2 } ?: return

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            // essai lecture batterie via ADV
            val advBatt = result.scanRecord
                ?.serviceData
                ?.get(BATTERY_SERVICE_UUID)
                ?.firstOrNull()
                ?.toInt()
                ?.and(0xFF)
                ?.takeIf { it in 0..100 }

            val battery: Int? = if (advBatt != null) {
                prefs.edit().putInt("battery_$mac", advBatt).apply()
                Log.d("BLE_BATT", "[ADV] $mac → $advBatt %")
                advBatt
            } else {
                prefs.getInt("battery_$mac", -1).takeIf { it >= 0 }?.also {
                    Log.d("BLE_BATT", "[SAVED] $mac → $it %")
                }
            }

            val customName = prefs.getString("${mac}_name", trimmed) ?: trimmed
            val auto = prefs.getBoolean("${mac}_auto", false)
            val connected = prefs.getBoolean("${mac}_connected", false)

            val newDev = BleDevice(customName, result.rssi, mac, auto, connected, trimmed, battery)
            val idx = scanResults.indexOfFirst { it.mac == mac }
            if (idx >= 0) {
                scanResults[idx] = newDev
                adapter.notifyItemChanged(idx)
            } else {
                scanResults.add(newDev)
                adapter.notifyItemInserted(scanResults.lastIndex)
            }

            log("📡 Détecté $mac ($customName), auto=$auto, conn=$connected, batt=${battery ?: "?"}%")
        }
    }

    private fun log(msg: String) {
        Log.d("BLE_SERVICE", "[${time()}] $msg")
    }
    private fun time(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    override fun onDestroyView() {
        super.onDestroyView()
        scanner?.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
    }
}