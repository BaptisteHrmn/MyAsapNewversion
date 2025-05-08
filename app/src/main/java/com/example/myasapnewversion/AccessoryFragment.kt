package com.example.myasapnewversion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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

class AccessoryFragment : Fragment() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scanResults = mutableListOf<BleDevice>()
    private lateinit var adapter: BleDeviceAdapter

    private val SCAN_DURATION = 10_000L
    private val REQUEST_CODE_PERMISSIONS = 101

    // UUID standard du service Batterie
    private val BATTERY_SERVICE_UUID = ParcelUuid(UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_accessory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RecyclerView
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_devices)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        // Adaptateur
        adapter = BleDeviceAdapter(
            requireContext(),
            scanResults
        ) { device ->
            val intent = Intent(requireContext(), AccessoryDetailActivity::class.java)
            intent.putExtra("device_mac", device.mac)
            intent.putExtra("device_name", device.name)
            startActivity(intent)
        }
        recycler.adapter = adapter

        // Initialisation Bluetooth
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val manager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        scanner = bluetoothAdapter.bluetoothLeScanner

        // Charge les appareils mémorisés
        injectStoredDevices(prefs)
        // Lance le scan continu
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
                val batteryRaw = prefs.getInt("battery_${mac}", -1)
                val battery: Int? = if (batteryRaw >= 0) batteryRaw else null
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
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }
        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { handleResult(it) }
        }

        private fun handleResult(result: ScanResult) {
            val device = result.device ?: return
            val mac = device.address ?: return

            // Lecture nom brut
            val raw = result.scanRecord?.deviceName ?: device.name
            if (raw.isNullOrBlank()) return
            val trimmed = raw.trim()
            if (trimmed.equals("Inconnu", true) || trimmed.length < 2 || trimmed.all { !it.isLetterOrDigit() }) return

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

            // Tenter de lire le niveau de batterie depuis l'advertisement (serviceData)
            val serviceData = result.scanRecord?.serviceData
            val advBattery = serviceData?.get(BATTERY_SERVICE_UUID)
                ?.firstOrNull()
                ?.toInt()
                ?.and(0xFF)
                ?.takeIf { it in 0..100 }

            val battery: Int? = when {
                advBattery != null -> {
                    // on sauvegarde pour réutilisation ultérieure
                    prefs.edit().putInt("battery_$mac", advBattery).apply()
                    advBattery
                }
                else -> {
                    val saved = prefs.getInt("battery_$mac", -1)
                    if (saved >= 0) saved else null
                }
            }

            val customName = prefs.getString("${mac}_name", trimmed) ?: trimmed
            val auto = prefs.getBoolean("${mac}_auto", false)
            val connected = prefs.getBoolean("${mac}_connected", false)

            val newDevice = BleDevice(
                name      = customName,
                rssi      = result.rssi,
                mac       = mac,
                auto      = auto,
                connected = connected,
                baseName  = trimmed,
                battery   = battery
            )

            val index = scanResults.indexOfFirst { it.mac == mac }
            if (index >= 0) {
                scanResults[index] = newDevice
                adapter.notifyItemChanged(index)
            } else {
                scanResults.add(newDevice)
                adapter.notifyItemInserted(scanResults.size - 1)
            }

            log("📡 Détecté : $mac ($customName), auto=$auto, connected=$connected, batt=${battery ?: "?"}%")
        }
    }

    private fun log(msg: String) {
        Log.d("BLE_SERVICE", "[${timestamp()}] $msg")
    }

    private fun timestamp(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    override fun onDestroyView() {
        super.onDestroyView()
        scanner?.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
    }
}