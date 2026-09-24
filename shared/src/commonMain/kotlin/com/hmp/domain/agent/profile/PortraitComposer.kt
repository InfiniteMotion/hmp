package com.hmp.domain.agent.profile

/**
 * 证据 → 侧写。**这是侧写唯一的派生点**（契约 §1.3）。
 *
 * 纯函数、无副作用、可脱库单测。它不做 IO，也不写库 ——
 * 调用方（`UserMemory`）负责取证据、存结果。
 *
 * 三条规则在这里落地：
 *
 * 1. **按槽位选优**：同一槽位若有多条证据（如形态阶段与内容阶段各写了一次），
 *    取 `confidence` 最高的那条 —— 内容建模 0.55 > 形态建模 0.4，
 *    所以「阶段二纠正阶段一」不用额外机制，**它是选优的自然结果**（契约 §4.1.2）。
 * 2. **否决优先**：存在 `portrait.denied`（value = 类型 id）时，该类型整体不产出（剧本 P14）。
 * 3. **分层按成熟机制**：曲库是长期资产 → 直接 L3；行为/对话按跨会话与持续时间晋升（契约 §3.1）。
 */
object PortraitComposer {

    /** 侧写的置信度取**支撑它的最强证据**，而非平均 —— 平均会让一个弱槽位拖垮整条侧写。 */
    fun compose(evidence: List<ProfileEvidence>, nowMs: Long): List<PortraitDraft> {
        if (evidence.isEmpty()) return emptyList()

        val denied: Set<String> = evidence
            .filter { it.predicate == PortraitType.DENIED }
            .map { it.value }
            .toSet()

        // 只认闭集内的谓词 —— 未知谓词一律忽略（写入面已经被 gate 挡过一次，这里是第二道）
        val parsed = evidence.mapNotNull { row ->
            PortraitType.parse(row.predicate)?.let { (type, slot) -> Triple(type, slot, row) }
        }

        return parsed
            .groupBy { it.first }
            .filterKeys { it.id !in denied }
            .map { (type, rows) -> buildPortrait(type, rows.map { it.second to it.third }, nowMs) }
            .sortedBy { it.type.ordinal }
    }

    private fun buildPortrait(
        type: PortraitType,
        slotRows: List<Pair<String, ProfileEvidence>>,
        nowMs: Long,
    ): PortraitDraft {
        // 每个槽位选一条最优证据
        val winners = slotRows
            .groupBy { it.first }
            .mapValues { (_, candidates) -> candidates.maxWith(evidenceOrder) }
            .mapValues { it.value.second }

        // 槽位按声明顺序排列 —— slots_json 因此稳定，diff 才有意义
        val slots = LinkedHashMap<String, String>()
        for (slot in type.slots) {
            winners[slot]?.let { slots[slot] = it.value }
        }

        val selected = winners.values.toList()
        val strongest = selected.maxOf { it.confidence }

        return PortraitDraft(
            type = type,
            tier = tierOf(type, selected, nowMs),
            slots = slots,
            evidenceRefs = selected.map { it.id }.filter { it != 0L }.sorted(),
            confidence = decayed(strongest, type, selected, nowMs),
            sources = selected.map { it.source }.toSet(),
        )
    }

    /**
     * 选优顺序：置信度高者胜；同分取**更晚更新**者；再同取 id 大者（保证确定性，
     * 否则同一份数据两次派生可能得到不同结果）。
     */
    private val evidenceOrder = compareBy<Pair<String, ProfileEvidence>>(
        { it.second.confidence },
        { it.second.updatedAt },
        { it.second.id },
    )

    /**
     * 落层规则（契约 §3.1 / §4.1）。
     *
     * - **曲库**：导入即全量、与时间无关 —— 它的成熟靠阶段切换（形态→内容），
     *   不靠"跨了几个会话"，所以**直接 L3**，也不随时间衰减。
     * - **行为 / 对话**：同一事实跨会话出现 ≥ N 次 → L2；持续 ≥ M 周且近期仍在续期 → L3。
     */
    private fun tierOf(type: PortraitType, rows: List<ProfileEvidence>, nowMs: Long): PortraitTier {
        return when (type.maturity) {
            PortraitMaturity.LIBRARY -> PortraitTier.L3
            PortraitMaturity.BEHAVIOR, PortraitMaturity.DIALOGUE -> {
                val pastPromotionWindow = rows.any { row ->
                    row.distinctSessions >= ProfileConfig.PROMOTE_MIN_SESSIONS &&
                        weeksBetween(row.createdAt, nowMs) >= ProfileConfig.PROMOTE_MIN_WEEKS
                }
                val recentlyRenewed = rows.any { weeksBetween(it.updatedAt, nowMs) < 1L }
                if (pastPromotionWindow && recentlyRenewed) PortraitTier.L3 else PortraitTier.L2
            }
        }
    }

    /**
     * 衰减（契约 §3.1「L3 降权」）：长期无新证据 → 降权但**不删除**
     * —— 设置页仍应看得见，只是不再进上下文（[selectForContext] 会按门槛滤掉）。
     *
     * 曲库侧写不衰减：曲库没变，不等于这份认识过期了。
     */
    private fun decayed(
        confidence: Double,
        type: PortraitType,
        rows: List<ProfileEvidence>,
        nowMs: Long,
    ): Double {
        if (type.maturity == PortraitMaturity.LIBRARY) return confidence
        val freshest = rows.maxOfOrNull { it.updatedAt } ?: return confidence
        val staleWeeks = weeksBetween(freshest, nowMs)
        return if (staleWeeks > ProfileConfig.DECAY_AFTER_WEEKS) confidence * 0.5 else confidence
    }

    /**
     * 上下文块取哪几条（契约 §7）。
     *
     * **门槛只过滤推断类**：曲库形态建模的置信度 0.4 < 0.5，若一律按 0.5 滤，
     * "新用户导入曲库后第一次对话就有内容"就永远做不到 —— 那是「数出来的事实」，
     * 不该和「猜出来的偏好」用同一把尺子。
     *
     * 排序：L3 在前（更稳），同级按置信度降序，再按类型序稳定排列。
     */
    fun selectForContext(
        portraits: List<PortraitDraft>,
        max: Int = ProfileConfig.CONTEXT_MAX_PORTRAITS,
    ): List<PortraitDraft> = portraits
        .filter { it.factualOnly || it.confidence >= ProfileConfig.CONTEXT_CONFIDENCE_FLOOR }
        .sortedWith(compareByDescending<PortraitDraft> { it.tier == PortraitTier.L3 }
            .thenByDescending { it.confidence }
            .thenBy { it.type.ordinal })
        .take(max)

    private fun weeksBetween(fromMs: Long, toMs: Long): Long =
        if (fromMs <= 0L) 0L else ((toMs - fromMs) / (7L * 24 * 3600 * 1000)).coerceAtLeast(0L)
}
