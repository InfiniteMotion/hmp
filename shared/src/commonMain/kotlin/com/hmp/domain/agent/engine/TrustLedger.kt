package com.hmp.domain.agent.engine

/**
 * 信任阶梯档位（M4-T2）：建议 → 代劳 → 静默。
 * 档位越靠后，对同类写动作的确认要求越低（先征询 → 直接代劳+通知 → 静默执行）。
 * STRONG_CONFIRM 类工具（删歌单/改 ID3）不随档位松绑，始终要求强确认（M6-T5 双确认链）。
 */
enum class TrustTier(val index: Int) {
    SUGGEST(0), // 建议：写动作先征询确认
    ACT(1),     // 代劳：直接执行 + 事后通知
    SILENT(2),  // 静默：直接执行，不打扰
}

/**
 * 信任阶梯状态机（M4-T2）。
 *
 * 规则（挂起参数默认值：升级阈值 3）：
 * - 升级：某类写动作连续隐式接受 3 次 → 升一档（建议→代劳→静默）。
 * - 回拨：任何一次用户否决/撤销 → 该类计数清零 + 整体档位下降一档；也可手动回拨到最低档。
 * - 档位永不因隐式接受越过 SILENT（最高档）。
 * - 「同类」按工具名聚合计数（同类写动作＝同名写工具）。
 */
class TrustLedger(
    private val escalationThreshold: Int = EngineDefaults.TRUST_ESCALATION_THRESHOLD,
) {
    var tier: TrustTier = TrustTier.SUGGEST
        private set

    private val implicitAcceptByTool = mutableMapOf<String, Int>()

    /** 某写动作被隐式接受（用户未否决且未确认拖拽偏离）。达到阈值即升档。 */
    fun onActionAccepted(toolName: String) {
        val c = (implicitAcceptByTool[toolName] ?: 0) + 1
        implicitAcceptByTool[toolName] = c
        if (c >= escalationThreshold && tier != TrustTier.SILENT) {
            tier = TrustTier.entries[tier.index + 1]
            // 升级后计数保留（达到即已兑现），不再累计到下档判断；置零避免下档立即再触发。
            implicitAcceptByTool[toolName] = 0
        }
    }

    /** 用户否决/撤销该动作：计数清零 + 档位回拨一档（最低档不再回拨）。 */
    fun onActionRejected(toolName: String) {
        implicitAcceptByTool[toolName] = 0
        if (tier != TrustTier.SUGGEST) {
            tier = TrustTier.entries[tier.index - 1]
        }
    }

    /** 手动回拨一档（设置页信任档位回拨 UI）。 */
    fun manualDialBack() {
        if (tier != TrustTier.SUGGEST) tier = TrustTier.entries[tier.index - 1]
        implicitAcceptByTool.clear()
    }

    /** 当前该工具的连续隐式接受计数（测试断言用）。 */
    fun consecutiveAccepts(toolName: String): Int = implicitAcceptByTool[toolName] ?: 0
}