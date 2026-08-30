package com.hearablemusic.player.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearablemusic.player.ui.platform.currentTimeMillis
import com.hmp.domain.agent.engine.AgentLog
import com.hmp.domain.agent.funnel.CommandLexicon
import com.hmp.domain.agent.funnel.FunnelResult
import com.hmp.domain.agent.port.AgentMessageStore
import com.hmp.domain.agent.port.PlaybackCommand
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.port.StoredAgentMessage
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val runningHint: String = "",
    val pendingConfirm: ConfirmCardState? = null,
    val input: String = "",
)

/**
 * M5-T1 ChatViewModel —— 对话页唯一状态源。
 *
 * 职责：问候区/正在听（由 UI 接播放控制器，VM 不持有播放状态）/任务进行条/确认聚合/执行回执。
 * 引擎侧经 [ChatAgentGateway] 接缝驱动；[ChatAgentEvent] → [CompanionMessage] 展平在 [buildAssistantBubbles]。
 */
class ChatViewModel(
    private val gateway: ChatAgentGateway,
    private val userSettings: UserSettingsUseCase,
    private val playbackCommandPort: PlaybackCommandPort,
    private val agentMessageStore: AgentMessageStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState(messages = listOf(greeting())))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var idCounter = 1000L
    private var job: Job? = null
    /** 本轮已提交的确认批次（R-T4：单轮多次确认时逐批累积，回执不互相覆盖）。 */
    private val submittedConfirms = mutableListOf<ConfirmCardState>()

    // ── R-T5 会话持久化：session_id + 历史加载 + 增量写入 ──
    private var sessionId: String? = null

    init {
        viewModelScope.launch {
            val sid = agentMessageStore.currentOrNewSessionId()
            sessionId = sid
            val history = agentMessageStore.loadSession(sid, SESSION_LOAD_LIMIT)
            AgentLog.i("session resume: sid=$sid history=${history.size}")
            if (history.isNotEmpty()) {
                _state.update {
                    it.copy(messages = history.map { m ->
                        CompanionMessage(
                            id = nextId(),
                            fromUser = m.role == "user",
                            // 历史里嵌套的 Markdown 标记一并清洗（Compose 不解析 ** / ` / *）
                            text = cleanAgentMarkdown(m.content.orEmpty()),
                            renderHint = hintFromStore(m.renderHint),
                        )
                    })
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
                )
            )
        }
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    /** 发送：追加用户气泡 → 启动一轮引擎对话。 */
    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(input = "") }
        sendText(text)
    }

    /** 外部入口带入（M1 锚点 → M5 对话）：不经过输入框，直接作为用户消息发送。 */
    fun sendPreloaded(text: String) = sendText(text.trim())

    fun cancel() {
        job?.cancel()
        job = null
    }

    // ── 确认卡交互 ──

    fun toggleConfirmItem(itemId: String) {
        val p = _state.value.pendingConfirm ?: return
        if (p.submitted) return
        val items = p.items.map {
            if (it.id == itemId) it.copy(selected = !it.selected) else it
        }
        _state.update { it.copy(pendingConfirm = p.copy(items = items)) }
    }

    /** 「照做」：把当前勾选提交给引擎，恢复挂起的确认批次（累积到 submittedConfirms）。 */
    fun submitConfirm() {
        val p = _state.value.pendingConfirm ?: return
        if (p.submitted) return
        submittedConfirms += p
        AgentLog.i("confirm 照做: turn=${p.turnId} selected=${p.items.count { it.selected }}/${p.items.size}")
        val approvals = p.items.map { it.selected }
        gatewayBridge?.submit(p.turnId, approvals)
        _state.update { it.copy(pendingConfirm = p.copy(submitted = true)) }
    }

    /** 「跳过全部」：整批否决（拒绝纪律：不纠缠）。 */
    fun skipConfirm() {
        val p = _state.value.pendingConfirm ?: return
        if (p.submitted) return
        val rejected = p.copy(items = p.items.map { it.copy(selected = false) })
        submittedConfirms += rejected
        AgentLog.i("confirm 跳过全部: turn=${p.turnId}")
        val approvals = List(p.items.size) { false }
        gatewayBridge?.submit(p.turnId, approvals)
        _state.update { it.copy(pendingConfirm = rejected.copy(submitted = true)) }
    }

    private var gatewayBridge: ConfirmBridge? = null

    /** 内部发送入口：空/运行中/未决确认卡未决时拒绝派发；追加用户气泡后按两级漏斗分发。 */
    private fun sendText(text: String) {
        if (text.isBlank() || _state.value.running) return
        val pending = _state.value.pendingConfirm
        if (pending != null && !pending.submitted) return // 未提交的确认卡未决前不派新一轮
        _state.update {
            it.copy(messages = it.messages + CompanionMessage(id = nextId(), fromUser = true, text = text))
        }
        persist("user", text, CompanionRenderHint.TEXT)
        when (val decision = CommandLexicon.classify(text)) {
            is FunnelResult.Direct -> {
                AgentLog.i("chat send (funnel=Direct): cmd=${decision.command}")
                launchDirect(decision.command)
            }
            FunnelResult.Upgrade -> {
                AgentLog.i("chat send (funnel=Upgrade): $text")
                launchRun(text)
            }
            FunnelResult.Pass -> {
                AgentLog.d("chat send (funnel=Pass): $text")
                launchRun(text)
            }
        }
    }

    /** R-T2 第一级：高频指令零 token 直执行（不消耗 LLM）。 */
    private fun launchDirect(command: PlaybackCommand) {
        viewModelScope.launch {
            val (_, msg) = playbackCommandPort.execute(command)
            AgentLog.i("direct command result: $msg")
            _state.update {
                it.copy(messages = it.messages + CompanionMessage(id = nextId(), fromUser = false, text = msg))
            }
            persist("agent", msg, CompanionRenderHint.TEXT)
        }
    }

    private fun launchRun(input: String) {
        if (_state.value.running) return
        val bridge = ConfirmBridge()
        gatewayBridge = bridge
        job = viewModelScope.launch {
            _state.update {
                it.copy(running = true, runningHint = pickRunningHint(it.messages.size))
            }
            try {
                val config = userSettings.getActiveAiConfig()
                gateway.run(input, config, bridge).collect { event ->
                    when (event) {
                        is ChatAgentEvent.NeedConfirm -> {
                            // R-T4 防御：不覆盖尚未提交的确认批次（引擎循环是顺序挂起的，正常不会触发）
                            val current = _state.value.pendingConfirm
                            if (current != null && !current.submitted) return@collect
                            val items = event.requests.map { r ->
                                ConfirmItem(id = r.toolName + "#" + r.argsSummary.hashCode(),
                                    toolName = r.toolName, argsSummary = r.argsSummary, selected = true)
                            }
                            _state.update { it.copy(pendingConfirm = ConfirmCardState(event.turnId, items)) }
                        }
                        is ChatAgentEvent.ToolExecuted -> Unit // 执行回执并入 Finished 展平气泡
                        is ChatAgentEvent.Finished -> {
                            // R-T4：逐批产出确认气泡，最终答复/工具记录一条 TEXT；回执互不覆盖
                            val bubbles = mutableListOf<CompanionMessage>()
                            submittedConfirms.forEach { b ->
                                bubbles += buildAssistantBubbles(text = "", confirmItems = b.items, records = emptyList())
                            }
                            bubbles += buildAssistantBubbles(text = event.text, confirmItems = emptyList(), records = event.toolCalls)
                            val ided = bubbles.map { it.copy(id = nextId()) } // 统一分配稳定 id，避免 LazyColumn 撞键（问候=1）
                            AgentLog.i("chat finished: bubbles=${ided.size} terminated=${event.terminatedBy} text=${AgentLog.truncate(event.text)}")
                            ided.forEach { persist("agent", it.text, it.renderHint) }
                            _state.update {
                                it.copy(
                                    running = false,
                                    runningHint = "",
                                    pendingConfirm = null,
                                    messages = it.messages + ided,
                                )
                            }
                            submittedConfirms.clear()
                            gatewayBridge = null
                        }
                        is ChatAgentEvent.Failed -> {
                            val err = "（暂时没连上伙伴，稍后再试）"
                            AgentLog.w("chat failed: ${event.message}")
                            _state.update {
                                it.copy(
                                    running = false,
                                    runningHint = "",
                                    pendingConfirm = null,
                                    messages = it.messages + CompanionMessage(
                                        id = nextId(), fromUser = false, text = err,
                                    ),
                                )
                            }
                            persist("agent", err, CompanionRenderHint.TEXT)
                            submittedConfirms.clear()
                            gatewayBridge = null
                        }
                    }
                }
            } finally {
                // 兜底清理：无论正常/异常，运行态复位（正常终态分支已复位，此处幂等）
                _state.update { if (it.running) it.copy(running = false, runningHint = "") else it }
                gatewayBridge = null
            }
        }
    }

    private fun nextId(): Long = idCounter++

    private fun pickRunningHint(messages: Int): String =
        RUNNING_HINTS[(messages % RUNNING_HINTS.size).coerceIn(RUNNING_HINTS.indices)]

    companion object {
        private const val SESSION_LOAD_LIMIT = 50
        private val RUNNING_HINTS = listOf(
            "正在翻你的曲库…", "让我想想…", "正在整理歌单…", "再等一下…",
        )
        private fun greeting() = CompanionMessage(
            id = 1,
            fromUser = false,
            text = "嗨，我是你的听歌伙伴。想找某首歌、整理歌单，还是问问你的听歌排行？直接告诉我就行。",
        )
        private fun hintFromStore(hint: String?): CompanionRenderHint =
            hint?.uppercase()?.let { s -> CompanionRenderHint.entries.firstOrNull { it.name == s } }
                ?: CompanionRenderHint.TEXT
    }
}