package com.example.myasapnewversion

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
        val macTv: TextView    = view.findViewById(R.id.text_mac)
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

        // Mise à jour du texte
        holder.nameTv.text = dev.name
        holder.macTv.text  = dev.mac
        holder.rssiTv.text = "${dev.rssi} dBm"
        holder.battTv.text = dev.battery?.let { "$it %" } ?: "–"

        // Icônes selon état
        holder.ivAuto.visibility = if (dev.auto) View.VISIBLE else View.GONE
        holder.ivConn.visibility = if (dev.connected) View.VISIBLE else View.GONE

        // Clic court : votre comportement existant
        holder.itemView.setOnClickListener { onClick(dev) }

        // Clic long : pairing ou connexion GATT
        holder.itemView.setOnLongClickListener {
            // Récupération du BluetoothDevice par son MAC
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val btDevice: BluetoothDevice = btManager.adapter.getRemoteDevice(dev.mac)

            if (btDevice.bondState != BluetoothDevice.BOND_BONDED) {
                // Pas encore appairé → lancer le pairing
                btDevice.createBond()
            } else {
                // Déjà appairé → démarrer service GATT comme avant
                val intent = Intent(context, BleAutoConnectService::class.java).apply {
                    action = if (dev.connected)
                        BleAutoConnectService.ACTION_DISCONNECT
                    else
                        BleAutoConnectService.ACTION_CONNECT
                    putExtra("device_mac", dev.mac)
                }
                ContextCompat.startForegroundService(context, intent)
            }
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