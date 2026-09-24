package com.hmp.domain.agent.profile

/**
 * 三个**纯数据快照** —— 建模器的输入。
 *
 * 契约：`agent-profile.md` v3.2 §4.1（曲库）/ §4.2（行为）/ §4.3（状态）
 *
 * 为什么单独抽出来：建模规则是**纯函数**（可脱库单测），而"从库里捞数据"是 IO。
 * 快照就是这两者之间的接缝 —— 与 `ReportStatsAggregator` 当初的切法一致。
 */

/** 曲库**内容**快照（阶段二，需要 `MusicLabel`）—— 回答"你的世界由什么构成"。 */
data class LibraryContentSnapshot(
    val totalSongs: Int,
    /** 有 AI 源标签的歌曲数（用于覆盖率与折扣） */
    val enrichedSongs: Int,
    /** 流派标签名 → 歌曲数（同一首歌同一流派只算一次） */
    val genreCounts: Map<String, Int> = emptyMap(),
    val moodCounts: Map<String, Int> = emptyMap(),
    val languageCounts: Map<String, Int> = emptyMap(),
    val eraCounts: Map<String, Int> = emptyMap(),
) {
    /**
     * 标签覆盖率。契约 §4.1.4 用它做两件事：判断内容建模该不该跑、以及给置信度打折。
     *
     * ⚠️ 只按"有没有 AI 源标签"算，与 `getEnrichHealth().coverageRate` 口径一致。
     */
    val coverageRate: Float
        get() = if (totalSongs == 0) 0f else enrichedSongs.toFloat() / totalSongs

    val hasContent: Boolean
        get() = genreCounts.isNotEmpty() || moodCounts.isNotEmpty() ||
            languageCounts.isNotEmpty() || eraCounts.isNotEmpty()
}

/**
 * 曲库**状态**快照（用户显式给的东西）。
 *
 * 契约 §4.3：这些不产生"事件"，它们是**状态** —— 所以要靠反复重算 + 唯一索引累加
 * 来记录"变化过"，而不是订阅事件（收藏 / 评分 / 歌单都不会派发事件）。
 */
data class LibraryStateSnapshot(
    val totalSongs: Int,
    val likedCount: Int,
    /** 明确不喜欢 —— 负向信号，比"喜欢什么"更能收紧推荐 */
    val dislikedCount: Int,
    /** 手工歌单名（用户自己的分类语言） */
    val playlistNames: List<String> = emptyList(),
    /** 被隐藏的文件夹数（底层是 `Music.isDeleted`，按目录聚合） */
    val hiddenFolderCount: Int,
    /** 用户亲手修正过的标签条数（`source = USER`）—— 暴露分类偏好与模型的系统性错法 */
    val userCorrectedLabelCount: Int = 0,
) {
    val hasAnySignal: Boolean
        get() = likedCount > 0 || dislikedCount > 0 || playlistNames.isNotEmpty() ||
            hiddenFolderCount > 0 || userCorrectedLabelCount > 0
}

/**
 * 行为快照（契约 §4.2：**只用播放记录**，不新增任何埋点）。
 *
 * [skipPointBuckets] 的三个桶是本模块最被低估的信号：
 * 「前奏就跳」与「听一半才跳」是两种完全不同的诊断 —— 前者说选曲不对，后者说歌不耐听。
 */
data class BehaviorSnapshot(
    val windowDays: Int,
    val totalPlays: Int,
    /** 有过播放的日历天数（活跃度） */
    val activeDays: Int,
    /** 小时（0..23）→ 播放次数 */
    val hourHistogram: Map<Int, Int> = emptyMap(),
    val completedPlays: Int = 0,
    /** 未听完的播放按"听到多少"分桶：INTRO / MIDDLE / LATE */
    val skipPointBuckets: Map<String, Int> = emptyMap(),
    /** 窗口内播放过的不同曲目数 */
    val distinctTracks: Int = 0,
    /** 其中在窗口内**首次**播放的曲目数（新歌） */
    val novelTracks: Int = 0,
    /**
     * 会话统计（B 类「注意力模式」的输入；T0b 补）。
     *
     * 会话是**推断**出来的边界：播放间隔超过 [SESSION_GAP_MS]（30 分钟）即视为新会话。
     * 契约 §5.2 明说「会话边界需推断」—— 这是 B 类"弱确证"的根源，所以下游
     * [BPortraitModeler] 对它的结论只能降级表达，且会话数不足时一律不说。
     */
    val sessionCount: Int = 0,
    /** 每会话**净听时长**均值（分钟；按 playDuration 累加，不含切歌间隙） */
    val avgSessionMinutes: Float = 0f,
    /** 每会话曲目数均值 */
    val avgTracksPerSession: Float = 0f,
) {
    companion object {
        /** 会话切分间隔：超过 30 分钟没播下一首，就当是另一次收听 */
        const val SESSION_GAP_MS = 30L * 60 * 1000
    }
    val hasSignal: Boolean get() = totalPlays > 0 && distinctTracks > 0

    /** 完播率（未听完的不计） */
    val completionRate: Float
        get() = if (totalPlays == 0) 0f else completedPlays.toFloat() / totalPlays

    /** 重复度：平均每首被听几次（1.0 = 从不重复） */
    val playsPerTrack: Float
        get() = if (distinctTracks == 0) 0f else totalPlays.toFloat() / distinctTracks

    /** 新歌占比（窗口内） */
    val newRatio: Float
        get() = if (distinctTracks == 0) 0f else novelTracks.toFloat() / distinctTracks
}
