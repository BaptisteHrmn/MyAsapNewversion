package com.example.myasapnewversion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BleDeviceAdapter(
    private val devices: MutableList<BleDevice>,
    private val onClick: (BleDevice) -> Unit
) : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = devices.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceName.text = device.name.ifEmpty { "Appareil inconnu" }
        holder.deviceAddress.text = device.mac
        holder.deviceBattery.text = device.batteryLevel?.let { "Batterie : $it%" } ?: "Batterie : N/A"
        // Affiche l'icône auto-connecté si besoin
        holder.deviceAutoConnect.visibility = if (device.isAutoConnected) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(device) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.tv_device_name)
        val deviceAddress: TextView = view.findViewById(R.id.tv_device_mac)
        val deviceBattery: TextView = view.findViewById(R.id.tv_device_battery)
        val deviceAutoConnect: ImageView = view.findViewById(R.id.iv_auto_connect)
    }
}