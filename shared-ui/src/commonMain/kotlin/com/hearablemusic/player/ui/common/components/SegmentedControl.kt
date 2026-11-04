package com.hearablemusic.player.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.components.base.HMPCard
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

// ==================== 增强版 Segmented Control 组件 ====================

/**
 * 增强版 Segmented Control 组件
 * 支持图标显示，Material Design 3 风格
 */
@Composable
fun SegmentedControl(
    modifier: Modifier,
    options: List<SegmentedOption>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    showIcons: Boolean = false
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    HMPCard(
        modifier = modifier.height(metrics.bar),
        shape = RoundedCornerShape(metrics.base),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        contentPadding = Modifier.padding(metrics.micro)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.hair)
        ) {
            options.forEach { option ->
                val isSelected = option.id == selectedOption
                Surface(
                    modifier = Modifier
                        .height(metrics.bar)
                        .weight(1f),
                    shape = RoundedCornerShape(metrics.base),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        Transparent
                    },
                    onClick = { onOptionSelected(option.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = metrics.compact),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showIcons && option.icon != null) {
                            Icon(
                                painter = option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(metrics.cozy),
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(Modifier.width(metrics.micro))
                        }
                        Text(
                            text = option.label,
                            style = typo.body.medium(),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ==================== 竖排样式 Segmented Control 组件 ====================

/**
 * 竖排样式的 Segmented Control 组件
 * 支持垂直排列选项，Material Design 3 风格
 */
@Composable
fun VerticalSegmentedControl(
    modifier: Modifier,
    options: List<SegmentedOption>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    itemHeight: Int = 44 // 每个选项的高度
) {
    val metrics = LocalHmpMetrics.current
    HMPCard(
        modifier = modifier,
        shape = RoundedCornerShape(metrics.base),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        contentPadding = Modifier.padding(metrics.micro)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(metrics.hair)
        ) {
            options.forEach { option ->
                val isSelected = option.id == selectedOption
                Surface(
                    modifier = Modifier
                        .height(itemHeight.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(metrics.base),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        Transparent
                    },
                    onClick = { onOptionSelected(option.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = metrics.base),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        option.icon?.let {
                            Icon(
                                painter = it,
                                contentDescription = null,
                                modifier = Modifier.size(metrics.cozy),
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class SegmentedOption(
    val id: String,
    val label: String,
    val icon: Painter? = null
)
