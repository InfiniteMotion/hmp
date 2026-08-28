package com.hearablemusic.player.ui.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hearablemusic.player.ui.common.util.rememberHapticFeedback
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.agent_quick_sheet_hint
import com.hearablemusic.player.ui.generated.resources.agent_quick_sheet_send
import org.jetbrains.compose.resources.stringResource

/**
 * 轻量对话浮层（任务书 M1-T3，设计总纲 3.3）。
 *
 * 单行输入条：长按伙伴胶囊（600ms）/播放页「对话」按钮/C 键唤起；自底上滑；
 * 有底栏时贴底栏上方、无底栏页面贴屏底（由调用方排布锚定）。
 * 回复二分法（一句话气泡 / 卡片摘要）在 M5 对话系统落地；本阶段提交后回调上层，输入清空。
 */
@Composable
fun AgentQuickSheet(
    visible: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val haptic = rememberHapticFeedback()
    // 唤起即聚焦输入框（review 2026-08-28：桌面此前需再点一次才可输入）
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) focusRequester.requestFocus()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(250),
        ) + fadeIn(animationSpec = tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(200),
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState -> if (focusState.isFocused) haptic.performClick() },
                placeholder = {
                    Text(stringResource(Res.string.agent_quick_sheet_hint))
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val input = text.trim()
                            if (input.isNotEmpty()) {
                                haptic.performConfirm()
                                onSubmit(input)
                                text = ""
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(Res.string.agent_quick_sheet_send),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = {
                        val input = text.trim()
                        if (input.isNotEmpty()) {
                            haptic.performConfirm()
                            onSubmit(input)
                            text = ""
                        }
                    }
                ),
            )
        }
    }
}