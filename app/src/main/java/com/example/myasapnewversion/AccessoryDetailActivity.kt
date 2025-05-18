package com.example.myasapnewversion

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AccessoryDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessory_detail)

        val tvDeviceName: TextView = findViewById(R.id.tv_device_name)
        val tvBattery: TextView = findViewById(R.id.tv_battery)
        val btnConnect: Button = findViewById(R.id.btn_connect)
        val etRename: EditText = findViewById(R.id.et_rename)
        val etBatteryNotif: EditText = findViewById(R.id.et_battery_notif)
        val btnSave: Button = findViewById(R.id.btn_save)
        val btnForget: Button = findViewById(R.id.btn_forget)
        val btnRing: Button = findViewById(R.id.btn_ring_device)
        val tvNotifLabel: TextView = findViewById(R.id.tv_notif_label)

        tvNotifLabel.text = "Notification en cas de batterie faible"

        // Ici tu mets le vrai nom, la vraie batterie, etc.
        tvDeviceName.text = "Nom de l'appareil"
        tvBattery.text = "Batterie : 100%"

        btnConnect.setOnClickListener {
            Toast.makeText(this, "Connexion à l'appareil...", Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener {
            Toast.makeText(this, "Appareil enregistré !", Toast.LENGTH_SHORT).show()
        }

        btnForget.setOnClickListener {
            Toast.makeText(this, "Appareil oublié !", Toast.LENGTH_SHORT).show()
        }

        btnRing.setOnClickListener {
            ringDevice()
        }
    }

    private fun ringDevice() {
        Toast.makeText(this, "On fait sonner l'appareil !", Toast.LENGTH_SHORT).show()
        // Ici tu ajoutes la vraie commande BLE pour faire sonner
    }
}