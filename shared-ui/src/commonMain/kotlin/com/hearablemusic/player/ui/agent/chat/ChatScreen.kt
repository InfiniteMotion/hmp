package com.hearablemusic.player.ui.agent.chat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.hearablemusic.player.ui.common.components.base.HMPTextField
import com.hearablemusic.player.ui.common.design.animation.HmpMotion
import com.hearablemusic.player.ui.common.design.metrics.LayoutConstraints
import com.hearablemusic.player.ui.common.design.metrics.LocalHmpMetrics
import com.hearablemusic.player.ui.common.design.typography.LocalHmpTypo
import com.hearablemusic.player.ui.common.layout.LocalWindowSizeInfo
import com.hearablemusic.player.ui.common.pages.base.SubScreen
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asString
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.agent_chat_input_placeholder
import com.hearablemusic.player.ui.generated.resources.agent_chat_send
import com.hearablemusic.player.ui.generated.resources.agent_chat_title
import com.hearablemusic.player.ui.generated.resources.ai_quota_exhausted_hint
import com.hearablemusic.player.ui.generated.resources.chevron_up
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.domain.music.MusicInfo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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

    val quotaBlocked by chatViewModel.quotaBlocked.collectAsState()
    ChatScreenContent(
        state = state,
        quotaBlocked = quotaBlocked,
        onInputChange = chatViewModel::onInputChange,
        onSend = chatViewModel::send,
        onToggleConfirm = chatViewModel::toggleConfirmItem,
        onToggleAlwaysAllow = chatViewModel::toggleAlwaysAllowConfirmItem,
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
    quotaBlocked: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleConfirm: (String) -> Unit,
    onToggleAlwaysAllow: (String) -> Unit,
    onSubmitConfirm: () -> Unit,
    onSkipConfirm: () -> Unit,
    onSongClick: (MusicInfo) -> Unit,
    onSongMenu: (MusicInfo) -> Unit,
    onPlaylistPlayAll: () -> Unit,
    onBackClick: () -> Unit,
) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    var inputFocused by remember { mutableStateOf(false) }
    val callbacks = CompanionCallbacks(
        onSongClick = onSongClick,
        onSongMenu = onSongMenu,
        onPlaylistPlayAll = onPlaylistPlayAll,
        onToggleConfirm = onToggleConfirm,
        onToggleAlwaysAllow = onToggleAlwaysAllow,
        onSubmitConfirm = onSubmitConfirm,
        onSkipConfirm = onSkipConfirm,
    )

    // 对话页：用 SubScreen 顶栏（返回 + 标题）；内容列 imePadding —— 键盘弹出时整块内容随之上移重排
    SubScreen(onBackClick = onBackClick, title = stringResource(Res.string.agent_chat_title)) {
        // F14-T1 自适应：宽窗（Medium/Expanded）下对话内容限宽居中，避免行长过长伤可读性；
        // 横屏（手机横屏 / 平板、桌面宽窗）抬高限宽 —— 对话确需更宽才不显局促，但也不能无限拉长行长，
        // 故取 840dp 而非铺满。Compact（手机竖屏）maxWidth 为 Dp.Unspecified → 不加约束，行为与改造前逐像素一致。
        val window = LocalWindowSizeInfo.current
        val isWide = window.isExpanded || window.isMedium
        val maxContentWidth = when {
            window.isLandscape -> LayoutConstraints.WidthBreakpointExpanded
            isWide -> LayoutConstraints.ContentMaxWidthMedium
            else -> Dp.Unspecified
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
                    .weight(1f),
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
                    modifier = Modifier.padding(horizontal = metrics.base),
                    enter = fadeIn(HmpMotion.enter()) + expandIn(HmpMotion.enter()),
                    exit = fadeOut(HmpMotion.exit()) + shrinkOut(HmpMotion.exit()),
                ) {
                    state.pendingConfirm?.let { p ->
                        ConfirmMatrixCard(
                            items = p.items,
                            submitted = p.submitted,
                            callbacks = callbacks,
                        )
                    }
                }

                // 配额耗尽提示：与输入栏同层，不占输入区排版
                if (quotaBlocked) {
                    Text(
                        text = stringResource(Res.string.ai_quota_exhausted_hint),
                        style = typo.caption,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = metrics.cozy, vertical = metrics.micro),
                    )
                }
                // 底部输入栏（自绘紧凑输入框 + 圆形发送）
                ChatInputBar(
                    input = state.input,
                    running = state.running || (state.pendingConfirm != null && !state.pendingConfirm.submitted),
                    quotaBlocked = quotaBlocked,
                    onInputChange = onInputChange,
                    onSend = onSend,
                    onFocusChanged = { inputFocused = it },
                )
            }
        }
    }
}

@Composable
private fun ChatMessageList(
    messages: List<CompanionMessage>,
    running: Boolean,
    runningHint: UiText?,
    callbacks: CompanionCallbacks,
    inputFocused: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalHmpMetrics.current
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = metrics.base, vertical = metrics.base),
        verticalArrangement = Arrangement.spacedBy(metrics.compact),
    ) {
        items(messages, key = { it.id }) { message ->
            CompanionBubble(message = message, callbacks = callbacks)
        }
        if (running) {
            item(key = "running-hint") {
                runningHint?.let { RunningHint(it) }
            }
        }
    }
}

@Composable
private fun RunningHint(hint: UiText) {
    val metrics = LocalHmpMetrics.current
    val typo = LocalHmpTypo.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = metrics.micro),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(metrics.base), strokeWidth = metrics.hair)
        Spacer(Modifier.width(metrics.compact))
        Text(
            text = hint.asString(),
            style = typo.footnote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    running: Boolean,
    quotaBlocked: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val metrics = LocalHmpMetrics.current
    val canSend = input.isNotBlank() && !running && !quotaBlocked
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.base)
            .padding(top = metrics.compact, bottom = metrics.cozy),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HMPTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = stringResource(Res.string.agent_chat_input_placeholder),
            modifier = Modifier.weight(1f),
            enabled = !running && !quotaBlocked,
            onFocusChanged = onFocusChanged,
        )
        Spacer(Modifier.width(metrics.compact))
        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.size(LayoutConstraints.ChatIconSize),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (canSend) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (canSend) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ),
        ) {
            Icon(
                painter = painterResource(Res.drawable.chevron_up),
                contentDescription = stringResource(Res.string.agent_chat_send),
                modifier = Modifier.size(metrics.cozy),
            )
        }
    }
}
