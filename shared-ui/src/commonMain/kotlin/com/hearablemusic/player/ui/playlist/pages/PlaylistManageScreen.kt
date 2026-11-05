package com.hearablemusic.player.ui.playlist.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.util.activityViewModel
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistNameError
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.chevron_right
import com.hearablemusic.player.ui.generated.resources.delete_playlist
import com.hearablemusic.player.ui.generated.resources.delete_playlist_confirm
import com.hearablemusic.player.ui.generated.resources.edit_list
import com.hearablemusic.player.ui.generated.resources.gearshape
import com.hearablemusic.player.ui.generated.resources.ic_gallery_material_select_checkbox
import com.hearablemusic.player.ui.generated.resources.minutes_format
import com.hearablemusic.player.ui.generated.resources.music_note_list
import com.hearablemusic.player.ui.generated.resources.no_playlists_available
import com.hearablemusic.player.ui.generated.resources.ok
import com.hearablemusic.player.ui.generated.resources.pin_playlist
import com.hearablemusic.player.ui.generated.resources.play_count_display
import com.hearablemusic.player.ui.generated.resources.playlist_name_empty
import com.hearablemusic.player.ui.generated.resources.playlist_name_exists
import com.hearablemusic.player.ui.generated.resources.playlist_name_hint
import com.hearablemusic.player.ui.generated.resources.playlist_name_too_long
import com.hearablemusic.player.ui.generated.resources.plus
import com.hearablemusic.player.ui.generated.resources.rename_playlist
import com.hearablemusic.player.ui.generated.resources.songs_count
import com.hearablemusic.player.ui.generated.resources.star
import com.hearablemusic.player.ui.generated.resources.star_fill
import com.hearablemusic.player.ui.generated.resources.title_manage_user_playlists
import com.hearablemusic.player.ui.generated.resources.trash
import com.hearablemusic.player.ui.generated.resources.unpin_playlist
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.util.UiState
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.playlist.manageablePlaylists
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics

@Composable
fun PlaylistManageScreen(
    playlistViewModel: PlaylistViewModel = activityViewModel(),
    dialogViewModel: DialogViewModel = activityViewModel(),
    navController: NavBackStack<NavKey>
) {
    val metrics = LocalHmpMetrics.current
    val userCustomPlaylistsState by playlistViewModel.userCustomPlaylistsState.collectAsState()
    val systemPlaylistIds by playlistViewModel.systemPlaylistIds.collectAsState()
    // D3-17①（用户决议）：管理页只列用户自建歌单，三个系统歌单不出现 ——
    // 它们既不能删（UseCase 会拒），也不该改名（名字会与 Res.string.heart 脱钩，自愈只认 id）。
    val userCustomPlaylists = remember(userCustomPlaylistsState, systemPlaylistIds) {
        manageablePlaylists(
            (userCustomPlaylistsState as? UiState.Success)?.data ?: emptyList(),
            systemPlaylistIds,
        )
    }
    val haptic = rememberHapticFeedback()
    var isEditMode by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var renameValue by remember { mutableStateOf("") }

    val sortedList = remember(userCustomPlaylists) {
        userCustomPlaylists.sortedWith(
            compareByDescending<Playlist> { it.isPinned }
                .thenByDescending { it.lastPlayedAt ?: 0L }
                .thenByDescending { it.updatedAt }
        )
    }

    if (playlistToDelete != null) {
        val p = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(Res.string.delete_playlist)) },
            text = { Text(stringResource(Res.string.delete_playlist_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistViewModel.deletePlaylist(p.id)
                        playlistToDelete = null
                    }
                ) {
                    Text(stringResource(Res.string.delete_playlist), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    if (playlistToRename != null) {
        val p = playlistToRename!!
        // 名称规则由 UseCase 强制（D3-05④），这里只做"把错误码翻成文案"——
        // 过去这个对话框只判非空，能改出超长名和大小写重名。
        var renameError by remember { mutableStateOf<PlaylistNameError?>(null) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null; renameValue = ""; renameError = null },
            title = { Text(stringResource(Res.string.rename_playlist)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = {
                        renameValue = it
                        if (renameError != null) renameError = null
                    },
                    label = { Text(stringResource(Res.string.playlist_name_hint)) },
                    singleLine = true,
                    isError = renameError != null,
                    supportingText = if (renameError == null) {
                        null
                    } else {
                        { Text(playlistNameErrorText(renameError!!), color = MaterialTheme.colorScheme.error) }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 不用先判空：空名由同一套规则判，界面只负责显示（否则又多一份口径）
                        playlistViewModel.renamePlaylist(p.id, renameValue) { error ->
                            if (error == null) {
                                playlistToRename = null
                                renameValue = ""
                            } else {
                                renameError = error
                            }
                        }
                    }
                ) {
                    Text(stringResource(Res.string.ok), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null; renameValue = ""; renameError = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    SubScreen(
        onBackClick = { navController.removeLastOrNull() },
        title = stringResource(Res.string.title_manage_user_playlists),
        trailingContent = {
            FilledIconButton(
                onClick = {
                    haptic.performClick()
                    isEditMode = !isEditMode
                },
                modifier = Modifier.size(metrics.block),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    painter = painterResource(if (isEditMode) Res.drawable.ic_gallery_material_select_checkbox else Res.drawable.gearshape),
                    contentDescription = stringResource(Res.string.edit_list),
                    modifier = Modifier.size(metrics.cozy)
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(metrics.cozy)
        ) {
            // 系统歌单不再列出后，只有系统歌单时这里会空 —— 给一句提示，别让页面看起来像坏了
            if (sortedList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.no_playlists_available),
                        style = LocalHmpTypo.current.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = metrics.compact),
                verticalArrangement = Arrangement.spacedBy(metrics.micro)
            ) {
                items(sortedList, key = { it.id }) { playlist ->
                    UserPlaylistRow(
                        playlist = playlist,
                        isEditMode = isEditMode,
                        onClick = {
                            haptic.performClick()
                            navController.add(Routes.Playlist.CustomPlaylist(playlist.id))
                        },
                        onDelete = { playlistToDelete = playlist },
                        onRename = {
                            playlistToRename = playlist
                            renameValue = playlist.name
                        },
                        onSetPinned = { pinned ->
                            haptic.performClick()
                            playlistViewModel.setPlaylistPinned(playlist.id, pinned)
                        }
                    )
                }
            }

            FilledIconButton(
                onClick = {
                    haptic.performClick()
                    dialogViewModel.showCreatePlaylistDialog { id ->
                        playlistViewModel.loadUserCustomPlaylists()
                        navController.add(Routes.Playlist.CustomPlaylist(id))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = metrics.row)
                    .size(metrics.bar),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    painter = painterResource(Res.drawable.plus),
                    contentDescription = stringResource(Res.string.edit_list),
                    modifier = Modifier.size(metrics.section)
                )
            }
        }
    }
}

@Composable
private fun UserPlaylistRow(
    playlist: Playlist,
    isEditMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onSetPinned: (Boolean) -> Unit
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val haptic = rememberHapticFeedback()
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
        if (!isEditMode) {
            Icon(
                painter = painterResource(Res.drawable.chevron_right),
                contentDescription = null,
                modifier = Modifier.size(metrics.cozy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.micro)) {
                IconButton(
                    onClick = {
                        haptic.performClick()
                        onSetPinned(!playlist.isPinned)
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            if (playlist.isPinned) Res.drawable.star_fill else Res.drawable.star
                        ),
                        contentDescription = stringResource(
                            if (playlist.isPinned) Res.string.unpin_playlist else Res.string.pin_playlist
                        ),
                        modifier = Modifier.size(metrics.section),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = { haptic.performClick(); onRename() }) {
                    Text(stringResource(Res.string.rename_playlist), style = typo.footnote.medium())
                }
                IconButton(
                    onClick = { haptic.performClick(); onDelete() }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.trash),
                        contentDescription = stringResource(Res.string.delete_playlist),
                        modifier = Modifier.size(metrics.section),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** 名称错误码 → 本地化文案。规则在 domain，文案在 UI（D3-05④）。 */
@Composable
private fun playlistNameErrorText(error: PlaylistNameError): String = when (error) {
    PlaylistNameError.EMPTY -> stringResource(Res.string.playlist_name_empty)
    PlaylistNameError.TOO_LONG -> stringResource(Res.string.playlist_name_too_long)
    PlaylistNameError.DUPLICATE -> stringResource(Res.string.playlist_name_exists)
}
