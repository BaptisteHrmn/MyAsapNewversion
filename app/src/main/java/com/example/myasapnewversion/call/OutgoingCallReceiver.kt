package com.example.myasapnewversion.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class OutgoingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
        Toast.makeText(
            context,
            "Appel sortant vers : $number",
            Toast.LENGTH_LONG
        ).show()
    }
}