package com.hearablemusic.player.ui.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.design.typography.medium
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.artist_placeholder
import com.hearablemusic.player.ui.generated.resources.backward_end_fill
import com.hearablemusic.player.ui.generated.resources.forward_end_fill
import com.hearablemusic.player.ui.generated.resources.music_title_placeholder
import com.hearablemusic.player.ui.generated.resources.next_desc
import com.hearablemusic.player.ui.generated.resources.pause
import com.hearablemusic.player.ui.generated.resources.pause_desc
import com.hearablemusic.player.ui.generated.resources.play_desc
import com.hearablemusic.player.ui.generated.resources.play_fill
import com.hearablemusic.player.ui.generated.resources.previous
import com.hearablemusic.player.ui.library.pages.components.AlbumCover
import com.hmp.domain.music.MusicInfo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 悬浮迷你播放器组件
 * 展示当前播放音乐信息和快捷控制按钮
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MiniPlayerBar(
    modifier: Modifier = Modifier,
    musicInfo: MusicInfo?,
    isPlaying: Boolean,
    progress: Float = 0f, // 0f-1f的进度值
    hazeState: HazeState? = null,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val haptic = rememberHapticFeedback()
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val barShape = RoundedCornerShape(metrics.block)
    val musicTitle = musicInfo?.music?.title ?: stringResource(Res.string.music_title_placeholder)
    val artistName = musicInfo?.music?.artist ?: stringResource(Res.string.artist_placeholder)
    val albumArtUri = musicInfo?.music?.albumArtUri ?: ""
    val coverRotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (isActive) {
            val current = coverRotation.value % 360f
            val remaining = 360f - current
            val durationMillis = ((remaining / 360f) * 8000f).toInt().coerceAtLeast(1)
            coverRotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
            )
            coverRotation.snapTo(0f)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = metrics.section, end = metrics.section, top = metrics.compact, bottom = metrics.cozy)
            .clip(barShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = hazeStyleForIntensity(),
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = normalizedProgress,
                    range = 0f..1f,
                )
            }
            .clickable {
                haptic.performLightClick()
                onOpenPlayer()
            },
        shape = barShape,
        colors = CardDefaults.cardColors(
            containerColor = if (hazeState != null) {
                MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = metrics.stroke.hairline,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.compact, vertical = metrics.compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 专辑封面
            AlbumCover(
                uri = albumArtUri,
                size = metrics.bar,
                corner = metrics.section,
                shadow = metrics.micro,
                modifier = Modifier.graphicsLayer {
                    rotationZ = coverRotation.value
                },
            )

            Spacer(modifier = Modifier.width(metrics.cozy))

            // 音乐信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = musicTitle,
                    style = typo.body.medium(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(metrics.compact))
                Text(
                    text = artistName,
                    style = typo.footnote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(metrics.compact))

            // 控制按钮组
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 上一首按钮
                IconButton(
                    onClick = {
                        haptic.performLightClick()
                        onPrev()
                    },
                    modifier = Modifier.size(metrics.block),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.backward_end_fill),
                        contentDescription = stringResource(Res.string.previous),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(metrics.section),
                    )
                }

                // 播放/暂停按钮
                IconButton(
                    onClick = {
                        haptic.performLightClick()
                        onPlayPause()
                    },
                    modifier = Modifier.size(metrics.bar),
                ) {
                    Icon(
                        painter = if (isPlaying) {
                            painterResource(Res.drawable.pause)
                        } else {
                            painterResource(Res.drawable.play_fill)
                        },
                        contentDescription = if (isPlaying) stringResource(Res.string.pause_desc) else stringResource(Res.string.play_desc),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(LayoutConstraints.CompactIconSize),
                    )
                }

                // 下一首按钮
                IconButton(
                    onClick = {
                        haptic.performLightClick()
                        onNext()
                    },
                    modifier = Modifier.size(metrics.block),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.forward_end_fill),
                        contentDescription = stringResource(Res.string.next_desc),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(metrics.section),
                    )
                }
            }
        }
    }
}
