package com.hearablemusic.player.ui.agent.cards
import com.hearablemusic.player.ui.common.design.colors.HelloCardPalette
import com.hearablemusic.player.ui.generated.resources.card_discover_footnote
import com.hearablemusic.player.ui.generated.resources.card_discover_title
import com.hearablemusic.player.ui.generated.resources.card_unknown_artist
import com.hearablemusic.player.ui.generated.resources.card_unknown_track
import org.jetbrains.compose.resources.stringResource

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.unknown
import com.hmp.domain.agent.card.DiscoverContent
import com.hmp.domain.agent.card.SlideCard
import com.hmp.domain.music.MusicRepository
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics



// ═══════════════════════════════════════════════════════════════════
// 家族 C：DISCOVER —— 多曲目探索
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun FamilyDiscoverCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val metrics = LocalHmpMetrics.current
    val c = card.content as DiscoverContent
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

    // Agent 预取字段为空（旧 Room 缓存兼容）→ UI 层异步补全 title/artist/albumArt
    val repo: MusicRepository = koinInject()
    val albumUris = remember(c.trackIds) { mutableStateOf<List<String?>>(List(c.trackIds.size) { null }) }
    val liveTitles = remember(c.trackIds) { mutableStateOf(c.trackIds.map { "" }) }
    val liveArtists = remember(c.trackIds) { mutableStateOf(c.trackIds.map { "" }) }
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
        modifier = clickMod,
        shape = RoundedCornerShape(metrics.section),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(HelloCardPalette.Discover.Start.copy(alpha = 0.95f), HelloCardPalette.Discover.End.copy(alpha = 0.90f))
                    )
                )
                .padding(metrics.cozy)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,   // 整体垂直居中
            ) {
                // ① 主题标签
                Text(
                    text = stringResource(Res.string.card_discover_title, c.target),
                    style = typo.subtitle,
                    color = Color.White,
                )

                // ② 推荐理由
                Text(
                    text = c.reason,
                    style = typo.footnote,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = metrics.hair, bottom = metrics.compact),
                )

                // ③ 曲目预览列表（最多 3 首，紧凑布局）
                c.trackIds.take(3).forEachIndexed { idx, _ ->
                    val uri = albumUris.value.getOrNull(idx)
                    val title = (c.trackTitles.getOrNull(idx).orEmpty().ifBlank { liveTitles.value.getOrNull(idx).orEmpty() }).ifBlank { stringResource(Res.string.card_unknown_track) }
                    val artist = (c.trackArtists.getOrNull(idx).orEmpty().ifBlank { liveArtists.value.getOrNull(idx).orEmpty() }).ifBlank { stringResource(Res.string.card_unknown_artist) }
                    val dur = c.trackDurations.getOrNull(idx)?.coerceAtLeast(0) ?: 0

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = metrics.hair),
                        horizontalArrangement = Arrangement.spacedBy(metrics.compact),
                    ) {
                        // 封面（48×48dp，圆角 10dp）
                        Box(
                            modifier = Modifier
                                .size(metrics.bar)
                                .clip(RoundedCornerShape(metrics.compact))
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
                                text = title.ifBlank { stringResource(Res.string.card_unknown_track) },
                                style = typo.body.medium(),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = artist.ifBlank { stringResource(Res.string.card_unknown_artist) },
                                style = typo.caption,
                                color = Color.White.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // 时长
                        Text(
                            text = if (dur > 0) formatDurationSec(dur) else "",
                            style = typo.caption,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }

                // ④ 底部总量
                Text(
                    text = stringResource(Res.string.card_discover_footnote, c.trackIds.size),
                    style = typo.footnote,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = metrics.compact),
                )
            }
        }
    }
}
