package com.hmp.data.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

actual fun todayDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

actual fun parseDateToMillis(date: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)?.time
} catch (_: Exception) {
    null
}

actual fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

actual fun millisUntilNextLocalMidnight(): Long {
    val cal = Calendar.getInstance()
    val now = cal.timeInMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.DAY_OF_YEAR, 1)
    return cal.timeInMillis - now
}

actual fun formatMmddFromMillis(epochMs: Long): String =
    SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(epochMs))