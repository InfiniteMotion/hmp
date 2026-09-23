package com.hearablemusic.player.ui.settings.pages
import com.hearablemusic.player.ui.common.text.asString
import com.hearablemusic.player.ui.generated.resources.usage_agent_awareness
import com.hearablemusic.player.ui.generated.resources.usage_agent_awareness_empty
import com.hearablemusic.player.ui.generated.resources.usage_genre
import com.hearablemusic.player.ui.generated.resources.usage_listened_min
import com.hearablemusic.player.ui.generated.resources.usage_mood
import com.hearablemusic.player.ui.generated.resources.usage_peak_night
import com.hearablemusic.player.ui.generated.resources.usage_peak_normal
import com.hearablemusic.player.ui.generated.resources.usage_period
import com.hearablemusic.player.ui.generated.resources.usage_play_skip
import com.hearablemusic.player.ui.generated.resources.usage_rank
import com.hearablemusic.player.ui.generated.resources.usage_recent
import com.hearablemusic.player.ui.generated.resources.usage_scenario
import com.hearablemusic.player.ui.generated.resources.usage_taste
import com.hearablemusic.player.ui.generated.resources.usage_when

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.SegmentedControl
import com.hearablemusic.player.ui.common.components.SegmentedOption
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens
import com.hearablemusic.player.ui.common.components.SectionHeader
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.util.HapticFeedbackHelper
import com.hearablemusic.player.ui.common.util.commonFormat
import com.hearablemusic.player.ui.common.util.formatEpochMillis
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.completed
import com.hearablemusic.player.ui.generated.resources.completion_rate
import com.hearablemusic.player.ui.generated.resources.incomplete
import com.hearablemusic.player.ui.generated.resources.loading
import com.hearablemusic.player.ui.generated.resources.skip_rate
import com.hearablemusic.player.ui.generated.resources.title_user_usage_data
import com.hearablemusic.player.ui.generated.resources.usage_data_empty
import com.hearablemusic.player.ui.player.components.MiniPlayerSafeSpacer
import com.hearablemusic.player.ui.settings.viewmodel.Dimension
import com.hearablemusic.player.ui.settings.viewmodel.PersonalityBundle
import com.hearablemusic.player.ui.settings.viewmodel.UserUsageDataViewModel
import com.hearablemusic.player.ui.settings.viewmodel.WindowedBundle
import com.hmp.domain.agent.card.NarrativeTimeRange
import com.hmp.domain.agent.card.zhName
import com.hmp.domain.music.HourlyDistributionRow
import com.hmp.domain.setting.model.LabelCountEntry
import com.hmp.domain.setting.model.RecentPlaybackEntry
import com.hmp.domain.setting.model.TopPlayedEntry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UserUsageDataScreen(
    navController: NavBackStack<NavKey>,
    viewModel: UserUsageDataViewModel = koinViewModel()
) {
    val personality by viewModel.personality.collectAsState()
    val dimension by viewModel.dimension.collectAsState()
    val timeRange by viewModel.timeRange.collectAsState()
    val windowed by viewModel.windowed.collectAsState()

    val scrollState = rememberScrollState()
    val dimens = LocalHMPDimens.current
    val haptic = rememberHapticFeedback()

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = stringResource(Res.string.title_user_usage_data),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .padding(dimens.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ═══════════ 固定置顶：累计画像（永不随筛选变）═══════════
            personality?.let { bundle ->
                PersonalitySection(bundle = bundle)
                Spacer(modifier = Modifier.height(dimens.spacing.lg))
            }
            // ═══════════ 筛选轴 ═══════════
            DimensionSelector(
                selected = dimension,
                onSelect = { viewModel.selectDimension(it) }
            )
            Spacer(modifier = Modifier.height(dimens.spacing.sm))
            TimeRangeSwitcher(
                selected = timeRange,
                onSelect = { viewModel.selectTimeRange(it) }
            )
            Spacer(modifier = Modifier.height(dimens.spacing.lg))

            // ═══════════ 内容区（WindowedBundle 按维度分发）═══════════
            when (dimension) {
                Dimension.OVERVIEW -> OverviewWindowContent(
                    windowed = windowed,
                    rangeLabel = timeRange.zhName(),
                )
                Dimension.TASTE -> TasteWindowContent(
                    windowed = windowed,
                    rangeLabel = timeRange.zhName(),
                )
                Dimension.HOUR -> {
                    if (windowed.hourlyDistribution.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionHeader(title = "时段分布（${timeRange.zhName()}）")
                            Spacer(modifier = Modifier.height(dimens.spacing.sm))
                            HourlyChart(rows = windowed.hourlyDistribution)
                        }
                    }
                }
                Dimension.RANK -> RankingWindowContent(
                    items = windowed.topSongs,
                    rangeLabel = timeRange.zhName(),
                    navController = navController,
                    haptic = haptic,
                )
                Dimension.RECENT -> RecentWindowContent(
                    items = windowed.recentPlayback,
                    rangeLabel = timeRange.zhName(),
                    navController = navController,
                    haptic = haptic,
                )
            }
            MiniPlayerSafeSpacer()
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// 新增组件：DimensionSelector / OverviewWindowContent / TasteWindowContent
//          / RankingWindowContent / RecentWindowContent / TopSongRow / EmptyHint
// ═════════════════════════════════════════════════════════════════════════

/** 维度 Tab 切换器（SegmentedControl 风格，5 档：概览/口味/时段/排行/最近）。 */
@Composable
private fun DimensionSelector(
    selected: Dimension,
    onSelect: (Dimension) -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val options = Dimension.entries.map { dim ->
        SegmentedOption(dim.name, dim.label.asString())
    }
    SegmentedControl(
        modifier = Modifier.fillMaxWidth(),
        options = options,
        selectedOption = selected.name,
        onOptionSelected = { raw ->
            val dim = Dimension.entries.firstOrNull { it.name == raw }
            if (dim != null) {
                onSelect(dim)
                haptic.performClick()
            }
        }
    )
}

/** 概览窗口：大数字 + 完播/跳过率进度条 + 播放来源饼图（全真窗口数据）。 */
@Composable
private fun OverviewWindowContent(
    windowed: WindowedBundle,
    rangeLabel: String,
) {
    val dimens = LocalHMPDimens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)
    ) {
        // 统计大数字 + 完播/跳过率
        windowed.analytics?.let { a ->
            HMPCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(dimens.corner.md),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                contentPadding = Modifier.padding(dimens.spacing.lg)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
                ) {
                    Text(
                        text = stringResource(Res.string.usage_period),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (rangeLabel.isNotBlank()) {
                        Text(
                            text = rangeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(dimens.corner.sm))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(dimens.spacing.sm))
                Text(
                    text = stringResource(Res.string.usage_listened_min, a.totalListeningMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(dimens.spacing.xs))
                Text(
                    text = stringResource(Res.string.usage_play_skip, a.totalPlayCount, a.totalSkipCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(dimens.spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)) {
                    RateProgressRow(
                        label = stringResource(Res.string.completion_rate),
                        rate = a.completionRate,
                        valueLabel = commonFormat("%.0f%%", a.completionRate * 100),
                        isPositive = true
                    )
                    RateProgressRow(
                        label = stringResource(Res.string.skip_rate),
                        rate = a.skipRate,
                        valueLabel = commonFormat("%.0f%%", a.skipRate * 100),
                        isPositive = false
                    )
                }
                // 播放来源饼图
                if (windowed.sourceBreakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PlaySourcePieChart(entries = windowed.sourceBreakdown.entries.toList())
                }
            }
        }

        // analytics 为 null —— 空态
        if (windowed.analytics == null) {
            EmptyHint(text = stringResource(Res.string.usage_data_empty))
        }
    }
}

/** 口味窗口：三段 LabelStackedBarWithLegend（流派 / 情绪 / 场景）。 */
@Composable
private fun TasteWindowContent(
    windowed: WindowedBundle,
    rangeLabel: String,
) {
    val dimens = LocalHMPDimens.current
    val hasAny = windowed.topGenres.isNotEmpty() ||
            windowed.topMoods.isNotEmpty() ||
            windowed.topScenarios.isNotEmpty()
    if (!hasAny) {
        EmptyHint(text = stringResource(Res.string.usage_data_empty))
        return
    }
    HMPCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.corner.md),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        contentPadding = Modifier.padding(dimens.spacing.lg)
    ) {
        Text(
            text = stringResource(Res.string.usage_taste),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = rangeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(dimens.corner.sm))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Spacer(modifier = Modifier.height(dimens.spacing.sm))

        if (windowed.topGenres.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.usage_genre),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(dimens.spacing.xs))
            LabelStackedBarWithLegend(entries = windowed.topGenres)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (windowed.topMoods.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.usage_mood),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(dimens.spacing.xs))
            LabelStackedBarWithLegend(entries = windowed.topMoods)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (windowed.topScenarios.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.usage_scenario),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(dimens.spacing.xs))
            LabelStackedBarWithLegend(entries = windowed.topScenarios)
        }
    }
}

/** 排行窗口：Top 歌曲列表 + 空态提示。 */
@Composable
private fun RankingWindowContent(
    items: List<TopPlayedEntry>,
    rangeLabel: String,
    navController: NavBackStack<NavKey>,
    haptic: HapticFeedbackHelper,
) {
    val dimens = LocalHMPDimens.current
    SectionHeader(title = stringResource(Res.string.usage_rank, rangeLabel))
    Spacer(modifier = Modifier.height(dimens.spacing.sm))
    if (items.isEmpty()) {
        EmptyHint(text = stringResource(Res.string.usage_data_empty))
    } else {
        HMPCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(dimens.corner.md),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
            contentPadding = Modifier.padding(dimens.spacing.lg)
        ) {
            items.forEachIndexed { index, entry ->
                TopSongRow(
                    rank = index + 1,
                    title = entry.title,
                    artist = entry.artist,
                    playCount = entry.playCount,
                    onClick = {
                        haptic.performClick()
                        navController.add(Routes.Library.SongDetail(entry.musicId))
                    }
                )
            }
        }
    }
}

/** 最近播放窗口：RecentPlaybackItem 列表 + 空态提示。 */
@Composable
private fun RecentWindowContent(
    items: List<RecentPlaybackEntry>,
    rangeLabel: String,
    navController: NavBackStack<NavKey>,
    haptic: HapticFeedbackHelper,
) {
    val dimens = LocalHMPDimens.current
    SectionHeader(title = stringResource(Res.string.usage_recent, rangeLabel))
    Spacer(modifier = Modifier.height(dimens.spacing.sm))
    if (items.isEmpty()) {
        EmptyHint(text = stringResource(Res.string.usage_data_empty))
    } else {
        items.forEach { entry ->
            RecentPlaybackItem(
                title = entry.title,
                artist = entry.artist,
                playedAt = entry.playedAt,
                playDuration = entry.playDuration,
                isCompleted = entry.isCompleted,
                onClick = {
                    haptic.performClick()
                    navController.add(Routes.Library.SongDetail(entry.musicId))
                }
            )
        }
    }
}

/** Top 歌曲排行行（序号 + 歌名 + 歌手 + 播放次数）。 */
@Composable
private fun TopSongRow(
    rank: Int,
    title: String,
    artist: String,
    playCount: Int,
    onClick: () -> Unit,
) {
    val dimens = LocalHMPDimens.current
    HMPCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(dimens.corner.sm),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = if (rank in 1..3) 0.35f else 0.25f
        ),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        contentPadding = Modifier.padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(dimens.icon.lg)
                    .clip(CircleShape)
                    .background(
                        if (rank <= 3) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (rank <= 3)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (rank <= 3) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "$playCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    Spacer(modifier = Modifier.height(dimens.spacing.xs))
}

/** 空态提示（统一样式）。 */
@Composable
private fun EmptyHint(text: String) {
    val dimens = LocalHMPDimens.current
    HMPCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.corner.md),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        contentPadding = Modifier.padding(vertical = dimens.spacing.xl)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun RateProgressRow(
    label: String,
    rate: Float,
    valueLabel: String,
    isPositive: Boolean
) {
    val dimens = LocalHMPDimens.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { rate.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (isPositive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/** 播放来源：饼状图 + 图例 */
@Composable
private fun PlaySourcePieChart(entries: List<Map.Entry<String, Int>>) {
    val dimens = LocalHMPDimens.current
    val total = entries.sumOf { it.value }.coerceAtLeast(1)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .size(dimens.component.md)
                .padding(dimens.spacing.sm)
        ) {
            val side = size.minDimension
            val left = (size.width - side) / 2
            val top = (size.height - side) / 2
            var startAngle = -90f // 从 12 点方向开始，顺时针
            entries.forEachIndexed { index, (_, count) ->
                val sweepAngle = (count.toFloat() / total * 360f).coerceIn(0f, 360f)
                if (sweepAngle > 0f) {
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        topLeft = Offset(left, top),
                        size = Size(side, side)
                    )
                    startAngle += sweepAngle
                }
            }
        }
        Spacer(modifier = Modifier.height(dimens.spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.forEachIndexed { index, (source, count) ->
                val pct = count.toFloat() / total
                val color = colors[index % colors.size]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                    Text(
                        text = source.ifEmpty { "—" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(pct * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 听歌口味每组：横向堆积条 + 纵向图例（与 OverviewCard 下半区原样式一致） */
@Composable
private fun LabelStackedBarWithLegend(
    entries: List<LabelCountEntry>
) {
    val dimens = LocalHMPDimens.current
    val total = entries.sumOf { it.count }.coerceAtLeast(1)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(dimens.corner.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        if (total > 0) {
            entries.forEachIndexed { index, entry ->
                val pct = entry.count.toFloat() / total
                val color = colors[index % colors.size]
                Box(
                    modifier = Modifier
                        .weight(pct.coerceIn(0.001f, 1f))
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(dimens.spacing.sm))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEachIndexed { index, entry ->
            val pct = entry.count.toFloat() / total
            val color = colors[index % colors.size]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
                Text(
                    text = entry.labelDisplayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(pct * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return commonFormat("%02d:%02d", minutes, seconds)
}

private fun formatTimestamp(timestamp: Long): String {
    return formatEpochMillis(timestamp, "yyyy-MM-dd HH:mm")
}

@Composable
private fun RecentPlaybackItem(
    title: String,
    artist: String,
    playedAt: Long,
    playDuration: Long,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    val dimens = LocalHMPDimens.current
    HMPCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(dimens.corner.sm),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        contentPadding = Modifier.padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                    )
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md)
                ) {
                    Text(
                        text = formatDuration(playDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTimestamp(playedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.corner.sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(if (isCompleted) Res.string.completed else Res.string.incomplete),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(dimens.spacing.xs))
}

@Composable
private fun UsageDataLoading() {
    val dimens = LocalHMPDimens.current
    HMPCard(
        modifier = Modifier.fillMaxWidth().height(dimens.component.lg),
        shape = RoundedCornerShape(dimens.corner.lg),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        contentPadding = Modifier.padding(24.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
// ═════════════════════════════════════════════════════════════════════════
// F9-T1 新增组件：人格区 / 时间切换器 / 报告叙事段 / 时段柱图
// ═════════════════════════════════════════════════════════════════════════

/**
 * 顶部累计画像区 — Agent 对用户的真实认知摘要。
 * 风格：无 Card 容器，直接渐变背景 + 大留白，让它从下方筛选器中跳出来。
 */
@Composable
private fun PersonalitySection(bundle: PersonalityBundle) {
    val dimens = LocalHMPDimens.current
    val card = bundle.card
    val narrativeState = bundle.narrative
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.corner.lg))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.18f),
                        surface.copy(alpha = 0.35f),
                    ),
                )
            )
            .padding(
                horizontal = dimens.spacing.xl,
                vertical = dimens.spacing.lg,
            )
    ) {
        Column {
            // 左上角小圆点装饰 + 标题
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
                Text(
                    text = stringResource(Res.string.usage_agent_awareness),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )
            }

            Spacer(modifier = Modifier.height(dimens.spacing.md))

            // 叙事正文：LLM 生成的 narrativeState 优先，没有就展示 observation 小条目
            val hasLlmNarrative = !narrativeState?.text.isNullOrBlank()
            if (hasLlmNarrative) {
                Text(
                    text = narrativeState.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6,
                )
            } else {
                // 无 LLM：用 sentences 模板句做 observation 列表展示
                val observations = card?.sentences?.take(3) ?: emptyList()
                if (observations.isNotEmpty()) {
                    observations.forEach { sentence ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodyMedium,
                                color = primary.copy(alpha = 0.6f),
                            )
                            Spacer(modifier = Modifier.width(dimens.spacing.sm))
                            Text(
                                text = sentence,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5,
                            )
                        }
                        Spacer(modifier = Modifier.height(dimens.spacing.sm))
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.usage_agent_awareness_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/** 时间维度切换器（SegmentedControl 风格，5 档）。 */
@Composable
private fun TimeRangeSwitcher(
    selected: NarrativeTimeRange,
    onSelect: (NarrativeTimeRange) -> Unit
) {
    val haptic = rememberHapticFeedback()
    val options = listOf(
        SegmentedOption(NarrativeTimeRange.DAY.name, NarrativeTimeRange.DAY.zhName()),
        SegmentedOption(NarrativeTimeRange.WEEK.name, NarrativeTimeRange.WEEK.zhName()),
        SegmentedOption(NarrativeTimeRange.MONTH.name, NarrativeTimeRange.MONTH.zhName()),
        SegmentedOption(NarrativeTimeRange.YEAR.name, NarrativeTimeRange.YEAR.zhName()),
        SegmentedOption(NarrativeTimeRange.ALL.name, NarrativeTimeRange.ALL.zhName()),
    )
    SegmentedControl(
        modifier = Modifier.fillMaxWidth(),
        options = options,
        selectedOption = selected.name,
        onOptionSelected = { raw ->
            val range = NarrativeTimeRange.entries.firstOrNull { it.name == raw }
            if (range != null) {
                onSelect(range)
                haptic.performClick()
            }
        }
    )
}

/** 报告叙事段（时间敏感）—— HelloReportNarrativeEntity 的 narrative 文本。 */
@Composable
private fun ReportNarrativeCard(text: String, rangeLabel: String) {
    val dimens = LocalHMPDimens.current
    HMPCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.corner.md),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
        contentPadding = Modifier.padding(dimens.spacing.lg)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Text(
                text = rangeLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(modifier = Modifier.height(dimens.spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.6
        )
    }
}

/** 时段分布柱图——24 小时 × 播放次数 + 伙伴解读。 */
@Composable
private fun HourlyChart(rows: List<HourlyDistributionRow>) {
    val dimens = LocalHMPDimens.current
    val maxCount = rows.maxOfOrNull { it.playCount }?.takeIf { it > 0 } ?: 1
    val byHour = rows.associate { it.hour to it.playCount }

    // 伙伴解读：找峰值时段
    val peakHour = rows.maxByOrNull { it.playCount }?.hour
    val isNightPeak = peakHour != null && (peakHour in 22..23 || peakHour in 0..5)
    val desc = when {
        peakHour == null -> null
        isNightPeak -> stringResource(Res.string.usage_peak_night, formatHourRange(peakHour))
        else -> stringResource(Res.string.usage_peak_normal, formatHourRange(peakHour))
    }

    HMPCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.corner.md),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        contentPadding = Modifier.padding(dimens.spacing.lg)
    ) {
        Text(
            text = stringResource(Res.string.usage_when),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        desc?.let {
            Spacer(modifier = Modifier.height(dimens.spacing.xs))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(dimens.spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            (0..23).forEach { hour ->
                val count = byHour[hour] ?: 0
                val heightRatio = count.toFloat() / maxCount
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (count > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightRatio.coerceIn(0.05f, 1f))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // 时间标签（只标 0 / 6 / 12 / 18）
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(0, 6, 12, 18, 23).forEachIndexed { idx, hour ->
                Text(
                    text = "$hour",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 把小时转成显示用的时段范围描述：22→「22-23」 / 0→「0-1」 / 12→「12-13」 */
private fun formatHourRange(hour: Int): String {
    val next = (hour + 1) % 24
    return if (next == 0) "-24" else "-"
}
