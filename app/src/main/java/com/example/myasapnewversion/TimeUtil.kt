package com.example.myasapnewversion

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {
    fun timestamp(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            .format(Date())
    }
}