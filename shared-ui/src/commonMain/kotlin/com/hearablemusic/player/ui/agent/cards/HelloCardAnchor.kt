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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
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
import com.hearablemusic.player.ui.generated.resources.none
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.agent.card.AnchorContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.SlideType
import com.hmp.domain.agent.card.TimePhase
import com.hmp.domain.agent.port.NowPlayingContext
import com.hmp.domain.lyrics.LrcParser
import com.hmp.domain.lyrics.findCurrentLyricIndex
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject



import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
// ═══════════════════════════════════════════════════════════════════
// ANCHOR / RADIO_STATUS 独立构建函数（UI 层直接调用，不走 agent）
// ═══════════════════════════════════════════════════════════════════

internal fun buildAnchorCardFromContext(
    ctx: NowPlayingContext?,
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

/** 电台卡「主播按语」位只认模型真按语——本地落库路径全是占位文案，不能当按语展示。 */
// ═══════════════════════════════════════════════════════════════════
// 家族 A：ANCHOR —— 播放器快照（封面主导）
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun FamilyAnchorCard(
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
                                progress = { c.progressPercent / 100f },
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
