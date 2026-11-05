package com.example.hearablemusicplayer.ui.player.floating

import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.hearablemusic.player.player.controller.MusicController
import com.hearablemusic.player.ui.common.design.colors.ColorTokens
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hmp.domain.config.LyricsConfig
import com.hmp.domain.lyrics.LrcParser
import com.hmp.domain.lyrics.LyricsComponent
import com.hmp.domain.lyrics.findCurrentLyricIndex
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase

@OptIn(UnstableApi::class)
@Composable
fun FloatingLyricsOverlay(
    musicController: MusicController,
    lyricsSettingsUseCase: LyricsSettingsUseCase,
    windowManager: WindowManager,
    initialLayoutParams: WindowManager.LayoutParams,
    onClose: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val overlayView = LocalView.current
    val displayMetrics = overlayView.resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels

    val lyrics by musicController.currentMusicLyrics.collectAsState()
    val currentPosition by musicController.currentPosition.collectAsState()
    val isPlaying by musicController.isPlaying.collectAsState()

    var resolvedConfig by remember { mutableStateOf(LyricsConfig()) }
    var isLocked by remember { mutableStateOf(false) }
    var animTargetX by remember { mutableIntStateOf(initialLayoutParams.x) }

    LaunchedEffect(Unit) {
        resolvedConfig = lyricsSettingsUseCase.resolveConfig(LyricsComponent.FLOATING)
    }

    val animatedX by animateIntAsState(animTargetX, tween(250), label = "snapX")
    LaunchedEffect(animatedX) {
        initialLayoutParams.x = animatedX
        try {
            windowManager.updateViewLayout(overlayView, initialLayoutParams)
        } catch (_: Exception) {}
    }

    val lyricsText = lyrics
    val parsedLyrics = remember(lyricsText) {
        if (lyricsText != null) LrcParser.parse(lyricsText) else emptyList()
    }

    val currentIndex by remember(parsedLyrics, currentPosition) {
        derivedStateOf { findCurrentLyricIndex(parsedLyrics, currentPosition) }
    }

    val currentLine = parsedLyrics.getOrNull(currentIndex)

    val background = ColorTokens.Shell.OverlayBackground
    val textColor = Color.White
    val dimTextColor = Color.White.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background, RoundedCornerShape(metrics.cozy))
            .clip(RoundedCornerShape(metrics.cozy))
            .then(
                if (!isLocked) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { _, d ->
                                initialLayoutParams.x += d.x.toInt()
                                initialLayoutParams.y += d.y.toInt()
                                try {
                                    windowManager.updateViewLayout(overlayView, initialLayoutParams)
                                } catch (_: Exception) {}
                            },
                            onDragEnd = {
                                val cx = initialLayoutParams.x + initialLayoutParams.width / 2
                                animTargetX = if (cx < screenWidth / 2) 0 else screenWidth - initialLayoutParams.width
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = metrics.compact, vertical = metrics.micro),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⋮⋮", color = dimTextColor, fontSize = LayoutConstraints.OverlayFontSizeBody)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { isLocked = !isLocked }, Modifier.size(metrics.section)) {
                Text(if (isLocked) "🔒" else "🔓", fontSize = LayoutConstraints.OverlayFontSizeSmall)
            }
            IconButton(onClick = onClose, Modifier.size(metrics.section)) {
                Text("✕", color = dimTextColor, fontSize = LayoutConstraints.OverlayFontSizeIcon, fontWeight = FontWeight.Bold)
            }
        }

        // Current lyric line — centered
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.cozy, vertical = metrics.base),
            contentAlignment = Alignment.Center,
        ) {
            if (currentLine != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentLine.originalText,
                        color = textColor,
                        fontSize = resolvedConfig.currentTimeTextSize.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (resolvedConfig.displayMode != com.hmp.domain.config.DisplayMode.LANG1) {
                        currentLine.translatedText?.let { translated ->
                            Spacer(Modifier.size(metrics.micro))
                            Text(
                                text = translated,
                                color = dimTextColor,
                                fontSize = resolvedConfig.translatedTextSize.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                Text("♪", color = dimTextColor, fontSize = LayoutConstraints.OverlayFontSizeNote)
            }
        }

        // Playing indicator
        if (isPlaying) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = metrics.micro),
            ) {
                Text("●", color = Color.White.copy(alpha = 0.15f), fontSize = LayoutConstraints.OverlayFontSizeDot)
            }
        }
    }
}
