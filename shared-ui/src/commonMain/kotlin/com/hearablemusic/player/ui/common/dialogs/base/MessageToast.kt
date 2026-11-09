package com.hearablemusic.player.ui.common.dialogs.base

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.dialogs.viewmodel.MessageDuration
import com.hearablemusic.player.ui.common.util.HazeRenderSettings
import com.hearablemusic.player.ui.common.util.LocalHazeRenderSettings
import com.hearablemusic.player.ui.common.util.ProvideHazeRenderSettings
import com.hearablemusic.player.ui.common.util.hazeStyleForIntensity
import com.hearablemusic.player.ui.common.util.hazeTintAlpha
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.delay

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MessageToast(
    message: String,
    duration: MessageDuration,
    id: Long,
    hazeState: HazeState?,
    hazeRenderSettings: HazeRenderSettings? = null,
    onDismiss: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    val resolvedHazeRenderSettings = hazeRenderSettings ?: LocalHazeRenderSettings.current
    // 每次消息变化时，重新创建可见性状态
    var visible by remember(id) { mutableStateOf(false) }
    var isExiting by remember(id) { mutableStateOf(false) }

    // 每次消息变化时，重新启动 LaunchedEffect
    LaunchedEffect(id) {
        // 延迟一点时间再显示，确保动画能够正确触发
        delay(50)
        visible = true
        isExiting = false

        val delayMs = when (duration) {
            MessageDuration.Short -> 2000L
            MessageDuration.Long -> 4000L
        }

        delay(delayMs)
        isExiting = true
        visible = false
        // 等待动画完成后再调用 onDismiss
        delay(300)
        onDismiss()
    }

    ProvideHazeRenderSettings(settings = resolvedHazeRenderSettings) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = HmpMotion.standard()) + slideInVertically(
                initialOffsetY = { -100 }, // 固定的偏移量，使动画更明显
                animationSpec = HmpMotion.standard(),
            ),
            exit = fadeOut(animationSpec = HmpMotion.standard()) + slideOutVertically(
                targetOffsetY = { if (isExiting) -100 else 100 }, // 正常退出向上，被覆盖时向下
                animationSpec = HmpMotion.standard(),
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = metrics.bar),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .width(LayoutConstraints.ToastMaxWidth) // 宽度缩减一半
                        .clip(RoundedCornerShape(metrics.block)) // 扩大圆角成胶囊形状
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
                        .background(
                            if (hazeState != null) {
                                MaterialTheme.colorScheme.surface.copy(alpha = hazeTintAlpha())
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .border(
                            BorderStroke(
                                width = metrics.stroke.hairline,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f),
                            ),
                            shape = RoundedCornerShape(metrics.block),
                        )
                        .padding(horizontal = metrics.cozy, vertical = metrics.base),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            message.split("\n").forEach { line ->
                                Text(
                                    text = line,
                                    style = typo.body,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
