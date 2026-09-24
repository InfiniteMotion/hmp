package com.hmp.domain.agent.profile

/**
 * 行为建模 —— 只用 `PlaybackHistory`，**零新埋点**（契约 §4.2）。
 *
 * 纯函数：输入一份 [BehaviorSnapshot]，输出证据草稿。它回答的是
 * 「你**怎么**听」（时段 / 完播 / 跳过点 / 重复 / 探索），与曲库侧的「你有什么」互补。
 *
 * 三条纪律：
 * 1. **数据太少就不说** —— 三次播放推不出任何东西。低于 [MIN_PLAYS] / [MIN_ACTIVE_DAYS] 直接返回空。
 * 2. **来源是 `T0_BEHAVIOR`（0.6）** —— 行为不会表演，但它是**推断**，不是事实。
 * 3. **落层交给 [PortraitComposer]** —— 这里只产证据，不决定 L2/L3（契约 §3.1）。
 */
object BehaviorModeler {

    // ── 取值域（闭集 token）──
    const val PART_DAWN = "DAWN"
    const val PART_MORNING = "MORNING"
    const val PART_NOON = "NOON"
    const val PART_EVENING = "EVENING"
    const val PART_NIGHT = "NIGHT"

    const val STABILITY_HIGH = "HIGH"
    const val STABILITY_MEDIUM = "MEDIUM"
    const val STABILITY_LOW = "LOW"

    const val RATE_HIGH = "HIGH"
    const val RATE_MEDIUM = "MEDIUM"
    const val RATE_LOW = "LOW"

    const val SKIP_INTRO = "INTRO"
    const val SKIP_MIDDLE = "MIDDLE"
    const val SKIP_LATE = "LATE"

    const val EXPLORE_HIGH = "EXPLORER"
    const val EXPLORE_BALANCED = "BALANCED"
    const val EXPLORE_REPEAT = "REPEATER"

    // ── 阈值（契约 §13 PF4/PF5；先硬编码，待真实数据校准）──
    /** 低于这些量就不建模：三次播放推不出"习惯"，只能推出一晚的心情 */
    private const val MIN_PLAYS = 20
    private const val MIN_ACTIVE_DAYS = 3
    /** 跳过点结论至少要有这么多次跳过才说，否则样本噪声 */
    private const val MIN_SKIPS_FOR_POINT = 5

    private const val STABILITY_HIGH_SHARE = 0.5
    private const val STABILITY_MEDIUM_SHARE = 0.3
    private const val COMPLETION_HIGH = 0.7
    private const val COMPLETION_LOW = 0.4

    /**
     * 重复度与探索的分档阈值 —— **public**：B 类的秩序确定性（`BPortraitModeler`）
     * 要做"重复度 × 新歌比例"的交叉，必须复用同一套分档，否则两个建模器口径漂移。
     */
    const val REPEAT_HIGH = 3.0
    const val REPEAT_LOW = 1.2
    const val EXPLORE_HIGH_RATIO = 0.4
    const val EXPLORE_REPEAT_RATIO = 0.1

    /** 时段分档（本地小时）。与 `ContextAssembler.buildTimeOfDay` 的粒度刻意不同：
     *  那个是"现在几点"的 6 段问候语，这里是"你常在几点听"的 5 段画像，合并了凌晨与深夜。 */
    fun partOf(hourOfDay: Int): String = when (hourOfDay) {
        in 0..4 -> PART_DAWN
        in 5..8 -> PART_MORNING
        in 9..14 -> PART_NOON
        in 15..18 -> PART_EVENING
        else -> PART_NIGHT
    }

    /**
     * 行为快照 → 证据草稿。
     *
     * 产出三个侧写的证据：
     * - `time_portrait` 主/次时段 + 时段稳定性
     * - `habits_portrait` 完播倾向 / 跳过点 / 重复度
     * - `exploration_portrait` 新歌比例 / 老歌回归率
     */
    fun toEvidenceDrafts(snapshot: BehaviorSnapshot, sessionId: String? = null): List<EvidenceDraft> {
        if (!snapshot.hasSignal) return emptyList()
        if (snapshot.totalPlays < MIN_PLAYS || snapshot.activeDays < MIN_ACTIVE_DAYS) return emptyList()

        val source = ProfileSources.T0_BEHAVIOR
        val confidence = ProfileSources.baseConfidence(source)
        fun draft(type: PortraitType, slot: String, value: String) = EvidenceDraft(
            predicate = PortraitType.predicateOf(type, slot),
            value = value,
            source = source,
            confidence = confidence,
            sessionId = sessionId,
        )

        return buildList {
            // ── time_portrait ──
            val byPart = snapshot.hourHistogram.entries
                .groupingBy { partOf(it.key) }
                .fold(0) { acc, e -> acc + e.value }
            val total = byPart.values.sum()
            if (total > 0) {
                val sorted = byPart.entries.sortedByDescending { it.value }
                val primary = sorted.first()
                add(draft(PortraitType.TIME, PortraitType.PRIMARY_PART, primary.key))
                sorted.getOrNull(1)?.let { add(draft(PortraitType.TIME, PortraitType.SECONDARY_PART, it.key)) }
                val share = primary.value.toDouble() / total
                add(
                    draft(
                        PortraitType.TIME, PortraitType.PART_STABILITY,
                        when {
                            share >= STABILITY_HIGH_SHARE -> STABILITY_HIGH
                            share >= STABILITY_MEDIUM_SHARE -> STABILITY_MEDIUM
                            else -> STABILITY_LOW
                        },
                    )
                )
            }

            // ── habits_portrait ──
            add(
                draft(
                    PortraitType.HABITS, PortraitType.COMPLETION_RATE,
                    when {
                        snapshot.completionRate >= COMPLETION_HIGH -> RATE_HIGH
                        snapshot.completionRate < COMPLETION_LOW -> RATE_LOW
                        else -> RATE_MEDIUM
                    },
                )
            )
            val skips = snapshot.skipPointBuckets.values.sum()
            if (skips >= MIN_SKIPS_FOR_POINT) {
                val dominant = snapshot.skipPointBuckets.entries.maxByOrNull { it.value }?.key
                if (dominant != null) {
                    add(draft(PortraitType.HABITS, PortraitType.SKIP_POINT, dominant))
                }
            }
            add(
                draft(
                    PortraitType.HABITS, PortraitType.REPEAT_RATE,
                    when {
                        snapshot.playsPerTrack >= REPEAT_HIGH -> RATE_HIGH
                        snapshot.playsPerTrack <= REPEAT_LOW -> RATE_LOW
                        else -> RATE_MEDIUM
                    },
                )
            )

            // ── exploration_portrait ──
            add(
                draft(
                    PortraitType.EXPLORATION, PortraitType.NEW_RATIO,
                    when {
                        snapshot.newRatio >= EXPLORE_HIGH_RATIO -> EXPLORE_HIGH
                        snapshot.newRatio < EXPLORE_REPEAT_RATIO -> EXPLORE_REPEAT
                        else -> EXPLORE_BALANCED
                    },
                )
            )
            val returnRatio = 1.0 - snapshot.newRatio
            add(
                draft(
                    PortraitType.EXPLORATION, PortraitType.RETURN_RATE,
                    when {
                        returnRatio >= 0.9 -> RATE_HIGH
                        returnRatio < 0.5 -> RATE_LOW
                        else -> RATE_MEDIUM
                    },
                )
            )
        }
    }
}
