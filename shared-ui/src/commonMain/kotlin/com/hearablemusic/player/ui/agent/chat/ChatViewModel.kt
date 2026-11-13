package com.hearablemusic.player.ui.agent.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.common.text.UiText
import com.hearablemusic.player.ui.common.text.asUiText
import com.hearablemusic.player.ui.generated.resources.Res
import com.hearablemusic.player.ui.generated.resources.agent_chat_error_offline
import com.hearablemusic.player.ui.generated.resources.agent_chat_greeting
import com.hearablemusic.player.ui.generated.resources.agent_chat_hint_playlist
import com.hearablemusic.player.ui.generated.resources.agent_chat_hint_scanning
import com.hearablemusic.player.ui.generated.resources.agent_chat_hint_thinking
import com.hearablemusic.player.ui.generated.resources.agent_chat_hint_wait
import com.hearablemusic.player.ui.platform.PlaybackController
import com.hmp.data.database.currentTimeMillis
import com.hmp.domain.agent.port.AgentMessageStore
import com.hmp.domain.agent.port.ConfirmOutcome
import com.hmp.domain.agent.port.StoredAgentMessage
import com.hmp.domain.agent.port.ToolPermissionLevel
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 对话页的一次性效果：导航由组合点消费（VM 不持有 NavBackStack）。 */
sealed interface ChatEffect {
    data object NavigateToPlayer : ChatEffect
}

/** 待确认的非模态卡片状态（M5-T4）。 */
data class ConfirmCardState(
    val turnId: String,
    val items: List<ConfirmItem>,
    /** 已提交勾选（提交后卡将常驻直至替代气泡上屏）。 */
    val submitted: Boolean = false,
)

/** 对话页 UI 状态（M5-T1）。 */
data class ChatUiState(
    val messages: List<CompanionMessage> = emptyList(),
    val running: Boolean = false,
    val runningHint: UiText? = null,
    val pendingConfirm: ConfirmCardState? = null,
    val input: String = "",
    /** Master 桶是否已耗尽（原独立 StateFlow 出口，7.5 P1 并入唯一出口）。 */
    val quotaBlocked: Boolean = false,
)

/**
 * M5-T1 ChatViewModel —— 对话页唯一状态源。
 *
 * 职责：问候区/任务进行条/确认聚合/执行回执；气泡里的歌曲点击与整单播放也在这里（VM 直接调平台门面，
 * 导航由组合点消费 [ChatEffect] 完成）。
 * 引擎侧经 [ChatAgentGateway] 接缝驱动；[ChatAgentEvent] → [CompanionMessage] 展平在 [buildAssistantBubbles]。
 */
class ChatViewModel(
    private val gateway: ChatAgentGateway,
    private val userSettings: UserSettingsUseCase,
    private val agentMessageStore: AgentMessageStore,
    private val quotaGate: com.hmp.domain.agent.quota.TokenQuotaGate,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _effect = MutableSharedFlow<ChatEffect>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effect: SharedFlow<ChatEffect> = _effect.asSharedFlow()

    /** 播放一首气泡里的歌（suspend 调用归 VM，离屏即取消）。 */
    fun onPlaySong(music: MusicInfo) {
        viewModelScope.launch { playbackController.playWith(music) }
    }

    /** 播放整张气泡歌单（顺序入队）并请求跳到播放页。 */
    fun onPlayPlaylistPlayAll(songs: List<MusicInfo>) {
        if (songs.isEmpty()) return
        playbackController.addAllToPlaylistInOrder(songs)
        viewModelScope.launch { _effect.emit(ChatEffect.NavigateToPlayer) }
    }

    /** Master 桶是否已耗尽（私有源；生效值并入 [ChatUiState.quotaBlocked]，不再单独暴露）。 */
    private val quotaBlocked: StateFlow<Boolean> = quotaGate.usage
        .map { quotaGate.isBucketExhausted(com.hmp.domain.agent.quota.QuotaBucket.MASTER.bucketId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), quotaGate.isBucketExhausted(com.hmp.domain.agent.quota.QuotaBucket.MASTER.bucketId))

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var idCounter = 1000L
    private var job: Job? = null

    /** 本轮已提交的确认批次（R-T4：单轮多次确认时逐批累积，回执不互相覆盖）。 */
    private val submittedConfirms = mutableListOf<ConfirmCardState>()

    // ── R-T5 会话持久化：session_id + 历史加载 + 增量写入 ──
    private var sessionId: String? = null

    init {
        viewModelScope.launch {
            quotaBlocked.collect { blocked -> _uiState.update { it.copy(quotaBlocked = blocked) } }
        }
        viewModelScope.launch {
            val sid = agentMessageStore.currentOrNewSessionId()
            sessionId = sid
            val history = agentMessageStore.loadSession(sid, SESSION_LOAD_LIMIT)
            HmpLog.i(LogTag.AgentChat) { "💬 session resume: sid=$sid history=${history.size}" }
            if (history.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        // tool_summary 只给模型看，不在聊天记录里显示（R3 后半）。
                        messages = history.filter { it.renderHint != com.hmp.domain.agent.port.TOOL_SUMMARY_RENDER_HINT }.map { m ->
                            CompanionMessage(
                                id = nextId(),
                                fromUser = m.role == "user",
                                // 历史里嵌套的 Markdown 标记一并清洗（Compose 不解析 ** / ` / *）
                                text = cleanAgentMarkdown(m.content.orEmpty()),
                                renderHint = hintFromStore(m.renderHint),
                            )
                        },
                    )
                }
            } else {
                // 无历史会话：填入问候语。只携带资源引用，解析留给组合期（随语言变化），
                // 不在 ViewModel 里 getString —— 那样既冻结语言，又让纯 JVM 单测崩。
                _uiState.update {
                    it.copy(
                        messages = listOf(
                            CompanionMessage(
                                id = 1,
                                fromUser = false,
                                textRes = Res.string.agent_chat_greeting.asUiText(),
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun persist(role: String, content: String?, renderHint: CompanionRenderHint) {
        val sid = sessionId ?: return
        viewModelScope.launch {
            agentMessageStore.append(
                StoredAgentMessage(
                    sessionId = sid,
                    role = role,
                    content = content,
                    renderHint = renderHint.name.lowercase(),
                    createdAt = currentTimeMillis(),
                ),
            )
        }
    }

    fun onInputChange(value: String) = _uiState.update { it.copy(input = value) }

    /** 发送：追加用户气泡 → 启动一轮引擎对话。 */
    fun send() {
        if (quotaBlocked.value) return // UI 已置灰；这里再兜一层，防止外部入口绕过
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return
        _uiState.update { it.copy(input = "") }
        sendText(text)
    }

    /**
     * 外部入口带入（M1 锚点 → M5 对话）。
     *
     * 运行中 / 未决确认卡未提交时 [sendText] 会拒绝派发 —— 这条文本**落到输入框**而不是丢掉，
     * 等本轮结束用户直接点发送即可（A22：broker 的消费与「发送是否成功」解耦）。
     */
    fun sendPreloaded(text: String) {
        val trimmed = text.trim()
        if (!sendText(trimmed)) {
            _uiState.update { it.copy(input = trimmed) }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    // ── 确认卡交互 ──

    fun toggleConfirmItem(itemId: String) {
        val p = _uiState.value.pendingConfirm ?: return
        if (p.submitted) return
        val items = p.items.map {
            if (it.id == itemId) it.copy(selected = !it.selected) else it
        }
        _uiState.update { it.copy(pendingConfirm = p.copy(items = items)) }
    }

    /** 切换某项的"总是允许"标记（勾上后该项会自动 selected）。 */
    fun toggleAlwaysAllowConfirmItem(itemId: String) {
        val p = _uiState.value.pendingConfirm ?: return
        if (p.submitted) return
        val items = p.items.map {
            if (it.id == itemId && !it.permanentAllowForbidden) {
                val newAlways = !it.alwaysAllow
                it.copy(alwaysAllow = newAlways, selected = it.selected || newAlways)
            } else {
                it
            }
        }
        _uiState.update { it.copy(pendingConfirm = p.copy(items = items)) }
    }

    /** 「照做」：把当前勾选提交给引擎，恢复挂起的确认批次（累积到 submittedConfirms）。 */
    fun submitConfirm() {
        val p = _uiState.value.pendingConfirm ?: return
        if (p.submitted) return
        submittedConfirms += p
        val alwaysCount = p.items.count { it.alwaysAllow }
        HmpLog.i(LogTag.AgentChat) { "💬 confirm 照做: turn=${p.turnId} selected=${p.items.count { it.selected }}/${p.items.size} alwaysAllow=$alwaysCount" }
        val outcomes = p.items.map {
            when {
                it.alwaysAllow && it.selected -> ConfirmOutcome.AllowAlways
                it.selected -> ConfirmOutcome.AllowOnce
                else -> ConfirmOutcome.Deny
            }
        }
        gatewayBridge?.submit(p.turnId, outcomes)
        _uiState.update { it.copy(pendingConfirm = p.copy(submitted = true)) }
    }

    /** 「跳过全部」：整批否决（拒绝纪律：不纠缠）。 */
    fun skipConfirm() {
        val p = _uiState.value.pendingConfirm ?: return
        if (p.submitted) return
        val rejected = p.copy(items = p.items.map { it.copy(selected = false) })
        submittedConfirms += rejected
        HmpLog.i(LogTag.AgentChat) { "💬 confirm 跳过全部: turn=${p.turnId}" }
        val outcomes = List(p.items.size) { ConfirmOutcome.Deny }
        gatewayBridge?.submit(p.turnId, outcomes)
        _uiState.update { it.copy(pendingConfirm = rejected.copy(submitted = true)) }
    }

    private var gatewayBridge: ConfirmBridge? = null

    /** 内部发送入口：空/运行中/未决确认卡未决时拒绝派发并返回 false；派发成功返回 true。 */
    private fun sendText(text: String): Boolean {
        if (text.isBlank() || _uiState.value.running) return false
        val pending = _uiState.value.pendingConfirm
        if (pending != null && !pending.submitted) return false // 未提交的确认卡未决前不派新一轮
        _uiState.update {
            it.copy(messages = it.messages + CompanionMessage(id = nextId(), fromUser = true, text = text))
        }
        persist("user", text, CompanionRenderHint.TEXT)
        // 只保留 LLM 路径（2026-11-11 决议）：本地词表已删除，播放控制由 playback_control 工具承担。
        HmpLog.d(LogTag.AgentChat) { "💬 chat send: $text" }
        launchRun(text)
        return true
    }

    private fun launchRun(input: String) {
        if (_uiState.value.running) return
        val bridge = ConfirmBridge()
        gatewayBridge = bridge
        job = viewModelScope.launch {
            _uiState.update {
                it.copy(running = true, runningHint = pickRunningHint(it.messages.size))
            }
            try {
                val config = userSettings.getActiveAiConfig()
                gateway.run(input, config, bridge).collect { event ->
                    when (event) {
                        is ChatAgentEvent.NeedConfirm -> {
                            // R-T4 防御：不覆盖尚未提交的确认批次（引擎循环是顺序挂起的，正常不会触发）
                            val current = _uiState.value.pendingConfirm
                            if (current != null && !current.submitted) return@collect
                            val items = event.requests.map { r ->
                                val strong = r.permissionLevel == ToolPermissionLevel.STRONG_CONFIRM
                                ConfirmItem(
                                    id = r.toolName + "#" + r.argsSummary.hashCode(),
                                    toolName = r.toolName,
                                    argsSummary = r.argsSummary,
                                    // 不可逆项不预选：用户必须看清它改的是什么再自己勾上
                                    selected = !strong,
                                    permissionLevel = r.permissionLevel,
                                )
                            }
                            _uiState.update { it.copy(pendingConfirm = ConfirmCardState(event.turnId, items)) }
                        }

                        is ChatAgentEvent.ToolExecuted -> Unit

                        // 执行回执并入 Finished 展平气泡
                        is ChatAgentEvent.Finished -> {
                            // R-T4：逐批产出确认气泡，最终答复/工具记录一条 TEXT；回执互不覆盖
                            val bubbles = mutableListOf<CompanionMessage>()
                            submittedConfirms.forEach { b ->
                                bubbles += buildAssistantBubbles(text = "", confirmItems = b.items, records = emptyList())
                            }
                            bubbles += buildAssistantBubbles(text = event.text, confirmItems = emptyList(), records = event.toolCalls)
                            val ided = bubbles.map { it.copy(id = nextId()) } // 统一分配稳定 id，避免 LazyColumn 撞键（问候=1）
                            HmpLog.i(LogTag.AgentChat) { "💬 chat finished: bubbles=${ided.size} terminated=${event.terminatedBy} text=${event.text.take(119)}…" }
                            // 资源文案气泡（textRes）无已解析文本，不落库（理由同 Failed 分支）
                            ided.filter { it.text.isNotBlank() }
                                .forEach { persist("agent", it.text, it.renderHint) }
                            _uiState.update {
                                it.copy(
                                    running = false,
                                    runningHint = null,
                                    pendingConfirm = null,
                                    messages = it.messages + ided,
                                )
                            }
                            submittedConfirms.clear()
                            gatewayBridge = null
                        }

                        is ChatAgentEvent.Failed -> {
                            HmpLog.w(LogTag.AgentChat) { "💬 chat failed: ${event.message}" }
                            _uiState.update {
                                it.copy(
                                    running = false,
                                    runningHint = null,
                                    pendingConfirm = null,
                                    messages = it.messages + CompanionMessage(
                                        id = nextId(),
                                        fromUser = false,
                                        textRes = Res.string.agent_chat_error_offline.asUiText(),
                                    ),
                                )
                            }
                            // 错误气泡是资源文案，不再落库：库里只能存已解析的字符串，
                            // 存下去等于把语言冻结在失败那一刻（下次读出来仍是旧语言）。
                            submittedConfirms.clear()
                            gatewayBridge = null
                        }

                        is ChatAgentEvent.RadioStarted -> {
                            val summaryMsg = CompanionMessage(
                                id = nextId(),
                                fromUser = false,
                                renderHint = CompanionRenderHint.TEXT,
                                text = event.summary,
                            )
                            val songlistMsg = CompanionMessage(
                                id = nextId(),
                                fromUser = false,
                                renderHint = CompanionRenderHint.SONGLIST,
                                songs = event.songs,
                            )
                            listOf(summaryMsg, songlistMsg).forEach { persist("agent", it.text, it.renderHint) }
                            _uiState.update { it.copy(messages = it.messages + summaryMsg + songlistMsg) }
                        }

                        is ChatAgentEvent.RadioStateChanged -> {
                            HmpLog.i(LogTag.AgentChat) { "💬 radio state changed: playing=${event.isPlaying} tracks=${event.trackCount}" }
                        }
                    }
                }
            } finally {
                // 兜底清理：正常 / 异常 / 取消都把运行态与确认态一起复位。
                // 只复位 running 会留下 pendingConfirm 而 gatewayBridge 已置空 ——
                // 用户点「照做」变成空操作，且输入一直被判为未提交确认卡阻塞。
                _uiState.update { it.copy(running = false, runningHint = null, pendingConfirm = null) }
                submittedConfirms.clear()
                gatewayBridge = null
            }
        }
    }

    private fun nextId(): Long = idCounter++

    private fun pickRunningHint(messages: Int): UiText = RUNNING_HINTS[(messages % RUNNING_HINTS.size).coerceIn(RUNNING_HINTS.indices)]

    companion object {
        private const val SESSION_LOAD_LIMIT = 50
        private val RUNNING_HINTS = listOf(
            Res.string.agent_chat_hint_scanning.asUiText(),
            Res.string.agent_chat_hint_thinking.asUiText(),
            Res.string.agent_chat_hint_playlist.asUiText(),
            Res.string.agent_chat_hint_wait.asUiText(),
        )
        private fun hintFromStore(hint: String?): CompanionRenderHint = hint?.uppercase()?.let { s -> CompanionRenderHint.entries.firstOrNull { it.name == s } }
            ?: CompanionRenderHint.TEXT
    }
}
