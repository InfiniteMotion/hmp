package com.hearablemusic.player.ui.agent.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.data.database.currentTimeMillis
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.card.AnchorContent
import com.hmp.domain.agent.card.DiscoverContent
import com.hmp.domain.agent.runtime.sub.enrich.EnrichProgress
import com.hmp.domain.agent.card.EnrichTrackingContent
import com.hmp.domain.agent.card.NarrativeContent
import com.hmp.domain.agent.card.NarrativeTimeRange
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.SlideType
import com.hmp.domain.agent.port.NowPlayingContext
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import com.hmp.domain.agent.runtime.sub.shared.RadioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject



import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
// ═══════════════════════════════════════════════════════════════════
// HelloSlideCardStack — 外部入口（混合架构：agent cards + 直接数据源）
// ═══════════════════════════════════════════════════════════════════

@Composable
fun HelloSlideCardStack(
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
) {
    val masterAgent: MasterAgent? = koinInject()

    // ① Agent 产物卡（5 种：GREETING/RECOMMEND/DISCOVER/FORGOTTEN/ANNIVERSARY）
    val emptyFlow = remember { MutableStateFlow<List<SlideCard>>(emptyList()) }
    val agentCards by remember(masterAgent) {
        masterAgent?.helloCards ?: emptyFlow
    }.collectAsState()

    // ② ANCHOR — 监听 PlaybackController StateFlow（切歌/暂停/进度变化自动触发重建）
    var anchorCard by remember { mutableStateOf<SlideCard?>(null) }
    var lastSongKey by remember { mutableStateOf("") }
    var lastPlaying by remember { mutableStateOf(false) }
    val pbCtrl = koinInject<PlaybackController>()
    val currentMusic by pbCtrl.currentPlayingMusic.collectAsState()
    val isPlayingNow by pbCtrl.isPlaying.collectAsState()
    val positionMs by pbCtrl.currentPosition.collectAsState()
    val durationMs by pbCtrl.duration.collectAsState()
    LaunchedEffect(currentMusic, isPlayingNow, positionMs, durationMs) {
        val newCard = buildAnchorCardFromContext(
            NowPlayingContext(
                currentMusicId = currentMusic?.music?.id,
                currentMusicInfo = currentMusic,
                isPlaying = isPlayingNow,
                currentPositionMs = positionMs,
                durationMs = durationMs,
            )
        )
        val ac = newCard.content as? AnchorContent
        val songKey = "${ac?.trackTitle}|${ac?.artistName}"
        val shouldFocus = (songKey != lastSongKey && lastSongKey.isNotEmpty()) ||
            (!lastPlaying && isPlayingNow && lastSongKey.isNotEmpty())
        anchorCard = if (shouldFocus) newCard.copy(focusedAt = currentTimeMillis()) else newCard
        lastSongKey = songKey
        lastPlaying = isPlayingNow
    }

    // ③ RADIO_STATUS — 订阅 radioState StateFlow + 同步 queryPlaylist
    val radioState by remember(masterAgent) {
        masterAgent?.radioState ?: MutableStateFlow<RadioState?>(null)
    }.collectAsState()
    // 镜像队列流：LLM 返回后 seedWhy/whys 落镜像只走这里（radioState/currentMusic 都不变），
    // 必须作为重建 key 之一，否则种子按语和换批后的按语会停留在本地占位快照
    val radioPlaylistFlow = remember(masterAgent) {
        masterAgent?.radioPlaylistFlow()
            ?: kotlinx.coroutines.flow.MutableStateFlow<List<RadioTrack>>(emptyList())
    }
    val radioPlaylist by radioPlaylistFlow.collectAsState()
    var radioStatusCard by remember { mutableStateOf<SlideCard?>(null) }
    // PAUSED 不算激活：暂停是临时态（用户去忙别的，恢复即续播），应释放堆叠锁、
    // 恢复自动轮播、并重新显示 ANCHOR / 叙事卡。仅 BUILDING/PLAYING 锁住电台卡。
    val radioActive =
        radioState is RadioState.BUILDING ||
            radioState is RadioState.PLAYING

    // ④ ENRICH_TRACKING — 订阅 Enrich 进度 StateFlow（活跃时显示，完成后隐藏）
    // 兜底走同一条 Flow 路径，而不是在 Elvis 两侧调不同的 Composable API：
    // 原写法 `by masterAgent?.progress?.collectAsState() ?: remember { mutableStateOf(...) }`
    // 在 masterAgent 由 null 变为非 null 时（DI 就绪时机差异），该 slot 的 Composable
    // 调用形状会从 `remember+mutableStateOf` 变成 `collectAsState` —— 组合调用图的节点数
    // 随之变化，这种情况不受 Compose 的位置记忆保护。改为与上面 radioState 一致的写法，
    // 两个分支都走 collectAsState()，形状恒定。
    val enrichProgressFlow = remember(masterAgent) {
        masterAgent?.enrichProgressState() ?: MutableStateFlow(EnrichProgress.IDLE)
    }
    val enrichProgress by enrichProgressFlow.collectAsState()
    val enrichTrackingCard = if (enrichProgress.state != AgentRunState.UNREGISTERED) {
        SlideCard(
            cardId = "enrich_tracking",
            type = SlideType.ENRICH_TRACKING,
            content = EnrichTrackingContent(
                state = enrichProgress.state.name,
                processed = enrichProgress.processed,
                success = enrichProgress.success,
                failed = enrichProgress.failed,
                currentUnitSize = enrichProgress.currentUnitSize,
                active = enrichProgress.state == AgentRunState.RUNNING,
                currentArtist = enrichProgress.currentArtist,
                chunkIndex = enrichProgress.chunkIndex,
                chunkTotal = enrichProgress.chunkTotal,
                phase = enrichProgress.phase,
            ),
        )
    } else null

    // ⑤ NARRATIVE — 报告叙事段（DAO 缓存；首帧 null，异步拉取后入卡）
    //    取 MONTH 维度：原设计「月度叙事卡融入此处」，报告页可切其他维度。
    var narrativeCard by remember { mutableStateOf<SlideCard?>(null) }
    LaunchedEffect(masterAgent) {
        if (masterAgent == null) return@LaunchedEffect
        runCatching {
            masterAgent.getReportNarrative(NarrativeTimeRange.MONTH)
        }.getOrNull()?.let { entity ->
            val text = entity.narrative
            if (text.isNotBlank()) {
                narrativeCard = SlideCard(
                    cardId = "narrative_${entity.timeRange}",
                    type = SlideType.NARRATIVE,
                    content = NarrativeContent(
                        narrative = text,
                        timeRange = runCatching {
                            NarrativeTimeRange.valueOf(entity.timeRange)
                        }.getOrDefault(NarrativeTimeRange.MONTH),
                        generatedAt = entity.generatedAt,
                        avgDailyMinutes = entity.avgDailyMinutes,
                    ),
                )
            }
        }
    }

    // RADIO_STATUS 重建：radioState 变化 + 切歌 currentMusic emit + 镜像队列更新（LLM 返回落 seedWhy/whys）
    LaunchedEffect(radioState, currentMusic, radioPlaylist) {
        radioStatusCard = buildRadioStatusCard(masterAgent, radioState)
    }
    val cardList = buildList {
        if (!radioActive) anchorCard?.let { add(it) }
        // 叙事卡：常驻，位于 ANCHOR 之后（原设计「建议常驻卡，位于 ANCHOR 之后」）
        if (!radioActive) narrativeCard?.let { add(it) }
        addAll(
            agentCards.filter {
                it.visible && it.type != SlideType.ANCHOR && it.type != SlideType.RADIO_STATUS &&
                    it.type != SlideType.ENRICH_TRACKING &&
                    // DISCOVER trackIds 为空时跳过
                    !(it.type == SlideType.DISCOVER && (it.content as? DiscoverContent)?.trackIds?.isEmpty() == true)
            }
        )
        radioStatusCard?.let { add(it) }
        enrichTrackingCard?.let { add(it) }
    }

    Box(modifier = modifier) {
        RotatingPersistentCards(
            cards = cardList.ifEmpty { listOf(helloFallBackCard()) },
            permanentLock = radioActive,
            modifier = Modifier.fillMaxSize(),
            onCardClick = onCardClick,
        )
    }
}
