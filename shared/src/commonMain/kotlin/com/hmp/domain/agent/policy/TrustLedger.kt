package com.hmp.domain.agent.policy

import com.hmp.domain.agent.runtime.EngineDefaults

/**
 * 信任阶梯状态机——驱动 AgentPolicyConfig.trustLevel 自动升降档。
 *
 * 档位说明（砍 TrustTier enum，直接用 Int）：
 *   0 = SUGGEST  谨慎：CONFIRM 级工具弹确认
 *   1 = ACT      代劳：CONFIRM 级工具直接执行 + 通知
 *   2 = SILENT   静默：CONFIRM 级工具静默执行
 *
 * STRONG_CONFIRM 级工具（删歌单/改 ID3）不随 trustLevel 松绑，始终 RequireConfirm（硬规则）。
 *
 * 规则：
 * - 升级：某工具连续隐式接受 N 次 → config.trustLevel++（阈值默认 3）
 * - 回拨：用户否决 → config.trustLevel--（最低 0）
 * - 计数器每次 run() 新建，不持久化（config.trustLevel 本身持久化）
 */
class TrustLedger(
    private val config: AgentPolicyConfig,
    private val escalationThreshold: Int = EngineDefaults.TRUST_ESCALATION_THRESHOLD,
    /** 档位或计数器变化时回调（非 suspend，MasterAgent 负责桥接 DataStore 写入） */
    private val onChange: (() -> Unit)? = null,
) {
    private val implicitAcceptByTool = mutableMapOf<String, Int>()

    /** 当前 trustLevel（直接透传 config.trustLevel，config 是持久化载体） */
    val trustLevel: Int get() = config.trustLevel

    /** 某写动作被隐式接受（ConfirmGate 返回 AllowOnce / AllowAlways） */
    fun onActionAccepted(toolName: String) {
        val c = (implicitAcceptByTool[toolName] ?: 0) + 1
        implicitAcceptByTool[toolName] = c
        if (c >= escalationThreshold && config.trustLevel < 2) {
            config.trustLevel++
            implicitAcceptByTool[toolName] = 0  // 置零避免下档立即触发
            onChange?.invoke()
        }
    }

    /** 用户否决该动作（ConfirmGate 返回 Deny） */
    fun onActionRejected(toolName: String) {
        implicitAcceptByTool[toolName] = 0
        if (config.trustLevel > 0) {
            config.trustLevel--
            onChange?.invoke()
        }
    }

    /** 手动回拨一档（设置页 UI 回退按钮） */
    fun manualDialBack() {
        if (config.trustLevel > 0) config.trustLevel--
        implicitAcceptByTool.clear()
        onChange?.invoke()
    }

    /** 手动升到目标档位（设置页 UI 直接选） */
    fun manualPromote(target: Int) {
        val newLevel = target.coerceIn(0, 2)
        if (config.trustLevel != newLevel) {
            config.trustLevel = newLevel
            onChange?.invoke()
        }
    }

    /** 当前该工具的连续隐式接受计数（测试断言用） */
    fun consecutiveAccepts(toolName: String): Int = implicitAcceptByTool[toolName] ?: 0
}

/** trustLevel 档位常量（砍 TrustTier enum 后的名字保留，避免魔法数字散落在代码里） */
object TrustLevel {
    const val SUGGEST = 0
    const val ACT = 1
    const val SILENT = 2
    const val MAX = 2
}
