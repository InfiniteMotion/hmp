package com.hmp.domain.music

/**
 * `PlaybackHistory.source` 的**闭集**（一-3 / D5-06）：这一首是**从哪里**开始播的。
 *
 * 为什么要有这张表：来源分布是使用数据页饼图与画像行为建模的输入，而三端此前各写各的字面量 ——
 * Android 一套大小写混排的字符串、Desktop 只写一个 `"direct"`、iOS 干脆恒传 `nil`
 * （`PlaybackHistoryDao` 的来源聚合是 `WHERE source IS NOT NULL`，于是 iOS 那块饼图永远不渲染，
 * 用户与开发者都分不清"没来源数据"和"功能坏了"）。
 *
 * 取值以 **Android 现有的拼写为准**：三端里只有它已发布、库里已有历史数据，
 * 换拼写会让同一来源在饼图里裂成两条。Desktop 的 `"direct"` 归到 [MANUAL]。
 */
object PlaybackSources {

    /** 用户自己点播 / 从队列里选一首 */
    const val MANUAL = "Manual"

    /** 下一首（手动切或播完自动续到下一首都算，见 [AUTO] 的区分） */
    const val NEXT = "Next"

    /** 上一首 */
    const val PREVIOUS = "Previous"

    /** 冷启动恢复上次进度 */
    const val RESUME = "Resume"

    /** 顺序模式下队列自动推进 */
    const val ORDER = "Order"

    /** 随机模式下队列自动推进 */
    const val SHUFFLE = "Shuffle"

    /** 心动模式（以当前曲为种子重排队列） */
    const val HEART_MODE = "HeartMode"

    /** 释放/退出播放器时落的最后一次结算，只作结束原因，不是"点播来源" */
    const val RELEASE = "Release"

    /** 同一首播完后按「单曲循环」重新开始 */
    const val AUTO = "Auto"

    /** 闭集本身：新增取值必须进这里，否则判据红（"两套口径"的起源就是没人管这张表）。 */
    val all: List<String> = listOf(
        MANUAL, NEXT, PREVIOUS, RESUME, ORDER, SHUFFLE, HEART_MODE, RELEASE, AUTO
    )

    fun isKnown(source: String?): Boolean = source != null && source in all
}
