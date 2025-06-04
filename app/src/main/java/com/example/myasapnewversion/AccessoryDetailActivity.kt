package com.example.myasapnewversion

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AccessoryDetailActivity : AppCompatActivity() {

    private var mac: String? = null
    private var device: BleDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessory_detail)

        mac = intent.getStringExtra("mac")
        if (mac == null) {
            Toast.makeText(this, "Adresse MAC absente", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        device = DeviceStorage.loadDevices(this).find { it.mac == mac }
        val tvDeviceName: TextView = findViewById(R.id.tv_device_name)
        val tvBattery: TextView = findViewById(R.id.tv_battery)
        val btnConnect: Button = findViewById(R.id.btn_connect)
        val etRename: EditText = findViewById(R.id.et_rename)
        val etBatteryNotif: EditText = findViewById(R.id.et_battery_notif)
        val btnSave: Button = findViewById(R.id.btn_save)
        val btnForget: Button = findViewById(R.id.btn_forget)
        val btnRing: Button = findViewById(R.id.btn_ring_device)
        val btnStopRing: Button = findViewById(R.id.btn_stop_ring)
        val tvNotifLabel: TextView = findViewById(R.id.tv_notif_label)

        tvNotifLabel.text = "Notification en cas de batterie faible"

        tvDeviceName.text = device?.name ?: "Nom inconnu"
        tvBattery.text = "Batterie : ${device?.batteryLevel?.toString() ?: "?"}%"
        etRename.setText(device?.name ?: "")

        btnConnect.text = if (device?.isAutoConnected == true) "Désactiver auto-connexion" else "Associer l'appareil"

        btnConnect.setOnClickListener {
            toggleAutoConnect()
        }

        btnSave.setOnClickListener {
            saveCustomName(etRename.text.toString())
        }

        btnForget.setOnClickListener {
            forgetDevice()
        }

        btnRing.setOnClickListener {
            ringDevice()
        }

        btnStopRing.setOnClickListener {
            stopRingDevice()
        }
    }

    private fun saveCustomName(name: String) {
        mac?.let { macAddr ->
            val devices = DeviceStorage.loadDevices(this).toMutableList()
            val idx = devices.indexOfFirst { it.mac == macAddr }
            if (idx >= 0) {
                val updated = devices[idx].copy(name = name)
                devices[idx] = updated
                DeviceStorage.saveDevices(this, devices)
                Toast.makeText(this, "Nom enregistré", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleAutoConnect() {
        mac?.let { macAddr ->
            val devices = DeviceStorage.loadDevices(this).toMutableList()
            val idx = devices.indexOfFirst { it.mac == macAddr }
            if (idx >= 0) {
                val updated = devices[idx].copy(isAutoConnected = !devices[idx].isAutoConnected)
                devices[idx] = updated
                DeviceStorage.saveDevices(this, devices)
                Toast.makeText(this, if (updated.isAutoConnected) "Appareil associé !" else "Auto-connexion désactivée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun forgetDevice() {
        mac?.let { macAddr ->
            val devices = DeviceStorage.loadDevices(this).filter { it.mac != macAddr }
            DeviceStorage.saveDevices(this, devices)
            Toast.makeText(this, "Appareil oublié !", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun ringDevice() {
        mac?.let { macAddr ->
            val service = BleServiceLocator.getService()
            if (service != null) {
                service.ringDevice(macAddr)
                Toast.makeText(this, "Commande envoyée pour faire sonner l'appareil", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Service BLE non disponible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRingDevice() {
        mac?.let { macAddr ->
            val service = BleServiceLocator.getService()
            if (service != null) {
                service.stopRingDevice(macAddr)
                Toast.makeText(this, "Commande envoyée pour arrêter le bip", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Service BLE non disponible", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// Singleton pour accéder au service BLE
object BleServiceLocator {
    private var service: BleAutoConnectService? = null
    fun setService(s: BleAutoConnectService?) { service = s }
    fun getService(): BleAutoConnectService? = service
}