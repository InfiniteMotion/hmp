package com.hmp.domain.agent.port

/**
 * 墙上时钟：给「开启电台的时刻」提供本地时间语义。
 *
 * **为什么需要它**：电台的选歌要感知「现在是几点 / 周几 / 什么时段」，
 * 而 `shared` 层只有 `kotlin.time.TimeSource.Monotonic` —— 它只能算间隔，
 * 算不出"现在几点"。`PlatformTime.currentTimeMillis()` 在 `shared-ui`，
 * domain 层拿不到，因此这里单开一个端口，由三端各注入一行。
 *
 * **为什么返回结构而不是毫秒**：本地时区 / 日历换算在 commonMain 里手写极易出错，
 * 交给各端系统 API 做（Android & Desktop 用 `java.time`，iOS 用 Foundation）。
 *
 * 见 `docs/7_x/B agent-build/design/agent-radio.md` §7.0.1（开启情境）。
 */
data class LocalMoment(
    /** 本地时间的小时，0-23 */
    val hourOfDay: Int,
    /** ISO 星期：1 = 周一 … 7 = 周日 */
    val dayOfWeek: Int,
) {
    /** 是否周末（周六 / 周日）。 */
    val isWeekend: Boolean get() = dayOfWeek >= 6
}

/** 时段划分 —— 选歌时「深夜」与「通勤」的取向显然不同。 */
enum class DayPart(val label: String) {
    /** 23:00-05:00 */
    LATE_NIGHT("深夜"),

    /** 05:00-08:00 */
    EARLY_MORNING("清晨"),

    /** 08:00-11:00 */
    MORNING("上午"),

    /** 11:00-14:00 */
    MIDDAY("午间"),

    /** 14:00-18:00 */
    AFTERNOON("午后"),

    /** 18:00-23:00 */
    EVENING("晚间"),
}

/** 由小时推时段。 */
fun LocalMoment.dayPart(): DayPart = when (hourOfDay) {
    in 5 until 8 -> DayPart.EARLY_MORNING
    in 8 until 11 -> DayPart.MORNING
    in 11 until 14 -> DayPart.MIDDAY
    in 14 until 18 -> DayPart.AFTERNOON
    in 18 until 23 -> DayPart.EVENING
    else -> DayPart.LATE_NIGHT   // 23:00-04:59
}

/** 渲染给模型看的时刻描述（例：「周六 · 深夜」）。只描述事实，不做偏好推断。 */
fun LocalMoment.describe(): String {
    val week = when (dayOfWeek) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> "未知"
    }
    return "$week · ${dayPart().label}（${hourOfDay}点）"
}

/**
 * 当前本地时刻。各端用系统 API 实现（含时区与夏令时）。
 */
expect fun currentLocalMoment(): LocalMoment
