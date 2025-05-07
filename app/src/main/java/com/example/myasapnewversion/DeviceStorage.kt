package com.example.myasapnewversion

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

object DeviceStorage {

    fun saveDevices(context: Context, devices: List<BleDevice>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val jsonArray = JSONArray()

        devices.forEach { device ->
            val obj = JSONObject().apply {
                put("mac", device.mac)
                put("name", device.name)
                put("auto", device.auto)
                put("connected", device.connected)
                put("battery", device.battery ?: -1)
            }
            jsonArray.put(obj)
        }

        prefs.edit()
            .putString("ble_devices", jsonArray.toString())
            .apply()
        Log.d("BLE_SERVICE", "[${TimeUtil.timestamp()}] 💾 Enregistré ${devices.size} appareils")
    }

    fun loadDevices(context: Context): List<BleDevice> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val stored = prefs.getString("ble_devices", null) ?: return emptyList()

        return try {
            val jsonArray = JSONArray(stored)
            val list = mutableListOf<BleDevice>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val mac = obj.getString("mac")
                val name = obj.getString("name")
                val auto = obj.optBoolean("auto", false)
                val connected = obj.optBoolean("connected", false)
                val battRaw = obj.optInt("battery", -1)
                val battery: Int? = if (battRaw >= 0) battRaw else null

                val device = BleDevice(
                    name = name,
                    rssi = -100,
                    mac = mac,
                    auto = auto,
                    connected = connected,
                    baseName = name,
                    battery = battery
                )
                list.add(device)
            }
            list
        } catch (e: Exception) {
            Log.e("BLE_SERVICE", "[${TimeUtil.timestamp()}] ❌ Erreur de chargement : ${e.message}")
            emptyList()
        }
    }
}