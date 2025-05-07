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

    private lateinit var prefs: SharedPreferences
    private lateinit var mac: String
    private lateinit var originalName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // On n'utilise pas DataBinding ici, juste setContentView
        setContentView(R.layout.activity_accessory_detail)

        // Récupération des extras
        originalName = intent.getStringExtra("device_name") ?: "Inconnu"
        mac = intent.getStringExtra("device_mac") ?: run {
            Log.e("DETAIL_ACTIVITY", "[init] ❌ MAC manquante")
            Toast.makeText(this, "Adresse MAC absente", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Liens vers les vues
        val tvTitle: TextView      = findViewById(R.id.tv_device_name_title)
        val etName: EditText       = findViewById(R.id.et_device_name)
        val tvBattery: TextView    = findViewById(R.id.tv_battery_level)
        val switchAuto: SwitchCompat = findViewById(R.id.switch_auto_connect)
        val btnSave: Button        = findViewById(R.id.btn_save_device)
        val btnForget: Button      = findViewById(R.id.btn_forget_device)

        // Titre et valeurs initiales
        title = getString(R.string.device_name_label)
        tvTitle.text = getString(R.string.device_name_label) + " : $originalName"

        val storedName  = prefs.getString("${mac}_name", null)
        val autoConnect = prefs.getBoolean("${mac}_auto", false)
        val batteryRaw  = prefs.getInt("battery_${mac}", -1)

        etName.setText(storedName ?: originalName)
        etName.hint = getString(R.string.device_name_hint)

        switchAuto.isChecked = autoConnect
        switchAuto.text = getString(R.string.auto_connect_label)

        tvBattery.text = if (batteryRaw >= 0) {
            getString(R.string.battery_level, batteryRaw)
        } else {
            getString(R.string.battery_level_unknown)
        }

        btnSave.text   = getString(R.string.save_button)
        btnForget.text = getString(R.string.forget_button)

        // Enregistrer les modifications
        btnSave.setOnClickListener {
            prefs.edit()
                .putString("${mac}_name", etName.text.toString().trim())
                .putBoolean("${mac}_auto", switchAuto.isChecked)
                .apply()
            Toast.makeText(this, "Modifications enregistrées", Toast.LENGTH_SHORT).show()
            Log.d("DETAIL_ACTIVITY", "[save] ✅ Appareil $mac mis à jour")
            finish()
        }

        // Oublier l’appareil
        btnForget.setOnClickListener {
            prefs.edit()
                .remove("${mac}_name")
                .remove("${mac}_auto")
                .remove("battery_${mac}")
                .remove("${mac}_connected")
                .apply()
            Toast.makeText(this, "Appareil oublié", Toast.LENGTH_SHORT).show()
            Log.w("DETAIL_ACTIVITY", "[forget] ❌ Appareil $mac supprimé")
            finish()
        }
    }
}