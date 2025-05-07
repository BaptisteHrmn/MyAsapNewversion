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
        val nameTv: TextView   = view.findViewById(R.id.text_name)
        val rssiTv: TextView   = view.findViewById(R.id.text_rssi)
        val battTv: TextView   = view.findViewById(R.id.text_battery)
        val ivAuto: ImageView  = view.findViewById(R.id.iv_auto_connect)
        val ivConn: ImageView  = view.findViewById(R.id.iv_connected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dev = devices[position]

        // Texte
        holder.nameTv.text = dev.name
        holder.rssiTv.text = "${dev.rssi} dBm"
        holder.battTv.text = dev.battery?.let { "$it %" } ?: "–"

        // Icônes : on affiche ou cache selon l’état, sans utiliser de drawables manquants
        holder.ivAuto.visibility = if (dev.auto) View.VISIBLE else View.GONE
        holder.ivConn.visibility = if (dev.connected) View.VISIBLE else View.GONE

        // Clic standard
        holder.itemView.setOnClickListener { onClick(dev) }

        // Clic long : lance le service de (dé)connexion auto
        holder.itemView.setOnLongClickListener {
            val intent = Intent(context, BleAutoConnectService::class.java).apply {
                action = if (dev.connected)
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