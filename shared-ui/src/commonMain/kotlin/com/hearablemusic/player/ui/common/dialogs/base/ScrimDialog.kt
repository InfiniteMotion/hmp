package com.hearablemusic.player.ui.common.dialogs.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 平台差异化弹窗属性：
 * Android 侧需 decorFitsSystemWindows=false 实现 edge-to-edge 全屏遮罩
 * （该参数为 Android 变体专属）；Desktop 无系统栏嵌合概念，仅禁用默认宽度。
 */
internal expect fun scrimDialogProperties(): DialogProperties

/**
 * 全屏遮罩弹窗样式：占满宽度、延伸到状态栏/导航栏，背部半透明黑色遮罩，点击遮罩关闭。
 * [content] 为居中展示的弹窗内容（如卡片）。
 * [enableScrimDismiss] 为 false 时点击遮罩不关闭（如扫描中禁止关闭）。
 */
@Composable
fun ScrimDialog(
    onDismissRequest: () -> Unit,
    enableScrimDismiss: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = scrimDialogProperties(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enableScrimDismiss) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismissRequest,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // 全屏半透明遮罩（视觉层，不单独处理点击，由外层 Box 统一处理）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
            )
            content()
        }
    }
}
