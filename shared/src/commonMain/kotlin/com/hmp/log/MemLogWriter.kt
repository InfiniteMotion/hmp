package com.hmp.log

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.hmp.platform.Synchronized
import com.hmp.platform.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 看板/诊断 UI 读取的单条日志。 */
data class LogEntry(
    val severity: Severity,
    val tag: String,
    val message: String,
    /** 递增序号，UI 用它做稳定的 Lazy key 与时间先后判断（不依赖平台时钟）。 */
    val sequence: Int,
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
        append(LogEntry(severity = severity, tag = tag, message = text, sequence = nextSeq()))
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