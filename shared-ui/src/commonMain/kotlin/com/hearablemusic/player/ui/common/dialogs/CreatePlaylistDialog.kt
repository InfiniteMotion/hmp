package com.hearablemusic.player.ui.common.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.common.text.asString
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.LocalHazeRenderSettings
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.add_songs_to_playlist
import com.hearablemusic.player.ui.generated.resources.cancel
import com.hearablemusic.player.ui.generated.resources.edit_playlist
import com.hearablemusic.player.ui.generated.resources.new_playlist
import com.hearablemusic.player.ui.generated.resources.new_playlist_dialog_title
import com.hearablemusic.player.ui.generated.resources.ok
import com.hearablemusic.player.ui.generated.resources.pin_playlist
import com.hearablemusic.player.ui.generated.resources.playlist_description_hint
import com.hearablemusic.player.ui.generated.resources.playlist_name_hint
import com.hearablemusic.player.ui.generated.resources.selected_n_songs
import com.hearablemusic.player.ui.playlist.playlistNameErrorText
import com.hearablemusic.player.ui.playlist.viewmodel.PlaylistEditorUiState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import org.jetbrains.compose.resources.stringResource

/**
 * 新建 / 编辑歌单弹窗（7.5 复审：弹窗归屏）。
 *
 * 只收屏级 VM 的状态与动作；名称校验错误以 [com.hmp.domain.playlist.PlaylistNameError] 交回，
 * 文案在渲染端翻译，不再让 VM 调 `getString`。
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun CreatePlaylistDialog(
    state: PlaylistEditorUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPinnedChange: (Boolean) -> Unit,
    onAddSongs: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
    hazeRenderSettings: HazeRenderSettings? = null,
) {
    if (!state.visible) return
    val metrics = LocalHmpMetrics.current
    val resolvedHazeRenderSettings = hazeRenderSettings ?: LocalHazeRenderSettings.current
    val typo = LocalHmpTypo.current

    ProvideHazeRenderSettings(settings = resolvedHazeRenderSettings) {
        ScrimDialog(
            onDismissRequest = {
                if (!state.isSubmitting) onDismiss()
            },
        ) {
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
                                style = hazeStyleForIntensity(),
                            )
                        } else {
                            Modifier
                        },
                    ),
                shape = dialogShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (hazeState != null) {
                        MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(metrics.section),
                    verticalArrangement = Arrangement.spacedBy(metrics.cozy),
                ) {
                    Text(
                        text = if (state.isEditing) {
                            stringResource(Res.string.edit_playlist)
                        } else {
                            stringResource(Res.string.new_playlist_dialog_title)
                        },
                        style = typo.subtitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = metrics.micro),
                    )
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(Res.string.playlist_name_hint)) },
                        singleLine = true,
                        isError = state.nameError != null,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            state.nameError?.let { error ->
                                Text(
                                    text = playlistNameErrorText(error),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = onDescriptionChange,
                        label = { Text(stringResource(Res.string.playlist_description_hint)) },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(Res.string.pin_playlist),
                            style = typo.body,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Switch(
                            checked = state.pinAfterCreate,
                            onCheckedChange = onPinnedChange,
                        )
                    }
                    if (!state.isEditing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(Res.string.selected_n_songs, state.selectedSongIds.size),
                                style = typo.footnote,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                enabled = !state.isSubmitting,
                                onClick = onAddSongs,
                                modifier = Modifier.padding(horizontal = metrics.micro),
                            ) {
                                Text(
                                    text = stringResource(Res.string.add_songs_to_playlist),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    state.submitError?.let { error ->
                        Text(
                            text = error.asString(),
                            style = typo.footnote,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = metrics.micro),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            enabled = !state.isSubmitting,
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = metrics.compact),
                        ) {
                            Text(
                                text = stringResource(Res.string.cancel),
                                style = typo.body,
                            )
                        }
                        TextButton(
                            enabled = state.canSubmit && !state.isSubmitting,
                            onClick = onSubmit,
                        ) {
                            Text(
                                text = if (state.isEditing) {
                                    stringResource(Res.string.ok)
                                } else {
                                    stringResource(Res.string.new_playlist)
                                },
                                color = MaterialTheme.colorScheme.primary,
                                style = typo.body,
                            )
                        }
                    }
                }
            }
        }
    }
}
