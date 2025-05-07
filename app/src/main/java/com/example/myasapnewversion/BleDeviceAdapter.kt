package com.example.myasapnewversion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BleDeviceAdapter(
    private val devices: List<BleDevice>,
    private val clickListener: (BleDevice) -> Unit
) : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView      = itemView.findViewById(R.id.tv_device_name)
        val rssiText: TextView      = itemView.findViewById(R.id.tv_device_rssi)
        val batteryText: TextView   = itemView.findViewById(R.id.tv_device_battery)
        val ivConnected: ImageView  = itemView.findViewById(R.id.iv_connected)
        val ivAutoConnect: ImageView= itemView.findViewById(R.id.iv_auto_connect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = devices.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]

        // Nom et RSSI
        holder.nameText.text   = device.name
        holder.rssiText.text   = "${device.rssi} dBm"

        // Batterie (nullable)
        holder.batteryText.text = device.battery?.let { "$it %" } ?: "?"

        // Icônes de statut
        holder.ivConnected.visibility   = if (device.connected) View.VISIBLE else View.GONE
        holder.ivAutoConnect.visibility = if (device.auto) View.VISIBLE else View.GONE

        // Clic sur la ligne
        holder.itemView.setOnClickListener { clickListener(device) }
    }
}