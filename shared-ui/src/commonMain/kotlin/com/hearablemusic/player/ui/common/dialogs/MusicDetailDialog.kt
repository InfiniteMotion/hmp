package com.hearablemusic.player.ui.common.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asString
import com.hearablemusic.player.ui.common.text.asUiText
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.LocalHazeRenderSettings
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.add_to_favorites
import com.hearablemusic.player.ui.generated.resources.add_to_specific_playlist
import com.hearablemusic.player.ui.generated.resources.album
import com.hearablemusic.player.ui.generated.resources.artist
import com.hearablemusic.player.ui.generated.resources.delete
import com.hearablemusic.player.ui.generated.resources.duration
import com.hearablemusic.player.ui.generated.resources.duration_format
import com.hearablemusic.player.ui.generated.resources.edit_music_tags
import com.hearablemusic.player.ui.generated.resources.favorite
import com.hearablemusic.player.ui.generated.resources.forward_end_fill
import com.hearablemusic.player.ui.generated.resources.heart
import com.hearablemusic.player.ui.generated.resources.heart_fill
import com.hearablemusic.player.ui.generated.resources.music
import com.hearablemusic.player.ui.generated.resources.music_note_list
import com.hearablemusic.player.ui.generated.resources.person
import com.hearablemusic.player.ui.generated.resources.play_next
import com.hearablemusic.player.ui.generated.resources.plus_square
import com.hearablemusic.player.ui.generated.resources.remove_from_current_playlist
import com.hearablemusic.player.ui.generated.resources.rename
import com.hearablemusic.player.ui.generated.resources.share
import com.hearablemusic.player.ui.generated.resources.timer
import com.hearablemusic.player.ui.generated.resources.title_song_detail
import com.hearablemusic.player.ui.generated.resources.trash
import com.hearablemusic.player.ui.library.pages.components.AlbumCover
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// 音乐详情卡片弹窗 - 优化版UI布局
@kotlin.OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MusicDetailDialog(
    state: DialogViewModel.DialogUiState.MusicDetail,
    dialogViewModel: DialogViewModel,
    hazeState: HazeState? = null,
    hazeRenderSettings: HazeRenderSettings? = null,
) {
    val metrics = LocalHmpMetrics.current
    val haptic = rememberHapticFeedback()
    // 状态由 AppRoot 单点订阅后传入；本组件不再重复 collect activeDialog（保留 VM 仅为动作入口）。
    val detail = state.state
    if (!detail.isVisible) return
    val musicInfo = detail.musicInfo
    val resolvedHazeRenderSettings = hazeRenderSettings ?: LocalHazeRenderSettings.current
    val typo = LocalHmpTypo.current

    ProvideHazeRenderSettings(settings = resolvedHazeRenderSettings) {
        ScrimDialog(onDismissRequest = dialogViewModel::dismissMusicDetailDialog) {
            val dialogShape = RoundedCornerShape(metrics.section)
            Card(
                modifier = Modifier
                    .padding(metrics.section)
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
                        MaterialTheme.colorScheme.surface
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(metrics.section)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(metrics.cozy),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = musicInfo.music.title,
                            style = typo.headline,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        // 收藏状态图标
                        IconButton(
                            onClick = {
                                haptic.performClick()
                                dialogViewModel.toggleFavorite()
                            },
                            modifier = Modifier.size(metrics.block),
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (musicInfo.userInfo?.liked == true) Res.drawable.heart_fill else Res.drawable.heart,
                                ),
                                contentDescription = stringResource(
                                    if (musicInfo.userInfo?.liked == true) Res.string.favorite else Res.string.add_to_favorites,
                                ),
                                tint = if (musicInfo.userInfo?.liked == true) Color.Red else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(max = LayoutConstraints.ListMaxHeight),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(metrics.panel)
                                .clip(RoundedCornerShape(metrics.cozy))
                                .clickable { dialogViewModel.playMusic() },
                        ) {
                            AlbumCover(
                                uri = musicInfo.music.albumArtUri,
                                size = metrics.panel,
                                corner = metrics.cozy,
                                shadow = metrics.compact,
                            )
                        }
                        Spacer(modifier = Modifier.width(metrics.cozy))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(metrics.compact),
                        ) {
                            // 艺术家信息
                            InfoRow(
                                iconRes = Res.drawable.person,
                                label = stringResource(Res.string.artist),
                                value = musicInfo.music.artist,
                                onClick = { dialogViewModel.viewArtist() },
                            )
                            // 专辑信息
                            InfoRow(
                                iconRes = Res.drawable.music_note_list,
                                label = stringResource(Res.string.album),
                                value = musicInfo.music.album,
                                onClick = { dialogViewModel.viewAlbum() },
                            )
                            // 时长信息（如果可用）
                            musicInfo.music.duration.let { duration ->
                                InfoRow(
                                    iconRes = Res.drawable.timer,
                                    label = stringResource(Res.string.duration),
                                    value = stringResource(
                                        Res.string.duration_format,
                                        duration / 1000 / 60,
                                        (duration / 1000) % 60,
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(metrics.compact))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        // 菜单按 state 在渲染端组装：VM 不再持 Compose 资源与 lambda
                        val menuConfig = detail.menuConfig
                        val menuOptions: List<Triple<DrawableResource, UiText, () -> Unit>> = buildList {
                            if (menuConfig.showViewDetail) {
                                add(Triple(Res.drawable.music, Res.string.title_song_detail.asUiText()) { dialogViewModel.viewDetail() })
                            }
                            if (menuConfig.showShare) {
                                add(Triple(Res.drawable.share, Res.string.share.asUiText()) { dialogViewModel.shareMusic() })
                            }
                            if (menuConfig.showEditTags) {
                                add(Triple(Res.drawable.rename, Res.string.edit_music_tags.asUiText()) { dialogViewModel.editTags() })
                            }
                            if (menuConfig.showAddToSpecificPlaylist) {
                                add(Triple(Res.drawable.plus_square, Res.string.add_to_specific_playlist.asUiText()) { dialogViewModel.addToSpecificPlaylist() })
                            }
                            if (menuConfig.showPlayNext) {
                                add(Triple(Res.drawable.forward_end_fill, Res.string.play_next.asUiText()) { dialogViewModel.playNext() })
                            }
                            if (menuConfig.showRemoveFromCurrentPlaylist) {
                                add(Triple(Res.drawable.trash, Res.string.remove_from_current_playlist.asUiText()) { dialogViewModel.removeFromCurrentPlaylist() })
                            }
                            if (menuConfig.showDelete) {
                                add(Triple(Res.drawable.trash, Res.string.delete.asUiText()) { dialogViewModel.deleteMusic() })
                            }
                        }

                        menuOptions.forEach { (icon, label, action) ->
                            MenuOption(iconRes = icon, label = label, onClick = action)
                            Spacer(modifier = Modifier.height(metrics.compact))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    iconRes: DrawableResource,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(metrics.cozy),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(metrics.base))
        Column {
            Text(
                text = label,
                style = typo.footnote.medium(),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = typo.caption,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MenuOption(
    iconRes: DrawableResource,
    label: UiText,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(metrics.micro)
            .clip(RoundedCornerShape(metrics.micro))
            .clickable { onClick() },
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label.asString(),
            modifier = Modifier
                .size(metrics.cozy),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(metrics.cozy))
        Text(
            text = label.asString(),
            style = typo.subtitle,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
