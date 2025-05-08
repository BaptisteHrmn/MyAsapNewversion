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

        originalName = intent.getStringExtra("device_name") ?: "Inconnu"
        mac = intent.getStringExtra("device_mac") ?: run {
            Log.e(TAG, "[init] ❌ MAC manquante")
            Toast.makeText(this, "Adresse MAC absente", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val tvTitle: TextView       = findViewById(R.id.tv_device_name_title)
        val etName: EditText        = findViewById(R.id.et_device_name)
        val tvBattery: TextView     = findViewById(R.id.tv_battery_level)
        val switchAuto: SwitchCompat= findViewById(R.id.switch_auto_connect)
        val btnSave: Button         = findViewById(R.id.btn_save_device)
        val btnForget: Button       = findViewById(R.id.btn_forget_device)

        // Titre
        tvTitle.text = getString(
            R.string.device_name_with_value,
            getString(R.string.device_name_label),
            originalName
        )

        // Valeurs initiales
        val storedName  = prefs.getString("${mac}_name", null)
        val autoConnect = prefs.getBoolean("${mac}_auto", false)
        val battRaw     = prefs.getInt("battery_$mac", -1)

        etName.setText(storedName ?: originalName)
        etName.hint = getString(R.string.device_name_hint)

        switchAuto.isChecked = autoConnect
        switchAuto.text = getString(R.string.auto_connect_label)

        tvBattery.text = if (battRaw >= 0)
            getString(R.string.battery_level, battRaw)
        else
            getString(R.string.battery_level_unknown)

        btnSave.text = getString(R.string.save_button)
        // Texte du bouton Oublier/Associer selon présence en prefs
        val isAssociated = prefs.contains("${mac}_name")
        btnForget.text = if (isAssociated)
            getString(R.string.forget_button)
        else
            getString(R.string.associate_button)

        // SAVE
        btnSave.setOnClickListener {
            prefs.edit()
                .putString("${mac}_name", etName.text.toString().trim())
                .putBoolean("${mac}_auto", switchAuto.isChecked)
                .apply()
            Log.d(TAG, "[UI_ACTION][SAVE] $mac → name='${etName.text}', auto=${switchAuto.isChecked}")
            Toast.makeText(this, "Modifications enregistrées", Toast.LENGTH_SHORT).show()
            finish()
        }

        // FORGET ou ASSOCIATE
        btnForget.setOnClickListener {
            if (isAssociated) {
                prefs.edit()
                    .remove("${mac}_name")
                    .remove("${mac}_auto")
                    .remove("battery_$mac")
                    .remove("${mac}_connected")
                    .apply()
                Log.d(TAG, "[UI_ACTION][FORGET] $mac")
                Toast.makeText(this, "Appareil oublié", Toast.LENGTH_SHORT).show()
            } else {
                // on associe l'appareil avec nom original + auto false par défaut
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