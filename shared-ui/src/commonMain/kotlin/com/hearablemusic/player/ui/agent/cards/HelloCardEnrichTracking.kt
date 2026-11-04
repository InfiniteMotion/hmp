package com.hearablemusic.player.ui.agent.cards
import com.hearablemusic.player.ui.generated.resources.card_enrich_mixed_group
import com.hearablemusic.player.ui.generated.resources.card_enrich_paused
import com.hearablemusic.player.ui.generated.resources.card_enrich_progress
import com.hearablemusic.player.ui.generated.resources.card_enrich_running
import com.hearablemusic.player.ui.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmp.domain.agent.card.EnrichTrackingContent
import com.hmp.domain.agent.card.SlideCard
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics



// ═══════════════════════════════════════════════════════════════════
// 家族 E：ENRICH_TRACKING —— 富化进度追踪卡
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun FamilyEnrichTrackingCard(
    card: SlideCard,
    modifier: Modifier = Modifier,
    onCardClick: ((SlideCard) -> Unit)? = null,
    onLongClick: ((SlideCard) -> Unit)? = null,
) {
    val metrics = LocalHmpMetrics.current
    val c = card.content as EnrichTrackingContent
    val pct = if (c.currentUnitSize > 0) (c.processed * 100 / c.currentUnitSize) else 0
    val isRunning = c.active
    val artistLabel = c.currentArtist?.let { if (it == "MIXED_GROUP") stringResource(Res.string.card_enrich_mixed_group) else it }
    val chunkLabel = if (c.chunkTotal > 0) " · chunk ${c.chunkIndex}/${c.chunkTotal}" else ""
    val typo = LocalHmpTypo.current

    // 点击手势统一处理（RadioStatus 同款）
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
                        colors = if (isRunning) {
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            )
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.04f),
                            )
                        }
                    )
                )
                .padding(horizontal = metrics.cozy, vertical = metrics.cozy),
        ) {
            // 本卡是"信息块 + 进度块"两段式。用 SpaceBetween 在大卡里同样会把两块推到
            // 上下两端、中间空出一条 → 改为 Center 分组，两段之间只留固定间隔。
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.compact)) {
                    // 标题
                    Text(
                        text = if (isRunning) stringResource(Res.string.card_enrich_running) else stringResource(Res.string.card_enrich_paused),
                        style = typo.subtitle,
                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    // 歌手 + chunk
                    if (artistLabel != null) {
                        Text(
                            text = "$artistLabel$chunkLabel",
                            style = typo.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 当前阶段
                    Text(
                        text = c.phase,
                        style = typo.body,
                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(Modifier.height(metrics.section))
                Column {
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth().height(metrics.compact),
                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(metrics.compact))
                    Text(
                        text = stringResource(Res.string.card_enrich_progress, c.processed, c.success, c.failed, pct),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
