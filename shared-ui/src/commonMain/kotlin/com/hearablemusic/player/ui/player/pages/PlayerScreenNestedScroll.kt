package com.hearablemusic.player.ui.player.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberPlayerScreenNestedScroll(
    dismissThreshold: Float,
    offsetY: Animatable<Float, *>,
    scope: CoroutineScope,
    haptic: () -> Unit,
    onDismiss: () -> Unit,
): NestedScrollConnection {
    val density = LocalDensity.current

    return remember(dismissThreshold, offsetY, scope, haptic, onDismiss) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && offsetY.value > 0f && source == NestedScrollSource.UserInput) {
                    val consumed = available.y.coerceAtLeast(-offsetY.value)
                    scope.launch {
                        val newOffset = (offsetY.value + consumed).coerceAtLeast(0f)
                        offsetY.snapTo(newOffset)
                    }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && consumed.y <= 0 && source == NestedScrollSource.UserInput) {
                    val delta = available.y
                    scope.launch {
                        val newOffset = (offsetY.value + delta).coerceAtLeast(0f)
                        offsetY.snapTo(newOffset)
                        if (newOffset > dismissThreshold * 0.5f && newOffset < dismissThreshold * 0.6f) {
                            haptic()
                        }
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > 0f) {
                    if (offsetY.value > dismissThreshold) {
                        onDismiss()
                        haptic()
                        offsetY.animateTo(
                            targetValue = with(density) { LayoutConstraints.NestedScrollTarget.toPx() },
                            animationSpec = HmpMotion.exit(),
                        )
                    } else {
                        haptic()
                        offsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = HmpMotion.snappySpring(),
                        )
                    }
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }
}
