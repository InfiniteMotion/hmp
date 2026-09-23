package com.hearablemusic.player.ui.agent.cards

import com.hmp.data.database.currentTimeMillis
import com.hmp.domain.agent.card.AnchorContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.SlideType
import com.hmp.domain.agent.card.TimePhase



internal fun formatGeneratedAgo(generatedAt: Long): String {
    if (generatedAt <= 0L) return ""
    val diffMs = currentTimeMillis() - generatedAt
    if (diffMs < 0) return "刚刚"
    val days = (diffMs / 86_400_000L).toInt()
    return when {
        days <= 0 -> "今天生成"
        days == 1 -> "昨天生成"
        days < 30 -> "$days 天前生成"
        days < 365 -> "${days / 30} 个月前生成"
        else -> "${days / 365} 年前生成"
    }
}

/** 日均听歌分钟 → "48 分钟" / "1.5 小时" */
internal fun formatAvgMinutes(minutes: Float): String =
    if (minutes < 60f) "${minutes.toInt()} 分钟"
    else "${((minutes / 6f).toInt() / 10f)} 小时"

// ═══════════════════════════════════════════════════════════════════
// 工具函数
// ═══════════════════════════════════════════════════════════════════

internal val TimePhase.label: String
    get() = when (this) {
        TimePhase.NIGHT -> "深夜"
        TimePhase.MORNING_COMMUTE -> "早高峰"
        TimePhase.WORK -> "工作"
        TimePhase.LUNCH -> "午休"
        TimePhase.EVENING_COMMUTE -> "晚高峰"
        TimePhase.EVENING_LEISURE -> "晚间"
        TimePhase.UNKNOWN -> ""
    }

internal fun formatDurationSec(sec: Int): String {
    if (sec <= 0) return ""
    val m = sec / 60
    val s = sec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

// ═══════════════════════════════════════════════════════════════════
// Fallback 卡池为空时的兜底卡
// ═══════════════════════════════════════════════════════════════════

internal fun helloFallBackCard() = SlideCard(
    cardId = "fallback",
    type = SlideType.ANCHOR,
    content = AnchorContent(
        trackTitle = null,
        artistName = null,
        bpm = null,
        phase = null,
        albumArtUri = null,
        isPlaying = false,
        durationSec = 0,
        progressPercent = 0,
        sourceLabel = null,
    ),
)
