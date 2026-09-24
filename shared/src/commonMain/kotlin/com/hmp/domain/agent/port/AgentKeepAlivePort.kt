package com.hmp.domain.agent.port

/**
 * Agent 保活端口（F11-L1）—— agent 运行时"我需要留在后台"的诉求出口。
 *
 * 背景：agent 运行时是进程级单例，但**没人对"进程该不该活着"负责**。退到后台后
 * 进程被系统回收，纯内存的电台会话随之消失（见 `docs/7_x/B agent-build/design/agent-lifecycle.md`）。
 * 本端口把"保活诉求"从 domain 层表达出来，由各平台实现到底层机制。
 *
 * 平台实现（由 DI 注入）：
 * - **Android**：转发到播放前台服务，让它在 agent 活跃期间保持前台（进程不被回收）。
 * - **iOS**：按 reason 申请 / 释放有限后台任务（`beginBackgroundTask` / `BGProcessingTask`）。
 * - **Desktop**：no-op（进程常驻，窗口/托盘即可）。
 *
 * **默认 null**（测试 / 未接线路径）→ 全部调用静默跳过，agent 前后台行为不受影响。
 * 与 [PlaybackCommandPort] / [NowPlayingContextProvider] 同一套"可空端口"惯例。
 */
interface AgentKeepAlivePort {
    /**
     * 声明 / 撤销一类保活诉求。
     *
     * 同一 [reason] 可能被多次声明与撤销，实现方需自行做**幂等或引用计数**；
     * 调用方（domain 层）只保证"活跃时 set(true)、退出活跃时 set(false)"。
     */
    fun setKeepAlive(reason: KeepAliveReason, active: Boolean)
}

/** 保活诉求类型——不同原因对应不同的平台机制与时长。 */
enum class KeepAliveReason {
    /** 有音频正在播放（最基础的后台存活场景）。 */
    PLAYING,
    /** 电台会话活跃（含"等模型出队列"的无音频窗口）。 */
    RADIO_ACTIVE,
    /** 富化等周期性后台任务（可走 BGProcessingTask / dataSync 型服务）。 */
    ENRICH_TASK,
    /** 主动问候等主动性工作。 */
    PROACTIVE,
}
