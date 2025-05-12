// Fichier : app/src/main/java/com/example/myasapnewversion/BleAutoConnectService.kt
package com.example.myasapnewversion

import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BleDeviceAdapter(
    private val devices: List<BleDevice>,
    private val onClick: (BleDevice) -> Unit
) : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dev = devices[position]
        holder.nameTv.text = dev.name
        holder.rssiTv.text = "RSSI: ${dev.rssi}"
        holder.battTv.text = "Batterie: ${dev.battery ?: "Inconnu"}%"

        holder.ivAuto.setImageResource(
            if (dev.auto) android.R.drawable.star_on else android.R.drawable.star_off
        )
        holder.ivConn.setImageResource(
            if (dev.connected) android.R.drawable.presence_online else android.R.drawable.presence_offline
        )

        holder.itemView.setOnClickListener {
            Log.d("UI_ACTION", "[ITEM_CLICK] ${dev.mac} (${dev.name})")
            onClick(dev)
        }
    }

    override fun getItemCount(): Int = devices.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTv: TextView = view.findViewById(R.id.text_name)
        val rssiTv: TextView = view.findViewById(R.id.text_rssi)
        val battTv: TextView = view.findViewById(R.id.text_battery)
        val ivConn: ImageView = view.findViewById(R.id.iv_connected)
        val ivAuto: ImageView = view.findViewById(R.id.iv_auto_connect)
    }
}