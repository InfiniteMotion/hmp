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
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.NSTimeIntervalSince1970

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
    formatter.dateFromString(date)?.let { nsDate ->
        // Foundation reference date 偏移：2001-01-01 距 1970-01-01 共 978307200 秒（对齐 PlatformTime.ios 惯例）
        ((nsDate.timeIntervalSinceReferenceDate * 1000.0) + 978_307_200_000.0).toLong()
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
    // epoch ms → NSDate（加回 Foundation 偏移 978307200 秒）
    val nsDate = NSDate(timeIntervalSinceReferenceDate = (epochMs / 1000.0) - 978_307_200.0)
    val formatter = NSDateFormatter()
    formatter.dateFormat = "MM-dd"
    return formatter.stringFromDate(nsDate)
}
@OptIn(ExperimentalForeignApi::class)
actual fun localHourOfDay(epochMs: Long): Int {
    val components = NSCalendar.currentCalendar.components(
        NSCalendarUnitHour,
        fromDate = NSDate(timeIntervalSince1970 = epochMs / 1000.0)
    )
    return components.hour.toInt()
}

@OptIn(ExperimentalForeignApi::class)
actual fun localDateString(epochMs: Long): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.timeZone = NSTimeZone.localTimeZone
    return formatter.stringFromDate(NSDate(timeIntervalSince1970 = epochMs / 1000.0))
}

@OptIn(ExperimentalForeignApi::class)
actual fun localDayRange(date: String): Pair<Long, Long>? = try {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.timeZone = NSTimeZone.localTimeZone
    val start = formatter.dateFromString(date) ?: return null
    val nextDay = NSCalendar.currentCalendar.dateByAddingUnit(
        unit = NSCalendarUnitDay,
        value = 1,
        toDate = start,
        options = NSCalendarOptionsMatchStrictlyNext
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
        fromDate = NSDate(timeIntervalSince1970 = epochMs / 1000.0)
    )
    val sundayFirst = components.weekday.toInt()
    return if (sundayFirst == 1) 7 else sundayFirst - 1
}
