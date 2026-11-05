package com.hearablemusic.player.ui.common.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * 标签图标展示（同步 painterResource 渲染，替代旧异步 SharedIconLoader + 手动解码链路）。
 */
@Composable
fun SharedLabelIcon(
    iconRes: DrawableResource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
