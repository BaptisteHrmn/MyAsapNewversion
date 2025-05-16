package com.example.myasapnewversion

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceManager

class AccessoryDetailActivity : AppCompatActivity() {

    private val TAG = "DETAIL_ACTIVITY"
    private lateinit var prefs: SharedPreferences
    private lateinit var mac: String
    private lateinit var originalName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessory_detail)

        val address = intent.getStringExtra("address") ?: return
        val battery = DeviceStorage.getBatteryLevel(this, address)
        val batteryTextView = findViewById<TextView>(R.id.tv_battery_level)
        batteryTextView.text = if (battery != -1) "Batterie : $battery%" else "Batterie : N/A"

        originalName = intent.getStringExtra("device_name") ?: "Inconnu"
        mac = intent.getStringExtra("device_mac") ?: run {
            Log.e(TAG, "[init] ❌ MAC manquante")
            Toast.makeText(this, "Adresse MAC absente", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val tvTitle: TextView = findViewById(R.id.tv_device_name_title)
        val etName: EditText = findViewById(R.id.et_device_name)
        val tvBattery: TextView = findViewById(R.id.tv_battery_level)
        val switchAuto: SwitchCompat = findViewById(R.id.switch_auto_connect)
        val etBatteryThreshold: EditText = findViewById(R.id.et_battery_threshold)
        val btnSave: Button = findViewById(R.id.btn_save_device)
        val btnForget: Button = findViewById(R.id.btn_forget_device)

        tvTitle.text = getString(
            R.string.device_name_with_value,
            getString(R.string.device_name_label),
            originalName
        )

        val storedName = prefs.getString("${mac}_name", null)
        val autoConnect = prefs.getBoolean("${mac}_auto", false)
        val battRaw = prefs.getInt("battery_$mac", -1)
        val storedThreshold = prefs.getInt("battery_threshold_$mac", 20)

        etName.setText(storedName ?: originalName)
        switchAuto.isChecked = autoConnect
        tvBattery.text = if (battRaw >= 0)
            getString(R.string.battery_level, battRaw)
        else
            getString(R.string.battery_level_unknown)
        etBatteryThreshold.setText(storedThreshold.toString())

        btnSave.setOnClickListener {
            val threshold = etBatteryThreshold.text.toString().toIntOrNull() ?: run {
                Toast.makeText(this, "Veuillez entrer un seuil valide", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit()
                .putString("${mac}_name", etName.text.toString().trim())
                .putBoolean("${mac}_auto", switchAuto.isChecked)
                .putInt("battery_threshold_$mac", threshold)
                .apply()
            Log.d(TAG, "[UI_ACTION][SAVE] $mac → seuil=$threshold")
            Toast.makeText(this, "Modifications enregistrées", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnForget.setOnClickListener {
            if (prefs.contains("${mac}_name")) {
                prefs.edit()
                    .remove("${mac}_name")
                    .remove("${mac}_auto")
                    .remove("battery_$mac")
                    .remove("battery_threshold_$mac")
                    .apply()
                Log.d(TAG, "[UI_ACTION][FORGET] $mac")
                Toast.makeText(this, "Appareil oublié", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit()
                    .putString("${mac}_name", originalName)
                    .putBoolean("${mac}_auto", false)
                    .apply()
                Log.d(TAG, "[UI_ACTION][ASSOCIATE] $mac")
                Toast.makeText(this, "Appareil associé", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}