package com.hmp.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.colors.ColorTokens
import com.hearablemusic.player.ui.common.design.metrics.HMPMetricTokens
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints

/**
 * 标题栏高度。Main.kt 将它 provides 给 LocalTitleBarInset，内容区为悬浮标题栏让位。
 */
internal val TITLE_BAR_HEIGHT = HMPMetricTokens.Compact.block

/**
 * Custom window title bar for undecorated desktop window.
 * Provides window dragging via WindowDraggableArea and minimize/close controls.
 *
 * Maximize is intentionally omitted due to a known Compose Multiplatform bug
 * (JetBrains/compose-multiplatform#3625).
 *
 * @param isDarkTheme Whether the app is in dark theme — affects button styling
 * @param isPlaying Whether audio is currently playing — title bar becomes transparent
 * @param onMinimize Called to minimize the window
 * @param onClose Called when the close button is clicked
 */
@Composable
fun CustomTitleBar(
    isDarkTheme: Boolean,
    isPlaying: Boolean = false,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    // 背景透明度：与下方动态背景完全相同的动画机制
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = HmpMotion.page(),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TITLE_BAR_HEIGHT),
    ) {
        // 背景层（alpha 动画）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = bgAlpha)
                .background(MaterialTheme.colorScheme.background),
        )

        // 内容层（始终可见）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Left: App title — dimmed when playing for immersive look
            val titleColor by animateColorAsState(
                targetValue = if (isPlaying) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                animationSpec = HmpMotion.page(),
            )
            Text(
                text = "HMP",
                modifier = Modifier.padding(start = HMPMetricTokens.Compact.cozy),
                color = titleColor,
                fontSize = LayoutConstraints.TitleBarFontSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = LayoutConstraints.TitleBarLetterSpacing,
            )

            // Center: Draggable area — uses java.awt.MouseInfo for screen-absolute
            // mouse position. This avoids the Compose coordinate feedback loop that
            // causes jitter when using detectDragGestures.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(TITLE_BAR_HEIGHT)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitPointerEvent(PointerEventPass.Initial)
                                if (!down.changes.any { it.pressed }) continue
                                down.changes.first().consume()

                                val awtWindow = java.awt.Window.getWindows().firstOrNull() ?: continue
                                val startMouse = java.awt.MouseInfo.getPointerInfo().location
                                val startWin = awtWindow.locationOnScreen

                                while (true) {
                                    val move = awaitPointerEvent(PointerEventPass.Initial)
                                    if (!move.changes.any { it.pressed }) break
                                    move.changes.first().consume()

                                    val mouse = java.awt.MouseInfo.getPointerInfo().location
                                    awtWindow.location = java.awt.Point(
                                        startWin.x + mouse.x - startMouse.x,
                                        startWin.y + mouse.y - startMouse.y,
                                    )
                                }
                            }
                        }
                    },
            )

            // Right: Window control buttons
            val closeInteraction = remember { MutableInteractionSource() }
            val closeHovered by closeInteraction.collectIsHoveredAsState()

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Minimize button
                WindowControlButton(
                    symbol = "─",
                    isDarkTheme = isDarkTheme,
                    isTransparent = isPlaying,
                    onClick = onMinimize,
                )

                // Close button — red highlight on hover
                WindowControlButton(
                    symbol = "✕",
                    isDarkTheme = isDarkTheme,
                    isClose = true,
                    isTransparent = isPlaying,
                    interactionSource = closeInteraction,
                    hoveredOverride = closeHovered,
                    onClick = onClose,
                )
            }
        }
    }
}

@Composable
private fun WindowControlButton(
    symbol: String,
    isDarkTheme: Boolean,
    isClose: Boolean = false,
    isTransparent: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hoveredOverride: Boolean? = null,
    onClick: () -> Unit,
) {
    val isHovered = hoveredOverride ?: interactionSource.collectIsHoveredAsState().value
    val isPressed by interactionSource.collectIsPressedAsState()

    // When transparent, use a slightly stronger hover/pressed overlay for visibility
    val hoverAlpha = if (isTransparent) 0.18f else 0.1f
    val pressedAlpha = if (isTransparent) 0.12f else 0.06f

    val bgColor = when {
        isClose && isHovered -> ColorTokens.Shell.CloseButtonHover
        isClose && isPressed -> ColorTokens.Shell.CloseButtonPressed
        isHovered -> if (isDarkTheme) Color.White.copy(alpha = hoverAlpha) else Color.Black.copy(alpha = hoverAlpha * 0.6f)
        isPressed -> if (isDarkTheme) Color.White.copy(alpha = pressedAlpha) else Color.Black.copy(alpha = pressedAlpha)
        else -> Color.Transparent
    }

    val textColor = when {
        isClose && (isHovered || isPressed) -> Color.White
        isTransparent -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .size(HMPMetricTokens.Compact.bar, TITLE_BAR_HEIGHT)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = textColor,
            fontSize = LayoutConstraints.TitleBarButtonFontSize,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Normal,
        )
    }
}
