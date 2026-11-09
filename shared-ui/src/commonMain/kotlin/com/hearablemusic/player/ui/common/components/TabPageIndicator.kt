package com.hearablemusic.player.ui.common.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

@Composable
fun TabPageIndicator(
    modifier: Modifier = Modifier,
    currentPage: Int,
    totalPages: Int = 4,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
) {
    val metrics = LocalHmpMetrics.current
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(top = metrics.cozy),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalPages) { index ->
            val isSelected = index == currentPage
            val barColor by animateColorAsState(
                targetValue = if (isSelected) activeColor else inactiveColor,
                animationSpec = HmpMotion.micro(),
            )
            val barWidth = metrics.block
            val barHeight = metrics.hair

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(barHeight)
                    .padding(horizontal = metrics.micro)
                    .background(
                        color = barColor,
                        shape = RoundedCornerShape(metrics.hair),
                    ),
            )
        }
    }
}
