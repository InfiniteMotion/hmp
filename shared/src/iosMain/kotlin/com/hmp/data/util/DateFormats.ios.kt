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
    formatter.dateFromString(date)?.timeIntervalSince1970?.times(1000.0)?.toLong()
} catch (_: Exception) {
    null
}