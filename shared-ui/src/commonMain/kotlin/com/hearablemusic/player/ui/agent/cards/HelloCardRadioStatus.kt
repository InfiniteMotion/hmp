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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.hearablemusic.player.ui.common.design.colors.HelloCardPalette
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.card_radio_candidate
import com.hearablemusic.player.ui.generated.resources.card_radio_continuing
import com.hearablemusic.player.ui.generated.resources.card_radio_enrich_optimizing
import com.hearablemusic.player.ui.generated.resources.card_radio_next
import com.hearablemusic.player.ui.generated.resources.card_radio_reorder_skipped
import com.hearablemusic.player.ui.generated.resources.card_radio_theme_ready
import com.hearablemusic.player.ui.generated.resources.card_radio_title_default
import com.hearablemusic.player.ui.generated.resources.card_radio_title_theme
import com.hearablemusic.player.ui.generated.resources.card_radio_upcoming
import com.hearablemusic.player.ui.generated.resources.card_radio_why
import com.hearablemusic.player.ui.generated.resources.unknown
import com.hmp.domain.agent.card.RadioStatusContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.agent.runtime.sub.radio.RadioCardState
import com.hmp.domain.agent.runtime.sub.radio.RadioMessage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
// ═══════════════════════════════════════════════════════════════════
// 家族 D：RADIO_STATUS —— 电台运行态（左 ANCHOR + 右 Radio 状态）
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun FamilyRadioStatusCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
    /** 由 HelloCardsViewModel 快照下发，卡片不再直连 MasterAgent。 */
    radioMessage: RadioMessage? = null,
    radioCard: RadioCardState = RadioCardState(theme = null, upcomingCount = 0, nextTitle = null, lastAdjust = null),
) {
    val metrics = LocalHmpMetrics.current
    val c = card.content as RadioStatusContent
    val typo = LocalHmpTypo.current
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

    // 短时消息与队列动态来自 HelloCardsViewModel 快照（数据组装不再在卡内直连 agent）。

    // 取当前播放封面主色做背景渐变（跟 FamilyAnchorCard 一致，切歌时平滑过渡）
    val themeVM: com.hearablemusic.player.ui.common.viewmodel.ThemeViewModel =
        activityViewModel()
    val palette by themeVM.paletteColors.collectAsState()
    val defaultPrimary = HelloCardPalette.CoverFallback.Primary
    val defaultBg = HelloCardPalette.CoverFallback.Background
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
                .padding(horizontal = metrics.cozy, vertical = metrics.cozy),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // —— 顶部主题栏（横跨全宽）——
                val titleMain = c.stationTheme?.let {
                    stringResource(Res.string.card_radio_title_theme, it)
                } ?: stringResource(Res.string.card_radio_title_default)
                Text(
                    text = "$titleMain · ${c.actionText}",
                    style = typo.body,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(metrics.base))

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
                                .size(metrics.panel)
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
                        Spacer(Modifier.height(metrics.compact))
                        c.nowPlayingTitle?.let {
                            Text(
                                text = it,
                                style = typo.body.medium(),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        c.nowPlayingArtist?.let {
                            Text(
                                text = it,
                                style = typo.footnote,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.width(metrics.cozy))

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
                                modifier = Modifier.fillMaxWidth().height(metrics.micro).clip(RoundedCornerShape(metrics.hair)),
                                color = Color.White.copy(alpha = 0.9f),
                                trackColor = Color.White.copy(alpha = 0.15f),
                            )
                            Spacer(Modifier.height(metrics.compact))
                            Text(
                                text = c.actionText,
                                style = typo.footnote.medium(),
                                color = Color.White.copy(alpha = 0.8f),
                            )
                            c.nowPlayingTitle?.let {
                                Spacer(Modifier.height(metrics.micro))
                                Text(
                                    text = stringResource(Res.string.card_radio_candidate, it),
                                    style = typo.caption,
                                    color = Color.White.copy(alpha = 0.55f),
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
                                    text = stringResource(Res.string.card_radio_why, why),
                                    style = typo.body.medium(),
                                    color = Color.White,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(metrics.compact))
                            }

                            // 队列动态：待播数与下一首分行（下一首本地推断兜底）
                            if (radioCard.upcomingCount > 0) {
                                Text(
                                    text = stringResource(Res.string.card_radio_upcoming, radioCard.upcomingCount),
                                    style = typo.footnote,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(metrics.micro))
                            }
                            (radioCard.nextTitle ?: c.nextTrackTitle)?.let {
                                Text(
                                    text = stringResource(Res.string.card_radio_next, it),
                                    style = typo.footnote,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(metrics.compact))
                            }

                            // 短时消息（4s 自动退场）——垂直居中布局，不再用 weight 撑底
                            radioMessage?.let { msg ->
                                val msgText = when (msg) {
                                    is RadioMessage.ReorderSkipped ->
                                        stringResource(Res.string.card_radio_reorder_skipped, msg.count)

                                    is RadioMessage.EnrichOptimizing ->
                                        stringResource(Res.string.card_radio_enrich_optimizing)

                                    is RadioMessage.TrackContinuing ->
                                        stringResource(Res.string.card_radio_continuing, msg.title)

                                    is RadioMessage.ThemeChanged ->
                                        stringResource(Res.string.card_radio_theme_ready, msg.theme)
                                }
                                Text(
                                    text = msgText,
                                    style = typo.caption,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(metrics.compact))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = metrics.compact, vertical = metrics.micro),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
