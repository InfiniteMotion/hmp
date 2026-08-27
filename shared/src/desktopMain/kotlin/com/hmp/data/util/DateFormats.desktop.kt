package com.hmp.data.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

actual fun todayDateString(): String =
    LocalDate.now().format(dateFormatter)

actual fun parseDateToMillis(date: String): Long? = try {
    LocalDate.parse(date, dateFormatter)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
} catch (_: Exception) {
    null
}