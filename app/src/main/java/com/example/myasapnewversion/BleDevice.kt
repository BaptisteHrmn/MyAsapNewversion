// BleDevice.kt
package com.example.myasapnewversion

data class BleDevice(
    val name: String,
    val rssi: Int,
    val mac: String,
    val auto: Boolean,
    val connected: Boolean,
    val baseName: String,
    val battery: Int?
)