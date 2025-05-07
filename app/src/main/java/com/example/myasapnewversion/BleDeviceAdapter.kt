package com.example.myasapnewversion

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BleDeviceAdapter(
    private val context: Context,
    private val devices: MutableList<BleDevice>,
    private val onClick: (BleDevice) -> Unit
) : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTv: TextView = view.findViewById(R.id.tv_device_name)
        val rssiTv: TextView = view.findViewById(R.id.tv_device_rssi)
        val battTv: TextView = view.findViewById(R.id.tv_device_battery)
        val ivAuto: ImageView = view.findViewById(R.id.iv_auto_connect)
        val ivConn: ImageView = view.findViewById(R.id.iv_connected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dev = devices[position]
        holder.nameTv.text = dev.name
        holder.rssiTv.text = "${dev.rssi} dBm"
        holder.battTv.text = if (dev.batteryLevel >= 0) "${dev.batteryLevel} %" else "–"
        holder.ivAuto.setImageResource(
            if (dev.isAutoConnected) R.drawable.ic_auto_connect else R.drawable.ic_auto_connect_off
        )
        holder.ivConn.setImageResource(
            if (dev.isConnected) R.drawable.ic_connected else R.drawable.ic_disconnected
        )
        holder.itemView.setOnClickListener { onClick(dev) }
        holder.itemView.setOnLongClickListener {
            val intent = Intent(context, BleAutoConnectService::class.java).apply {
                action = if (dev.isConnected)
                    BleAutoConnectService.ACTION_DISCONNECT
                else
                    BleAutoConnectService.ACTION_CONNECT
                putExtra("device_mac", dev.mac)
            }
            ContextCompat.startForegroundService(context, intent)
            true
        }
    }

    override fun getItemCount(): Int = devices.size

    fun update(devs: List<BleDevice>) {
        devices.clear()
        devices.addAll(devs)
        notifyDataSetChanged()
    }
}