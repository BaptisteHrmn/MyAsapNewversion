package com.example.myasapnewversion

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import java.util.*

class AccessoryDetailActivity : AppCompatActivity() {
    private val TAG = "DETAIL_ACTIVITY"
    private lateinit var prefs: SharedPreferences
    private lateinit var mac: String
    private lateinit var originalName: String

    // UUID Batterie standard
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_CHAR   = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

    private lateinit var tvBattery: TextView
    private var gatt: BluetoothGatt? = null

    // permission launcher
    private val requestConnect = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) readBatteryOverGatt()
        else Toast.makeText(this, "Permission BLUETOOTH_CONNECT refusée", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessory_detail)

        originalName = intent.getStringExtra("device_name") ?: "Inconnu"
        mac = intent.getStringExtra("device_mac") ?: run {
            Log.e(TAG, "[init] ❌ MAC manquante")
            Toast.makeText(this, "Adresse MAC absente", Toast.LENGTH_LONG).show()
            finish(); return
        }
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // vues
        val tvTitle: TextView       = findViewById(R.id.tv_device_name_title)
        val etName: EditText        = findViewById(R.id.et_device_name)
        tvBattery                   = findViewById(R.id.tv_battery_level)
        val switchAuto: SwitchCompat= findViewById(R.id.switch_auto_connect)
        val btnSave: Button         = findViewById(R.id.btn_save_device)
        val btnToggle: Button       = findViewById(R.id.btn_forget_device)

        // titre
        tvTitle.text = getString(
            R.string.device_name_with_value,
            getString(R.string.device_name_label),
            originalName
        )

        // état enregistré ?
        val storedName  = prefs.getString("${mac}_name", null)
        val isAssociated= storedName != null

        // adaptatif : « Associer » ou « Oublier »
        btnToggle.text = if (isAssociated)
            getString(R.string.forget_button)
        else
            getString(R.string.associate_button)

        // valeurs existantes ou défaut
        etName.setText(storedName ?: originalName)
        etName.hint = getString(R.string.device_name_hint)
        switchAuto.isChecked = prefs.getBoolean("${mac}_auto", false)
        switchAuto.text = getString(R.string.auto_connect_label)

        // état batterie pré-enregistré
        val battRaw = prefs.getInt("battery_$mac", -1)
        tvBattery.text = if (battRaw >= 0)
            getString(R.string.battery_level, battRaw)
        else
            getString(R.string.battery_level_unknown)

        // enregistrement/modif
        btnSave.setOnClickListener {
            prefs.edit()
                .putString("${mac}_name", etName.text.toString().trim())
                .putBoolean("${mac}_auto", switchAuto.isChecked)
                .apply()
            Toast.makeText(this, "Modifications enregistrées", Toast.LENGTH_SHORT).show()
            finish()
        }

        // associer ou oublier
        btnToggle.setOnClickListener {
            if (isAssociated) {
                prefs.edit()
                    .remove("${mac}_name")
                    .remove("${mac}_auto")
                    .remove("battery_$mac")
                    .remove("${mac}_connected")
                    .apply()
                Toast.makeText(this, "Appareil oublié", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit()
                    .putString("${mac}_name", originalName)
                    .putBoolean("${mac}_auto", false)
                    .apply()
                Toast.makeText(this, "Appareil associé", Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        // lecture GATT du niveau batterie
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            readBatteryOverGatt()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestConnect.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun readBatteryOverGatt() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val dev = btManager.adapter.getRemoteDevice(mac)
        gatt = dev.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                g: BluetoothGatt, status: Int, newState: Int
            ) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                }
            }
            override fun onServicesDiscovered(
                g: BluetoothGatt, status: Int
            ) {
                val service = g.getService(BATTERY_SERVICE_UUID) ?: return
                val char = service.getCharacteristic(BATTERY_LEVEL_CHAR) ?: return
                g.readCharacteristic(char)
            }
            override fun onCharacteristicRead(
                g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
            ) {
                if (characteristic.uuid == BATTERY_LEVEL_CHAR && status == BluetoothGatt.GATT_SUCCESS) {
                    val level = characteristic.value.first().toInt() and 0xFF
                    runOnUiThread {
                        tvBattery.text = getString(R.string.battery_level, level)
                    }
                    prefs.edit().putInt("battery_$mac", level).apply()
                    g.disconnect()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        gatt?.close()
    }
}