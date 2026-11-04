package com.hearablemusic.player.ui.common.dialogs

import com.hearablemusic.player.ui.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import com.hearablemusic.player.ui.generated.resources.chevron_right
import com.hearablemusic.player.ui.generated.resources.minutes_format
import com.hearablemusic.player.ui.generated.resources.music_note_list
import com.hearablemusic.player.ui.generated.resources.play_count_display
import com.hearablemusic.player.ui.generated.resources.songs_count

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hmp.domain.playlist.Playlist
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.LocalHazeRenderSettings
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
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    title: String,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    hazeState: HazeState? = null,
    hazeRenderSettings: HazeRenderSettings? = null
) {
    val metrics = LocalHmpMetrics.current
    val resolvedHazeRenderSettings = hazeRenderSettings ?: LocalHazeRenderSettings.current
    val typo = LocalHmpTypo.current
    
    ProvideHazeRenderSettings(settings = resolvedHazeRenderSettings) {
        ScrimDialog(onDismissRequest = onDismiss) {
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
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(metrics.cozy)
                ) {
                    Text(
                        text = title,
                        style = typo.headline,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(LayoutConstraints.PanelHeight),
                        verticalArrangement = Arrangement.spacedBy(metrics.compact)
                    ) {
                        items(playlists) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                onClick = {
                                    onSelectPlaylist(playlist)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.base))
            .clickable(onClick = onClick)
            .padding(vertical = metrics.compact, horizontal = metrics.micro),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.cozy)
    ) {
        Box(
            modifier = Modifier
                .size(metrics.bar)
                .clip(RoundedCornerShape(metrics.compact))
        ) {
            if (playlist.coverUri != null && playlist.coverUri!!.isNotBlank()) {
                AsyncImage(
                    model = playlist.coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.music_note_list),
                        contentDescription = null,
                        modifier = Modifier.size(metrics.block),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = typo.subtitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (playlist.songCount > 0 || playlist.totalDurationMs > 0 || playlist.playCount > 0) {
                val parts = mutableListOf<String>()
                if (playlist.songCount > 0) {
                    parts.add(stringResource(Res.string.songs_count, playlist.songCount))
                }
                if (playlist.totalDurationMs > 0) {
                    val minutes = (playlist.totalDurationMs / 1000 / 60).toInt()
                    parts.add(stringResource(Res.string.minutes_format, minutes))
                }
                if (playlist.playCount > 0 && parts.size < 2) {
                    parts.add(stringResource(Res.string.play_count_display, playlist.playCount))
                }
                if (parts.isNotEmpty()) {
                    Text(
                        text = parts.take(2).joinToString(" · "),
                        style = typo.footnote,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = metrics.hair)
                    )
                }
            }
        }
        Icon(
            painter = painterResource(Res.drawable.chevron_right),
            contentDescription = null,
            modifier = Modifier.size(metrics.cozy),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
