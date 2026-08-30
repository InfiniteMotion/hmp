package com.hmp.domain.agent.engine

/**
 * Agent 运行时日志（KMP 安全：commonMain 用 println，三端一致）。
 *
 * 与 `agent_audit_log`（结构化审计，落库 agent_audit_log 表）互补：
 * 这里是开发/排查用的运行时 trace（console），那里是用户可查的审计留痕。
 * 级别：d/i 常规流程；w 异常降级；e 错误/失败。前缀 `[AGENT]` 便于 grep。
 */
object AgentLog {
    private const val TAG = "[AGENT]"
    private const val MASK = "…"

    fun d(msg: String) = println("$TAG $msg")
    fun i(msg: String) = println("$TAG $msg")
    fun w(msg: String) = println("$TAG [WARN] $msg")
    fun e(msg: String) = println("$TAG [ERR] $msg")

    /** 截断长内容（LLM 文本/参数），防止日志刷屏。 */
    fun truncate(s: String, max: Int = 120): String = if (s.length <= max) s else s.take(max - MASK.length) + MASK
}
