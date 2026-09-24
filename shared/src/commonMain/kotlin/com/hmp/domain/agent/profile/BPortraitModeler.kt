package com.hmp.domain.agent.profile

/**
 * B 类三侧写（秩序 / 情绪强度 / 注意力）—— **只能降级表达**（契约 `agent-profile.md` v3.4 §5.2）。
 *
 * 纯函数、只产证据，与 [BehaviorModeler] / [LibraryModeler] 同一纪律：不写侧写、不碰库。
 * 它们的"弱"不在置信度数字上，而在**推导链**上 —— 会话边界是推断的、情绪维度是标签近似的，
 * 所以渲染层对它们强制「你在音乐里表现出的…」前缀（[UserProfileRenderer] 按
 * `PortraitType.weakEvidence` 加）。
 *
 * **实现状态（v3.4 如实登记，不假装知道）**：
 * - `attention_portrait`：`sessionLength` / `fragmentation` ✅ —— 会话由 30 分钟间隔推断
 * - `order_portrait.determinism` ✅ —— 重复度 × 新歌比例交叉（契约要的"交叉"）
 * - `order_portrait.structureTolerance` ⏸ —— 需会话内顺序 × 专辑内序，两源都未采集，不写
 * - `intensity_portrait.moodConsistency` ✅ —— 曲库 MOOD 分布的近似（带覆盖率折扣）
 * - `intensity_portrait.energyTendency` ⏸ —— MOOD 标签没有能量维度，硬造等于编，不写
 */
object BPortraitModeler {

    // ── 取值域（与 BehaviorModeler 的 HIGH/MEDIUM/LOW 同语义）──
    private const val RATE_HIGH = "HIGH"
    private const val RATE_MEDIUM = "MEDIUM"
    private const val RATE_LOW = "LOW"

    /** 会话数下限：三次"收听"推不出注意力模式，只能推出三次巧合 */
    private const val MIN_SESSIONS = 3

    // ── 阈值（先硬编码，待真实数据校准；契约 §13 PF4 的既有做法）──
    private const val SESSION_LONG_MINUTES = 45f
    private const val SESSION_SHORT_MINUTES = 15f
    private const val FRAG_HIGH_TRACKS = 3f
    private const val FRAG_LOW_TRACKS = 8f

    /** 复用主行为建模的样本量纪律（BehaviorModeler 的 MIN_PLAYS / MIN_ACTIVE_DAYS 同值） */
    private const val MIN_PLAYS = 20
    private const val MIN_ACTIVE_DAYS = 3

    /** Float 快照字段与 Double 阈值比较的容差 */
    private const val FLOAT_EPS = 1e-3f

    /**
     * 行为可推出的 B 类证据：`order.determinism` + `attention.sessionLength` / `fragmentation`。
     *
     * 数据太少就不说：主样本量闸门（播放数/活跃天）不过 → 空；会话数不足 → 注意力两项不说。
     */
    fun toBehaviorEvidenceDrafts(snapshot: BehaviorSnapshot, sessionId: String? = null): List<EvidenceDraft> {
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
            // ── order_portrait.determinism：重复度 × 新歌比例的交叉（契约 §5.2"单源不足"的回应）──
            // 与 exploration/habits 的单项不同：只有"反复听同一批"**且**"几乎不追新"同时成立才敢说 HIGH。
            // 快照字段是 Float，与 Double 阈值比较时留一个 epsilon，别让 0.1f > 0.1 这种精度噪声翻案。
            val deterministic =
                snapshot.playsPerTrack >= BehaviorModeler.REPEAT_HIGH - FLOAT_EPS &&
                    snapshot.newRatio <= BehaviorModeler.EXPLORE_REPEAT_RATIO + FLOAT_EPS
            val chasing =
                snapshot.newRatio >= BehaviorModeler.EXPLORE_HIGH_RATIO - FLOAT_EPS &&
                    snapshot.playsPerTrack <= BehaviorModeler.REPEAT_LOW + FLOAT_EPS
            add(
                draft(
                    PortraitType.ORDER, PortraitType.DETERMINISM,
                    when {
                        deterministic -> RATE_HIGH
                        chasing -> RATE_LOW
                        else -> RATE_MEDIUM
                    },
                ),
            )

            // ── attention_portrait：会话数够才说 ──
            if (snapshot.sessionCount >= MIN_SESSIONS) {
                add(
                    draft(
                        PortraitType.ATTENTION, PortraitType.SESSION_LENGTH,
                        when {
                            snapshot.avgSessionMinutes >= SESSION_LONG_MINUTES -> "LONG"
                            snapshot.avgSessionMinutes < SESSION_SHORT_MINUTES -> "SHORT"
                            else -> "MEDIUM"
                        },
                    ),
                )
                add(
                    draft(
                        PortraitType.ATTENTION, PortraitType.FRAGMENTATION,
                        when {
                            snapshot.avgTracksPerSession <= FRAG_HIGH_TRACKS -> RATE_HIGH
                            snapshot.avgTracksPerSession >= FRAG_LOW_TRACKS -> RATE_LOW
                            else -> RATE_MEDIUM
                        },
                    ),
                )
            }
        }
    }

    /**
     * `intensity_portrait.moodConsistency` —— 曲库 MOOD 分布的近似。
     *
     * 为什么挂曲库来源而不是行为：它取自内容建模的 `moodMix`（数出来的分布），
     * 但"情绪一致性"这个**解读**是弱确证的 —— 所以槽位在 B 类（渲染降级），
     * 置信度继承内容建模的覆盖率折扣。覆盖率过低（连分布都靠不住）就不写。
     */
    fun moodConsistencyDraft(moodMix: String?, coverageRate: Float, sessionId: String? = null): EvidenceDraft? {
        val value = when (moodMix) {
            LibraryModeler.MIX_SINGLE -> RATE_HIGH
            LibraryModeler.MIX_VARIED -> RATE_LOW
            LibraryModeler.MIX_MEDIUM -> RATE_MEDIUM
            else -> return null
        }
        if (coverageRate < MIN_COVERAGE_FOR_MOOD) return null
        val source = ProfileSources.LIBRARY_CONTENT
        return EvidenceDraft(
            predicate = PortraitType.predicateOf(PortraitType.INTENSITY, PortraitType.MOOD_CONSISTENCY),
            value = value,
            source = source,
            confidence = ProfileSources.baseConfidence(source) * coverageRate.coerceIn(0f, 1f).toDouble(),
            sessionId = sessionId,
        )
    }

    /** MOOD 标签连 30% 的歌都没覆盖时，"情绪基调统一"纯属分布噪声 */
    private const val MIN_COVERAGE_FOR_MOOD = 0.3f
}
