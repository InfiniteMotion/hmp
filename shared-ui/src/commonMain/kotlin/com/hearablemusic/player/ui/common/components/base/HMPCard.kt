package com.hearablemusic.player.ui.common.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens

/**
 * 应用标准卡片容器。
 *
 * 默认值：surfaceContainer 15% 底色 + outlineVariant 50% 细描边 + dimens.corner.md 圆角。
 * 允许传 shape / borderColor / containerColor 覆盖默认。
 *
 * clip(shape) 在最外层 —— 确保所有子内容（包括调用方 modifier 里 clickable 产生的
 * 按压指示 / ripple）都被圆角正确裁切。调用方无论传 clickable / padding / size 都安全。
 */
@Composable
fun HMPCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalHMPDimens.current.corner.md),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.15f),
    contentPadding: Modifier = Modifier.padding(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(shape)
            .then(modifier)
            .border(1.dp, borderColor, shape)
            .background(containerColor, shape)
    ) {
        Column(
            modifier = Modifier
                .then(contentPadding),
            content = content,
        )
    }
}
