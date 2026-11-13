package com.hearablemusic.player.ui.agent.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.card_radio_paused
import com.hearablemusic.player.ui.generated.resources.card_radio_playing
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.data.database.HelloCardCacheDao
import com.hmp.data.database.currentTimeMillis
import com.hmp.data.util.todayDateString
import com.hmp.domain.agent.card.AnchorContent
import com.hmp.domain.agent.card.AnniversaryContent
import com.hmp.domain.agent.card.DiscoverContent
import com.hmp.domain.agent.card.EnrichTrackingContent
import com.hmp.domain.agent.card.ForgottenContent
import com.hmp.domain.agent.card.NarrativeContent
import com.hmp.domain.agent.card.NarrativeTimeRange
import com.hmp.domain.agent.card.RadioStatusContent
import com.hmp.domain.agent.card.RecommendContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.SlideType
import com.hmp.domain.agent.card.toSlideCard
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.sub.enrich.EnrichProgress
import com.hmp.domain.agent.runtime.sub.radio.RadioCardState
import com.hmp.domain.agent.runtime.sub.radio.RadioMessage
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import com.hmp.domain.agent.runtime.sub.shared.RadioTrack
import com.hmp.domain.lyrics.LrcParser
import com.hmp.domain.lyrics.findCurrentLyricIndex
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Hello 卡堆数据 VM（7.5 P1 · UI 侧 UDF 收尾）。
 *
 * 原 `rememberHelloCardsState()` 在 composable 里 `koinInject<MasterAgent?>`、直读
 * `getReportNarrative`（DAO）并用 remember 手工管理 5 路组装；现在数据组装全部搬进本 VM：
 * composable 只 collect [uiState]。masterAgent 由 DI 注入（可空，与原语义一致），
 * 为 null 时各路走空流兜底 —— 卡堆只显示 fallback 卡。
 */
/**
 * 主播按语的机械标签黑名单 —— 这些是**上游下发的内部标记**（非自然语言），不上 UI。
 * 属数据约定值（与 `RadioSubAgent` 的 `why` 字段同源），**不参与本地化**。
 */
private const val WHY_TAG_MATCH_PREFIX = "标签匹配"

private val MECHANICAL_WHY_TAGS =
    setOf("在播", "在播·电台起点", "电台续播", "热门曲目", "补充曲目")

private fun hostWhy(why: String?): String? = why?.takeIf {
    it.isNotBlank() && !it.startsWith(WHY_TAG_MATCH_PREFIX) && it !in MECHANICAL_WHY_TAGS
}

internal class HelloCardsViewModel(
    private val masterAgent: MasterAgent?,
    playbackController: PlaybackController,
    private val musicRepository: MusicRepository,
    private val cardCacheDao: HelloCardCacheDao? = null,
) : ViewModel() {

    /** 卡片引用曲目的补全结果：VM 查库后随快照下发，卡片不再自己起协程。 */
    private val trackLookup = MutableStateFlow<Map<Long, MusicInfo>>(emptyMap())

    /** 当前行歌词：歌词解析只在歌词变化时做，位置变化只算索引，结果去重后下发。 */
    private val currentLyricLine: StateFlow<String?> = run {
        val parsedLyrics = playbackController.currentMusicLyrics
            .map { lyrics -> lyrics?.let { LrcParser.parse(it) } ?: emptyList() }
            .distinctUntilChanged()
        combine(parsedLyrics, playbackController.currentPosition) { parsed, position ->
            parsed.getOrNull(findCurrentLyricIndex(parsed, position))?.originalText
        }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    private val emptyCards = MutableStateFlow<List<SlideCard>>(emptyList())

    /**
     * 从 `hello_card_cache` 恢复的今日卡。
     * Hello 未启动（没有可用 LLM）时，页面靠这份缓存仍有内容可展示。
     */
    private val recoveredCards = MutableStateFlow<List<SlideCard>>(emptyList())

    init {
        viewModelScope.launch {
            recoveredCards.value = runCatching {
                val dao = cardCacheDao ?: return@runCatching emptyList()
                val today = todayDateString()
                dao.getTodayAllEntities(currentTimeMillis() - 24L * 60 * 60 * 1000)
                    .filter { it.generatedForDate == today }
                    .mapNotNull { it.toSlideCard() }
            }.getOrDefault(emptyList())
        }
    }

    // ① Agent 产物卡（同类型内存优先，其余用缓存补位；Hello 未启动时整份来自缓存）
    private val agentCards = combine(
        masterAgent?.helloCards ?: emptyCards,
        recoveredCards,
    ) { live, cached ->
        if (live.isEmpty()) {
            cached
        } else {
            val liveTypes = live.map { it.type }.toSet()
            live + cached.filter { it.type !in liveTypes }
        }
    }

    // ②③⑤ 三个重建结果的落点
    private val anchorCard = MutableStateFlow<SlideCard?>(null)
    private val radioStatusCard = MutableStateFlow<SlideCard?>(null)
    private val narrativeCard = MutableStateFlow<SlideCard?>(null)

    // 电台激活锁 + 各路源（masterAgent 为 null 时全部走空流，与原 Elvis 兜底一致）
    private val radioActive = MutableStateFlow(false)
    private val radioStateFlow = masterAgent?.radioState ?: MutableStateFlow<RadioState?>(null)
    private val radioPlaylistFlow = masterAgent?.radioPlaylistFlow()
        ?: MutableStateFlow<List<RadioTrack>>(emptyList())
    private val enrichFlow = masterAgent?.enrichProgressState() ?: MutableStateFlow(EnrichProgress.IDLE)
    private val radioMessageFlow = masterAgent?.radioMessageFlow() ?: MutableStateFlow<RadioMessage?>(null)
    private val radioCardState = masterAgent?.radioCardState
        ?: MutableStateFlow(RadioCardState(theme = null, upcomingCount = 0, nextTitle = null, lastAdjust = null))

    // ANCHOR 重建的跨事件记忆（与原 remember { mutableStateOf } 语义一致）
    private var lastSongKey = ""
    private var lastPlaying = false

    private data class CardsPart(
        val agent: List<SlideCard>,
        val anchor: SlideCard?,
        val radio: SlideCard?,
        val narrative: SlideCard?,
        val trackLookup: Map<Long, MusicInfo>,
    )

    private data class ExtraPart(
        val enrich: EnrichProgress,
        val active: Boolean,
        val radioMessage: RadioMessage?,
        val radioCard: RadioCardState,
        val lyricLine: String?,
    )

    init {
        // 卡片引用曲目的补全：只有 id 集合变化才重新查库
        viewModelScope.launch {
            agentCards
                .map { cards -> cards.flatMap(::trackIdsOf).distinct() }
                .distinctUntilChanged()
                .collectLatest { ids ->
                    if (ids.isEmpty()) {
                        trackLookup.value = emptyMap()
                        return@collectLatest
                    }
                    trackLookup.value = runCatching { musicRepository.getMusicInfoByIds(ids) }
                        .getOrNull().orEmpty().associateBy { it.music.id }
                }
        }

        // ② ANCHOR：切歌/暂停/时长变化触发重建（含「新歌聚焦」记忆逻辑）。
        //    不含 currentPosition：进度由卡片直接读 controller，避免整份卡堆以 10Hz 重建。
        viewModelScope.launch {
            combine(
                playbackController.currentPlayingMusic,
                playbackController.isPlaying,
                playbackController.duration,
            ) { music, playing, durationMs ->
                val card = buildAnchorCard(music, playing, durationMs)
                val ac = card.content as? AnchorContent
                Triple(card, "${ac?.trackTitle}|${ac?.artistName}", playing)
            }.collect { (card, songKey, playing) ->
                val shouldFocus = (songKey != lastSongKey && lastSongKey.isNotEmpty()) ||
                    (!lastPlaying && playing && lastSongKey.isNotEmpty())
                anchorCard.value = if (shouldFocus) card.copy(focusedAt = currentTimeMillis()) else card
                lastSongKey = songKey
                lastPlaying = playing
            }
        }

        // ③ RADIO_STATUS 重建：radioState 变化 + 切歌 emit + 镜像队列更新（LLM 返回落 seedWhy/whys）。
        //    PAUSED 不算激活：暂停是临时态，应释放堆叠锁并重新显示 ANCHOR / 叙事卡。
        viewModelScope.launch {
            combine(radioStateFlow, playbackController.currentPlayingMusic, radioPlaylistFlow) { state, _, _ ->
                radioActive.value = state is RadioState.BUILDING || state is RadioState.PLAYING
                buildRadioStatusCard(state)
            }.collect { radioStatusCard.value = it }
        }

        // ⑤ NARRATIVE：报告叙事段（DAO 缓存；首帧 null，启动拉一次，MONTH 维度）
        viewModelScope.launch {
            if (masterAgent == null) return@launch
            runCatching { masterAgent.getReportNarrative(NarrativeTimeRange.MONTH) }
                .getOrNull()?.let { entity ->
                    val text = entity.narrative
                    if (text.isNotBlank()) {
                        narrativeCard.value = SlideCard(
                            cardId = "narrative_${entity.timeRange}",
                            type = SlideType.NARRATIVE,
                            content = NarrativeContent(
                                narrative = text,
                                timeRange = runCatching { NarrativeTimeRange.valueOf(entity.timeRange) }
                                    .getOrDefault(NarrativeTimeRange.MONTH),
                                generatedAt = entity.generatedAt,
                                avgDailyMinutes = entity.avgDailyMinutes,
                            ),
                        )
                    }
                }
        }
    }

    private suspend fun buildRadioStatusCard(
        radioState: RadioState?,
    ): SlideCard? {
        val rs = radioState ?: return null
        val stationTheme = masterAgent?.queryRadioStationTheme()

        return when (rs) {
            RadioState.IDLE -> null

            is RadioState.BUILDING -> {
                // BUILDING 极短——本地 fallback 已经构建好并推入播放引擎，能拿到 nowPlaying 封面
                val playlist = masterAgent?.queryRadioPlaylist() ?: emptyList()
                val nowPlaying = runCatching { masterAgent?.queryNowPlayingContext() }.getOrNull()
                val first = playlist.firstOrNull()
                SlideCard(
                    cardId = "radio_status",
                    type = SlideType.RADIO_STATUS,
                    content = RadioStatusContent(
                        stationTheme = stationTheme,
                        actionText = rs.actionText,
                        nowPlayingTitle = nowPlaying?.currentMusicInfo?.music?.title ?: first?.title,
                        nowPlayingArtist = nowPlaying?.currentMusicInfo?.music?.artist ?: first?.artist,
                        albumArtUri = nowPlaying?.currentMusicInfo?.music?.albumArtUri,
                        nowPlayingWhy = null,
                        nextTrackTitle = playlist.getOrNull(1)?.title,
                        nextTrackWhy = null,
                        playlistCount = playlist.size,
                        progressPercent = rs.progressPercent,
                        targetCount = rs.targetCount,
                    ),
                )
            }

            is RadioState.PLAYING -> {
                val playlist = masterAgent?.queryRadioPlaylist() ?: emptyList()
                val nowPlaying = runCatching { masterAgent?.queryNowPlayingContext() }.getOrNull()
                val currentId = nowPlaying?.currentMusicId

                // 找出 playlist 里哪首正在播（按 musicId 匹配）
                val nowPlayingTrack = playlist.find { it.musicId == currentId }
                val currentTitle = nowPlaying?.currentMusicInfo?.music?.title
                    ?: nowPlayingTrack?.title
                val currentArtist = nowPlaying?.currentMusicInfo?.music?.artist
                    ?: nowPlayingTrack?.artist
                val albumArtUri = nowPlaying?.currentMusicInfo?.music?.albumArtUri
                val currentWhy = hostWhy(nowPlayingTrack?.why)

                // 下一首 = 当前播放曲目下一首；找不到就取 playlist.first
                val currentIdx = nowPlayingTrack?.let { playlist.indexOf(it) }
                val nextTrack = if (currentIdx != null && currentIdx >= 0 && currentIdx + 1 < playlist.size) {
                    playlist[currentIdx + 1]
                } else {
                    playlist.getOrNull(0)
                }

                SlideCard(
                    cardId = "radio_status",
                    type = SlideType.RADIO_STATUS,
                    content = RadioStatusContent(
                        stationTheme = stationTheme,
                        actionText = getString(Res.string.card_radio_playing),
                        nowPlayingTitle = currentTitle,
                        nowPlayingArtist = currentArtist,
                        albumArtUri = albumArtUri,
                        nowPlayingWhy = currentWhy,
                        nextTrackTitle = nextTrack?.title,
                        nextTrackWhy = hostWhy(nextTrack?.why),
                        playlistCount = playlist.size,
                        progressPercent = null,
                        targetCount = null,
                    ),
                )
            }

            is RadioState.PAUSED -> {
                // PAUSED：复用 PLAYING 分支结构，封面/标题/why 保留（用户知道自己停了什么）
                val playlist = masterAgent?.queryRadioPlaylist() ?: emptyList()
                val nowPlaying = runCatching { masterAgent?.queryNowPlayingContext() }.getOrNull()
                val currentId = nowPlaying?.currentMusicId
                val nowPlayingTrack = playlist.find { it.musicId == currentId }
                val nextTrack = nowPlayingTrack?.let { playlist.indexOf(it) }?.let { idx ->
                    playlist.getOrNull(idx + 1) ?: playlist.firstOrNull()
                } ?: playlist.firstOrNull()

                SlideCard(
                    cardId = "radio_status",
                    type = SlideType.RADIO_STATUS,
                    content = RadioStatusContent(
                        stationTheme = stationTheme,
                        actionText = getString(Res.string.card_radio_paused),
                        nowPlayingTitle = nowPlaying?.currentMusicInfo?.music?.title
                            ?: nowPlayingTrack?.title,
                        nowPlayingArtist = nowPlaying?.currentMusicInfo?.music?.artist
                            ?: nowPlayingTrack?.artist,
                        albumArtUri = nowPlaying?.currentMusicInfo?.music?.albumArtUri,
                        nowPlayingWhy = hostWhy(nowPlayingTrack?.why),
                        nextTrackTitle = nextTrack?.title,
                        nextTrackWhy = hostWhy(nextTrack?.why),
                        playlistCount = playlist.size,
                        progressPercent = null,
                        targetCount = null,
                    ),
                )
            }
        }
    }

    /** 只收集卡片实际引用到的曲目 id。 */
    private fun trackIdsOf(card: SlideCard): List<Long> = when (val c = card.content) {
        is RecommendContent -> listOf(c.trackId)
        is ForgottenContent -> listOf(c.trackId)
        is AnniversaryContent -> listOf(c.trackId).filter { it > 0L }
        is DiscoverContent -> c.trackIds
        else -> emptyList()
    }

    val uiState: StateFlow<HelloCardsState> = combine(
        combine(agentCards, anchorCard, radioStatusCard, narrativeCard, trackLookup) { agent, anchor, radio, narrative, lookup ->
            CardsPart(agent, anchor, radio, narrative, lookup)
        },
        combine(enrichFlow, radioActive, radioMessageFlow, radioCardState, currentLyricLine) { enrich, active, message, card, line ->
            ExtraPart(enrich, active, message, card, line)
        },
    ) { part, extra ->
        val enrich = extra.enrich
        val active = extra.active
        // ④ ENRICH_TRACKING：活跃时显示，完成后隐藏
        val enrichTrackingCard = if (enrich.state != AgentRunState.UNREGISTERED) {
            SlideCard(
                cardId = "enrich_tracking",
                type = SlideType.ENRICH_TRACKING,
                content = EnrichTrackingContent(
                    state = enrich.state.name,
                    processed = enrich.processed,
                    success = enrich.success,
                    failed = enrich.failed,
                    currentUnitSize = enrich.currentUnitSize,
                    active = enrich.state == AgentRunState.RUNNING,
                    currentArtist = enrich.currentArtist,
                    chunkIndex = enrich.chunkIndex,
                    chunkTotal = enrich.chunkTotal,
                    phase = enrich.phase,
                ),
            )
        } else {
            null
        }
        val cardList = buildList {
            if (!active) part.anchor?.let { add(it) }
            // 叙事卡：常驻，位于 ANCHOR 之后（原设计「建议常驻卡，位于 ANCHOR 之后」）
            if (!active) part.narrative?.let { add(it) }
            addAll(
                part.agent.filter {
                    it.visible && it.type != SlideType.ANCHOR && it.type != SlideType.RADIO_STATUS &&
                        it.type != SlideType.ENRICH_TRACKING &&
                        // DISCOVER trackIds 为空时跳过
                        !(it.type == SlideType.DISCOVER && (it.content as? DiscoverContent)?.trackIds?.isEmpty() == true)
                },
            )
            part.radio?.let { add(it) }
            enrichTrackingCard?.let { add(it) }
        }
        HelloCardsState(
            cards = cardList.ifEmpty { listOf(helloFallBackCard()) },
            permanentLock = active,
            radioMessage = extra.radioMessage,
            radioCard = extra.radioCard,
            trackLookup = part.trackLookup,
            currentLyricLine = extra.lyricLine,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HelloCardsState(cards = listOf(helloFallBackCard()), permanentLock = false),
    )
}
