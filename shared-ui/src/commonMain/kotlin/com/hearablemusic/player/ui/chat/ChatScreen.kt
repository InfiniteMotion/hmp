package com.hearablemusic.player.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.chevron_up
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * M5-T1 ChatScreen —— 听歌伙伴对话页（确认卡片流宿主）。
 *
 * 布局（配合 SubScreen 基座后的内容区）：
 * - 顶部「正在听」胶囊（接 [PlaybackController]，无曲目时隐藏）
 * - 中部对话流 LazyColumn（新增消息在底部自动滚；用户上滑翻看时不打断）
 * - 确认卡（非模态）：`pendingConfirm` 未决/已提交时悬浮于输入条上方，不阻塞对话流回看
 * - 底部输入条（多平台 IME padding）
 */
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = koinViewModel(),
    navController: NavBackStack<NavKey>,
    playbackController: PlaybackController = koinInject(),
    chatEntryBroker: ChatEntryBroker = koinInject(),
) {
    val state by chatViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    // M1 锚点 → M5 对话入口：进入时消费待带话，作为首条消息发送（同 session_id 语义）
    LaunchedEffect(Unit) {
        chatEntryBroker.pendingInput.value?.let { input ->
            chatEntryBroker.pendingInput.value = null
            chatViewModel.sendPreloaded(input)
        }
    }

    ChatScreenContent(
        state = state,
        onInputChange = chatViewModel::onInputChange,
        onSend = chatViewModel::send,
        onToggleConfirm = chatViewModel::toggleConfirmItem,
        onSubmitConfirm = chatViewModel::submitConfirm,
        onSkipConfirm = chatViewModel::skipConfirm,
        onSongClick = { music -> scope.launch { playbackController.playWith(music) } },
        onSongMenu = {},
        onPlaylistPlayAll = {},
        onBackClick = { navController.removeLastOrNull() },
    )
}

@Composable
private fun ChatScreenContent(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleConfirm: (String) -> Unit,
    onSubmitConfirm: () -> Unit,
    onSkipConfirm: () -> Unit,
    onSongClick: (MusicInfo) -> Unit,
    onSongMenu: (MusicInfo) -> Unit,
    onPlaylistPlayAll: () -> Unit,
    onBackClick: () -> Unit,
) {
    var inputFocused by remember { mutableStateOf(false) }
    val callbacks = CompanionCallbacks(
        onSongClick = onSongClick,
        onSongMenu = onSongMenu,
        onPlaylistPlayAll = onPlaylistPlayAll,
        onToggleConfirm = onToggleConfirm,
        onSubmitConfirm = onSubmitConfirm,
        onSkipConfirm = onSkipConfirm,
    )

    // 对话页：用 SubScreen 顶栏（返回 + 标题）；内容列 imePadding —— 键盘弹出时整块内容随之上移重排
    SubScreen(onBackClick = onBackClick, title = "听歌伙伴") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
        ChatMessageList(
            messages = state.messages,
            running = state.running,
            runningHint = state.runningHint,
            callbacks = callbacks,
            inputFocused = inputFocused,
            modifier = Modifier.weight(1f),
        )

        AnimatedVisibility(
            visible = state.pendingConfirm != null,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            state.pendingConfirm?.let { p ->
                ConfirmMatrixCard(
                    items = p.items,
                    submitted = p.submitted,
                    receipt = "",
                    callbacks = callbacks,
                )
            }
        }

        // 底部输入栏（自绘紧凑输入框 + 圆形发送）
        ChatInputBar(
            input = state.input,
            running = state.running || (state.pendingConfirm != null && !state.pendingConfirm.submitted),
            onInputChange = onInputChange,
            onSend = onSend,
            onFocusChanged = { inputFocused = it },
        )
        }
    }
}

@Composable
private fun ChatMessageList(
    messages: List<CompanionMessage>,
    running: Boolean,
    runningHint: String,
    callbacks: CompanionCallbacks,
    inputFocused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 新增任何消息（用户/伙伴）→ 自动滚到最底部显示最新一条
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }
    // 聚焦输入栏（键盘弹出）→ 也滚到底，让最新一条显示在输入栏上方
    LaunchedEffect(inputFocused) {
        if (inputFocused && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    var prevHeight by remember { mutableStateOf(Int.MIN_VALUE) }
    val scrollScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            // 高度变化（键盘弹出/收起撑开内容）→ 布局定型后滚到底，保证最新消息在输入栏上方
            .onSizeChanged { size ->
                val h = size.height
                val changed = prevHeight != Int.MIN_VALUE && h != prevHeight
                prevHeight = h
                if (changed && messages.isNotEmpty()) {
                    scrollScope.launch { listState.scrollToItem(messages.lastIndex) }
                }
            },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            CompanionBubble(message = message, callbacks = callbacks)
        }
        if (running) {
            item(key = "running-hint") {
                RunningHint(runningHint)
            }
        }
    }
}

@Composable
private fun RunningHint(hint: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    running: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val canSend = input.isNotBlank() && !running
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 紧凑自绘输入框（高 44dp，无内部空白）
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (input.isEmpty()) {
                Text(
                    text = "想找歌、整理歌单，或看看听歌排行？",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                enabled = !running,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) },
            )
        }
        Spacer(Modifier.width(10.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.size(42.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (canSend) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                painter = painterResource(Res.drawable.chevron_up),
                contentDescription = "发送",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}