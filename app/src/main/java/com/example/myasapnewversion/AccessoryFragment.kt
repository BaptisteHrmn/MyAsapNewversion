package com.example.myasapnewversion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
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
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings

class AccessoryFragment : Fragment() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scanResults = mutableListOf<BleDevice>()
    private lateinit var adapter: BleDeviceAdapter

    private val SCAN_DURATION = 10_000L
    private val REQUEST_CODE_PERMISSIONS = 101

    private val BATTERY_SERVICE_UUID =
        ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_accessory, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RecyclerView
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_devices)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        // Adaptateur (plus besoin du context en paramètre)
        adapter = BleDeviceAdapter(scanResults) { device ->
            Intent(requireContext(), AccessoryDetailActivity::class.java).also { intent ->
                intent.putExtra("device_mac", device.mac)
                intent.putExtra("device_name", device.name)
                startActivity(intent)
            }
        }
        recycler.adapter = adapter

        // Bluetooth
        val manager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        scanner = bluetoothAdapter.bluetoothLeScanner

        // Appareils stockés
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        injectStoredDevices(prefs)

        // Scan continu
        startBleScan()
    }

    private fun injectStoredDevices(prefs: SharedPreferences) {
        val devices = prefs.all.keys
            .filter { it.endsWith("_name") }
            .mapNotNull { key ->
                val mac = key.removeSuffix("_name")
                val name = prefs.getString(key, null) ?: return@mapNotNull null
                if (!BluetoothAdapter.checkBluetoothAddress(mac)) return@mapNotNull null
                val auto = prefs.getBoolean("${mac}_auto", false)
                val connected = prefs.getBoolean("${mac}_connected", false)
                val batteryRaw = prefs.getInt("battery_$mac", -1)
                val battery: Int? = batteryRaw.takeIf { it >= 0 }
                BleDevice(name, -100, mac, auto, connected, name, battery)
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
        log("🟢 Démarrage du scan BLE (LOW_LATENCY continu)")
        handler.postDelayed({
            scanner?.stopScan(scanCallback)
            log("⏹️ Fin du scan BLE")
            startBleScan()
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
        override fun onScanResult(callbackType: Int, result: ScanResult) = handleResult(result)
        override fun onBatchScanResults(results: List<ScanResult>) { results.forEach(::handleResult) }

        private fun handleResult(result: ScanResult) {
            val mac = result.device.address ?: return
            val raw = result.scanRecord?.deviceName ?: result.device.name ?: return
            val trimmed = raw.trim().takeIf { it.length >= 2 && it.any { it.isLetterOrDigit() } } ?: return

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val advBattery = result.scanRecord
                ?.serviceData
                ?.get(BATTERY_SERVICE_UUID)
                ?.firstOrNull()
                ?.toInt()
                ?.and(0xFF)
                ?.takeIf { it in 0..100 }

            val battery: Int? = advBattery?.also {
                prefs.edit().putInt("battery_$mac", it).apply()
            } ?: prefs.getInt("battery_$mac", -1).takeIf { it >= 0 }

            val customName = prefs.getString("${mac}_name", trimmed) ?: trimmed
            val auto      = prefs.getBoolean("${mac}_auto", false)
            val connected = prefs.getBoolean("${mac}_connected", false)

            val newDevice = BleDevice(customName, result.rssi, mac, auto, connected, trimmed, battery)
            val idx = scanResults.indexOfFirst { it.mac == mac }
            if (idx >= 0) {
                scanResults[idx] = newDevice
                adapter.notifyItemChanged(idx)
            } else {
                scanResults.add(newDevice)
                adapter.notifyItemInserted(scanResults.lastIndex)
            }
            log("📡 Détecté : $mac ($customName), auto=$auto, connected=$connected, batt=${battery ?: "?"}%")
        }
    }

    private fun log(msg: String) = Log.d("BLE_SERVICE", "[${timestamp()}] $msg")
    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    override fun onDestroyView() {
        super.onDestroyView()
        scanner?.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
    }
}