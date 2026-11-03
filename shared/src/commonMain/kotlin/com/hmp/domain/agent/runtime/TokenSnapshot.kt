package com.hmp.domain.agent.runtime

/**
 * 看板用的 Token 用量快照。
 *
 * 数据来自 [com.hmp.domain.agent.quota.TokenQuotaGate]（免费总额 / 自定义日限额），
 * 不再是进程内的日累加器 —— 那样重启就归零，看板会显示假数字。
 *
 * 保留这个形状（而不是直接把 gate 的内部结构暴露给 UI）是 F13 的"数据形状与写入能力分开"：
 * UI 只需要读 `used / quota / rate`。
 */
data class TokenSnapshot(
    val used: Long,
    val quota: Long,
) {
    val remaining: Long = (quota - used).coerceAtLeast(0)

    val rate: Float = if (quota > 0) (used.toFloat() / quota).coerceIn(0f, 1f) else 0f
}
