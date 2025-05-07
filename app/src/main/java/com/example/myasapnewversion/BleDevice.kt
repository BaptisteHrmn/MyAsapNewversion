package com.example.myasapnewversion

data class BleDevice(
    val name: String,
    val mac: String,
    var rssi: Int,
    var batteryLevel: Int = -1,
    var isAutoConnected: Boolean = false,
    var isConnected: Boolean = false
)