package com.example.myasapnewversion

data class BleDevice(
    val name: String,
    val mac: String,
    val batteryLevel: Int?,
    val isAutoConnected: Boolean
)