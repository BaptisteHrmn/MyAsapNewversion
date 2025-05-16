package com.example.myasapnewversion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class BleDevice(
    val name: String?,
    val address: String,
    var batteryLevel: Int = -1
)

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
        holder.deviceName.text = device.name ?: "Appareil inconnu"
        holder.deviceAddress.text = device.address
        holder.deviceBattery.text = if (device.batteryLevel != -1)
            "Batterie : ${device.batteryLevel}%"
        else
            "Batterie : N/A"
        holder.itemView.setOnClickListener { onClick(device) }
    }

    fun updateBatteryLevel(address: String?, battery: Int) {
        val index = devices.indexOfFirst { it.address == address }
        if (index != -1) {
            devices[index].batteryLevel = battery
            notifyItemChanged(index)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.tv_device_name)
        val deviceAddress: TextView = view.findViewById(R.id.tv_device_mac)
        val deviceBattery: TextView = view.findViewById(R.id.tv_device_battery)
    }
}