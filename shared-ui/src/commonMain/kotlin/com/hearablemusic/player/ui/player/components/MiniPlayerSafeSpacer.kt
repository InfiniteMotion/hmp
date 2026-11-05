package com.hearablemusic.player.ui.player.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.metrics.HMPMetricTokens

/**
 * Reserve bottom safe space for MiniPlayerBar overlap.
 */
@Composable
fun MiniPlayerSafeSpacer(
    height: Dp = HMPMetricTokens.Compact.row,
) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    )
}
