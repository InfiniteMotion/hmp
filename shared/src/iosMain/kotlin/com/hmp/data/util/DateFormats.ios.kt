package com.hmp.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

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