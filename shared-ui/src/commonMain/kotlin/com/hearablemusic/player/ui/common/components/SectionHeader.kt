package com.hearablemusic.player.ui.common.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

@Composable
fun SectionHeader(title: String) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = metrics.micro),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(metrics.micro)
                .height(metrics.cozy)
                .clip(RoundedCornerShape(metrics.hair))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(metrics.cozy))
        Text(
            text = title,
            style = typo.subtitle,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
