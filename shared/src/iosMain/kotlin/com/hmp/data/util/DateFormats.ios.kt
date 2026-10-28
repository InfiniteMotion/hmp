package com.hmp.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSTimeZone
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

@OptIn(ExperimentalForeignApi::class)
actual fun todayDateString(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.stringFromDate(NSDate())
}

@OptIn(ExperimentalForeignApi::class)
actual fun parseDateToMillis(date: String): Long? = try {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    // 本地时区当日 00:00 → epoch 毫秒（timeIntervalSince1970 就是"距 1970 的秒"，
    // 不必再手写 Foundation reference date 的 978307200 偏移）
    formatter.dateFromString(date)?.let { nsDate ->
        (nsDate.timeIntervalSince1970 * 1000.0).toLong()
    }
} catch (_: Exception) {
    null
}

@OptIn(ExperimentalForeignApi::class)
actual fun currentHour(): Int {
    val cal = NSCalendar.currentCalendar
    val components = cal.components(
        NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
        fromDate = NSDate()
    )
    return components.hour.toInt()
}

@OptIn(ExperimentalForeignApi::class)
actual fun millisUntilNextLocalMidnight(): Long {
    val cal = NSCalendar.currentCalendar
    val now = NSDate()
    // 计算今天剩余秒数：hour*3600 + minute*60 + second
    val components = cal.components(
        NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
        fromDate = now
    )
    val remainingSeconds = ((23 - components.hour) * 3600.0) +
        ((59 - components.minute) * 60.0) +
        (60 - components.second)
    return (remainingSeconds * 1000.0).toLong()
}

@OptIn(ExperimentalForeignApi::class)
actual fun formatMmddFromMillis(epochMs: Long): String {
    val nsDate = NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)
    val formatter = NSDateFormatter()
    formatter.dateFormat = "MM-dd"
    return formatter.stringFromDate(nsDate)
}
@OptIn(ExperimentalForeignApi::class)
actual fun localHourOfDay(epochMs: Long): Int {
    val components = NSCalendar.currentCalendar.components(
        NSCalendarUnitHour,
        fromDate = NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)
    )
    return components.hour.toInt()
}

@OptIn(ExperimentalForeignApi::class)
actual fun localDateString(epochMs: Long): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.timeZone = NSTimeZone.localTimeZone()
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0))
}

@OptIn(ExperimentalForeignApi::class)
actual fun localDayRange(date: String): Pair<Long, Long>? = try {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.timeZone = NSTimeZone.localTimeZone()
    val start = formatter.dateFromString(date) ?: return null
    // options 传 0：只要"次日本地 00:00"，不要 NSCalendar 的任何进位偏好。
    // Desktop 端是 day.plusDays(1).atStartOfDay(localZone)，DST 由日历本身处理，这里同口径。
    val nextDay = NSCalendar.currentCalendar.dateByAddingUnit(
        unit = NSCalendarUnitDay,
        value = 1,
        toDate = start,
        options = 0uL
    ) ?: return null
    (start.timeIntervalSince1970 * 1000.0).toLong() to (nextDay.timeIntervalSince1970 * 1000.0).toLong()
} catch (_: Exception) {
    null
}

@OptIn(ExperimentalForeignApi::class)
actual fun localDayOfWeekIso(epochMs: Long): Int {
    // NSCalendar.weekday: 1=周日 … 7=周六 → 折成 ISO 的 1=周一 … 7=周日
    val components = NSCalendar.currentCalendar.components(
        NSCalendarUnitWeekday,
        fromDate = NSDate.dateWithTimeIntervalSince1970(epochMs / 1000.0)
    )
    val sundayFirst = components.weekday.toInt()
    return if (sundayFirst == 1) 7 else sundayFirst - 1
}
