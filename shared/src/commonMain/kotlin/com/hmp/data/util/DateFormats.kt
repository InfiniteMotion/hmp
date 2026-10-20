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
/**
 * epoch 毫秒 → **本地时区**的小时（0-23）。
 *
 * 为什么必须有（D5-01）：本域此前有两套口径 —— SQL 侧 `strftime('%H', ..., 'localtime')`
 * 与 Kotlin 侧 `(epochMs / 3_600_000) % 24`（那是 **UTC**）。同一台设备上"时段分布"页
 * 与写进画像的 `hourHistogram` 能差一个时区偏移，UTC+8 上差 8 小时，"夜猫子"判定整段错位。
 * 现在两者都必须经这里，判据是 `HourBucketParityTest`（SQL 与 Kotlin 取同一值）。
 */
expect fun localHourOfDay(epochMs: Long): Int

/**
 * epoch 毫秒 → **本地时区**的日历日字符串（`yyyy-MM-dd`，与 `listeningDuration.date` 同格式）。
 *
 * 同样是为 D5-01：`activeDays` 此前按 UTC 日界切，跨零点的那一小时被算进相邻两天。
 * 这里返回字符串而不是"日序号"：唯一消费点是"跨了几几天"的**去重计数**，
 * 标签稳定就够，不必在三端各写一套 epoch-day 换算（那才是新的漂移面）。
 */
expect fun localDateString(epochMs: Long): String

/**
 * 本地时区某个日历日（`yyyy-MM-dd`，即 `listeningDuration.date` 的格式）的 **[当日 00:00, 次日 00:00)** 毫秒区间。
 *
 * 用区间而不是 `strftime('%m-%d')` 过滤（D5-11 未做的那半）：`playedAt` 上有索引，
 * 区间扫描能走索引，逐行 `strftime` 求值不能。解析失败返回 null。
 *
 * ⚠️ 返回 null 时调用方**不要**退回 `strftime` —— 那会把两套口径又并存一次；按"查不到"处理。
 */
expect fun localDayRange(date: String): Pair<Long, Long>?

/**
 * epoch 毫秒 → **本地时区**的星期序号（ISO：1=周一 … 7=周日）。
 *
 * 热力图按真实星期对齐列时要用（D5-08）：此前 `ListeningChart` 的表头是硬编码的
 * `M T W T F S S`，与格子里那一天真正的星期毫无关系。
 */
expect fun localDayOfWeekIso(epochMs: Long): Int
