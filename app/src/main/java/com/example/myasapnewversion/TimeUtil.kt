package com.example.myasapnewversion

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun timestamp(): String {
        return formatter.format(Date())
    }
}