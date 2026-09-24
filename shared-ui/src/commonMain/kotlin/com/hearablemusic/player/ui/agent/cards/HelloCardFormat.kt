package com.hearablemusic.player.ui.agent.cards

import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asUiText
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.card_ago_days
import com.hearablemusic.player.ui.generated.resources.card_ago_just_now
import com.hearablemusic.player.ui.generated.resources.card_ago_months
import com.hearablemusic.player.ui.generated.resources.card_ago_today
import com.hearablemusic.player.ui.generated.resources.card_ago_years
import com.hearablemusic.player.ui.generated.resources.card_ago_yesterday
import com.hearablemusic.player.ui.generated.resources.card_avg_hours
import com.hearablemusic.player.ui.generated.resources.card_avg_minutes
import com.hearablemusic.player.ui.generated.resources.card_phase_evening_commute
import com.hearablemusic.player.ui.generated.resources.card_phase_evening_leisure
import com.hearablemusic.player.ui.generated.resources.card_phase_lunch
import com.hearablemusic.player.ui.generated.resources.card_phase_morning_commute
import com.hearablemusic.player.ui.generated.resources.card_phase_night
import com.hearablemusic.player.ui.generated.resources.card_phase_work
import com.hmp.data.database.currentTimeMillis
import com.hmp.domain.agent.card.AnchorContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.SlideType
import com.hmp.domain.agent.card.TimePhase



internal fun formatGeneratedAgo(generatedAt: Long): UiText? {
    if (generatedAt <= 0L) return null
    val diffMs = currentTimeMillis() - generatedAt
    if (diffMs < 0) return UiText.Res(Res.string.card_ago_just_now)
    val days = (diffMs / 86_400_000L).toInt()
    return when {
        days <= 0 -> UiText.Res(Res.string.card_ago_today)
        days == 1 -> UiText.Res(Res.string.card_ago_yesterday)
        days < 30 -> UiText.Res(Res.string.card_ago_days, listOf(days))
        days < 365 -> UiText.Res(Res.string.card_ago_months, listOf(days / 30))
        else -> UiText.Res(Res.string.card_ago_years, listOf(days / 365))
    }
}

/** 日均听歌分钟 → "48 分钟" / "1.5 小时" */
internal fun formatAvgMinutes(minutes: Float): UiText =
    if (minutes < 60f) UiText.Res(Res.string.card_avg_minutes, listOf(minutes.toInt()))
    else UiText.Res(Res.string.card_avg_hours, listOf((minutes / 6f).toInt() / 10f))

// ═══════════════════════════════════════════════════════════════════
// 工具函数
// ═══════════════════════════════════════════════════════════════════

internal val TimePhase.label: UiText
    get() = when (this) {
        TimePhase.NIGHT -> Res.string.card_phase_night.asUiText()
        TimePhase.MORNING_COMMUTE -> Res.string.card_phase_morning_commute.asUiText()
        TimePhase.WORK -> Res.string.card_phase_work.asUiText()
        TimePhase.LUNCH -> Res.string.card_phase_lunch.asUiText()
        TimePhase.EVENING_COMMUTE -> Res.string.card_phase_evening_commute.asUiText()
        TimePhase.EVENING_LEISURE -> Res.string.card_phase_evening_leisure.asUiText()
        TimePhase.UNKNOWN -> UiText.Raw("")
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
