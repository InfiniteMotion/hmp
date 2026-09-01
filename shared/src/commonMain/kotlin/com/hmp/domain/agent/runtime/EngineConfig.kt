package com.hmp.domain.agent.runtime

/**
 * 时间来源（毫秒）。引擎平面保持纯 domain：所有需要"当前时间"的组件注入此函数，
 * 生产走 DI（绑 data 层 currentTimeMillis），测试注入脚本化递增时钟。
 */
typealias TimeProvider = () -> Long

/** 引擎默认参数（见任务书第 5 章挂起参数；实施中可调） */
object EngineDefaults {
    /** 单任务步数预算（总纲 7.1）——每次 LLM 调用 + 其工具执行合计耗尽即熔断。 */
    const val STEP_BUDGET = 8
    /** 信任阶梯升级阈值：同类写动作连续隐式接受 N 次升一档。 */
    const val TRUST_ESCALATION_THRESHOLD = 3
    /** 单日云端调用额度（防电台与高频对话日成本累积）。 */
    const val DAILY_CLOUD_QUOTA = 100
    /** 召唤/事件触发的最小间隔（冷却），毫秒。 */
    const val CALL_COOLDOWN_MS = 5_000L
    /** 事件触发冷却（更保守，防事件风暴）。 */
    const val EVENT_COOLDOWN_MS = 30_000L
    /** 上下文三层配额：task 状态 / 曲库清单 / 工具结果滚动保留。 */
    const val MAX_TASK_STATE_CHARS = 600
    const val MAX_LIBRARY_LIST_CHARS = 1200
    const val MAX_TOOL_RESULTS_KEPT = 6
}
