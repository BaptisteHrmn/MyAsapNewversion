package com.example.myasapnewversion

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AccessoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BleDeviceAdapter
    private val scanResults = mutableListOf<BleDevice>()
    private val logTag = "AccessoryFragment"
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    // Simule la récupération des appareils associés et renommés
    private fun getAssociatedMacs(context: Context): Set<String> {
        // Ici tu peux utiliser SharedPreferences ou ta base de données pour stocker les adresses MAC associées
        // Pour l’exemple, on retourne un set vide
        return DeviceStorage.getAssociatedMacs(context)
    }

    // Simule la récupération des noms personnalisés
    private fun getCustomName(context: Context, mac: String): String? {
        return DeviceStorage.getCustomName(context, mac)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_accessory, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = BleDeviceAdapter(scanResults) { device ->
            // Ouvre la fiche détaillée de l’appareil
            val intent = Intent(requireContext(), AccessoryDetailActivity::class.java)
            intent.putExtra("mac", device.mac)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        return view
    }

    override fun onResume() {
        super.onResume()
        startScan()
    }

    override fun onPause() {
        super.onPause()
        stopScan()
    }

    private fun startScan() {
        if (isScanning) return
        isScanning = true
        scanResults.clear()
        adapter.notifyDataSetChanged()
        log("🔍 Démarrage du scan BLE")
        // Ici tu lances le scan BLE (à adapter selon ta logique)
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.startScan(leScanCallback)
        // Arrête le scan après 10 secondes
        handler.postDelayed({ stopScan() }, 10000)
    }

    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        log("🛑 Arrêt du scan BLE")
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner?.stopScan(leScanCallback)
    }

    // Callback pour chaque appareil détecté
    private val leScanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult?) {
            result?.device?.let { device ->
                addOrUpdateDevice(device, result.rssi)
            }
        }
    }

    private fun addOrUpdateDevice(device: BluetoothDevice, rssi: Int) {
        val mac = device.address
        val originalName = device.name ?: ""
        val context = requireContext()
        val associatedMacs = getAssociatedMacs(context)
        val customName = getCustomName(context, mac)

        // Filtrage : on garde si nom contient itag ou TY, ou si l’adresse MAC est associée
        val isItagOrTY = originalName.contains("itag", ignoreCase = true) ||
                originalName.contains("TY", ignoreCase = true)
        val isAssociated = associatedMacs.contains(mac)

        if (isItagOrTY || isAssociated) {
            // On affiche le nom personnalisé s’il existe, sinon le nom d’origine
            val displayName = customName ?: originalName
            val batteryLevel = DeviceStorage.getBatteryLevel(context, mac)

            // On regarde si l’appareil est associé à l’appli
            val autoConnected = isAssociated

            val newDevice = BleDevice(
                name = displayName,
                mac = mac,
                batteryLevel = batteryLevel,
                isAutoConnected = autoConnected
            )

            // Mise à jour ou ajout dans la liste
            val index = scanResults.indexOfFirst { it.mac == mac }
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

    private fun log(msg: String) {
        Log.d(logTag, "[${timestamp()}] $msg")
    }

    private fun timestamp(): String {
        return java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
    }
}