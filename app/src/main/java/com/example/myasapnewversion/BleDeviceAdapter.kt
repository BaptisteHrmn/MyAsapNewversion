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

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tv_device_name)
        val rssiText: TextView = view.findViewById(R.id.tv_device_rssi)
        val batteryText: TextView = view.findViewById(R.id.tv_device_battery)
        val iconConnected: ImageView = view.findViewById(R.id.iv_connected)
        val iconAutoConnect: ImageView = view.findViewById(R.id.iv_auto_connect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = devices.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.nameText.text = device.name
        holder.rssiText.text = "${device.rssi} dBm"
        holder.batteryText.text = device.batteryLevel?.let { "$it%" } ?: "N/A"
        holder.iconConnected.visibility = if (device.isConnected) View.VISIBLE else View.GONE
        holder.iconAutoConnect.visibility = if (device.isAutoConnected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onClick(device) }
    }
}