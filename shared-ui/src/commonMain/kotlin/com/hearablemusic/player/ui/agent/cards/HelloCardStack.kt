package com.hearablemusic.player.ui.agent.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.runtime.sub.radio.RadioCardState
import com.hmp.domain.agent.runtime.sub.radio.RadioMessage
import com.hmp.domain.music.MusicInfo
import org.koin.compose.viewmodel.koinViewModel

// ═══════════════════════════════════════════════════════════════════
// Hello 卡堆 —— 数据组装与渲染解耦
//
//   rememberHelloCardsState()  →  组装卡列表（5 路数据源，与渲染无关）
//        ├─ HelloSlideCardStack   （竖屏 / Compact：Portrait 调参）
//        └─ HelloCardCoverflow    （横屏宽窗左栏：Landscape 调参）
//
//   两者是**同一个自绘组件**、仅调参不同 —— 见 HelloCardCoverflowTuning.kt。
// ═══════════════════════════════════════════════════════════════════

/**
 * Hello 卡堆的**数据组装结果** —— 卡列表 + 电台永久锁标记。
 * 与渲染解耦：横竖屏渲染是同一个 `HelloCardCoverflow`，只是 tuning 不同。
 * 见 F14 §3.8。
 */
internal data class HelloCardsState(
    val cards: List<SlideCard>,
    val permanentLock: Boolean,
    /** 电台卡片的动态数据由 VM 快照下发，卡片不再直连 MasterAgent。 */
    val radioMessage: RadioMessage? = null,
    val radioCard: RadioCardState = RadioCardState(theme = null, upcomingCount = 0, nextTitle = null, lastAdjust = null),
    /** 卡片引用曲目的补全结果（封面/标题/艺术家），由 VM 查询后下发。 */
    val trackLookup: Map<Long, MusicInfo> = emptyMap(),
    /** 当前行歌词，由 VM 解析并去重后下发。 */
    val currentLyricLine: String? = null,
)

/**
 * 组装 Hello 卡堆的数据（不做任何渲染）。
 *
 * 抽出来的原因：数据来源有 5 路（agent 产物 / ANCHOR / RADIO_STATUS / ENRICH / NARRATIVE），
 * 逻辑复杂且与渲染无关。两种渲染实现（Pager / Coverflow）必须看到**完全一致**的卡列表，
 * 否则会出现"换个断点卡就变了"的怪 bug。
 */
@Composable
internal fun rememberHelloCardsState(): HelloCardsState {
    // 7.5 P1：5 路数据组装（含 masterAgent 注入与 DAO 直读）全部搬进 HelloCardsViewModel，composable 只 collect
    val viewModel: HelloCardsViewModel = koinViewModel()
    return viewModel.uiState.collectAsState().value
}

/**
 * HelloSlideCardStack — 外部入口（竖屏 / Compact）。
 *
 * 内部走竖屏调参的自绘堆叠：一次只显示一张（stepFactor = 1.0，邻卡整体越出视口）、卡形由调用方的
 * `aspectRatio` 决定 —— 观感等同原来的"一页一卡"，但获得**无限循环**（Pager 版滚到头就停）。
 * 横屏宽窗请直接用 `HelloCardCoverflow` 并传横屏调参。见 F14 §3.10。
 */
@Composable
fun HelloSlideCardStack(
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
) {
    HelloCardCoverflow(
        modifier = modifier,
        tuning = CardStackTuning.Portrait,
        onCardClick = onCardClick,
    )
}
