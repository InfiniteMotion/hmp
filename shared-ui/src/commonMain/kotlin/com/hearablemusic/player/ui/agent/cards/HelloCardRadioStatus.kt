package com.hearablemusic.player.ui.agent.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.unknown
import com.hmp.domain.agent.card.RadioStatusContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.SlideType
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.sub.radio.RadioMessage
import com.hmp.domain.agent.runtime.sub.radio.RadioState
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject



import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
private fun hostWhy(why: String?): String? = why?.takeIf {
    it.isNotBlank() && !it.startsWith("标签匹配") &&
        it !in setOf("在播", "在播·电台起点", "电台续播", "热门曲目", "补充曲目")
}

internal suspend fun buildRadioStatusCard(
    masterAgent: MasterAgent?,
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
                    actionText = "播放中",
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
                    actionText = "已暂停",
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

// ═══════════════════════════════════════════════════════════════════
// 家族 D：RADIO_STATUS —— 电台运行态（左 ANCHOR + 右 Radio 状态）
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun FamilyRadioStatusCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val c = card.content as RadioStatusContent
    val clickMod = modifier.clip(RoundedCornerShape(25.dp)).let { m ->
        when {
            onLongClick != null -> m.pointerInput(card.cardId) {
                detectTapGestures(
                    onLongPress = { onLongClick.invoke(card) },
                    onTap = { /* 短按留给 Pager 手势 */ },
                )
            }
            onCardClick != null -> m.clickable { onCardClick.invoke(card) }
            else -> m
        }
    }

    // 短时消息（4s 自动消失，RadioSubAgent 内部管理生命周期）
    val masterAgent = koinInject<MasterAgent>()
    val radioMessage by masterAgent.radioMessageFlow().collectAsState()

    // 电台卡状态（主播编排思路 lastAdjust / 待播数 / 下一首）——本卡是信息展示主力，
    // 这些动态信息从 agent 的权威 StateFlow 取，不再依赖本地 playlist 推断
    val radioCard by masterAgent.radioCardState.collectAsState()

    // 取当前播放封面主色做背景渐变（跟 FamilyAnchorCard 一致，切歌时平滑过渡）
    val themeVM: com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel =
        org.koin.compose.viewmodel.koinViewModel()
    val palette by themeVM.paletteColors.collectAsState()
    val defaultPrimary = Color(0xFF3D5AFE)
    val defaultBg = Color(0xFF1A237E)
    val primaryAnimated by androidx.compose.animation.animateColorAsState(
        targetValue = if (palette.background != defaultBg) palette.primary else defaultPrimary,
        label = "radio-primary",
    )
    val bgAnimated by androidx.compose.animation.animateColorAsState(
        targetValue = if (palette.background != defaultBg) palette.background else defaultBg,
        label = "radio-bg",
    )

    Card(
        modifier = clickMod,
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
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // —— 顶部主题栏（横跨全宽）——
                val titleMain = c.stationTheme?.let { "${it}电台" } ?: "电台"
                Text(
                    text = "$titleMain · ${c.actionText}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))

                // —— 始终左右分栏（BUILDING/PLAYING/PAUSED 统一结构）——
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ══ 左半：简化 ANCHOR（封面 + 标题 + 歌手，BUILDING 时可能为空） ══
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 圆形封面
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!c.albumArtUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = c.albumArtUri,
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
                        Spacer(Modifier.height(10.dp))
                        c.nowPlayingTitle?.let {
                            Text(
                                text = it,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        c.nowPlayingArtist?.let {
                            Text(
                                text = it,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    // ══ 右半：Radio 专属状态区（BUILDING 显示 loading，PLAYING/PAUSED 显示按语/队列动态/短时消息） ══
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (c.progressPercent != null) {
                            // —— BUILDING：右半显示 loading 进度 ——
                            LinearProgressIndicator(
                                progress = { c.progressPercent!! / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color.White.copy(alpha = 0.9f),
                                trackColor = Color.White.copy(alpha = 0.15f),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = c.actionText,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 18.sp,
                            )
                            c.nowPlayingTitle?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "候选：$it",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            // 主播按语 —— 电台灵魂（大字体核心差异化）。
                            // 只展示**这首歌**的按语（模型 whys 字段下发、随曲目换段更新）；
                            // 整段的编排思路（lastAdjust）是主播内部工作记忆，不上 UI。
                            c.nowPlayingWhy?.let { why ->
                                Text(
                                    text = "「$why」",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 20.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(10.dp))
                            }

                            // 队列动态：待播数与下一首分行（下一首本地推断兜底）
                            if (radioCard.upcomingCount > 0) {
                                Text(
                                    text = "还有 ${radioCard.upcomingCount} 首待播",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            (radioCard.nextTitle ?: c.nextTrackTitle)?.let {
                                Text(
                                    text = "下一首：$it",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            // 短时消息（4s 自动退场）——垂直居中布局，不再用 weight 撑底
                            radioMessage?.let { msg ->
                                val msgText = when (msg) {
                                    is RadioMessage.ReorderSkipped ->
                                        "跳过 ${msg.count} 首，正在重选..."
                                    is RadioMessage.EnrichOptimizing ->
                                        "AI 正在优化歌单..."
                                    is RadioMessage.TrackContinuing ->
                                        "续播「${msg.title}」"
                                    is RadioMessage.ThemeChanged ->
                                        "${msg.theme} 已就绪"
                                }
                                Text(
                                    text = msgText,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
