package com.example.myasapnewversion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AccessoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BleDeviceAdapter
    private val scanResults = mutableListOf<BleDevice>()

    private val bleUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadDevices()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_accessory, container, false)
        recyclerView = view.findViewById(R.id.recycler_devices)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = BleDeviceAdapter(scanResults) { device ->
            val intent = Intent(requireContext(), AccessoryDetailActivity::class.java)
            intent.putExtra("mac", device.mac)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        return view
    }

    override fun onResume() {
        super.onResume()
        requireContext().registerReceiver(bleUpdateReceiver, IntentFilter("BLE_LIST_UPDATE"))
        reloadDevices()
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(bleUpdateReceiver)
    }

    private fun reloadDevices() {
        val devices = DeviceStorage.loadDevices(requireContext())
        scanResults.clear()
        scanResults.addAll(devices)
        adapter.notifyDataSetChanged()
    }
}