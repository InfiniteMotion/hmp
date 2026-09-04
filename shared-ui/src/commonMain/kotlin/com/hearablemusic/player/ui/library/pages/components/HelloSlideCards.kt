package com.hearablemusic.player.ui.library.pages.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.none
import com.hearablemusic.player.ui.generated.resources.unknown
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.agent.sub.AnchorContent
import com.hmp.domain.agent.sub.AnniversaryContent
import com.hmp.domain.agent.sub.AnniversarySubtype
import com.hmp.domain.agent.sub.DiscoverContent
import com.hmp.domain.agent.sub.ForgottenContent
import com.hmp.domain.agent.sub.GreetingContent
import com.hmp.domain.agent.sub.RadioStatusContent
import com.hmp.domain.agent.sub.RecommendContent
import com.hmp.domain.agent.sub.SlideCard
import com.hmp.domain.agent.sub.SlideType
import com.hmp.domain.agent.sub.TimePhase
import com.hmp.domain.agent.sub.zhName
import com.hmp.domain.lyrics.LrcParser
import com.hmp.domain.lyrics.findCurrentLyricIndex
import com.hmp.domain.music.MusicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

private const val AUTO_ROTATE_MS = 4000L
private const val FOCUS_DISPLAY_MS = 4000L

// ═══════════════════════════════════════════════════════════════════
// HelloSlideCardStack — 外部入口（混合架构：agent cards + 直接数据源）
// ═══════════════════════════════════════════════════════════════════

@Composable
fun HelloSlideCardStack(
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
) {
    val masterAgent: com.hmp.domain.agent.runtime.MasterAgent? = koinInject()

    // ① Agent 产物卡（5 种：GREETING/RECOMMEND/DISCOVER/FORGOTTEN/ANNIVERSARY）
    val emptyFlow = remember { MutableStateFlow<List<SlideCard>>(emptyList()) }
    val agentCards by remember(masterAgent) {
        masterAgent?.helloAgent()?.cards ?: emptyFlow
    }.collectAsState()

    // ② ANCHOR — UI 层独立 poll 播放引擎（每秒一次，比 agent 分钟级快 60 倍）
    var anchorCard by remember { mutableStateOf<SlideCard?>(null) }
    var lastSongKey by remember { mutableStateOf("") }
    var lastPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(masterAgent) {
        while (isActive) {
            val ctx = masterAgent?.queryNowPlayingContext()
            val newCard = buildAnchorCardFromContext(ctx)
            val ac = newCard.content as? AnchorContent
            val songKey = "${ac?.trackTitle}|${ac?.artistName}"
            val isPlaying = ac?.isPlaying == true
            // 聚焦触发：切歌 或 暂停→播放 转换
            val shouldFocus = (songKey != lastSongKey && lastSongKey.isNotEmpty()) ||
                (!lastPlaying && isPlaying && lastSongKey.isNotEmpty())
            anchorCard = if (shouldFocus) {
                newCard.copy(focusedAt = System.currentTimeMillis())
            } else {
                newCard
            }
            lastSongKey = songKey
            lastPlaying = isPlaying
            delay(1000L)
        }
    }

    // ③ RADIO_STATUS — 订阅 radioState StateFlow + 同步 queryPlaylist
    val radioState by remember(masterAgent) {
        masterAgent?.radioState ?: kotlinx.coroutines.flow.MutableStateFlow<com.hmp.domain.agent.sub.RadioState?>(null)
    }.collectAsState()
    var radioStatusCard by remember { mutableStateOf<SlideCard?>(null) }
    val radioActive = radioState !is com.hmp.domain.agent.sub.RadioState.IDLE && radioState != null

    // state 变化 → 重建卡片；permanentLock（电台激活）直接从 radioActive 计算，
    // 传给 RotatingPersistentCards 控制轮播，不再需要每秒续 focusedAt
    LaunchedEffect(radioState) {
        radioStatusCard = buildRadioStatusCard(masterAgent, radioState)
    }
    val cardList = buildList {
        if (!radioActive) anchorCard?.let { add(it) }
        addAll(
            agentCards.filter {
                it.visible && it.type != SlideType.ANCHOR && it.type != SlideType.RADIO_STATUS &&
                    // DISCOVER trackIds 为空时跳过
                    !(it.type == SlideType.DISCOVER && (it.content as? DiscoverContent)?.trackIds?.isEmpty() == true)
            }
        )
        radioStatusCard?.let { add(it) }
    }

    Box(modifier = modifier) {
        RotatingPersistentCards(
            cards = if (cardList.isEmpty()) listOf(HelloFallbackCard()) else cardList,
            permanentLock = radioActive,
            modifier = Modifier.fillMaxSize(),
            onCardClick = onCardClick,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// ANCHOR / RADIO_STATUS 独立构建函数（UI 层直接调用，不走 agent）
// ═══════════════════════════════════════════════════════════════════

private fun buildAnchorCardFromContext(
    ctx: com.hmp.domain.agent.port.NowPlayingContext?,
): SlideCard {
    val music = ctx?.currentMusicInfo
    val durationMs = ctx?.durationMs ?: 0L
    val currentPosMs = ctx?.currentPositionMs ?: 0L
    val progressPct = if (durationMs > 0) {
        (currentPosMs * 100 / durationMs).toInt().coerceIn(0, 100)
    } else 0
    return SlideCard(
        cardId = "anchor",  // 固定 ID，避免每次 replace 生成新 cardId 导致动画
        type = SlideType.ANCHOR,
        content = AnchorContent(
            trackTitle = music?.music?.title,
            artistName = music?.music?.artist,
            bpm = null,
            phase = detectTimePhaseLocal(com.hmp.data.util.currentHour()),
            albumArtUri = music?.music?.albumArtUri,
            isPlaying = ctx?.isPlaying == true,
            durationSec = (durationMs / 1000).toInt(),
            progressPercent = progressPct,
            sourceLabel = null,
        ),
    )
}

/** UI 层需要当前时间，共享层用 JVM 的 LocalTime 可能会编译不通过 KMP */
private fun detectTimePhaseLocal(hour: Int): TimePhase = when (hour) {
    in 0..6 -> TimePhase.NIGHT
    in 7..9 -> TimePhase.MORNING_COMMUTE
    in 9..11 -> TimePhase.WORK
    in 12..13 -> TimePhase.LUNCH
    in 14..17 -> TimePhase.WORK
    in 18..19 -> TimePhase.EVENING_COMMUTE
    in 20..23 -> TimePhase.EVENING_LEISURE
    else -> TimePhase.UNKNOWN
}

private suspend fun buildRadioStatusCard(
    masterAgent: com.hmp.domain.agent.runtime.MasterAgent?,
    radioState: com.hmp.domain.agent.sub.RadioState?,
): SlideCard? {
    val rs = radioState ?: return null
    val stationTheme = masterAgent?.queryRadioStationTheme()

    return when (rs) {
        com.hmp.domain.agent.sub.RadioState.IDLE -> null

        is com.hmp.domain.agent.sub.RadioState.BUILDING -> SlideCard(
            cardId = "radio_status",
            type = SlideType.RADIO_STATUS,
            content = RadioStatusContent(
                stationTheme = stationTheme,
                actionText = rs.actionText,
                nowPlayingTitle = null,
                nowPlayingArtist = null,
                nextTrackTitle = null,
                nextTrackWhy = null,
                playlistCount = null,
                progressPercent = rs.progressPercent,
                targetCount = rs.targetCount,
            ),
        )

        is com.hmp.domain.agent.sub.RadioState.PLAYING -> {
            val playlist = masterAgent?.queryRadioPlaylist() ?: emptyList()
            val nowPlaying = runCatching { masterAgent?.queryNowPlayingContext() }.getOrNull()
            val currentId = nowPlaying?.currentMusicId
            val firstOrNull = playlist.firstOrNull()

            // 找出 playlist 里哪首正在播（按 musicId 匹配）
            val nowPlayingTrack = playlist.find { it.musicId == currentId }
            val currentTitle = nowPlaying?.currentMusicInfo?.music?.title
                ?: nowPlayingTrack?.title
            val currentArtist = nowPlaying?.currentMusicInfo?.music?.artist
            // 下一首 = 当前播放曲目的下一首；找不到就取 playlist.first
            val currentIdx = nowPlayingTrack?.let { playlist.indexOf(it) }
            val nextTrack = if (currentIdx != null && currentIdx >= 0 && currentIdx + 1 < playlist.size) {
                playlist[currentIdx + 1]
            } else {
                firstOrNull
            }

            SlideCard(
                cardId = "radio_status",
                type = SlideType.RADIO_STATUS,
                content = RadioStatusContent(
                    stationTheme = stationTheme,
                    actionText = "播放中",
                    nowPlayingTitle = currentTitle,
                    nowPlayingArtist = currentArtist,
                    nextTrackTitle = nextTrack?.title,
                    nextTrackWhy = nextTrack?.why?.takeIf { it.isNotBlank() && !it.startsWith("标签匹配") },
                    playlistCount = playlist.size,
                    progressPercent = null,
                    targetCount = null,
                ),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// RotatingPersistentCards — Pager 轮播
// 单一 while 循环统一处理：永久锁、用户滚动锁、聚焦滚页、自动轮播、溢出保护
// 不再用多个 LaunchedEffect——它们互相 race 且 cards 每秒变化会触发无谓重启
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RotatingPersistentCards(
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
    var lastAutoRotateAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var userLockUntil by remember { mutableStateOf(0L) }
    var lastFocusHandled by remember { mutableStateOf(0L) }
    var lastSeenSize by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        // snapshotFlow 读 mutableStateOf 包装后的 cardsState，每次重组都会更新
        var cardsSnapshot = cardsState
        launch { snapshotFlow { cardsState }.collect { cardsSnapshot = it } }

        while (isActive) {
            val now = System.currentTimeMillis()
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
            if (userLockUntil > 0 && now > userLockUntil) {
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
private fun FamilyDispatch(
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
    }
}

// ═══════════════════════════════════════════════════════════════════
// 家族 A：ANCHOR —— 播放器快照（封面主导）
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun FamilyAnchorCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
    isCurrentPage: Boolean = true,
) {
    val c = card.content as AnchorContent

    // 取封面主色做背景渐变（切歌时平滑过渡）
    val themeVM: com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel =
        org.koin.compose.viewmodel.koinViewModel()
    val palette by themeVM.paletteColors.collectAsState()
    val defaultPrimary = Color(0xFF3D5AFE)
    val defaultBg = Color(0xFF1A237E)
    val primaryAnimated by androidx.compose.animation.animateColorAsState(
        targetValue = if (palette.background != defaultBg) palette.primary else defaultPrimary,
        label = "anchor-primary",
    )
    val bgAnimated by androidx.compose.animation.animateColorAsState(
        targetValue = if (palette.background != defaultBg) palette.background else defaultBg,
        label = "anchor-bg",
    )

    // 当前行歌词
    val pbController = koinInject<PlaybackController>()
    val lyricsText by pbController.currentMusicLyrics.collectAsState()
    val currentPos by pbController.currentPosition.collectAsState()
    val parsedLyrics = remember(lyricsText) {
        lyricsText?.let { LrcParser.parse(it) } ?: emptyList()
    }
    val currentLyric by remember(parsedLyrics, currentPos) {
        androidx.compose.runtime.derivedStateOf {
            val idx = findCurrentLyricIndex(parsedLyrics, currentPos)
            parsedLyrics.getOrNull(idx)?.originalText
        }
    }

    val clickMod = when {
        onLongClick != null -> modifier.pointerInput(card.cardId) {
            detectTapGestures(
                onLongPress = { onLongClick?.invoke(card) },
                onTap = { /* 短按留给 Pager 手势 */ },
            )
        }
        onCardClick != null -> modifier.clickable { onCardClick?.invoke(card) }
        else -> modifier
    }

    Card(
        modifier = clickMod.clip(RoundedCornerShape(25.dp)),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryAnimated.copy(alpha = 0.95f),
                            bgAnimated.copy(alpha = 0.90f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(2f)
                ) {
                    when {
                        c.albumArtUri != null -> AsyncImage(
                            model = c.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.clip(RoundedCornerShape(25.dp))
                        )

                        else -> Image(
                            painter = painterResource(Res.drawable.none),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.clip(RoundedCornerShape(25.dp))
                        )
                    }
                }

                // 右侧：歌名 + 艺术家 + 当前歌词 + 元数据
                Column(
                    modifier = Modifier.weight(1f).padding(8.dp)
                ) {
                    Text(
                        text = c.trackTitle ?: "未播放",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = c.artistName ?: "未知",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 当前行歌词（有歌词才显示）
                    currentLyric?.let { lyric ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = lyric,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (c.durationSec > 0) {
                        Column {
                            LinearProgressIndicator(
                                progress = { c.progressPercent!! / 100f },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = Color.White.copy(alpha = 0.8f),
                                trackColor = Color.White.copy(alpha = 0.15f),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = formatDurationSec((c.progressPercent * c.durationSec / 100f).toInt()),
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 8.sp,
                                )
                                Text(
                                    text = formatDurationSec(c.durationSec),
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 8.sp,
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 家族 B-new：ANNIVERSARY 专用卡——4 种子类型差异化视觉
// ═══════════════════════════════════════════════════════════════════

/** ANNIVERSARY 子类型 → 视觉配置（颜色、emoji、徽章文本、标签）。 */
private data class AnniversaryVisual(
    val themeColor: Color,
    val label: String,        // 顶部小标签（如 "N 年前的今天"）
    val badgeMain: String,    // 徽章主文本（如 "3" / "100" / "50h"）
    val badgeSub: String?,    // 徽章副文本（如 "年前" / "次" / null）
    val footerParts: List<String>,  // 底部数字条分片
)

private fun AnniversaryContent.resolveVisual(): AnniversaryVisual = when (subtype) {
    AnniversarySubtype.FIRST_PLAY -> AnniversaryVisual(
        themeColor = Color(0xFFFFA000),
        label = "${yearsAgo} 年前的今天",
        badgeMain = (yearsAgo ?: 1).toString(),
        badgeSub = "年前",
        footerParts = buildList {
            // 只取日期部分，"N 年前" 已由 label + 徽章表达
            specificDate?.substringBefore("·")?.trim()?.let { add(it) }
            if ((thatDayPlays ?: 0) > 0) add("那天听了 ${thatDayPlays} 遍")
            add("累计 $totalPlays 次")
            totalListenHours?.let { if (it > 0) add("${it}h") }
        },
    )
    AnniversarySubtype.PLAYLIST_CREATE -> AnniversaryVisual(
        themeColor = Color(0xFFAB47BC),
        label = "${yearsAgo} 年前建的歌单",
        badgeMain = "歌单",
        badgeSub = null,
        footerParts = buildList {
            specificDate?.substringBefore("·")?.trim()?.let { add(it) }
            add("$totalPlays 次播放")
        },
    )
    AnniversarySubtype.PLAY_MILESTONE -> AnniversaryVisual(
        themeColor = Color(0xFF26A69A),
        label = "里程碑",
        badgeMain = (milestoneValue ?: 100).toString(),
        badgeSub = "次",
        footerParts = buildList {
            // "100 次" 已由徽章表达，放总数供参考
            add("共 $totalPlays 次")
            totalListenHours?.let { if (it > 0) add("${it}h") }
        },
    )
    AnniversarySubtype.DURATION_MILESTONE -> AnniversaryVisual(
        themeColor = Color(0xFF42A5F5),
        label = "里程碑",
        badgeMain = "${milestoneValue ?: 10}h",
        badgeSub = null,
        footerParts = buildList {
            // "50h" 已由徽章表达，放累计总数 + 播放次数
            add("共 $totalPlays 次")
            totalListenHours?.let { if (it > 0) add("${it}h") }
        },
    )
}

@Composable
private fun FamilyAnniversaryCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val content = card.content as? AnniversaryContent ?: return
    val visual = content.resolveVisual()

    val clickMod = when {
        onLongClick != null -> modifier.pointerInput(card.cardId) {
            detectTapGestures(
                onLongPress = { onLongClick?.invoke(card) },
                onTap = { /* 短按留给 Pager 手势 */ },
            )
        }
        onCardClick != null -> modifier.clickable { onCardClick?.invoke(card) }
        else -> modifier
    }

    Card(
        modifier = clickMod.clip(RoundedCornerShape(25.dp)),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(visual.themeColor.copy(alpha = 0.95f), visual.themeColor.copy(alpha = 0.75f)),
                    )
                )
                .padding(20.dp)
        ) {
            // 装饰性圆斑（右上角）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Color.White.copy(alpha = 0.06f))
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ═══ 左：徽章区（ANNIVERSARY 专属视觉焦点）═══
                AnniversaryBadge(
                    visual = visual,
                    size = 110.dp,
                )

                // ═══ 右：信息区 ═══
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 标签
                    Text(
                        text = visual.label,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // 主标题
                    Text(
                        text = content.trackTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    // 分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(vertical = 8.dp)
                    )

                    // 情感文案（Llm 生成的那句话）
                    content.emotionText?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // 底部数据点（多行垂直排列）
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        visual.footerParts.forEach { part ->
                            Text(
                                text = "· $part",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** ANNIVERSARY 专属徽章：圆形或方形（PLAYLIST_CREATE），内含大数字/emoji。 */
@Composable
private fun AnniversaryBadge(
    visual: AnniversaryVisual,
    size: androidx.compose.ui.unit.Dp,
) {
    val isSquareEmoji = visual.badgeSub == null && visual.badgeMain.length <= 2
    Box(
        modifier = Modifier
            .size(size)
            .clip(if (isSquareEmoji) RoundedCornerShape(20.dp) else RoundedCornerShape(size / 2))
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (visual.badgeSub != null) {
            // 徽章主副两行（如 "3" + "年前" / "100" + "次"）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = visual.badgeMain,
                    color = Color.White,
                    fontSize = when (visual.badgeMain.length) {
                        in 1..2 -> 26.sp
                        3 -> 20.sp
                        else -> 16.sp
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = visual.badgeSub,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        } else {
            // 单行徽章文本（如 "歌单" / "50h"）
            Text(
                text = visual.badgeMain,
                color = Color.White,
                fontSize = when {
                    visual.badgeMain.length <= 2 -> 22.sp
                    visual.badgeMain.length <= 4 -> 18.sp
                    else -> 16.sp
                },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 家族 B：单曲展示卡（RECOMMEND / FORGOTTEN 共用骨架）
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun FamilySingleTrackCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val clickMod = when {
        onLongClick != null -> modifier.pointerInput(card.cardId) {
            detectTapGestures(
                onLongPress = { onLongClick?.invoke(card) },
                onTap = { /* 短按留给 Pager 手势 */ },
            )
        }
        onCardClick != null -> modifier.clickable { onCardClick?.invoke(card) }
        else -> modifier
    }

    val meta = buildSingleTrackMeta(card)

    // 异步查封面 + 艺术家（一次查询拿全）
    val repo: MusicRepository = koinInject()
    var asyncCoverUri by remember(meta.trackId) { mutableStateOf<String?>(null) }
    var asyncArtist by remember(meta.trackId) { mutableStateOf<String?>(null) }
    LaunchedEffect(meta.trackId) {
        runCatching { repo.getMusicInfoByIds(listOf(meta.trackId)) }
            .getOrNull()?.firstOrNull()?.let { info ->
                asyncCoverUri = info.music.albumArtUri
                asyncArtist = info.music.artist
            }
    }

    Card(
        modifier = clickMod.clip(RoundedCornerShape(25.dp)),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(meta.themeColor.copy(alpha = 0.95f), meta.themeColor.copy(alpha = 0.80f))
                    )
                )
                .padding(20.dp)
        ) {
            // 主体：文字 Column（填满空间）
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                // 标签
                Text(
                    text = meta.label,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 歌曲名（加大字号，作为视觉焦点）
                Text(
                    text = meta.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, end = 72.dp),
                )

                // 艺术家（异步查回来就显示）
                asyncArtist?.let { artist ->
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // 分割线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(vertical = 8.dp)
                )

                // 补充文案（LLM 生成的推荐语 / 怀旧随笔）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    meta.subtitle?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 底部数字条
                meta.footer?.let { footerText ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = footerText,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                        )
                        if (meta.durationSec > 0) {
                            Text(
                                text = "· ${formatDurationSec(meta.durationSec)}",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // 右上角小封面（64dp，装饰性）
            AsyncCoverForTrack(
                coverUri = asyncCoverUri,
                size = 64.dp,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 家族 B 辅助：SingleTrackMeta + buildSingleTrackMeta
// ═══════════════════════════════════════════════════════════════════

private data class SingleTrackMeta(
    val trackId: Long,
    val label: String,
    val title: String,
    val subtitle: String?,       // 补充文案（推荐理由 / 情感化文本）
    val footer: String?,          // 底部数字条文本
    val durationSec: Int,
    val themeColor: Color,
)

private fun buildSingleTrackMeta(card: SlideCard): SingleTrackMeta {
    return when (val c = card.content) {
        is RecommendContent -> SingleTrackMeta(
            trackId = c.trackId,
            label = "为你推荐 · ${c.currentPhase.label}",
            title = c.trackTitle,
            subtitle = c.reason,
            footer = if (!c.sourceLabel.isNullOrEmpty()) "来自 ${c.sourceLabel} · AI 推荐" else "AI 推荐",
            durationSec = c.durationSec,
            themeColor = Color(0xFF7C4DFF),
        )

        is ForgottenContent -> SingleTrackMeta(
            trackId = c.trackId,
            label = "${c.daysSince} 天没听了",
            title = c.trackTitle,
            subtitle = c.emotionText,
            footer = "累计 ${c.playCount} 次",
            durationSec = c.durationSec,
            themeColor = Color(0xFF78909C),
        )

        else -> SingleTrackMeta(
            trackId = 0, label = "未知", title = "未知",
            subtitle = null, footer = null, durationSec = 0,
            themeColor = Color.Gray,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// AsyncCoverForTrack — 纯渲染：coverUri 有值显示 AsyncImage，否则占位 unknown
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AsyncCoverForTrack(
    coverUri: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!coverUri.isNullOrEmpty()) {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(Res.drawable.unknown),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 家族 C：DISCOVER —— 多曲目探索
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun FamilyDiscoverCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val c = card.content as DiscoverContent
    val clickMod = when {
        onLongClick != null -> modifier.pointerInput(card.cardId) {
            detectTapGestures(
                onLongPress = { onLongClick?.invoke(card) },
                onTap = { /* 短按留给 Pager 手势 */ },
            )
        }
        onCardClick != null -> modifier.clickable { onCardClick?.invoke(card) }
        else -> modifier
    }

    // Agent 预取字段为空（旧 Room 缓存兼容）→ UI 层异步补全 title/artist/albumArt
    val repo: MusicRepository = koinInject()
    val albumUris = remember(c.trackIds) { mutableStateOf<List<String?>>(List(c.trackIds.size) { null }) }
    val liveTitles = remember(c.trackIds) { mutableStateOf<List<String>>(c.trackIds.map { "" }) }
    val liveArtists = remember(c.trackIds) { mutableStateOf<List<String>>(c.trackIds.map { "" }) }
    LaunchedEffect(c.trackIds) {
        runCatching { repo.getMusicInfoByIds(c.trackIds) }
            .onSuccess { infos ->
                val infoMap = infos.associateBy { it.music.id }
                albumUris.value = c.trackIds.map { infoMap[it]?.music?.albumArtUri }
                liveTitles.value = c.trackIds.map { infoMap[it]?.music?.title.orEmpty() }
                liveArtists.value = c.trackIds.map { infoMap[it]?.music?.artist.orEmpty() }
            }
    }

    Card(
        modifier = clickMod.clip(RoundedCornerShape(25.dp)),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF00ACC1).copy(alpha = 0.95f), Color(0xFF006064).copy(alpha = 0.90f))
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,   // 整体垂直居中
            ) {
                // ① 主题标签
                Text(
                    text = "发现「${c.target}」",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                // ② 推荐理由
                Text(
                    text = c.reason,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )

                // ③ 曲目预览列表（最多 3 首，紧凑布局）
                c.trackIds.take(3).forEachIndexed { idx, trackId ->
                    val uri = albumUris.value.getOrNull(idx)
                    val title = (c.trackTitles.getOrNull(idx).orEmpty().ifBlank { liveTitles.value.getOrNull(idx).orEmpty() }).ifBlank { "未知曲目" }
                    val artist = (c.trackArtists.getOrNull(idx).orEmpty().ifBlank { liveArtists.value.getOrNull(idx).orEmpty() }).ifBlank { "未知艺术家" }
                    val dur = c.trackDurations.getOrNull(idx)?.coerceAtLeast(0) ?: 0

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 封面（48×48dp，圆角 10dp）
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!uri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Image(
                                    painter = painterResource(Res.drawable.unknown),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        // 标题 + 艺术家
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.ifBlank { "未知曲目" },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = artist.ifBlank { "未知艺术家" },
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // 时长
                        Text(
                            text = if (dur > 0) formatDurationSec(dur) else "",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                        )
                    }
                }

                // ④ 底部总量
                Text(
                    text = "· 共 ${c.trackIds.size} 首可探索",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 家族 D：RADIO_STATUS —— 电台运行态
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun FamilyRadioStatusCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val c = card.content as RadioStatusContent
    val clickMod = when {
        onLongClick != null -> modifier.pointerInput(card.cardId) {
            detectTapGestures(
                onLongPress = { onLongClick?.invoke(card) },
                onTap = { /* 短按留给 Pager 手势 */ },
            )
        }
        onCardClick != null -> modifier.clickable { onCardClick?.invoke(card) }
        else -> modifier
    }

    Card(
        modifier = clickMod.clip(RoundedCornerShape(25.dp)),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF00C853).copy(alpha = 0.95f), Color(0xFF00695C).copy(alpha = 0.90f))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // —— 电台标题：🎵 主题 · 状态 ——
                val titleMain = c.stationTheme?.let { "🎵 ${it}电台" } ?: "🎵 电台"
                Text(
                    text = if (c.actionText == "播放中") "$titleMain · 播放中" else titleMain,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(14.dp))

                if (c.progressPercent != null) {
                    // —— BUILDING：加载态 ——
                    LinearProgressIndicator(
                        progress = { c.progressPercent!! / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color.White.copy(alpha = 0.9f),
                        trackColor = Color.White.copy(alpha = 0.15f),
                    )
                    Text(
                        text = c.actionText,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    // —— PLAYING：核心态 ——
                    // 正在播
                    c.nowPlayingTitle?.let { title ->
                        Text(
                            text = "正在播",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                        )
                        Text(
                            text = buildString {
                                append(title)
                                c.nowPlayingArtist?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                            },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // 下一首 + why（🌟 电台灵魂）
                    c.nextTrackTitle?.let { nextTitle ->
                        val hasWhy = !c.nextTrackWhy.isNullOrBlank()
                        Text(
                            text = if (hasWhy) "下一首 · $nextTitle" else "下一首：$nextTitle",
                            color = Color.White.copy(alpha = if (hasWhy) 0.65f else 0.85f),
                            fontSize = if (hasWhy) 11.sp else 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasWhy) {
                            Text(
                                text = "「${c.nextTrackWhy}」",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                // —— 底部概览 ——
                c.playlistCount?.let { count ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "· 共 $count 首 · 电台为你选",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 家族 E：GREETING —— 纯文案（去封面槽）
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun FamilyGreetingCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val c = card.content as GreetingContent
    val clickMod = when {
        onLongClick != null -> modifier.pointerInput(card.cardId) {
            detectTapGestures(
                onLongPress = { onLongClick?.invoke(card) },
                onTap = { /* 短按留给 Pager 手势 */ },
            )
        }
        onCardClick != null -> modifier.clickable { onCardClick?.invoke(card) }
        else -> modifier
    }

    Card(
        modifier = clickMod.clip(RoundedCornerShape(25.dp)),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFB300).copy(alpha = 0.95f), Color(0xFFFF6F00).copy(alpha = 0.90f))
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                // 时段感知问候
                Text(
                    text = "${c.phase.label}好～",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // 主文案（居中，根据内容长度自动适配）
                Text(
                    text = c.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 23.sp,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 工具函数
// ═══════════════════════════════════════════════════════════════════

private val TimePhase.label: String
    get() = when (this) {
        TimePhase.NIGHT -> "深夜"
        TimePhase.MORNING_COMMUTE -> "早高峰"
        TimePhase.WORK -> "工作"
        TimePhase.LUNCH -> "午休"
        TimePhase.EVENING_COMMUTE -> "晚高峰"
        TimePhase.EVENING_LEISURE -> "晚间"
        TimePhase.UNKNOWN -> ""
    }

private fun formatDurationSec(sec: Int): String {
    if (sec <= 0) return ""
    val m = sec / 60
    val s = sec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

// ═══════════════════════════════════════════════════════════════════
// Fallback 卡池为空时的兜底卡
// ═══════════════════════════════════════════════════════════════════

private fun HelloFallbackCard() = SlideCard(
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

