package com.hearablemusic.player.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens

/**
 * 听歌密度热力图（D5-08 重做）。
 *
 * 输入是 [HeatmapGrid] —— 日期到格子的对齐、缺日补零都由 `buildListeningHeatmap` 那个纯函数负责，这里只画。
 * 旧版把两件事都做在组件里且都做错了：格子来自「有听歌的最后 35 行」（没听歌的日子整段被压缩掉），
 * 表头硬编码 `M T W T F S S` 而填充是行主序 —— 列与那一天真正的星期毫无关系。
 *
 * 现在的布局：**7 行（周一至周日）× N 列（周）**，左侧一列星期标签，整块横向滚动。
 * 标签与格子对齐不再是约定，而是布局本身保证的（行序 = ISO 星期）。全年视图就是 53 列；
 * 竖着排 53 行会把这张卡撑成一屏多，横向滚动是这类密度图的既有读法。
 */
@Composable
fun ListeningChart(
    grid: HeatmapGrid,
) {
    val dimens = LocalHMPDimens.current
    val weeks = grid.rows
    if (weeks.isEmpty()) return

    val weekCount = weeks.maxOf { it.size }.coerceAtLeast(1)
    val maxValue = weeks.flatten().filter { it.date != null }.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    val scrollState = rememberScrollState()
    val cell = CELL_DP.dp
    val gap = dimens.spacing.xs

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        // 左侧星期标签（行序 = ISO 1..7 = 周一至周日）
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            WEEKDAY_LABELS.forEach { label ->
                Box(modifier = Modifier.size(width = cell, height = cell), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = dimens.type.xs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.horizontalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            for (weekdayIndex in 0 until 7) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (weekIndex in 0 until weekCount) {
                        val item = weeks.getOrNull(weekIndex)?.getOrNull(weekdayIndex)
                        Box(
                            modifier = Modifier
                                .size(cell)
                                .clip(RoundedCornerShape(dimens.corner.xs))
                                .background(
                                    when {
                                        // 窗口外的补齐格不上色：它不是"那天没听歌"
                                        item?.date == null -> Color.Transparent
                                        item.minutes == 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.primary.copy(
                                            alpha = (item.minutes.toFloat() / maxValue).coerceIn(0.2f, 1f)
                                        )
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 行序对应 ISO 星期（1=周一 … 7=周日）。
 * 星期缩写的本地化属 D8-06 的 i18n 面；本条只负责"标签与格子真的是同一个星期"。
 */
private val WEEKDAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

private const val CELL_DP = 12
