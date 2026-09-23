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

/** W0 HelloSubAgent: 当前小时数（本地时区，0-23） */
expect fun currentHour(): Int

/** W0 HelloSubAgent: 从现在到下一个本地时区凌晨 00:00 的毫秒数 */
expect fun millisUntilNextLocalMidnight(): Long

/** 将 epoch 毫秒转为本地时区的 MM-dd 字符串（用于 ANNIVERSARY 卡月-日匹配） */
expect fun formatMmddFromMillis(epochMs: Long): String