package com.hearablemusic.player.ui.common.dialogs

import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asString
import com.hearablemusic.player.ui.generated.resources.Res
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

import com.hearablemusic.player.ui.generated.resources.add_to_favorites
import com.hearablemusic.player.ui.generated.resources.album
import com.hearablemusic.player.ui.generated.resources.artist
import com.hearablemusic.player.ui.generated.resources.duration
import com.hearablemusic.player.ui.generated.resources.duration_format
import com.hearablemusic.player.ui.generated.resources.favorite
import com.hearablemusic.player.ui.generated.resources.heart
import com.hearablemusic.player.ui.generated.resources.heart_fill
import com.hearablemusic.player.ui.generated.resources.music_note_list
import com.hearablemusic.player.ui.generated.resources.person
import com.hearablemusic.player.ui.generated.resources.timer

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.dialogs.base.ScrimDialog
import com.hearablemusic.player.ui.library.pages.components.AlbumCover
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.LocalHazeRenderSettings
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.common.navigation.Routes
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.common.dialogs.viewmodel.DialogViewModel
import com.hearablemusic.player.ui.common.navigation.RouteNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

// 音乐详情卡片弹窗 - 优化版UI布局
@kotlin.OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MusicDetailDialog(
    dialogViewModel: DialogViewModel,
    onDismiss: () -> Unit,
    router: RouteNavigator,
    hazeState: HazeState? = null,
    hazeRenderSettings: HazeRenderSettings? = null
) {
    val haptic = rememberHapticFeedback()
    val musicDetailState by dialogViewModel.musicDetailState.collectAsState()
    val musicInfo = musicDetailState?.musicInfo
    val resolvedHazeRenderSettings = hazeRenderSettings ?: LocalHazeRenderSettings.current
    
    if (musicInfo == null || !musicDetailState!!.isVisible) return

    ProvideHazeRenderSettings(settings = resolvedHazeRenderSettings) {
        ScrimDialog(onDismissRequest = onDismiss) {
            val dialogShape = RoundedCornerShape(28.dp)
            Card(
                modifier = Modifier
                    .padding(24.dp)
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
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = musicInfo.music.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        // 收藏状态图标
                        IconButton(
                            onClick = {
                                haptic.performClick()
                                dialogViewModel.toggleFavorite()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (musicInfo.userInfo?.liked == true) Res.drawable.heart_fill else Res.drawable.heart
                                ),
                                contentDescription = stringResource(
                                    if (musicInfo.userInfo?.liked == true) Res.string.favorite else Res.string.add_to_favorites
                                ),
                                tint = if (musicInfo.userInfo?.liked == true) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(max = 130.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    dialogViewModel.playMusic {
                                        dialogViewModel.dismissMusicDetailDialog()
                                        onDismiss()
                                    }
                                }
                        ) {
                            AlbumCover(
                                uri = musicInfo.music.albumArtUri,
                                size = 120.dp,
                                corner = 20.dp,
                                shadow = 10.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 艺术家信息
                            InfoRow(
                                iconRes = Res.drawable.person,
                                label = stringResource(Res.string.artist),
                                value = musicInfo.music.artist,
                                onClick = {
                                    router.navigateTo(Routes.Library.Artist(musicInfo.music.artist))
                                    dialogViewModel.dismissMusicDetailDialog()
                                    onDismiss()
                                }
                            )
                            // 专辑信息
                            InfoRow(
                                iconRes = Res.drawable.music_note_list,
                                label = stringResource(Res.string.album),
                                value = musicInfo.music.album,
                                onClick = {
                                    router.navigateTo(Routes.Library.Album(musicInfo.music.album))
                                    dialogViewModel.dismissMusicDetailDialog()
                                    onDismiss()
                                }
                            )
                            // 时长信息（如果可用）
                            musicInfo.music.duration.let { duration ->
                                InfoRow(
                                    iconRes = Res.drawable.timer,
                                    label = stringResource(Res.string.duration),
                                    value = stringResource(
                                        Res.string.duration_format,
                                        duration / 1000 / 60,
                                        (duration / 1000) % 60
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        val menuOptions = dialogViewModel.getMenuOptions {
                            dialogViewModel.dismissMusicDetailDialog()
                            onDismiss()
                        }

                        menuOptions.forEach { (icon, label, action) ->
                            MenuOption(iconRes = icon, label = label, onClick = action)
                            Spacer(modifier = Modifier.height(8.dp))
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
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MenuOption(
    iconRes: DrawableResource,
    label: UiText,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable { onClick() }
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label.asString(),
            modifier = Modifier
                .size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label.asString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}