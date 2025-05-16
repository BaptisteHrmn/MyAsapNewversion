package com.example.myasapnewversion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
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
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.*

class AccessoryFragment : Fragment() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val scanResults = mutableListOf<BleDevice>()
    private lateinit var adapter: BleDeviceAdapter

    private val SCAN_DURATION = 10_000L
    private val SCAN_INTERVAL = 15_000L
    private val REQUEST_CODE_PERMISSIONS = 101

    private val logTag = "BLE_SERVICE"

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "BATTERY_UPDATE") {
                val address = intent.getStringExtra("address")
                val battery = intent.getIntExtra("battery", -1)
                adapter.updateBatteryLevel(address, battery)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_accessory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_devices)

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

        startScanLoop()
    }

    override fun onStart() {
        super.onStart()
        requireContext().registerReceiver(batteryReceiver, IntentFilter("BATTERY_UPDATE"))
    }

    override fun onStop() {
        super.onStop()
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
            val device = result.device ?: return
            val mac = device.address ?: return
            val baseName = device.name ?: return
            if (baseName.isBlank()) return

            val customName = prefs.getString("${mac}_name", baseName) ?: baseName
            val auto = prefs.getBoolean("${mac}_auto", false)
            val connected = prefs.getBoolean("${mac}_connected", false)
            val battery = prefs.getInt("battery_${mac}", -1).takeIf { it >= 0 }

            val showThisDevice = auto || baseName.contains("iTAG", true) || baseName.contains("TY", true)
            if (!showThisDevice) return

            val newDevice = BleDevice(
                name = customName,
                rssi = result.rssi,
                mac = mac,
                isAutoConnected = auto,
                isConnected = connected,
                baseName = baseName,
                batteryLevel = battery
            )

            val index = scanResults.indexOfFirst { it.mac == mac }
            if (index >= 0) {
                scanResults[index] = newDevice
                adapter.notifyItemChanged(index)
            } else {
                scanResults.add(newDevice)
                adapter.notifyItemInserted(scanResults.size - 1)
            }

            log("📡 Détecté $mac ($customName), auto=$auto, conn=$connected, batt=${battery ?: "?"}%")
        }
    }

    private fun log(msg: String) {
        Log.d(logTag, "[${timestamp()}] $msg")
    }

    private fun timestamp(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        handler.removeCallbacksAndMessages(null)
    }
}