package com.hmp.log

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.hmp.platform.Synchronized
import com.hmp.platform.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kermit tag → 归一化 Agent 桶。未列出的 tag（UI/Data/Player/System 等）归为「框架」。
 * 只在 sink 处推导一次，打点调用点无需改动。
 */
private val TAG_TO_AGENT: Map<String, String> = mapOf(
    LogTag.AgentMaster.v to "Master",
    LogTag.AgentReActLoop.v to "Master",
    LogTag.AgentScheduler.v to "Master",
    LogTag.AgentTool.v to "Master",
    LogTag.AgentLlmCall.v to "Master",
    LogTag.AgentProfile.v to "Master",
    LogTag.AgentGateway.v to "Master",
    LogTag.AgentChat.v to "Master",
    LogTag.AgentPort.v to "Master",
    LogTag.AgentRadio.v to "Radio",
    LogTag.AgentEnrich.v to "Enrich",
    LogTag.AgentHello.v to "Hello",
    LogTag.AgentSub.v to "框架",
    LogTag.AgentContext.v to "框架",
)

/**
 * 看板/诊断 UI 读取的单条日志。
 * [agent] 为归一化后的「所属 Agent 桶」（Master/Radio/Enrich/Hello/框架），由 [TAG_TO_AGENT]
 * 从 Kermit tag 推导，供看板按 Agent 筛选/分组，不侵入任何打点调用点。
 */
data class LogEntry(
    val severity: Severity,
    val tag: String,
    val message: String,
    /** 递增序号，UI 用它做稳定的 Lazy key 与时间先后判断（不依赖平台时钟）。 */
    val sequence: Int,
    /** 归一化 Agent 桶；非 agent 子系统（UI/Data/Player…）归为「框架」。 */
    val agent: String = "",
)

/**
 * 内存环形日志缓冲 — 追加在 Kermit Logger 的 writer 链上。
 *
 * 作用：把 `HmpLog`/`Logger` 产出的日志同时写入一份**有界内存缓冲**，
 * 供 AgentMonitorScreen 等 UI 以 StateFlow 订阅回看最近 N 条（不需读 Logcat/OSLog）。
 *
 * 零侵入：只在 [com.hmp.initKermit]/[com.hmp.initKermitForIos] 里 `Logger.addLogWriter`，
 * 不碰任何日志调用点。
 *
 * 有界：环形覆盖，固定 [capacity]，不落盘、不引 kermit-io（延续极简纪律）。
 * 只保留最近 DEFAULT_CAPACITY 条；`logs` 对外永远是只读快照。
 *
 * 惯例：写 buffer 绝不抛异常、绝不影响主日志链路——失败即吞。
 */
class MemLogWriter(
    private val capacity: Int = DEFAULT_CAPACITY,
) : LogWriter() {

    private val ring = Array<LogEntry?>(capacity) { null }
    private var head: Int = 0
    private var size: Int = 0
    private var seq: Int = 0

    @Volatile
    private var cacheDirty = false
    private var cachedSnapshot: List<LogEntry> = emptyList()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val text = if (throwable != null) "$message\n$throwable" else message
        append(LogEntry(severity = severity, tag = tag, message = text, sequence = nextSeq(), agent = TAG_TO_AGENT[tag] ?: "框架"))
    }

    @Synchronized
    private fun nextSeq(): Int = ++seq

    @Synchronized
    private fun append(entry: LogEntry) {
        ring[head] = entry
        head = (head + 1) % capacity
        if (size < capacity) size++
        cacheDirty = true
        _logs.value = snapshotLocked()
    }

    /** 最近 count 条（旧→新）。count <= 0 或 >= size 时返回全部。 */
    fun snapshot(count: Int = 0): List<LogEntry> {
        @Synchronized
        fun locked(): List<LogEntry> = snapshotLocked(count)
        return locked()
    }

    @Synchronized
    private fun snapshotLocked(count: Int = 0): List<LogEntry> {
        if (!cacheDirty && count <= 0) return cachedSnapshot
        val n = when {
            count <= 0 || count >= size -> size
            else -> count
        }
        val out = ArrayList<LogEntry>(n)
        var idx = (head - n + capacity) % capacity
        repeat(n) {
            ring[idx]?.let { out += it }
            idx = (idx + 1) % capacity
        }
        if (count <= 0) {
            cachedSnapshot = out
            cacheDirty = false
        }
        return out
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 200
    }
}