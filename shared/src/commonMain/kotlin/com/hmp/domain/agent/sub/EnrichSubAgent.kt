package com.hmp.domain.agent.sub

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.port.AuditLogPort
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.policy.AgentPolicy
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.runtime.AgentContextBudget
import com.hmp.domain.agent.runtime.AgentRunState
import com.hmp.domain.agent.runtime.LlmCallExecutor
import com.hmp.domain.agent.runtime.StopSignal
import com.hmp.domain.agent.runtime.ToolCallExecutor
import com.hmp.domain.agent.runtime.ToolRegistryView
import com.hmp.domain.agent.tool.ToolRegistry
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * T3 EnrichSubAgent：纯被动执行器（设计铁则 F1-F6）。
 *
 * - F1：只有执行权，无决策权。由 Master 创建设派活/销毁。
 * - F2：绑定独立 LlmTransport + AgentContextBudget(32K)。
 * - F3：暂停/恢复由 AgentScheduler 触发（priority=3）。
 * - F4：Master 派活/验收循环不用 LLM。
 * - F5：system prompt 由 Master 注入，无自演化。
 * - F6：不知道富化进度/电量/网络——只处理 Master 派发的批次。
 *
 * ═══ 权限体系接入（v7.1 P0-② 修复）═══
 * 旧版 DirectToolExecutor 裸跑，完全绕过 PolicyGuard。
 * 现在接入 ToolCallExecutor + AgentPolicy.enrich()：
 *   - enrichPolicyConfig.trustLevel = SILENT(2) + alwaysAllow 全部自身可见工具名
 *   - PolicyGuard Phase0 alwaysAllow 命中 → AllowSilent（无需弹确认）
 *   - Phase1 Agent 身份门 ENRICH → maxLevel=1(NOTIFY)，CONFIRM/STRONG_CONFIRM 级工具自动 Deny
 *   - PolicyGuard.auditLog 记录每次裁决
 */
class EnrichSubAgent(
    /** Agent 唯一标识 */
    agentId: String = "enrich",
    /** 独立 LLM 上下文窗口（32K，轻量批量模型省 Token） */
    contextBudget: AgentContextBudget,
    /** 权限过滤后的工具视图（只有 library_* + song_*） */
    toolRegistryView: ToolRegistryView,
    /** 底层完整 ToolRegistry（ToolCallExecutor 需要 find + execute） */
    private val toolRegistry: ToolRegistry,
    /** Master 注入的执行手册（无自演化） */
    private var systemPrompt: String,
    /** 音乐库仓库（写富化结果用） */
    private val musicRepository: MusicRepository,
    /** 存在感总线（emit 进度） */
    private val presenceBus: PresenceBus? = null,
    /** LLM API 端点配置（Master 创建时注入，null 表示开发模式跳过 LLM 调用） */
    private val enrichConfig: AiEndpointConfig? = null,
    /** 批次配置 */
    private val maxBatchSize: Int = 20,
    /** LLM 调用温度（批量富化用低温度） */
    private val temperature: Float = 0.3f,
    /** 停止/暂停信号（SchedulerStopSignal 替换旧的 while(PAUSED)+delay 轮询） */
    private val stopSignal: StopSignal? = null,

    // ═══ P0-② 新增：权限体系接入 ═══
    /** 权限裁决器（三阶段裁决 Phase0→1→2） */
    private val policyGuard: PolicyGuard? = null,
    /** per-Agent 权限包（role=ENRICH + config） */
    private val agentPolicy: AgentPolicy? = null,
    /** 审计日志（PolicyGuard 记录裁决） */
    private val auditLog: AuditLogPort? = null,
) : SubAgent(agentId, contextBudget, toolRegistryView) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 唯一输入口：Master 派发到这里；capacity=10 保证 pause 期间不丢批次 */
    private val batchChannel = Channel<List<MusicInfo>>(capacity = 10)

    /** 当前批次 */
    @Volatile
    private var currentBatch: List<MusicInfo> = emptyList()

    /** 累计处理歌曲数 */
    @Volatile
    private var processedCount: Int = 0

    /** 当前进度（Master 验收查询用） */
    data class EnrichProgress(
        val processed: Int,
        val currentBatchSize: Int,
        val state: AgentRunState,
    )

    // ===== 对外接口（只有 Master / Scheduler 能调） =====

    /** Master 派发批次的唯一入口 */
    fun assignBatch(batch: List<MusicInfo>) {
        val sent = batchChannel.trySend(batch)
        if (sent.isFailure) {
            Logger.w("Agent.Enrich") { "[$agentId] batchChannel full, dropping ${batch.size} songs" }
        } else {
            Logger.i("Agent.Enrich") { "[$agentId] batch dispatched: ${batch.size} songs" }
        }
    }

    /** Master 更新注入的 system prompt（F5：Master 注入，Enrich 不自演化） */
    fun updateSystemPrompt(newPrompt: String) {
        Logger.i("Agent.Enrich") { "[$agentId] system prompt updated by Master" }
        systemPrompt = newPrompt
    }

    /** 查询当前进度（Master 验收用） */
    fun getProgress(): EnrichProgress = EnrichProgress(
        processed = processedCount,
        currentBatchSize = currentBatch.size,
        state = runState,
    )

    override suspend fun shutdown() {
        super.shutdown()
        batchChannel.close()
        Logger.i("Agent.Enrich") { "[$agentId] shutdown complete (processed=$processedCount total)" }
    }

    /** 手动暂停（Master LLM 通过 enrich_pause 工具调） */
    override suspend fun pause() {
        (stopSignal as? com.hmp.domain.agent.runtime.SchedulerStopSignal)?.onSchedulerPaused()
        runState = AgentRunState.PAUSED
        Logger.i("Agent.Enrich") { "[$agentId] manually paused" }
    }

    /** 手动恢复（Master LLM 通过 enrich_resume 工具调） */
    override suspend fun resume() {
        (stopSignal as? com.hmp.domain.agent.runtime.SchedulerStopSignal)?.onSchedulerResumed()
        runState = AgentRunState.RUNNING
        Logger.i("Agent.Enrich") { "[$agentId] manually resumed" }
    }

    // ===== 执行循环（F6：极简，不做任何决策） =====

    /**
     * Enrich 执行循环。
     *
     * 被动等待 → 收到批次 → 构建批次消息 → 调独立 LLM → 收集 toolCalls → 权限裁决 → 执行工具 → 写 DB → emit 进度。
     * 无任何决策逻辑——收到什么就处理什么，Scheduler pause 就挂起。
     *
     * 权限链路（v7.1）：
     *   ToolCallExecutor.batchDecideApprovals(agentPolicy, toolCalls)
     *     → Phase0 alwaysAllow（全部命中 → AllowSilent）
     *     → Phase1 Agent 身份门 CONFIRM/STRONG_CONFIRM 级 → Deny
     *     → 无 ConfirmGate → RequireConfirm 级自动 Deny（安全兜底）
     *   ToolCallExecutor.executeOne(trackMessages=false, messages=null)
     */
    override suspend fun runLoop() {
        Logger.i("Agent.Enrich") { "[$agentId] runLoop start (policyGuard=${policyGuard != null}, agentPolicy=${agentPolicy?.role})" }
        isActive = true
        runState = AgentRunState.RUNNING

        // ToolCallExecutor 无状态——构造一次复用（trackMessages=false，因为 Enrich 批次不累积 LLM history）
        val toolExecutor = ToolCallExecutor(
            registry = toolRegistry,
            policyGuard = policyGuard,
            auditLog = auditLog,
            trackMessages = false,  // 批次场景：不回传 tool result 给 LLM
        )

        while (isActive && scope.isActive) {
            // Scheduler pause → Mutex 真正挂起（零唤醒），旧 while(PAUSED)+delay(500) 已废弃
            stopSignal?.waitResume()
            if (!isActive) break

            // 熔断：token 配额耗尽或外部 shutdown
            if (stopSignal?.shouldSoftStop() == true) {
                Logger.i("Agent.Enrich") { "[$agentId] stopSignal.shouldSoftStop() → exiting runLoop" }
                break
            }

            // 阻塞等待 Master 派发的批次——绝不主动拉活
            val batch = try {
                batchChannel.receive()
            } catch (e: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                Logger.i("Agent.Enrich") { "[$agentId] batchChannel closed, exiting" }
                break
            }

            currentBatch = batch
            Logger.i("Agent.Enrich") { "[$agentId] received batch: ${batch.size} songs" }

            // ═══ 完整 LLM 执行链路（LlmCallExecutor 统一路径，与 MasterAgent/ReActLoop 复用）═══
            // 1. 构建批次消息（每批次 fresh start，不累积 history）
            val batchMessages = buildBatchMessages(batch)

            // 2. 调独立 LLM 实例（AgentContextBudget.llmClient 是 Enrich 专属 transport）
            if (enrichConfig != null) {
                val turn = LlmCallExecutor().call(
                    transport = contextBudget.llmClient,
                    config = enrichConfig,
                    messages = buildList {
                        add(LlmMessage(role = "system", content = systemPrompt))
                        addAll(batchMessages)
                    },
                    tools = toolRegistryView.llmSpecs,
                    temperature = temperature,
                )

                if (turn.failed) {
                    Logger.e("Agent.Enrich") { "[$agentId] LLM failed: ${turn.failedMessage}" }
                } else {
                    Logger.i("Agent.Enrich") { "[$agentId] LLM responded: ${turn.toolCalls.size} toolCalls for ${batch.size} songs" }

                    // ═══ 权限裁决 + 执行（v7.1 P0-②：接入 ToolCallExecutor，不再 DirectToolExecutor 裸跑）═══
                    if (turn.toolCalls.isNotEmpty()) {
                        val approvals = if (policyGuard != null && agentPolicy != null) {
                            // 完整权限链路：三阶段裁决 + 审计
                            toolExecutor.batchDecideApprovals(agentPolicy, turn.toolCalls)
                        } else {
                            // 无 policyGuard/agentPolicy → 旧行为：全自动通过（开发模式兜底）
                            Logger.w("Agent.Enrich") { "[$agentId] no policyGuard/agentPolicy → auto-approve all toolCalls (开发模式)" }
                            turn.toolCalls.associate { it.id to true }
                        }

                        for (tc in turn.toolCalls) {
                            val approved = approvals[tc.id] ?: false
                            if (!approved) {
                                Logger.w("Agent.Enrich") { "[$agentId] tool ${tc.name} denied by policy, skipping" }
                                continue
                            }
                            val record = toolExecutor.executeOne(tc, messages = null, approved = true)
                            Logger.d("Agent.Enrich") { "[$agentId] tool ${tc.name} result: success=${record.outcome}" }
                        }
                    }
                }
            } else {
                // 无 LLM config（开发模式 / 未配置 API Key）——跳过 LLM 调用
                Logger.w("Agent.Enrich") { "[$agentId] No AiEndpointConfig available, skipping LLM call (batch=${batch.size})" }
            }

            // 批次结束：清理 history，保证每个批次 fresh start（不泄漏前序批次的 messages）
            contextBudget.clearHistory()

            processedCount += batch.size
            presenceBus?.emit(com.hmp.domain.agent.infra.PresenceEvent.AgentProgress(
                agentId = agentId,
                processed = processedCount,
                total = batch.size,
            ))
            currentBatch = emptyList()
        }
    }

    /** 构建批次的 LLM messages（每首歌一行，带已有标签概览） */
    private fun buildBatchMessages(batch: List<MusicInfo>): List<LlmMessage> {
        val songsText = batch.joinToString("\n") { song ->
            "- [${song.music.id}] \"${song.music.title}\" by ${song.music.artist} (album=${song.music.album})"
        }
        return listOf(
            LlmMessage(
                role = "user",
                content = """
以下是需要富化的歌曲列表（${batch.size} 首）。
请为每首歌补充 AI 标签（genre 风格 / mood 情绪 / scenario 场景 最多各 1 个），调用 song_tag_ai_add 工具写入。
已有 USER 源标签不要覆盖。

$songsText
""".trimIndent(),
            )
        )
    }

    // ===== Enrich system prompt 常量（Master 注入，Enrich 不自演化） =====

    companion object {
        /** Enrich 默认 system prompt 模板（Master 根据 EnrichTask 填充参数后注入） */
        fun buildSystemPrompt(taskDescription: String, maxBatchSize: Int): String = """
你是一个音乐标签富化助手，负责给歌曲补充 AI 生成的标签。

执行规则：
1. 只处理 Master Agent 派发的当前批次歌曲，不要处理其他歌曲
2. 每首歌最多生成 3 个 AI 标签（风格 / 情绪 / 场景 各 1 个）
3. 调用 song_tag_ai_add 工具写入，source="LLM"，不要覆盖已有 USER 源标签
4. 当前批次大小上限：$maxBatchSize

任务描述：
$taskDescription
""".trimIndent()
    }
}

/** PresenceBus Agent 进度事件（简化版，完整事件体系在 T4 补齐） */
// 注：PresenceEvent.AgentProgress 是 T 阶段新增事件类型，定义在 PresenceBus.kt 中
