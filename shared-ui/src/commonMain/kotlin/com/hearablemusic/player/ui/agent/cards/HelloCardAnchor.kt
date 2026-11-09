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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.hearablemusic.player.ui.common.design.colors.HelloCardPalette
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.card_anchor_not_playing
import com.hearablemusic.player.ui.generated.resources.card_unknown
import com.hearablemusic.player.ui.generated.resources.none
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.agent.card.AnchorContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.card.SlideType
import com.hmp.domain.agent.card.TimePhase
import com.hmp.domain.music.MusicInfo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
// ═══════════════════════════════════════════════════════════════════
// ANCHOR / RADIO_STATUS 独立构建函数（UI 层直接调用，不走 agent）
// ═══════════════════════════════════════════════════════════════════

/**
 * 只快照**低频**信息（曲目 / 播放态 / 时长）。实时进度不进快照：
 * 它是 100ms 一跳的高频数据，进快照会让整份 HelloCardsState 以 10Hz 重建。
 */
internal fun buildAnchorCard(
    music: MusicInfo?,
    isPlaying: Boolean,
    durationMs: Long,
): SlideCard = SlideCard(
    cardId = "anchor", // 固定 ID，避免每次 replace 生成新 cardId 导致动画
    type = SlideType.ANCHOR,
    content = AnchorContent(
        trackTitle = music?.music?.title,
        artistName = music?.music?.artist,
        bpm = null,
        phase = detectTimePhaseLocal(com.hmp.data.util.currentHour()),
        albumArtUri = music?.music?.albumArtUri,
        isPlaying = isPlaying,
        durationSec = (durationMs / 1000).toInt(),
        sourceLabel = null,
    ),
)

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
    /** 当前行歌词：由 HelloCardsViewModel 解析并去重后下发。 */
    currentLyricLine: String? = null,
) {
    val metrics = LocalHmpMetrics.current
    val c = card.content as AnchorContent
    val typo = LocalHmpTypo.current

    // 取封面主色做背景渐变（切歌时平滑过渡）
    val themeVM: com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel =
        activityViewModel()
    val palette by themeVM.paletteColors.collectAsState()
    val defaultPrimary = HelloCardPalette.CoverFallback.Primary
    val defaultBg = HelloCardPalette.CoverFallback.Background
    val primaryAnimated by androidx.compose.animation.animateColorAsState(
        targetValue = if (palette.background != defaultBg) palette.primary else defaultPrimary,
        label = "anchor-primary",
    )
    val bgAnimated by androidx.compose.animation.animateColorAsState(
        targetValue = if (palette.background != defaultBg) palette.background else defaultBg,
        label = "anchor-bg",
    )

    // 高频播放进度只把 State 往下传，不在本函数体里读 value：
    // 谁读谁重组。进度条下沉到叶子组件，卡片主体不再以 10Hz 重组。
    val pbController = koinInject<PlaybackController>()
    val positionState = pbController.currentPosition.collectAsState()

    val clickMod = modifier.clip(RoundedCornerShape(metrics.section)).let { m ->
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
        shape = RoundedCornerShape(metrics.section),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryAnimated.copy(alpha = 0.95f),
                            bgAnimated.copy(alpha = 0.90f),
                        ),
                    ),
                )
                .padding(metrics.cozy),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(2f),
                ) {
                    when {
                        c.albumArtUri != null -> AsyncImage(
                            model = c.albumArtUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.clip(RoundedCornerShape(metrics.section)),
                        )

                        else -> Image(
                            painter = painterResource(Res.drawable.none),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.clip(RoundedCornerShape(metrics.section)),
                        )
                    }
                }

                // 右侧：歌名 + 艺术家 + 当前歌词 + 元数据
                Column(
                    modifier = Modifier.weight(1f).padding(metrics.compact),
                ) {
                    Text(
                        text = c.trackTitle ?: stringResource(Res.string.card_anchor_not_playing),
                        style = typo.footnote.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(metrics.micro))
                    Text(
                        text = c.artistName ?: stringResource(Res.string.card_unknown),
                        style = typo.caption,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 当前行歌词（有歌词才显示）：解析与去重都在 VM
                    currentLyricLine?.let { lyric ->
                        Spacer(Modifier.height(metrics.compact))
                        Text(
                            text = lyric,
                            style = typo.micro,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(metrics.cozy))
                    if (c.durationSec > 0) {
                        Column {
                            AnchorProgressBar(
                                positionState = positionState,
                                durationSec = c.durationSec,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                AnchorElapsedText(positionState = positionState)
                                Text(
                                    text = formatDurationSec(c.durationSec),
                                    style = typo.micro,
                                    color = Color.White.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 进度条：进度 lambda 里读 State，只触发绘制阶段更新，不重组卡片。 */
@Composable
private fun AnchorProgressBar(
    positionState: State<Long>,
    durationSec: Int,
) {
    val metrics = LocalHmpMetrics.current
    LinearProgressIndicator(
        progress = {
            if (durationSec > 0) {
                (positionState.value / 1000f / durationSec).coerceIn(0f, 1f)
            } else {
                0f
            }
        },
        modifier = Modifier.fillMaxWidth().height(metrics.hair),
        color = Color.White.copy(alpha = 0.8f),
        trackColor = Color.White.copy(alpha = 0.15f),
    )
}

/** 已播时间：按秒派生，1Hz 更新且只重组这一个 Text。 */
@Composable
private fun AnchorElapsedText(positionState: State<Long>) {
    val typo = LocalHmpTypo.current
    val elapsedSec by remember(positionState) {
        derivedStateOf { (positionState.value / 1000L).toInt() }
    }
    Text(
        text = formatDurationSec(elapsedSec),
        style = typo.micro,
        color = Color.White.copy(alpha = 0.45f),
    )
}
