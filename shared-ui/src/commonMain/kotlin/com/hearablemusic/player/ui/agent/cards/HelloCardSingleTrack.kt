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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
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
import com.hearablemusic.player.ui.generated.resources.unknown
import com.hmp.domain.agent.card.ForgottenContent
import com.hmp.domain.agent.card.RecommendContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.music.MusicRepository
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject



import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
// ═══════════════════════════════════════════════════════════════════
// 家族 B：单曲展示卡（RECOMMEND / FORGOTTEN 共用骨架）
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun FamilySingleTrackCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
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
        modifier = clickMod,
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
