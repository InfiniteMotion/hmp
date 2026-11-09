package com.hearablemusic.player.ui.common.util

/**
 * Token 数的紧凑展示：1_234_567 → 1.2M，12_345 → 12.3K。
 *
 * 设置页与 Agent 看板共用这一处口径；此前两份实现的 M 档精度不同（1.2M vs 1.23M），
 * 注释却都声称「同口径」。
 */
fun fmtTokens(n: Long): String {
    if (n >= 1_000_000L) return "${(n / 100_000L) / 10.0}M"
    if (n >= 1000L) return "${(n / 100L) / 10.0}K"
    return "$n"
}
