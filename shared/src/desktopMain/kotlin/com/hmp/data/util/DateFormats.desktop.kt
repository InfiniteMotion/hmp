package com.hmp.data.util

import java.time.LocalDate
import java.time.LocalTime
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

actual fun currentHour(): Int = java.time.LocalTime.now().hour

actual fun millisUntilNextLocalMidnight(): Long {
    val now = java.time.LocalDateTime.now()
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
    return java.time.Duration.between(now, nextMidnight).toMillis()
}

private val mmddFormatter = DateTimeFormatter.ofPattern("MM-dd")
actual fun formatMmddFromMillis(epochMs: Long): String =
    java.time.Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(mmddFormatter)