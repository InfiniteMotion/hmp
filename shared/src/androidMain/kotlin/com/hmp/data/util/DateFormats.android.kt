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
actual fun localHourOfDay(epochMs: Long): Int =
    Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.HOUR_OF_DAY)

actual fun localDateString(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMs))

actual fun localDayRange(date: String): Pair<Long, Long>? {
    val start = parseDateToMillis(date) ?: return null
    // 次日 00:00 由 Calendar 求，不写死 +24h —— 夏令时切换那天既不是 23h 也不是 25h 的整数倍关系
    val cal = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.DAY_OF_YEAR, 1)
    }
    return start to cal.timeInMillis
}
