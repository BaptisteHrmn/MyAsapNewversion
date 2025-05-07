@file:Suppress("NotifyDataSetChanged")

package com.example.myasapnewversion

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("MissingPermission")
class AccessoryFragment : Fragment() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var scanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scanResults = mutableListOf<BleDevice>()
    private lateinit var adapter: BleDeviceAdapter

    private val SCAN_DURATION = 10_000L
    private val SCAN_INTERVAL = 15_000L
    private val REQUEST_CODE_PERMISSIONS = 101

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_accessory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_devices)

        adapter = BleDeviceAdapter(scanResults) { device ->
            val intent = Intent(requireContext(), AccessoryDetailActivity::class.java)
            intent.putExtra("device_mac", device.mac)
            intent.putExtra("device_name", device.name)
            startActivity(intent)
        }

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        bluetoothAdapter = (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        scanner = bluetoothAdapter.bluetoothLeScanner

        injectStoredDevices(prefs)
        startBleScan()
    }

    private fun injectStoredDevices(prefs: SharedPreferences) {
        val devices = prefs.all.keys
            .filter { it.endsWith("_name") }
            .mapNotNull { key ->
                val mac = key.removeSuffix("_name")
                val baseName = prefs.getString(key, null) ?: return@mapNotNull null
                if (!BluetoothAdapter.checkBluetoothAddress(mac)) return@mapNotNull null
                val auto      = prefs.getBoolean("${mac}_auto", false)
                val connected = prefs.getBoolean("${mac}_connected", false)
                val battRaw   = prefs.getInt("battery_$mac", -1)
                val battery: Int? = battRaw.takeIf { it >= 0 }

                BleDevice(
                    name      = prefs.getString("${mac}_name", baseName) ?: baseName,
                    rssi      = -100,
                    mac       = mac,
                    auto      = auto,
                    connected = connected,
                    baseName  = baseName,
                    battery   = battery
                )
            }

        scanResults.apply {
            clear()
            addAll(devices)
        }
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }
        scanner?.startScan(scanCallback)
        log("🟢 Démarrage du scan BLE")
        handler.postDelayed({
            scanner?.stopScan(scanCallback)
            log("⏹️ Fin du scan BLE")
            handler.postDelayed({ startBleScan() }, SCAN_INTERVAL)
        }, SCAN_DURATION)
    }

    private fun hasPermissions(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }
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
            val dev = result.device ?: return
            val mac = dev.address ?: return
            val baseName = dev.name ?: return
            if (baseName.isBlank()) return

            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val customName = prefs.getString("${mac}_name", baseName) ?: baseName
            val auto       = prefs.getBoolean("${mac}_auto", false)
            val connected  = prefs.getBoolean("${mac}_connected", false)
            val battRaw    = prefs.getInt("battery_$mac", -1)
            val battery: Int? = battRaw.takeIf { it >= 0 }

            val newDevice = BleDevice(
                name      = customName,
                rssi      = result.rssi,
                mac       = mac,
                auto      = auto,
                connected = connected,
                baseName  = baseName,
                battery   = battery
            )

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