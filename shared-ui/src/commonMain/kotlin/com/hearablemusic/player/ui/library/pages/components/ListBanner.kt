package com.hearablemusic.player.ui.library.pages.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

/**
 * 列表页分组标题。
 */
@Composable
fun ListGroupName(
    bannerNameF: String,
    bannerNameS: String,
    themeColor: Color,
    trailing: @Composable (() -> Unit)? = null
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = metrics.cozy)
    ) {
        // 标题部分
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = metrics.cozy, end = metrics.cozy, top = metrics.cozy, bottom = metrics.compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bannerNameF,
                    style = typo.headline,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(metrics.compact))
                Box(
                    modifier = Modifier
                        .size(metrics.compact)
                        .clip(CircleShape)
                        .background(themeColor)
                )
                Spacer(modifier = Modifier.width(metrics.compact))
                Text(
                    text = bannerNameS,
                    style = typo.headline,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (trailing != null) trailing()
        }
    }
}
