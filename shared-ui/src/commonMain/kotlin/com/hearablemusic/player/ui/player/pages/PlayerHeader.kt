package com.hearablemusic.player.ui.player.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.back
import com.hearablemusic.player.ui.generated.resources.chevron_down
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerHeader(onBackClick: () -> Unit) {
    val metrics = LocalHmpMetrics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(bottom = metrics.cozy),
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(metrics.block),
        ) {
            Icon(
                painter = painterResource(Res.drawable.chevron_down),
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = stringResource(Res.string.back),
                modifier = Modifier.size(metrics.section),
            )
        }
    }
}
