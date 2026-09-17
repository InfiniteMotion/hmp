package com.hearablemusic.player.ui.common.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.design.dimens.LocalHMPDimens
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * HMP 基础搜索/输入框组件。
 *
 * 以 ChatScreen 对话输入框为 base，统一首页搜索条、搜索页输入框、对话页输入框的样式。
 * 三种使用模式：
 *   - 普通输入：传 [value] + [onValueChange]，可输入
 *   - 点击跳转：只传 [onClick]，渲染为静态搜索条（首页模式）
 *   - 带尾部：传 [trailingContent]（如发送按钮），外部自行 Row 组合
 *
 * 统一样式：
 *   - 背景 surfaceVariant 60% 半透明（不遮挡动态背景）
 *   - 边框 1dp outlineVariant
 *   - 圆角 corner.lg（响应式 token）
 *   - 高度 44dp
 */
@Composable
fun HMPTextField(
    value: String = "",
    onValueChange: (String) -> Unit = {},
    placeholder: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface
    ),
) {
    val dimens = LocalHMPDimens.current
    val shape = RoundedCornerShape(dimens.corner.md)
    val bgColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.15f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val placeholderStyle = MaterialTheme.typography.bodySmall

    val innerBox = Modifier
        .height(44.dp)
        .background(bgColor, shape)
        .border(1.dp, borderColor, shape)
        .padding(horizontal = 14.dp)

    Box(
        modifier = modifier
            .then(if (onClick != null) {
                Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                )
            } else Modifier)
            .then(innerBox),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.invoke()
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = placeholderStyle,
                        color = placeholderColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onClick == null) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = textStyle,
                        singleLine = singleLine,
                        enabled = enabled,
                        keyboardOptions = keyboardOptions,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { onFocusChanged(it.isFocused) },
                    )
                }
            }
            trailingContent?.invoke()
        }
    }
}
