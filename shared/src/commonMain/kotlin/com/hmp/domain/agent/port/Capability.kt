package com.hmp.domain.agent.port

import kotlinx.coroutines.flow.StateFlow

/**
 * SubAgent 对外暴露的统一能力接口（F9-A0）。
 *
 * 所有后台能力（Radio / Enrich / Hello）都实现此接口。
 * MasterAgent 通过 `Map<String, Capability>` 统一管理所有能力，
 * ToolRegistry 的状态查询与能力控制工具都从这套接口走。
 *
 * **为什么在 port**：它是**能力契约**而非引擎实现 —— `tool/`、`runtime/`、`sub/`
 * 都要引用它。若留在 `runtime/` 会形成 `tool → runtime` 反向依赖
 * （工具层不该依赖引擎目录）。
 *
 * 设计原则：
 * 1. **接口极简** —— 强制要求 capabilityName + stateFlow（这是核心价值：
 *    统一状态查询入口），start/stop 可选（不同 SubAgent 生命周期模式不同：
 *    Radio 是单例 start/stop 复用，Enrich 每次 start 新建实例，Hello 自动启动）。
 * 2. **stateFlow 是唯一状态出口** —— Tool 层、UI 层、日志都从这里读状态，
 *    绝不允许外部直接访问 SubAgent 的私有 MutableStateFlow。
 * 3. 每个 SubAgent 可以有额外的专属方法（如 RadioSubAgent.startRadio(seed)），
 *    那是实现类自己的东西，Capability 只要求最小集合。
 */
interface Capability {
    /** 能力名（"radio" / "enrich" / "hello"），用作 Map key */
    val capabilityName: String

    /** 当前状态流——外部只读不写，映射到统一 CapabilityState */
    val stateFlow: StateFlow<CapabilityState>
}

/**
 * 能力的**只读快照**（2026-11-13，C 类收口）。
 *
 * 对外出口只给这个类型，不再交 `Capability` / `SubAgent` 实例：
 * 没有方法、没有 `raw` 引用，调用方在类型上就无法写状态或下转型。
 * `start/stop` 已随本次收口删除（全仓零调用）。
 */
data class CapabilitySnapshot(
    val name: String,
    val status: CapabilityState.Status,
    val detail: String,
)

/** 内部状态 → 对外快照；刻意丢弃 `raw`（可能携带子 Agent 内部对象）。 */
internal fun CapabilityState.toSnapshot(name: String): CapabilitySnapshot = CapabilitySnapshot(name = name, status = status, detail = detail)

/**
 * Capability 统一状态模型。
 *
 * 每个 SubAgent 的具体内部状态（RadioState / Enrich 活动状态 / Hello 运行状态）
 * 映射到这个统一模型，让 Tool 层（capability_status）和 UI 层能用同一套逻辑展示。
 */
data class CapabilityState(
    val status: Status,
    val detail: String = "",
    /** SubAgent 内部原始状态对象（可选，调试或特殊 UI 用） */
    val raw: Any? = null,
) {
    enum class Status {
        IDLE, // 未启动或已停止
        BUILDING, // 启动中（电台拉种子、富化检查覆盖率）
        RUNNING, // 正常运行
        PAUSED, // 暂停（富化 pause / 电台 pause）
        COMPLETED, // 正常完成（富化达到目标覆盖率自行退出）
        ERROR, // 不可恢复错误（LLM 连败等）
    }
}
