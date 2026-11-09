package com.hearablemusic.player.ui.agent.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asString
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.card_label_recommend_phase
import com.hearablemusic.player.ui.generated.resources.card_single_ai_pick
import com.hearablemusic.player.ui.generated.resources.card_single_days_ago
import com.hearablemusic.player.ui.generated.resources.card_single_source_ai
import com.hearablemusic.player.ui.generated.resources.card_single_unknown
import com.hearablemusic.player.ui.generated.resources.card_total_plays
import com.hearablemusic.player.ui.generated.resources.unknown
import com.hmp.domain.agent.card.ForgottenContent
import com.hmp.domain.agent.card.RecommendContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.music.MusicInfo
import org.jetbrains.compose.resources.painterResource
// ═══════════════════════════════════════════════════════════════════
// 家族 B：单曲展示卡（RECOMMEND / FORGOTTEN 共用骨架）
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun FamilySingleTrackCard(
    card: SlideCard,
    trackLookup: Map<Long, MusicInfo>,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val metrics = LocalHmpMetrics.current
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

    val typo = LocalHmpTypo.current
    val meta = buildSingleTrackMeta(card)
    // 补全结果由 VM 查询后随快照下发，卡片不再自己起协程查库
    val resolved = trackLookup[meta.trackId]
    val coverUri = resolved?.music?.albumArtUri
    val artist = resolved?.music?.artist

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
                        colors = listOf(meta.themeColor.copy(alpha = 0.95f), meta.themeColor.copy(alpha = 0.80f)),
                    ),
                )
                .padding(metrics.cozy),
        ) {
            // 主体：文字 Column（填满空间）
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                // 标签
                Text(
                    text = meta.label.asString(),
                    style = typo.footnote.medium(),
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 歌曲名（加大字号，作为视觉焦点）
                Text(
                    text = meta.title.asString(),
                    style = typo.title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = metrics.micro, end = metrics.row),
                )

                // 艺术家（VM 补全后显示）
                artist?.let { artistName ->
                    Text(
                        text = artistName,
                        style = typo.footnote,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = metrics.hair),
                    )
                }

                // 分割线
                // 原写法 `height(1.dp).background(...).padding(vertical = 8.dp)` 是**错的顺序**：
                // padding 在 background 之后 ⇒ 先画 1dp 细线、再在外侧套 8dp 空白，
                // 但 Modifier 链是从外到内 apply，padding 在内层意味着总高仍是 1dp，
                // 8dp 间距**从未生效**（线被压扁、与上下文字挤在一起）。正确写法是
                // `padding(vertical).height(1.dp).background(...)`：先占 17dp 外部空间，内部才是 1dp 线。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = metrics.compact)
                        .height(metrics.stroke.thin)
                        .background(Color.White.copy(alpha = 0.2f)),
                )

                // 补充文案（LLM 生成的推荐语 / 怀旧随笔）
                // `weight(1f, fill = false)`：允许该行吸收余量，但**不必填满** ——
                // 用 `fill = false` 后它只取内容实际需要的高度，剩余空间交还给父 Column 的
                // `Arrangement.Center` 去居中整组内容。原 `weight(1f)`（fill 默认 true）
                // 会把该行**强行撑满**所有余量，于是顶部标签/曲名与底部数字条被推到两端、
                // 中间留下大块空白 —— 这正是宽窗下卡片"一大坨死紫"的直接成因。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    meta.subtitle?.let {
                        Text(
                            text = it.asString(),
                            style = typo.footnote,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 底部数字条
                meta.footer?.let { footerText ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(metrics.micro),
                    ) {
                        Text(
                            text = footerText.asString(),
                            style = typo.footnote,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                        if (meta.durationSec > 0) {
                            Text(
                                text = "· ${formatDurationSec(meta.durationSec)}",
                                style = typo.footnote,
                                color = Color.White.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }

            // 右上角小封面（64dp，装饰性）
            AsyncCoverForTrack(
                coverUri = coverUri,
                size = metrics.row,
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
    val label: UiText,
    val title: UiText,
    val subtitle: UiText?, // 补充文案（LLM 产出 → Raw）
    val footer: UiText?, // 底部数字条文本
    val durationSec: Int,
    val themeColor: Color,
)

private fun buildSingleTrackMeta(card: SlideCard): SingleTrackMeta = when (val c = card.content) {
    is RecommendContent -> SingleTrackMeta(
        trackId = c.trackId,
        label = UiText.Res(Res.string.card_label_recommend_phase, listOf(c.currentPhase.label)),
        title = UiText.Raw(c.trackTitle),
        subtitle = c.reason?.let { UiText.Raw(it) },
        footer = if (!c.sourceLabel.isNullOrEmpty()) {
            UiText.Res(Res.string.card_single_source_ai, listOf(c.sourceLabel.orEmpty()))
        } else {
            UiText.Res(Res.string.card_single_ai_pick)
        },
        durationSec = c.durationSec,
        themeColor = HelloCardPalette.SingleTrack.Recommend,
    )

    is ForgottenContent -> SingleTrackMeta(
        trackId = c.trackId,
        label = UiText.Res(Res.string.card_single_days_ago, listOf(c.daysSince)),
        title = UiText.Raw(c.trackTitle),
        subtitle = c.emotionText?.let { UiText.Raw(it) },
        footer = UiText.Res(Res.string.card_total_plays, listOf(c.playCount)),
        durationSec = c.durationSec,
        themeColor = HelloCardPalette.SingleTrack.Forgotten,
    )

    else -> SingleTrackMeta(
        trackId = 0,
        label = UiText.Res(Res.string.card_single_unknown),
        title = UiText.Res(Res.string.card_single_unknown),
        subtitle = null,
        footer = null,
        durationSec = 0,
        themeColor = Color.Gray,
    )
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
    val metrics = LocalHmpMetrics.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(metrics.base))
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
