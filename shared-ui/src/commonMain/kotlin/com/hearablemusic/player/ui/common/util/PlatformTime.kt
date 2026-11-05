package com.hearablemusic.player.ui.common.util

/**
 * 时间工具的多平台抽象。
 *
 * Android actual 用 Calendar，Desktop/iOS actual 用各自时钟 API。
 */

/** 毫秒时间戳 → (年, 月[1..12])。 */
expect fun epochMillisToYearMonth(epochMillis: Long): Pair<Int, Int>

/** 当前时间戳（毫秒）。 */
expect fun nowEpochMillis(): Long

/** 毫秒时间戳 → 按给定 pattern 格式化（默认时区/语言环境，语义同 java.text.SimpleDateFormat）。 */
expect fun formatEpochMillis(epochMillis: Long, pattern: String): String
