package com.example.myasapnewversion.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.widget.Toast

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bundle: Bundle? = intent.extras
        if (bundle != null) {
            val pdus = bundle["pdus"] as? Array<*>
            pdus?.forEach { pdu ->
                val msg = SmsMessage.createFromPdu(pdu as ByteArray)
                val sender = msg.originatingAddress
                val body = msg.messageBody

                Toast.makeText(
                    context,
                    "SMS reçu de $sender: $body",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}