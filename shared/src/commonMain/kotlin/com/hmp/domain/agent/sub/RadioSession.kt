package com.hmp.domain.agent.sub

import co.touchlab.kermit.Logger
import com.hmp.domain.agent.port.DayPart
import com.hmp.domain.agent.port.LlmMessage
import com.hmp.domain.agent.port.PauseEvent
import com.hmp.domain.agent.port.TrackOutcome
import com.hmp.domain.agent.port.TrackSettledEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

/** 触发原因。只用于日志与审计，不参与判断。 */
enum class RadioTriggerCause { SETTLED, QUEUE_LOW }

/** 模型唯一能选的三个动作。 */
enum class RadioAction { NONE, APPEND, REPLACE }

/**
 * 一次判断的产物。
 *
 * [reason] 是主播写给**节目档案**的编排思路（spec §7.2）：
 * 进档案（之后每轮可见，作为工作记忆）、上 RadioCard `lastAdjust`（§3.3 2026-09-13 决议）；
 * 但仍是结构化 JSON 里的可枚举字段，不允许拼接成面向用户的自由文案。
 */
data class RadioVerdict(
    val action: RadioAction,
    val cause: RadioTriggerCause,
    val musicIds: List<Long> = emptyList(),
    val appendCount: Int = 0,
    val reason: String? = null,
    /** 与 musicIds 一一对应的每首歌主播按语（模型没给就为空，落地时回退占位文案） */
    val whys: List<String> = emptyList(),
)

/** 队列条目（id + 标题）：节目档案里要给模型看 id（否则 replace 只能编 id）。 */
data class RadioQueueEntry(
    val musicId: Long,
    val title: String,
)

/** 判断时附加的**系统状态**（不属于用户行为，因此不进记录）。 */
data class RadioContextSnapshot(
    /** 当前在播曲目 —— 用于约束「不许动它」 */
    val playingTitle: String?,
    val playingMusicId: Long?,
    val queueRemaining: Int,
    /** 当前播放曲之后的曲目（带 id，限量避免 token 膨胀）。标题视图取 [upcoming] 的 title 即可。 */
    val upcoming: List<RadioQueueEntry> = emptyList(),
)

/**
 * 电台决策内核（`docs/7_x/agent-radio-spec.md` §7）。
 *
 * **一次电台 = 一段对话**：点开即开一段 `messages`，关闭即丢弃（C2 只做会话内）。
 * 模型在对话中记得住自己说过什么、做过什么。
 *
 * 它是一根管道，不是一台裁判机 —— 这里没有权重、没有衰减、没有阈值表：
 * - 记录只记**事实**（怎么结束的 + 播了多久），不推断「喜不喜欢」
 * - 判断全部交给模型
 * - [judge] 失败 / 输出 none → **沉默**（沉默是默认答案）
 *
 * 串行保证：单消费者协程。判断 / 执行进行中新到的事件只会排队，不会重入。
 *
 * @param judge 送模型判断。入参是**完整消息列表**（含历史），返回原始文本；null = 调用失败
 * @param onVerdict 执行判定结果（由调用方落队列）
 * @param fallbackRefill 队列见底且模型没给动作时的本地补歌（spec §7.5 D2）
 */
/** 进程内单调时钟原点 —— 复用窗口与会话都用它，保证时基一致。 */
private val monotonicOrigin = TimeSource.Monotonic.markNow()

/** 单调时钟毫秒（进程启动至今）。 */
internal fun nowMonotonicMs(): Long = monotonicOrigin.elapsedNow().inWholeMilliseconds

/** 节目档案里「编排思路」最多保留几条（更早的被挤出 = 档案也做轻度遗忘）。 */
private const val INTENT_KEEP = 5

/** 「之后是」速览只给最近几首标题；完整队列构成（带 id）由节目档案承载。 */
private const val UPCOMING_PROSE = 8

/**
 * 上一段电台对话的快照 —— **仅存于内存，不落盘**。
 *
 * 关闭电台现在只暂停、不清播放列表，所以对话内容在重开时往往仍然有效：
 * 队列没变、情境没变，模型记得住上一档聊到哪。
 *
 * 这是「延迟丢弃」而不是长期记忆 —— 情境换代（时段/日期变了）或队列已被换掉就作废，
 * 也从不写入任何持久化存储，因此不违反 C2（只做会话内、不写长期偏好）。
 */
data class RadioConversation(
    val messages: List<LlmMessage>,
    /** 关闭时的队列，用于判断「重开时播放器里还是不是这些歌」 */
    val playlist: List<RadioTrack>,
    val playingId: Long?,
    /** 关闭时刻（单调时钟毫秒，与 [RadioSession] 同一时基由调用方保证）。仅作兜底，见 [REUSE_BACKSTOP_MS] */
    val closedAtMs: Long,
    /**
     * 关闭时的电台主题（用户种子）。
     * 重开时若用户给了**不同的** seed，说明是新的一档，不能复用旧对话。
     */
    val seed: String? = null,
    /**
     * 本档开播时的时段（WallClock DayPart）。
     * 复用判断用「情境换代」替代死板的时长窗口：时段变了 = 另一场节目（早班/夜班）。
     */
    val dayPart: DayPart? = null,
)

/**
 * 复用兜底窗口：距离关闭超过这么久，无论环境多完整都当作新的一档。
 * 主判断是**情境**（队列指纹 / 当前曲目 / seed / DayPart），时间只兜"隔了好几天"的底。
 */
const val REUSE_BACKSTOP_MS = 12 * 60 * 60 * 1000L

/** 电台决策与观测的统一日志 tag —— 真机追踪时 `adb logcat -s RadioTrace` 即可看完整时间线。 */
const val RADIO_TRACE_TAG = "RadioTrace"

class RadioSession(
    private val targetCount: Int = 12,
    /** 连跳合并窗口：等一下看是不是连跳，而不是"攒够 N 条" */
    private val mergeWindowMs: Long = 2_500L,
    /**
     * 对话历史保留的轮数；超出后最早的轮次丢弃。
     * 收听台账每轮**全量**携带（不依赖历史存事实），历史只负责对话质感——
     * 模型自己最近几轮的判定与理由。台账轻、候选池重，窗口收窄才能省下重复候选块的上下文。
     */
    private val historyKeepTurns: Int = 4,
    private val clock: () -> Long = { 0L },
    /** 本档主题（用户种子），进节目档案；null = 自动电台 */
    private val theme: String? = null,
    private val judge: suspend (List<LlmMessage>) -> String?,
    /**
     * 执行判定结果（由调用方落队列）。
     * 入参 [Int] 是**提问时刻的世代快照**（[generation]），执行前由调用方比对：
     * 返回 false = 已过期被丢弃（期间被关闭/暂停），true = 已执行。
     */
    private val onVerdict: suspend (generation: Int, RadioVerdict) -> Boolean,
    private val fallbackRefill: suspend () -> Unit = {},
    /** 每轮提问时实时取世代 —— per-turn 快照：只有"该轮在途期间"发生的关闭/暂停才作废结果 */
    private val generation: () -> Int = { 0 },
    /**
     * 曲库候选池（每轮重取）。返回渲染好的文本块（可为空串 = 本轮不给）。
     * replace/append 的 id 只能从这里挑 —— 不给 id 来源模型只能编幻觉 id。
     */
    private val candidates: suspend () -> String = { "" },
    /**
     * 系统状态快照。**必须是 suspend**：真机验证发现靠自己维护「已播几首」会失真
     * （换批走 AGENT_INTERNAL，不 emit 切歌事件，计数永远不涨，模型拿到的是过期的在播曲目）。
     * 在播这一项要以播放器真实状态为准，不能靠推算。
     */
    private val snapshot: suspend () -> RadioContextSnapshot = { RadioContextSnapshot(null, null, 0) },
    private val onTurn: (RadioVerdict?) -> Unit = {},
) {
    private sealed interface Trigger {
        data class Settled(val event: TrackSettledEvent) : Trigger
        data class Paused(val event: PauseEvent) : Trigger
        data class QueueLow(val remaining: Int) : Trigger
    }

    private val inbox = Channel<Trigger>(Channel.UNLIMITED)
    private var job: Job? = null

    private val settled = mutableListOf<TrackSettledEvent>()
    private val pauses = mutableListOf<PauseEvent>()
    private val executed = mutableListOf<String>()
    /** 节目档案里的「编排思路」：每轮判定的 reason + 开场编排的 reason（最多留最近几条） */
    private val intents = mutableListOf<String>()
    private val messages = mutableListOf<LlmMessage>()
    private var originMs: Long? = null
    private var sentSettled = 0
    private var sentPauses = 0
    private var turnIndex = 0

    val isRunning: Boolean get() = job?.isActive == true

    /** 供测试 / 审计观察当前对话。 */
    fun messagesSnapshot(): List<LlmMessage> = messages.toList()

    /** 供测试 / 审计观察事实账本里的暂停条数（验证瞬时暂停是否被上游滤掉）。 */
    internal fun recordedPauseCount(): Int = pauses.size

    /** 开启对话：把 system 入栈。**不发起判断** —— Start 的选歌由 Start 流程自己做。 */
    fun begin() {
        messages.clear()
        messages += LlmMessage(role = "system", content = systemPrompt(targetCount))
    }

    /**
     * 接着上一段对话继续（关闭电台只暂停、播放列表还在时）。
     *
     * 复用整段 messages，于是**不必再注入曲库视图、不必重问开播队列** ——
     * 那一次组装是整个流程里最贵的，省掉它正是复用最大的收益。
     */
    fun resumeFrom(previous: List<LlmMessage>) {
        messages.clear()
        if (previous.isEmpty()) {
            messages += LlmMessage(role = "system", content = systemPrompt(targetCount))
        } else {
            messages += previous
        }
    }

    /** 导出当前对话，供下次开电台时复用。 */
    fun exportMessages(): List<LlmMessage> = messages.toList()

    /** 记一首歌的结算（非阻塞）。 */
    fun onTrackSettled(event: TrackSettledEvent) {
        inbox.trySend(Trigger.Settled(event))
    }

    /** 记暂停 / 继续（不触发判断，但进上下文）。 */
    fun onPause(event: PauseEvent) {
        inbox.trySend(Trigger.Paused(event))
    }

    /** 队列见底（"值得看一眼"，不是判定）。 */
    fun onQueueLow(remaining: Int) {
        inbox.trySend(Trigger.QueueLow(remaining))
    }

    /** 记录一次已执行的动作（**只写事实，不写理由**）。 */
    fun noteExecuted(fact: String) {
        executed += fact
    }

    /** 记一条编排思路进节目档案（模型 reason / 开场编排思路）。空值忽略；只留最近几条。 */
    fun noteIntent(intent: String?) {
        if (intent.isNullOrBlank()) return
        intents += intent.trim()
        if (intents.size > INTENT_KEEP) {
            intents.removeAt(0)
        }
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    /** 关闭：在途事件处理完自然退出，不打断正在执行的回合。 */
    fun close() {
        inbox.close()
    }

    private suspend fun run() {
        Logger.i("Agent.Radio.Session") { "session started" }
        for (first in inbox) {
            if (originMs == null) originMs = first.atMs()

            // ── 合并窗口：连跳会被并成一批 ──
            val batch = mutableListOf(first)
            if (first !is Trigger.QueueLow) {
                while (true) {
                    val next = nextOrNull(mergeWindowMs) ?: break
                    batch += next
                    if (next is Trigger.QueueLow) break   // 见底优先处理
                }
            }

            // ── 入账：忠实记录，不做推断 ──
            var cause: RadioTriggerCause? = null
            var queueRemaining: Int? = null
            for (t in batch) when (t) {
                is Trigger.Settled -> { settled += t.event; cause = RadioTriggerCause.SETTLED }
                is Trigger.Paused -> pauses += t.event
                is Trigger.QueueLow -> { queueRemaining = t.remaining; cause = RadioTriggerCause.QUEUE_LOW }
            }

            // 只有暂停/继续 → 不判断（暂停不是表态）
            if (cause == null) continue

            val turn = ++turnIndex
            Logger.i(RADIO_TRACE_TAG) {
                "[TURN#$turn] 触发=${cause} 新结算=${batch.count { it is Trigger.Settled }} 条" +
                    "（合并窗口 ${mergeWindowMs}ms 内并批）"
            }

            val outcome = askModel(cause, queueRemaining, turn)
            onTurn(outcome?.second)

            if (outcome != null) {
                val (g, verdict) = outcome
                if (verdict.action != RadioAction.NONE) {
                    val applied = onVerdict(g, verdict)
                    if (applied) {
                        Logger.i(RADIO_TRACE_TAG) { "[TURN#$turn] 执行=${verdict.action}" }
                    } else {
                        Logger.i(RADIO_TRACE_TAG) {
                            "[TURN#$turn] 结果已过期（提问后被关闭/暂停）→ 丢弃 ${verdict.action}"
                        }
                    }
                } else if (cause == RadioTriggerCause.QUEUE_LOW) {
                    // D2：模型没给动作，但队列确实见底了 → 本地补歌保底出声
                    // 同样受世代守卫：提问后台已被关闭/暂停 → 不补
                    if (generation() == g) {
                        Logger.i(RADIO_TRACE_TAG) { "[TURN#$turn] 判定=none 但队列见底 → 本地补歌" }
                        runCatching { fallbackRefill() }
                            .onFailure { e ->
                                if (e is CancellationException) throw e
                                Logger.w("Agent.Radio.Session", e) { "fallback refill failed" }
                            }
                    } else {
                        Logger.i(RADIO_TRACE_TAG) {
                            "[TURN#$turn] 判定=none 但世代已变（期间被关闭/暂停）→ 不补歌"
                        }
                    }
                }
            } else if (cause == RadioTriggerCause.QUEUE_LOW) {
                // 判断调用本身失败（模型没看到）→ 事实已撤回，本地补歌保底出声
                Logger.i(RADIO_TRACE_TAG) { "[TURN#$turn] 判断失败但队列见底 → 本地补歌" }
                runCatching { fallbackRefill() }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        Logger.w("Agent.Radio.Session", e) { "fallback refill failed" }
                    }
            }

            trimHistory()
        }
        Logger.i("Agent.Radio.Session") { "session stopped" }
    }

    /** @return 提问时刻的世代 + 判定；null = 调用失败（已撤回 user 消息，事实留待下轮） */
    private suspend fun askModel(
        cause: RadioTriggerCause,
        queueRemaining: Int?,
        turn: Int,
    ): Pair<Int, RadioVerdict>? {
        val g = generation()   // per-turn 快照：此后到结果回来之间世代变了 = 该轮作废
        val snap = snapshot()   // suspend：问播放器当前到底在播什么
        val userContent = renderUserMessage(cause, queueRemaining, snap)
        // 完整上下文打 d 级（长），摘要打 i 级 —— 追踪时先 grep RadioTrace 看时间线，
        // 需要细看再放开 debug。
        Logger.d(RADIO_TRACE_TAG) { "[TURN#$turn] 上下文：\n$userContent" }
        Logger.i(RADIO_TRACE_TAG) {
            "[TURN#$turn] 问模型：事实 ${settled.size - sentSettled} 条 / 历史 ${messages.size} 条消息"
        }
        messages += LlmMessage(role = "user", content = userContent)

        val raw = try {
            judge(messages.toList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.w("Agent.Radio.Session", e) { "judge failed → silent" }
            null
        }

        // 调用失败：模型根本没看到，撤回这条 user 消息，事实下一轮再送
        if (raw.isNullOrBlank()) {
            messages.removeLastOrNull()
            Logger.w(RADIO_TRACE_TAG) { "[TURN#$turn] 模型没回（调用失败/无端点）→ 沉默，事实留到下一轮" }
            return null
        }

        // 模型看到了，事实记为已送达
        sentSettled = settled.size
        sentPauses = pauses.size
        Logger.i(RADIO_TRACE_TAG) { "[TURN#$turn] 模型回复：${raw.trim().take(200)}" }

        val parsed = parseVerdict(raw)
        if (parsed == null) {
            // 输出不可解析 = 沉默。补一条 assistant 回复，保持对话 user/assistant 交替。
            Logger.w("Agent.Radio.Session") { "unparsable verdict → silent: ${raw.take(120)}" }
            messages += LlmMessage(role = "assistant", content = """{"action":"none"}""")
            return null
        }

        messages += LlmMessage(role = "assistant", content = raw.trim())
        parsed.reason?.let { noteIntent(it) }
        Logger.i(RADIO_TRACE_TAG) {
            "[TURN#$turn] 判定=${parsed.action}" +
                (if (parsed.action == RadioAction.REPLACE) " (${parsed.musicIds.size} 首)" else "") +
                " reason=${parsed.reason?.take(60)}"
        }
        return g to RadioVerdict(
            action = parsed.action,
            cause = cause,
            musicIds = parsed.musicIds,
            appendCount = parsed.count,
            reason = parsed.reason,
            whys = parsed.whys,
        )
    }

    /**
     * 渲染本轮 user 消息：**增量事实 + 节目档案 + 曲库候选池 + 实时状态**（spec §7.2）。
     *
     * 旧事实也在台账里（全量携带，★ 标新增），但**节目档案每轮重渲染**——
     * 历史只保最近几轮的对话质感，主题/队列构成/已执行动作/编排思路沉在档案里，裁历史不丢认知。
     */
    private suspend fun renderUserMessage(
        cause: RadioTriggerCause,
        queueRemaining: Int?,
        snap: RadioContextSnapshot,
    ): String {
        return buildString {
            // 完整收听台账：一档节目播的歌就几十首，一条事实一行，全量携带成本可忽略；
            // 却让模型始终拥有整档的行为画像（连跳模式/听完的歌不会因历史裁剪失忆）。
            // ★ 标出本轮新增 —— 旧事实在台账里，新信号一眼可辨。
            appendLine("## 本档收听台账（开播以来累计，★ 为本轮新增）")
            if (settled.isEmpty()) {
                appendLine("（暂无结算）")
            } else {
                settled.forEachIndexed { i, e ->
                    val mark = if (i >= sentSettled) "★" else " "
                    appendLine("-$mark ${e.renderFact(originMs ?: e.atMs)}")
                }
            }
            if (pauses.isNotEmpty()) {
                val last = if (pauses.last().resumed) "继续" else "暂停"
                appendLine("- 本档累计暂停/继续 ${pauses.size} 次（最近一次：$last）")
            }

            appendLine()
            appendLine("## 当前状态")
            appendLine("- 在播：《${snap.playingTitle ?: "无"}》——**这一首不可替换，必须放完**")
            appendLine("- 队列剩余：${queueRemaining ?: snap.queueRemaining} 首")

            appendLine()
            appendLine("## 节目档案（你的工作记忆，每轮都在）")
            appendLine("- 主题：${theme?.takeIf { it.isNotBlank() } ?: "自动（无明确主题）"}")
            if (snap.upcoming.isEmpty()) {
                appendLine("- 当前队列（在播之后）：（空，该续上了）")
            } else {
                appendLine("- 当前队列（在播之后）：${snap.upcoming.joinToString("、") { "${it.musicId}|${it.title}" }}")
                appendLine("- 之后是：${snap.upcoming.take(UPCOMING_PROSE).joinToString("、") { it.title }}")
            }
            if (executed.isNotEmpty()) {
                executed.forEach { appendLine("- 已执行：$it") }
            }
            if (intents.isNotEmpty()) {
                intents.forEach { appendLine("- 编排思路：$it") }
            }

            val pool = candidates()
            if (pool.isNotBlank()) {
                appendLine()
                appendLine(pool.trimEnd())
            }

            appendLine()
            appendLine(
                when (cause) {
                    RadioTriggerCause.QUEUE_LOW -> "队列快见底了。选一个动作。"
                    RadioTriggerCause.SETTLED -> "选一个动作。判不准就选 none。"
                }
            )
        }
    }

    /** 历史超长时丢弃最早的轮次（system 保留；收听台账每轮全量重发，裁历史不失事实）。 */
    private fun trimHistory() {
        val keep = historyKeepTurns * 2
        if (messages.size > keep + 1) {
            val head = messages.first()
            val tail = messages.takeLast(keep)
            messages.clear()
            messages += head
            messages += tail
        }
    }

    /** 取下一条；超时、通道关闭或任何异常都返回 null（超时 = 静默满窗，该送判断了）。 */
    private suspend fun nextOrNull(timeoutMs: Long): Trigger? = try {
        withTimeoutOrNull(timeoutMs) { inbox.receive() }
    } catch (e: CancellationException) {
        throw e   // 协程取消不吞
    } catch (_: Throwable) {
        // close() 时 receive() 会抛 ClosedReceiveChannelException —— 那是正常退出路径，不是错误
        null
    }

    private fun Trigger.atMs(): Long = when (this) {
        is Trigger.Settled -> event.atMs
        is Trigger.Paused -> event.atMs
        is Trigger.QueueLow -> clock()
    }

    /** 解析模型输出；不可解析返回 null（= 沉默）。 */
    internal fun parseVerdict(raw: String): ParsedVerdict? {
        val body = jsonBody(raw) ?: return null
        return try {
            val dto = json.decodeFromString<VerdictDto>(body)
            ParsedVerdict(
                action = when (dto.action.trim().lowercase()) {
                    "append" -> RadioAction.APPEND
                    "replace" -> RadioAction.REPLACE
                    "none" -> RadioAction.NONE
                    else -> return null
                },
                musicIds = dto.musicIds,
                count = dto.count,
                reason = dto.reason,
                whys = dto.whys,
            )
        } catch (_: Throwable) {
            null
        }
    }

    internal data class ParsedVerdict(
        val action: RadioAction,
        val musicIds: List<Long>,
        val count: Int,
        val reason: String?,
        /** 与 musicIds 一一对应的每首歌主播按语（模型没给就为空） */
        val whys: List<String> = emptyList(),
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * 从模型输出里抠出 `musicIds` 数组（Start 定队列时用）。
         *
         * 只认 JSON，不认曲名 —— 曲名会幻觉、会错字、会重名，id 才能逐条校验。
         * 解析不出返回 null（调用方据此保留本地队列）。
         */
        fun parseMusicIds(raw: String): List<Long>? {
            val text = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            val body = if (start >= 0 && end > start) text.substring(start, end + 1) else text
            return try {
                json.decodeFromString<QueueDto>(body).musicIds.takeIf { it.isNotEmpty() }
            } catch (_: Throwable) {
                null
            }
        }

        @Serializable
        private data class QueueDto(val musicIds: List<Long> = emptyList())

        @Serializable
        private data class VerdictDto(
            val action: String = "none",
            val musicIds: List<Long> = emptyList(),
            val count: Int = 0,
            val reason: String? = null,
            /** 与 musicIds 一一对应的每首歌主播按语（可缺省——旧格式/模型偷懒都不炸） */
            val whys: List<String> = emptyList(),
            /** 开播特有：为正在播的电台起点写一句按语（接播路径才需要，可缺省） */
            val seedWhy: String? = null,
        )

        /** 从原始回复中抠出 JSON 体（容忍 markdown 代码块与前后闲话）。 */
        private fun jsonBody(raw: String): String? {
            val text = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            return if (start >= 0 && end > start) text.substring(start, end + 1) else null
        }

        /** 开播编排回复里的 reason（开场编排思路，进节目档案）。 */
        internal fun parseOpeningReason(raw: String): String? = runCatching {
            jsonBody(raw)?.let { json.decodeFromString<VerdictDto>(it).reason }
        }.getOrNull()?.takeIf { it.isNotBlank() }

        /** 开播/换批回复里的每首歌按语（与 musicIds 按位置对应；模型没给/解析失败 = 空表）。 */
        internal fun parseOpeningWhys(raw: String): List<String> = runCatching {
            jsonBody(raw)?.let { json.decodeFromString<VerdictDto>(it).whys }
        }.getOrNull().orEmpty().map { it.trim() }.filter { it.isNotBlank() }

        /** 开播回复里为在播曲（电台起点）写的按语（模型没给/解析失败 = null）。 */
        internal fun parseOpeningSeedWhy(raw: String): String? = runCatching {
            jsonBody(raw)?.let { json.decodeFromString<VerdictDto>(it).seedWhy }
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }

        fun systemPrompt(targetCount: Int = 12): String = """
你是这个音乐电台的主播。从听众点开电台那一刻起，这一场节目由你负责：
感知他的每一个操作和播放器里的动静，把播放列表编排出让他愿意一直听下去的样子。

你掌控的唯一资源：当前正在播放那首之后的队列。
你不可触碰的：正在播放的那首（必须放完）；播放器的播放/暂停不归你管。

你的动作只有三种，必须且只能选一种：
- none    ：不动。主播的默认姿态——节目正在流动，没有充分理由不打断编排。
- append  ：往队列末尾续歌（节目按原方向走，只是加长）。
- replace ：整体替换当前曲之后的队列（承认这一段不对味，换个段落）。

分寸感（你自己的直播经验，最终由你自己权衡）：
- 刚起播就被切走是明确的负反馈；快听完才换是自然流动
- 连切好几首，说明你编排的这一段不对味——该换了，换就换彻底
- 暂停/继续是生活噪音，不是表态；别因为几次暂停就推翻编排
- 队列见底前主动续上，不让电台冷场

硬性约束：
- 你不会跟听众说话，不生成任何面向用户的文案，不要寒暄，不要解释。
- musicIds 只能从上下文给出的曲库/候选里挑，**绝不编造不存在的 id**。
- 只输出 JSON，不要 markdown 代码块，不要任何多余文字。

输出格式：
{"action":"none|append|replace","musicIds":[123,456],"count":0,"reason":"…","whys":["…","…"]}
- action=none 时 musicIds 与 count 留空
- action=replace 时 musicIds 给出**当前播放曲之后**的完整新队列，最多 $targetCount 首
- action=append 时 count 给出要补几首
- whys 与 musicIds **一一对应、长度一致**：每首歌一句主播按语（不超过 16 字），写给听众看——这首歌为什么排在这里、它和前后曲目怎么接。这是电台卡片上唯一展示你的话的地方，好好写。
- 开播且上下文里标注了「正在播（电台起点）」时，额外给 seedWhy：为**这首正在播的歌**写一句按语（不超过 16 字），同样展示给听众
- reason 是写给节目档案的编排思路——你之后每一轮都会看到它，用它记住"我为什么这么排"
""".trimIndent()
    }
}

private fun TrackSettledEvent.renderFact(originMs: Long): String {
    val at = ((atMs - originMs) / 60_000L).coerceAtLeast(0)
    val pct = (playedRatio * 100).toInt()
    val verb = when (outcome) {
        TrackOutcome.COMPLETED -> "听完"
        TrackOutcome.SKIPPED_NEXT -> "下一曲切走"
        TrackOutcome.SKIPPED_PREV -> "上一曲切走"
        TrackOutcome.SWITCHED_AWAY -> "切去别的"
        TrackOutcome.STOPPED -> "停止"
    }
    return "第${at}分钟 $verb《$title》· 已播 $pct%（${playedMs / 1000}s / ${totalMs / 1000}s）"
}
