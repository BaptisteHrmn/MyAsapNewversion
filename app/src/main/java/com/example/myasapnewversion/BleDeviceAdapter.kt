package com.example.myasapnewversion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun getItemCount(): Int = devices.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dev = devices[position]
        holder.nameTv.text = dev.name
        holder.rssiTv.text = "${dev.rssi} dBm"
        holder.battTv.text = dev.battery?.let { "$it %" } ?: "? %"

        // Icône auto-connect (étoile pleine/vide)
        holder.ivAuto.setImageResource(
            if (dev.auto) android.R.drawable.star_on
            else               android.R.drawable.star_off
        )
        // Icône connexion (online/offline)
        holder.ivConn.setImageResource(
            if (dev.connected) android.R.drawable.presence_online
            else               android.R.drawable.presence_offline
        )

        holder.itemView.setOnClickListener { onClick(dev) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTv: TextView  = view.findViewById(R.id.text_name)
        val rssiTv: TextView  = view.findViewById(R.id.text_rssi)
        val battTv: TextView  = view.findViewById(R.id.text_battery)
        val ivConn: ImageView = view.findViewById(R.id.iv_connected)
        val ivAuto: ImageView = view.findViewById(R.id.iv_auto_connect)
    }
}