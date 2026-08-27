package com.hmp.data.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun todayDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

actual fun parseDateToMillis(date: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.time
} catch (_: Exception) {
    null
}