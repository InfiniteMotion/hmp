package com.hearablemusic.player.ui.common.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.generated.resources.*
import com.hearablemusic.player.ui.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
fun DefaultLoading(
    modifier: Modifier = Modifier,
    message: String = stringResource(Res.string.loading),
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(metrics.bar),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(metrics.cozy))
        Text(
            text = message,
            style = typo.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
