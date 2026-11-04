package com.hearablemusic.player.ui.common.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.ok
import com.hearablemusic.player.ui.generated.resources.scan_complete_desc
import com.hearablemusic.player.ui.generated.resources.scan_music_title
import com.hearablemusic.player.ui.generated.resources.scanning_desc
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.LocalHazeRenderSettings
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.library.viewmodel.LibraryViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MusicScanDialog(
    libraryViewModel: LibraryViewModel,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
    hazeRenderSettings: HazeRenderSettings? = null
) {
    val metrics = LocalHmpMetrics.current
    val isLoading by libraryViewModel.isScanning.collectAsState(initial = false)
    val musicCount by libraryViewModel.musicCount.collectAsState(initial = 0)
    val resolvedHazeRenderSettings = hazeRenderSettings ?: LocalHazeRenderSettings.current
    val typo = LocalHmpTypo.current

    ProvideHazeRenderSettings(settings = resolvedHazeRenderSettings) {
        ScrimDialog(
            onDismissRequest = onDismiss,
            enableScrimDismiss = !isLoading,
        ) {
            val dialogShape = RoundedCornerShape(metrics.section)
            Card(
                modifier = Modifier
                    .padding(metrics.section)
                    .clip(dialogShape)
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeEffect(
                                state = hazeState,
                                style = hazeStyleForIntensity()
                            )
                        } else Modifier
                    ),
                shape = dialogShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (hazeState != null) {
                        MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(metrics.section)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(Res.string.scan_music_title),
                        style = typo.subtitle.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(bottom = metrics.cozy)
                    )

                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(Res.string.scanning_desc),
                                style = typo.body
                            )
                            Spacer(modifier = Modifier.height(metrics.section))
                            CircularProgressIndicator(
                                modifier = Modifier.size(metrics.bar),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(metrics.section))
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(Res.string.scan_complete_desc, musicCount),
                                style = typo.body
                            )
                            Spacer(modifier = Modifier.height(metrics.section))
                        }
                    }

                    if (!isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(metrics.base)
                            ) {
                                Text(stringResource(Res.string.ok))
                            }
                        }
                    }
                }
            }
        }
}
}
