package com.hearablemusic.player.ui.common.dialogs

import com.hearablemusic.player.ui.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.confirm
import com.hearablemusic.player.ui.generated.resources.no_songs_available
import com.hearablemusic.player.ui.generated.resources.selected_n_songs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmp.domain.music.MusicInfo
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MusicPickerDialog(
    allMusic: List<MusicInfo>,
    selectedIds: Set<Long>,
    title: String,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
    hazeRenderSettings: HazeRenderSettings = HazeRenderSettings()
) {
    val metrics = LocalHmpMetrics.current
    var currentSelectedIds by remember(selectedIds) { mutableStateOf(selectedIds) }
    val typo = LocalHmpTypo.current

    ProvideHazeRenderSettings(settings = hazeRenderSettings) {
        ScrimDialog(onDismissRequest = onDismiss) {
            val dialogShape = RoundedCornerShape(metrics.section)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.section)
                    .clip(dialogShape)
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeEffect(
                                state = hazeState,
                                style = hazeStyleForIntensity()
                            )
                        } else {
                            Modifier
                        }
                    ),
                shape = dialogShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (hazeState != null) {
                        MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(metrics.cozy)
                ) {
                    Text(
                        text = title,
                        style = typo.subtitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = metrics.cozy)
                    )
                    Text(
                        text = stringResource(Res.string.selected_n_songs, currentSelectedIds.size),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = metrics.base)
                    )

                    if (allMusic.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(metrics.card),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.no_songs_available),
                                style = typo.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = LayoutConstraints.DialogMaxHeightLarge),
                            verticalArrangement = Arrangement.spacedBy(metrics.hair)
                        ) {
                            items(allMusic, key = { it.music.id }) { musicInfo ->
                                val songId = musicInfo.music.id
                                val checked = currentSelectedIds.contains(songId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentSelectedIds = if (checked) {
                                                currentSelectedIds - songId
                                            } else {
                                                currentSelectedIds + songId
                                            }
                                        }
                                        .padding(vertical = metrics.micro, horizontal = metrics.micro),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            currentSelectedIds = if (isChecked) {
                                                currentSelectedIds + songId
                                            } else {
                                                currentSelectedIds - songId
                                            }
                                        }
                                    )
                                    Column(
                                        modifier = Modifier
                                            .padding(start = metrics.compact)
                                            .weight(1f)
                                    ) {
                                        Text(
                                            text = musicInfo.music.title,
                                            style = typo.body,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = musicInfo.music.artist,
                                            style = typo.footnote,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = metrics.cozy),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(Res.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(metrics.compact))
                        TextButton(onClick = { onConfirm(currentSelectedIds) }) {
                            Text(stringResource(Res.string.confirm))
                        }
                    }
                }
            }
        }
}
}
