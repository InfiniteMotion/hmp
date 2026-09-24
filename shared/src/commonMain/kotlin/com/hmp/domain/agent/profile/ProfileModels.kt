package com.hmp.domain.agent.profile

/**
 * 用户认识模块的领域模型（纯 Kotlin，**不依赖 Room**）。
 *
 * 契约：`docs/7_x/B agent-build/design/agent-profile.md` v3.1 §2 / §3 / §7
 *
 * 实体 ↔ 领域对象的换算在 `data/mapper/UserProfileMapper.kt`，
 * 与 `PlaybackHistory` / `MusicLabel` 的既有做法一致。
 */

/** 来源分档与置信度起点（契约 §2.5）。 */
object ProfileSources {
    /** 用户显式给的（收藏 / 评分 / 对话明说 / 设置页改）—— 最高 ground truth */
    const val T1_USER = "T1_USER"

    /** 无意识播放行为（定时聚合）—— 行为不会表演 */
    const val T0_BEHAVIOR = "T0_BEHAVIOR"

    /** 曲库内容建模（富化完成）—— 聚合抵消个体噪声，但仍是"拥有" */
    const val LIBRARY_CONTENT = "LIBRARY_CONTENT"

    /** 曲库形态建模（扫描完成）—— 只有结构，可能失真 */
    const val LIBRARY_SHAPE = "LIBRARY_SHAPE"

    /** 从对话推断的偏好 —— 只是假设，等行为确证 */
    const val T2_DIALOGUE = "T2_DIALOGUE"

    fun baseConfidence(source: String): Double = when (source) {
        T1_USER -> 0.9
        T0_BEHAVIOR -> 0.6
        LIBRARY_CONTENT -> 0.55
        LIBRARY_SHAPE -> 0.4
        T2_DIALOGUE -> 0.35
        else -> 0.3
    }

    /**
     * 是不是**事实类**来源。
     *
     * 用途见 [ProfileConfig.contextConfidenceFloor] 的 KDoc —— 曲库侧写是"数出来的"，
     * 不是"猜出来的"，所以不受推断类门槛约束。
     */
    fun isFactual(source: String): Boolean =
        source == LIBRARY_SHAPE || source == LIBRARY_CONTENT
}

/** 分级阈值与配额（契约 §13 的 PF4 / PF14；先硬编码，将来可搬 DataStore）。 */
object ProfileConfig {
    /** L1 → L2：同一事实需在**不同会话**里出现 ≥ 该次数（同一会话内高频不算） */
    const val PROMOTE_MIN_SESSIONS = 3

    /** L2 → L3：L2 条目需持续存在 ≥ 该周数，且每轮聚合都有新证据续期 */
    const val PROMOTE_MIN_WEEKS = 3L

    /** L3 降权：连续该周数无新证据 → 降 confidence（**不删除**） */
    const val DECAY_AFTER_WEEKS = 4L

    /** 上下文块最多放几条侧写 */
    const val CONTEXT_MAX_PORTRAITS = 8

    /**
     * 上下文块的置信度门槛。
     *
     * ⚠️ **只对推断类来源生效**：曲库形态建模的置信度是 0.4，低于 0.5 ——
     * 若一律按 0.5 过滤，新用户导入曲库后画像块永远是空的，
     * 「第一次对话就有内容」这个判据（契约 §12）就永远达不成。
     * 形态建模是**数出来的事实**，与"推断出的偏好"不该用同一把尺子。
     */
    const val CONTEXT_CONFIDENCE_FLOOR = 0.5

    /**
     * 上下文块硬字数上限（超出截断，宁可少说不可刷屏）。
     * v3.5 从 400 提到 500：画像叙事（两面共用）入场后，槽位行与叙事共享这个预算。
     */
    const val CONTEXT_MAX_CHARS = 500

    /** 行为建模窗口：最近这么多天（契约 §4.2 的 90 天口径） */
    const val BEHAVIOR_WINDOW_DAYS = 90

    /**
     * 行为刷新最小间隔 —— 契约说"定时聚合"，本项目没有常驻调度器，
     * 所以改成**低频闸门 + 事件触发**：每次调用先看证据表里 `T0_BEHAVIOR` 的最新时间，
     * 不足 20 小时就跳过。效果等于"每天最多算一次"，且不需要新增调度设施。
     */
    const val BEHAVIOR_REFRESH_MIN_INTERVAL_MS = 20L * 3600 * 1000

    /** 富化覆盖率目标线 —— 与 `EnrichSubAgent` 的构造默认值（0.9f）对齐，但不持有 EnrichTask */
    const val ENRICH_TARGET_COVERAGE = 0.9
}

/**
 * 待写入的证据（纯值，还没落库、没有 id）。
 *
 * 建模器只产这个 —— **建模器不写侧写**，侧写统一由 [PortraitComposer] 派生，
 * 保证"侧写只有一条派生路径"（契约 §1.3）。
 */
data class EvidenceDraft(
    val predicate: String,
    val value: String,
    val source: String,
    val confidence: Double,
    /** 同一会话内的多次观察：`+1` 表示这是一次新的"会话"，`0` 表示同一会话内重复 */
    val sessionDelta: Int = 1,
    val sessionId: String? = null,
)

/** 证据行（已落库，带 id —— 侧写的反链指的就是它）。 */
data class ProfileEvidence(
    /** `"<portraitType>.<slotKey>"`，闭集见 [PortraitType] */
    val predicate: String,
    val value: String,
    val source: String,
    val confidence: Double,
    val id: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** 被观察到的总次数（审计四问"被确证过吗"） */
    val evidenceCount: Int = 1,
    /** 出现过的不同会话数（L1 → L2 晋升用，**与 evidenceCount 不是一个东西**） */
    val distinctSessions: Int = 1,
    val lastSessionId: String? = null,
)

/** 侧写（已派生，待落库或已落库）。 */
data class PortraitDraft(
    val type: PortraitType,
    val tier: PortraitTier,
    /** 槽位 → 取值（闭集 token 或已脱敏的短语）。按 [PortraitType.slots] 的声明顺序排列，保证 `slots_json` 稳定 */
    val slots: Map<String, String>,
    /** 反链到证据行 id */
    val evidenceRefs: List<Long>,
    val confidence: Double,
    /** 这个侧写由哪些来源的证据支撑 —— 上下文门槛要按它区分"事实"与"推断"（见 [ProfileConfig.CONTEXT_CONFIDENCE_FLOOR]） */
    val sources: Set<String> = emptySet(),
    val coverageAtModeling: Double? = null,
) {
    /** 是否**全部**由事实类来源支撑 —— 事实类不让 0.5 门槛拦，否则冷启动期画像永远是空的 */
    val factualOnly: Boolean
        get() = sources.isNotEmpty() && sources.all { ProfileSources.isFactual(it) }
}

/**
 * 曲库建模的输入行 —— 只取真正用得上的四个字段。
 *
 * 刻意不传 `title`：形态建模不看歌名（看了也只是噪声），少一个字段就少一处泄漏面。
 */
data class LibraryTrack(
    val artist: String,
    val album: String,
    /** 毫秒 */
    val durationMs: Long,
    /** 绝对路径（`/Users/<用户名>/Music/...`）—— **只在建模内部用，不得直接进上下文**（契约 §7.2） */
    val path: String,
)
