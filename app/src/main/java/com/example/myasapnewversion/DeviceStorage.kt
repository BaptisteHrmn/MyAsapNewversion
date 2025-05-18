package com.example.myasapnewversion

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

object DeviceStorage {

    private const val TAG = "BLE_SERVICE"

    fun saveDevices(context: Context, devices: List<BleDevice>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val jsonArray = JSONArray()

        devices.forEach { device ->
            val obj = JSONObject().apply {
                put("mac", device.mac)
                put("name", device.name)
                put("auto", device.isAutoConnected)
                put("battery", device.batteryLevel ?: -1)
            }
            jsonArray.put(obj)
        }

        prefs.edit().putString("ble_devices", jsonArray.toString()).apply()
        Log.d(TAG, "[${TimeUtil.timestamp()}] 💾 Enregistré ${devices.size} appareils")
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
                val battery = obj.optInt("battery", -1)

                val device = BleDevice(
                    name = name,
                    mac = mac,
                    batteryLevel = if (battery >= 0) battery else null,
                    isAutoConnected = auto
                )

                list.add(device)
            }

            list
        } catch (e: Exception) {
            Log.e(TAG, "[${TimeUtil.timestamp()}] ❌ Erreur de chargement : ${e.message}")
            emptyList()
        }
    }

    fun saveBatteryLevel(context: Context, address: String, battery: Int) {
        val prefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("battery_$address", battery).apply()
    }

    fun getBatteryLevel(context: Context, address: String): Int? {
        val value = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
            .getInt("battery_$address", -1)
        return if (value >= 0) value else null
    }

    // Ajoute cette fonction pour la liste des MAC associées
    fun getAssociatedMacs(context: Context): Set<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val stored = prefs.getString("ble_devices", null) ?: return emptySet()
        return try {
            val jsonArray = JSONArray(stored)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optBoolean("auto", false)) {
                    set.add(obj.getString("mac"))
                }
            }
            set
        } catch (e: Exception) {
            emptySet()
        }
    }

    // Ajoute cette fonction pour le nom personnalisé (si tu veux l'utiliser)
    fun getCustomName(context: Context, mac: String): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val stored = prefs.getString("ble_devices", null) ?: return null
        return try {
            val jsonArray = JSONArray(stored)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("mac") == mac) {
                    return obj.getString("name")
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}