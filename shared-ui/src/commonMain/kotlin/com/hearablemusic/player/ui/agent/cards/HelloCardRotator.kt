package com.hearablemusic.player.ui.agent.cards

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.hmp.data.database.currentTimeMillis
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
import com.hmp.domain.agent.card.SlideType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch



import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
private const val AUTO_ROTATE_MS = 4000L
private const val FOCUS_DISPLAY_MS = 4000L

// ═══════════════════════════════════════════════════════════════════
// RotatingPersistentCards — Pager 轮播
// 单一 while 循环统一处理：永久锁、用户滚动锁、聚焦滚页、自动轮播、溢出保护
// 不再用多个 LaunchedEffect——它们互相 race 且 cards 每秒变化会触发无谓重启
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun RotatingPersistentCards(
    cards: List<SlideCard>,
    permanentLock: Boolean,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
) {
    val pagerState = rememberPagerState(pageCount = { cards.size })

    // permanentLock 和 cards 都是 Composable 参数，会随重组变化；
    // 用 MutableState 包装让协程读到最新值（LaunchedEffect(Unit) 闭包只捕获第一次的值）
    var permanentLockState by remember { mutableStateOf(permanentLock) }
    permanentLockState = permanentLock
    var cardsState by remember { mutableStateOf(cards) }
    cardsState = cards

    // —— while 循环内部状态（Composable 层不用读，只在协程内读写）——
    var lastAutoRotateAt by remember { mutableStateOf(currentTimeMillis()) }
    var userLockUntil by remember { mutableStateOf(0L) }
    var lastFocusHandled by remember { mutableStateOf(0L) }
    var lastSeenSize by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        // snapshotFlow 读 mutableStateOf 包装后的 cardsState，每次重组都会更新
        var cardsSnapshot = cardsState
        launch { snapshotFlow { cardsState }.collect { cardsSnapshot = it } }

        while (isActive) {
            val now = currentTimeMillis()
            val size = cardsSnapshot.size

            // 卡数量变化 → 重置轮播计时（避免旧值导致立即翻页或跳过）
            if (size != lastSeenSize) {
                lastSeenSize = size
                lastAutoRotateAt = now
            }

            // ① 永久锁（电台激活）—— 先滚到 RADIO_STATUS，然后停
            if (permanentLockState) {
                val radioIdx = cardsSnapshot.indexOfFirst { it.type == SlideType.RADIO_STATUS }
                if (radioIdx >= 0 && radioIdx != pagerState.currentPage && !pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(
                        radioIdx,
                        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                    )
                }
                delay(100)
                continue
            }

            // ② 临时锁到期自动解除（用户锁或聚焦锁过期）
            if (userLockUntil in 1..<now) {
                userLockUntil = 0L
            }

            // ③ 用户手动滚 → 触发 5s 临时锁（用户锁优先覆盖已过期的锁）
            if (pagerState.isScrollInProgress && userLockUntil == 0L) {
                userLockUntil = now + 5_000L
            }

            // ④ 临时锁中 → 停轮播
            if (userLockUntil > now) { delay(100); continue }

            // ⑤ 聚焦信号 —— 有新的 focusedAt → 滚到对应卡 + 4s 临时锁
            val maxFocus = cardsSnapshot.maxOfOrNull { it.focusedAt } ?: 0L
            if (maxFocus > lastFocusHandled && !pagerState.isScrollInProgress && size > 0) {
                val focusedIndex = cardsSnapshot.indexOfFirst { it.focusedAt == maxFocus }
                if (focusedIndex >= 0 && focusedIndex != pagerState.currentPage) {
                    pagerState.animateScrollToPage(
                        focusedIndex,
                        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                    )
                }
                lastFocusHandled = maxFocus
                userLockUntil = now + FOCUS_DISPLAY_MS  // 聚焦后 4s 临时锁
                lastAutoRotateAt = now  // 重置轮播计时
                delay(100)
                continue
            }

            // ⑥ 自动轮播 —— 4s 到点且不在滚
            if (now - lastAutoRotateAt >= AUTO_ROTATE_MS && !pagerState.isScrollInProgress && size > 1) {
                val next = (pagerState.currentPage + 1) % size
                pagerState.animateScrollToPage(next, animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing))
                lastAutoRotateAt = now
            }

            // ⑦ 页数溢出保护 —— 卡数量从多变少时，当前页可能越界
            if (size > 0 && pagerState.currentPage >= size) {
                pagerState.animateScrollToPage(size - 1, animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing))
            }

            delay(100)
        }
    }

    Box(modifier = modifier) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
            contentPadding = PaddingValues(vertical = 24.dp),
            pageSpacing = 10.dp,
        ) { page ->
            val rawOffset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                .let { if (it < 0) -it else it }
            val eased = FastOutSlowInEasing.transform(rawOffset.coerceIn(0f, 1f))
            val cardScale = 1f - eased * 0.12f    // 最小 88%
            val cardAlpha = 1f - eased * 0.5f       // 最小 50%

            FamilyDispatch(
                card = cards[page],
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        alpha = cardAlpha
                    },
                onCardClick = onCardClick,
                isCurrentPage = rawOffset < 0.05f,
            )
        }

        // 右侧垂直 indicator dots
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            cards.forEachIndexed { index, _ ->
                val isActive = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (isActive) 6.dp else 4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isActive) Color.White
                            else Color.White.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// FamilyDispatch — 统一分发到 5 个家族
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun FamilyDispatch(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
    isCurrentPage: Boolean = true,
) {
    when (card.content) {
        is AnchorContent -> FamilyAnchorCard(card, modifier, onCardClick, onLongClick, isCurrentPage)
        is RecommendContent,
        is ForgottenContent -> FamilySingleTrackCard(card, modifier, onCardClick, onLongClick)
        is AnniversaryContent -> FamilyAnniversaryCard(card, modifier, onCardClick, onLongClick)
        is DiscoverContent -> FamilyDiscoverCard(card, modifier, onCardClick, onLongClick)
        is RadioStatusContent -> FamilyRadioStatusCard(card, modifier, onCardClick, onLongClick)
        is GreetingContent -> FamilyGreetingCard(card, modifier, onCardClick, onLongClick)
        is EnrichTrackingContent -> FamilyEnrichTrackingCard(card, modifier, onCardClick, onLongClick)
        is NarrativeContent -> FamilyNarrativeCard(card, modifier, onCardClick, onLongClick)
    }
}
