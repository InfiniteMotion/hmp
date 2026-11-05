package com.hearablemusic.player.ui.common.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.add_songs_to_playlist
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.no_songs_available
import com.hearablemusic.player.ui.library.pages.components.musiclist.EditConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.FullItemOptions
import com.hearablemusic.player.ui.library.pages.components.musiclist.HeaderConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemConfig
import com.hearablemusic.player.ui.library.pages.components.musiclist.ItemVariant
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicList
import com.hearablemusic.player.ui.library.pages.components.musiclist.MusicListCallbacksAdapter
import com.hearablemusic.player.ui.library.pages.components.musiclist.defaultMusicListConfig
import com.hmp.domain.music.MusicInfo
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddSongToPlaylistDialog(
    allMusic: List<MusicInfo>,
    currentInPlaylistIds: Set<Long>,
    onAdd: (musicId: Long, path: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val toShow = allMusic.filter { it.music.id !in currentInPlaylistIds }
    ScrimDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(metrics.section),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = metrics.compact),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(metrics.cozy),
            ) {
                Text(
                    text = stringResource(Res.string.add_songs_to_playlist),
                    style = typo.subtitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = metrics.cozy),
                )

                if (toShow.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(metrics.card),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.no_songs_available),
                            style = typo.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val callbacks = object : MusicListCallbacksAdapter() {
                        override fun onItemClick(musicInfo: MusicInfo, index: Int) {
                            onAdd(musicInfo.music.id, musicInfo.music.path)
                        }
                    }
                    val config = defaultMusicListConfig(callbacks).copy(
                        header = HeaderConfig.None,
                        item = ItemConfig(
                            variant = ItemVariant.Full,
                            fullOptions = FullItemOptions(
                                showPinButton = false,
                                showRemoveButton = false,
                                showMenuButton = false,
                            ),
                        ),
                        edit = EditConfig(enabled = false),
                    )
                    MusicList(
                        musicInfoList = toShow,
                        config = config,
                        modifier = Modifier.fillMaxWidth().heightIn(max = LayoutConstraints.DialogMaxHeight),
                        isPlaying = false,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = metrics.cozy),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            }
        }
    }
}
