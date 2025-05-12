package com.example.myasapnewversion

data class BleDevice(
    val name: String,
    val rssi: Int,
    val mac: String,
    val isAutoConnected: Boolean,
    val isConnected: Boolean,
    val baseName: String,
    val batteryLevel: Int? = null
)