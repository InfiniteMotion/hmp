package com.hearablemusic.player.ui.agent.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hmp.domain.agent.card.AnchorContent
import com.hmp.domain.agent.card.AnniversaryContent
import com.hmp.domain.agent.card.DiscoverContent
import com.hmp.domain.agent.card.EnrichTrackingContent
import com.hmp.domain.agent.card.ForgottenContent
import com.hmp.domain.agent.card.GreetingContent
import com.hmp.domain.agent.card.NarrativeContent
import com.hmp.domain.agent.card.RadioStatusContent
import com.hmp.domain.agent.card.RecommendContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.runtime.sub.radio.RadioCardState
import com.hmp.domain.agent.runtime.sub.radio.RadioMessage
import com.hmp.domain.music.MusicInfo

// ═══════════════════════════════════════════════════════════════════
// FamilyDispatch — 统一分发到 8 个卡型家族
//
// 注：本文件原含 Pager 版轮播 `RotatingPersistentCards`，2026-09-22 竖屏改用
// 自绘堆叠（`HelloCardCoverflow` + `CardStackTuning.Portrait`）后已删除。
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun FamilyDispatch(
    card: SlideCard,
    trackLookup: Map<Long, MusicInfo> = emptyMap(),
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
    isCurrentPage: Boolean = true,
    radioMessage: RadioMessage? = null,
    radioCard: RadioCardState = RadioCardState(theme = null, upcomingCount = 0, nextTitle = null, lastAdjust = null),
    currentLyricLine: String? = null,
) {
    when (card.content) {
        is AnchorContent -> FamilyAnchorCard(
            card = card,
            modifier = modifier,
            onCardClick = onCardClick,
            onLongClick = onLongClick,
            isCurrentPage = isCurrentPage,
            currentLyricLine = currentLyricLine,
        )

        is RecommendContent,
        is ForgottenContent,
        -> FamilySingleTrackCard(card, trackLookup, modifier, onCardClick, onLongClick)

        is AnniversaryContent -> FamilyAnniversaryCard(card, modifier, onCardClick, onLongClick)

        is DiscoverContent -> FamilyDiscoverCard(card, trackLookup, modifier, onCardClick, onLongClick)

        is RadioStatusContent -> FamilyRadioStatusCard(
            card = card,
            modifier = modifier,
            onCardClick = onCardClick,
            onLongClick = onLongClick,
            radioMessage = radioMessage,
            radioCard = radioCard,
        )

        is GreetingContent -> FamilyGreetingCard(card, modifier, onCardClick, onLongClick)

        is EnrichTrackingContent -> FamilyEnrichTrackingCard(card, modifier, onCardClick, onLongClick)

        is NarrativeContent -> FamilyNarrativeCard(card, modifier, onCardClick, onLongClick)
    }
}
