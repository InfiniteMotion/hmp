package com.hearablemusic.player.ui.common.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo

@Composable
fun TitleWidget(
    title: String,
    content: @Composable () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    HMPCard {
        // Header Section with enhanced visual hierarchy
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Enhanced accent indicator with gradient effect
            Box(
                modifier = Modifier
                    .width(metrics.micro)
                    .height(metrics.section)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            ),
                        ),
                        shape = RoundedCornerShape(metrics.hair),
                    ),
            )

            Spacer(modifier = Modifier.width(metrics.cozy))

            Text(
                text = title,
                style = typo.headline,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier,
            )
        }

        // Content Section with subtle top padding
        Box(
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = metrics.cozy),
        ) {
            content()
        }
    }
}
