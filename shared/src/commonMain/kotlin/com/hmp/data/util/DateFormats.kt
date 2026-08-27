package com.hmp.data.util

/**
 * 本地时区当日的 yyyy-MM-dd 字符串（ListeningDuration.date 的存储格式）。
 * 平台差异（SimpleDateFormat / LocalDate / NSDateFormatter）收口在此，供三端共享统计逻辑（设计总纲 B0 去重）。
 */
expect fun todayDateString(): String

/**
 * 将 yyyy-MM-dd 解析为本地时区当日 00:00 的 epoch 毫秒；解析失败返回 null。
 */
expect fun parseDateToMillis(date: String): Long?