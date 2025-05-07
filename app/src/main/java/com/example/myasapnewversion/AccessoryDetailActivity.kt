package com.example.myasapnewversion

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceManager

class AccessoryDetailActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var mac: String? = null
    private lateinit var originalName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessory_detail)

        originalName = intent.getStringExtra("device_name") ?: "Inconnu"
        mac = intent.getStringExtra("device_mac")

        if (mac.isNullOrBlank()) {
            Log.e("DETAIL_ACTIVITY", "[init] ❌ MAC manquante")
            Toast.makeText(this, "Adresse MAC absente", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val tvTitle = findViewById<TextView>(R.id.tv_device_name_title)
        val etName = findViewById<EditText>(R.id.et_device_name)
        val tvBattery = findViewById<TextView>(R.id.tv_battery_level)
        val switchAuto = findViewById<SwitchCompat>(R.id.switch_auto_connect)
        val btnSave = findViewById<Button>(R.id.btn_save_device)
        val btnForget = findViewById<Button>(R.id.btn_forget_device)

        title = "Détail de l'appareil"
        tvTitle.text = "Appareil détecté : $originalName"

        val storedName = prefs.getString("${mac}_name", null)
        val autoConnect = prefs.getBoolean("${mac}_auto", false)
        val battery = prefs.getInt("battery_${mac}", -1)

        etName.setText(storedName ?: originalName)
        switchAuto.isChecked = autoConnect
        tvBattery.text = if (battery >= 0) {
            "Batterie actuelle : $battery%"
        } else {
            "Batterie actuelle : Inconnue"
        }

        btnSave.setOnClickListener {
            prefs.edit()
                .putString("${mac}_name", etName.text.toString().trim())
                .putBoolean("${mac}_auto", switchAuto.isChecked)
                .apply()
            Toast.makeText(this, "Modifications enregistrées", Toast.LENGTH_SHORT).show()
            Log.d("DETAIL_ACTIVITY", "[save] ✅ Appareil $mac mis à jour")
            finish()
        }

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