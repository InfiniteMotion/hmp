package com.hmp.domain.agent.profile

import kotlin.math.roundToLong

/**
 * 曲库**形态建模**（阶段一）—— 纯函数，输入曲库行，输出证据草稿。
 *
 * 契约：`docs/7_x/B agent-build/design/agent-profile.md` v3.1 §4.1
 *
 * 阶段一在**扫描完成**时即可用（导入瞬间），完全不依赖标签富化：
 * 只看 `artist / album / duration / path` 四个字段。
 *
 * 它回答的是「你有**多少**、**怎么组织**、集中在谁身上」——
 * 至于"你的世界由什么类型构成"，那是阶段二（内容建模）的事，那时才有 `MusicLabel`。
 *
 * ⚠️ **调用方必须只传未软删除的歌**（`Music.isDeleted = 0`）——
 * 否则"隐藏一个文件夹"会被误读成"曲库变小"或"口味变了"（契约 §4.3 硬纪律）。
 */
object LibraryModeler {

    // ── 取值域（闭集 token；中文说法在 UserProfileRenderer 里）────────────

    const val SCALE_TINY = "TINY"          // < 50
    const val SCALE_SMALL = "SMALL"        // < 300
    const val SCALE_MEDIUM = "MEDIUM"      // < 1000
    const val SCALE_LARGE = "LARGE"        // < 5000
    const val SCALE_HUGE = "HUGE"          // ≥ 5000

    const val CONCENTRATION_FOCUSED = "FOCUSED"
    const val CONCENTRATION_BALANCED = "BALANCED"
    const val CONCENTRATION_BROAD = "BROAD"

    const val ALBUM_ORIENTED = "ALBUM_ORIENTED"
    const val ALBUM_MIXED = "MIXED"
    const val ALBUM_SINGLE_ORIENTED = "SINGLE_ORIENTED"

    const val PATH_ORGANIZED = "ORGANIZED"
    const val PATH_MIXED = "MIXED"
    const val PATH_FLAT = "FLAT"

    const val DURATION_SHORT = "SHORT"
    const val DURATION_MEDIUM = "MEDIUM"
    const val DURATION_LONG = "LONG"

    // ── 阈值（先硬编码常量，待真实曲库验证后调；契约 §13 PF7）──────────────

    /** 专辑型判定：该 `album` 组的曲目数 ≥ 此值才算"整张专辑" */
    private const val COMPLETE_ALBUM_MIN_TRACKS = 6

    /** 专辑型判定：属于"整张专辑"的曲目占比 ≥ 此值 → ALBUM_ORIENTED，< 0.25 → SINGLE_ORIENTED */
    private const val ALBUM_ORIENTED_SHARE = 0.5
    private const val SINGLE_ORIENTED_SHARE = 0.25

    /** 口味集中度：top1 占比 ≥ 此值，或 top3 占比 ≥ 0.6 → FOCUSED */
    private const val FOCUSED_TOP1_SHARE = 0.2
    private const val FOCUSED_TOP3_SHARE = 0.6

    /** 口味集中度：top5 占比 < 此值 → BROAD */
    private const val BROAD_TOP5_SHARE = 0.2

    /** 路径分组被视为"有组织"的下限：可识别目录 ≥ 此数，且最大一组占比 < 0.6 */
    private const val ORGANIZED_MIN_GROUPS = 3
    private const val ORGANIZED_MAX_TOP_SHARE = 0.6

    /** 单曲时长倾向以**中位数**判定（均值会被长曲拉偏） */
    private const val SHORT_DURATION_MS = 3 * 60 * 1000L + 30_000L   // 3:30
    private const val LONG_DURATION_MS = 5 * 60 * 1000L + 30_000L    // 5:30

    /** 路径分类最多记几个目录名（多了就成流水账） */
    private const val MAX_PATH_GROUPS = 5

    /**
     * 通用容器目录名 —— 它们不是"用户的分类"，只是文件系统约定。
     *
     * 命中即视为**无分类**（否则每个人都会得到「你按 Music 分类」这种废话）。
     */
    private val GENERIC_CONTAINERS = setOf(
        "music", "音乐", "musiclibrary", "downloads", "download", "下载", "desktop", "桌面",
        "documents", "文档", "audio", "audios", "音频", "media", "媒体",
        "sd", "sdcard", "storage", "external", "internal", "dcim",
    )

    /** 家目录的父级名 —— 命中说明这一段是**用户名**，绝不能进槽位（契约 §7.2） */
    private val HOME_PARENTS = setOf("users", "home", "user", "文档")

    /**
     * 曲库形态的一次快照。做成独立数据类是为了让它**可单测**：
     * `computeShape` 是纯的，证据组装是另一层。
     */
    data class Shape(
        val totalCount: Int,
        val scale: String,
        val artistConcentration: String,
        val albumCompleteness: String,
        val pathStructure: String,
        /** 已脱敏的末级目录名，按出现次数降序，最多 [MAX_PATH_GROUPS] 个 */
        val pathGroups: List<String>,
        val durationTendency: String,
    )

    /** 计算形态快照。空曲库返回 null（**不产出任何证据**，冷启动不留空壳 —— 剧本 P1）。 */
    fun computeShape(tracks: List<LibraryTrack>): Shape? {
        if (tracks.isEmpty()) return null

        val total = tracks.size
        val byArtist = tracks.groupingBy { it.artist.trim() }.eachCount()
        val sortedArtists = byArtist.values.sortedDescending()
        val top1 = (sortedArtists.getOrNull(0) ?: 0).toDouble() / total
        val top3 = sortedArtists.take(3).sum().toDouble() / total
        val top5 = sortedArtists.take(5).sum().toDouble() / total

        val artistConcentration = when {
            top1 >= FOCUSED_TOP1_SHARE || top3 >= FOCUSED_TOP3_SHARE -> CONCENTRATION_FOCUSED
            top5 < BROAD_TOP5_SHARE -> CONCENTRATION_BROAD
            else -> CONCENTRATION_BALANCED
        }

        val byAlbum = tracks.groupingBy { it.album.trim() }.eachCount()
        val albumBound = tracks.count { (byAlbum[it.album.trim()] ?: 0) >= COMPLETE_ALBUM_MIN_TRACKS }
        val albumShare = albumBound.toDouble() / total
        val albumCompleteness = when {
            albumShare >= ALBUM_ORIENTED_SHARE -> ALBUM_ORIENTED
            albumShare < SINGLE_ORIENTED_SHARE -> ALBUM_SINGLE_ORIENTED
            else -> ALBUM_MIXED
        }

        val groups = tracks.mapNotNull { pathGroupOf(it.path) }
        val groupCounts = groups.groupingBy { it }.eachCount()
        val sortedGroups = groupCounts.entries.sortedByDescending { it.value }
        val pathGroups = sortedGroups.take(MAX_PATH_GROUPS).map { it.key }
        val topGroupShare = (sortedGroups.firstOrNull()?.value ?: 0).toDouble() / total.coerceAtLeast(1)
        val pathStructure = when {
            groupCounts.isEmpty() -> PATH_FLAT
            groupCounts.size >= ORGANIZED_MIN_GROUPS && topGroupShare < ORGANIZED_MAX_TOP_SHARE -> PATH_ORGANIZED
            else -> PATH_MIXED
        }

        return Shape(
            totalCount = total,
            scale = scaleOf(total),
            artistConcentration = artistConcentration,
            albumCompleteness = albumCompleteness,
            pathStructure = pathStructure,
            pathGroups = pathGroups,
            durationTendency = durationTendencyOf(tracks.map { it.durationMs }),
        )
    }

    /**
     * 形态快照 → 证据草稿（`library_portrait` 的槽位，契约 §5.1）。
     *
     * 曲库是**长期资产**：导入即全量，与时间无关，所以它的成熟不靠"跨会话晋升"，
     * 落 L3（由 [PortraitComposer] 决定层，这里只管产证据）。
     *
     * [sessionId] 传扫描事件的会话标识，用于跨会话计数。
     */
    fun toEvidenceDrafts(shape: Shape?, sessionId: String? = null): List<EvidenceDraft> {
        if (shape == null) return emptyList()
        val source = ProfileSources.LIBRARY_SHAPE
        val confidence = ProfileSources.baseConfidence(source)
        fun draft(slot: String, value: String) = EvidenceDraft(
            predicate = PortraitType.predicateOf(PortraitType.LIBRARY, slot),
            value = value,
            source = source,
            confidence = confidence,
            sessionId = sessionId,
        )

        return buildList {
            add(draft(PortraitType.SCALE, shape.scale))
            add(draft(PortraitType.ARTIST_CONCENTRATION, shape.artistConcentration))
            add(draft(PortraitType.ALBUM_COMPLETENESS, shape.albumCompleteness))
            add(draft(PortraitType.PATH_STRUCTURE, shape.pathStructure))
            // 没有可识别目录就不写这条 —— 空值进库只会让设置页多一行废话
            if (shape.pathGroups.isNotEmpty()) {
                add(draft(PortraitType.PATH_GROUPS, shape.pathGroups.joinToString(",")))
            }
            add(draft(PortraitType.DURATION_TENDENCY, shape.durationTendency))
        }
    }

    private fun scaleOf(total: Int): String = when {
        total < 50 -> SCALE_TINY
        total < 300 -> SCALE_SMALL
        total < 1000 -> SCALE_MEDIUM
        total < 5000 -> SCALE_LARGE
        else -> SCALE_HUGE
    }

    private fun durationTendencyOf(durations: List<Long>): String {
        val sorted = durations.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }
        return when {
            median < SHORT_DURATION_MS -> DURATION_SHORT
            median > LONG_DURATION_MS -> DURATION_LONG
            else -> DURATION_MEDIUM
        }
    }

    /**
     * 从绝对路径取出**末级目录名**，并过掉两类不该进画像的东西：
     *
     * 1. **通用容器名**（`Music` / `Downloads` …）—— 那是文件系统约定，不是用户的分类
     * 2. **家目录名**（`/Users/<用户名>` / `/home/<用户名>`）—— 取它就是取用户名
     *
     * 返回 null 表示"这段路径说明不了用户的分类方式"。契约 §7.2 的路径脱敏落在这里。
     */
    fun pathGroupOf(rawPath: String): String? {
        val normalized = rawPath.replace('\\', '/')
        val dir = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        if (dir.isBlank()) return null
        val segment = dir.substringAfterLast('/')
        if (segment.isBlank()) return null
        if (segment.lowercase() in GENERIC_CONTAINERS) return null
        // 再看它的上一级：若那是 Users / home，说明这一级就是用户名
        val grandParent = dir.substringBeforeLast('/', missingDelimiterValue = "")
        if (grandParent.isNotBlank()) {
            val grandParentSegment = grandParent.substringAfterLast('/')
            if (grandParentSegment.lowercase() in HOME_PARENTS) return null
        }
        return segment
    }

    /** 供渲染用的规模描述（把 banded token 说成人话）。 */
    fun scaleLabel(scale: String): String = when (scale) {
        SCALE_TINY -> "规模还小"
        SCALE_SMALL -> "小而完整"
        SCALE_MEDIUM -> "规模中等"
        SCALE_LARGE -> "是个大曲库"
        SCALE_HUGE -> "是个很庞大的曲库"
        else -> "规模不明"
    }

    /** 中位数时长（毫秒）—— 渲染器想说"平均每首 4 分 12 秒"时用得上。 */
    fun medianDurationMs(tracks: List<LibraryTrack>): Long {
        if (tracks.isEmpty()) return 0
        val sorted = tracks.map { it.durationMs }.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else ((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2).toLong()
    }

    // ══════════════ 阶段二 · 内容建模（契约 §4.1.2 / §4.1.4）══════════════

    // 取值域（闭集 token）—— 内容阶段
    const val BREADTH_NARROW = "NARROW"
    const val BREADTH_MEDIUM = "MEDIUM"
    const val BREADTH_BROAD = "BROAD"

    const val MIX_SINGLE = "SINGLE"
    const val MIX_VARIED = "VARIED"
    const val MIX_MEDIUM = "MEDIUM"

    const val LANG_SINGLE = "SINGLE"
    const val LANG_BILINGUAL = "BILINGUAL"
    const val LANG_MULTILINGUAL = "MULTILINGUAL"

    const val ERA_CONCENTRATED = "CONCENTRATED"
    const val ERA_SPREAD = "SPREAD"

    const val COVERAGE_LOW = "LOW"
    const val COVERAGE_MEDIUM = "MEDIUM"
    const val COVERAGE_HIGH = "HIGH"

    /** 状态快照的占比分档（占曲库比例） */
    const val SHARE_NONE = "NONE"
    const val SHARE_SOME = "SOME"
    const val SHARE_MANY = "MANY"

    private const val NARROW_TOP_SHARE = 0.4
    private const val BROAD_MIN_DISTINCT = 8
    private const val BROAD_TOP_SHARE = 0.25
    private const val SINGLE_TOP_SHARE = 0.5
    private const val VARIED_MIN_DISTINCT = 4
    private const val LANG_DOMINANT_SHARE = 0.8
    private const val LANG_MINORITY_SHARE = 0.1
    private const val ERA_CONCENTRATED_SHARE = 0.5
    private const val ERA_MINORITY_SHARE = 0.08
    private const val SHARE_MANY_THRESHOLD = 0.1
    private const val PLAYLIST_NAME_MAX_LEN = 12
    private const val MAX_PLAYLIST_NAMES = 5

    /** 内容阶段的产出。**阶段二按槽位覆盖阶段一**，不是叠加（契约 §4.1.2）。 */
    data class ContentShape(
        val genreBreadth: String,
        val moodMix: String,
        val languageMix: String,
        val eraMix: String,
        val coverageBand: String,
    )

    /**
     * 内容建模 —— 标签分布的形状。
     *
     * 注意它的定性：**「阶段二是纠正，不是补全」**。形态阶段看到"前 5 个歌手占 40%"
     * 只能判"口味集中"；到了这里才看得出那 40% 是横跨 6 个流派（人集中、风格分散）。
     *
     * 没有任何标签时返回 null —— 不假装知道（契约 §4.1.6）。
     */
    fun computeContent(snapshot: LibraryContentSnapshot): ContentShape? {
        if (!snapshot.hasContent) return null
        return ContentShape(
            genreBreadth = breadthOf(snapshot.genreCounts),
            moodMix = mixOf(snapshot.moodCounts),
            languageMix = languageMixOf(snapshot.languageCounts),
            eraMix = eraMixOf(snapshot.eraCounts),
            coverageBand = coverageBandOf(snapshot.coverageRate.toDouble()),
        )
    }

    /**
     * 内容证据草稿 —— 携带**覆盖率折扣**：`confidence = 0.55 × 覆盖率`。
     *
     * 为什么必须打折：内容建模的输入是单条置信度仅 0.3 的 T2 标签。
     * 上千条的分布形状确实比单条可信（聚合抵消个体噪声），
     * 但"覆盖率 0.9 的『爵士占 38%』"和"覆盖率 0.3 的同一句话"显然不是一回事 ——
     * 后者只是"库里 30% 的歌被认识过"，剩下 70% 是未知。一个乘法解决。
     */
    fun toContentEvidenceDrafts(
        content: ContentShape?,
        coverageRate: Float,
        sessionId: String? = null,
    ): List<EvidenceDraft> {
        if (content == null) return emptyList()
        val source = ProfileSources.LIBRARY_CONTENT
        val confidence = ProfileSources.baseConfidence(source) * coverageRate.coerceIn(0f, 1f).toDouble()
        fun draft(slot: String, value: String) = EvidenceDraft(
            predicate = PortraitType.predicateOf(PortraitType.LIBRARY, slot),
            value = value,
            source = source,
            confidence = confidence,
            sessionId = sessionId,
        )
        return listOf(
            draft(PortraitType.GENRE_BREADTH, content.genreBreadth),
            draft(PortraitType.MOOD_MIX, content.moodMix),
            draft(PortraitType.LANGUAGE_MIX, content.languageMix),
            draft(PortraitType.ERA_MIX, content.eraMix),
            draft(PortraitType.COVERAGE, content.coverageBand),
        )
    }

    private fun breadthOf(counts: Map<String, Int>): String {
        val total = counts.values.sum()
        if (total == 0) return BREADTH_MEDIUM
        val top = counts.values.max().toDouble() / total
        return when {
            top >= NARROW_TOP_SHARE || counts.size < 3 -> BREADTH_NARROW
            counts.size >= BROAD_MIN_DISTINCT && top < BROAD_TOP_SHARE -> BREADTH_BROAD
            else -> BREADTH_MEDIUM
        }
    }

    private fun mixOf(counts: Map<String, Int>): String {
        val total = counts.values.sum()
        if (total == 0) return MIX_MEDIUM
        val top = counts.values.max().toDouble() / total
        return when {
            top >= SINGLE_TOP_SHARE -> MIX_SINGLE
            counts.size >= VARIED_MIN_DISTINCT -> MIX_VARIED
            else -> MIX_MEDIUM
        }
    }

    private fun languageMixOf(counts: Map<String, Int>): String {
        val total = counts.values.sum()
        if (total == 0) return LANG_SINGLE
        val sorted = counts.values.sortedDescending()
        val top = sorted.first().toDouble() / total
        if (top >= LANG_DOMINANT_SHARE) return LANG_SINGLE
        val significant = sorted.count { it.toDouble() / total >= LANG_MINORITY_SHARE }
        return if (significant >= 3) LANG_MULTILINGUAL else LANG_BILINGUAL
    }

    private fun eraMixOf(counts: Map<String, Int>): String {
        val total = counts.values.sum()
        if (total == 0) return ERA_CONCENTRATED
        val top = counts.values.max().toDouble() / total
        if (top >= ERA_CONCENTRATED_SHARE) return ERA_CONCENTRATED
        val significant = counts.values.count { it.toDouble() / total >= ERA_MINORITY_SHARE }
        return if (significant >= 4) ERA_SPREAD else ERA_CONCENTRATED
    }

    private fun coverageBandOf(rate: Double): String = when {
        rate < 0.5 -> COVERAGE_LOW
        rate < 0.9 -> COVERAGE_MEDIUM
        else -> COVERAGE_HIGH
    }

    // ══════════════ 状态快照（契约 §4.3）══════════════

    /**
     * 状态证据草稿 —— 用户**显式给的**东西，来源 `T1_USER`（0.9）。
     *
     * 为什么这些能做 T1：收藏 / 明确不喜欢 / 评分 / 歌单名都需要**主动劳动**，
     * 它们是 ground truth，不像播放行为那样需要推断。
     *
     * 为什么不需要"diff 表"：证据表的唯一索引 + 计数累加本身就是变化记录 ——
     * 重算一遍，值变了就多一行、没变就只是计数 +1。契约 §4.3 说的 diff，落在库里就是这个。
     */
    fun toStateEvidenceDrafts(
        state: LibraryStateSnapshot,
        sessionId: String? = null,
    ): List<EvidenceDraft> {
        if (!state.hasAnySignal) return emptyList()
        val source = ProfileSources.T1_USER
        val confidence = ProfileSources.baseConfidence(source)
        fun draft(slot: String, value: String) = EvidenceDraft(
            predicate = PortraitType.predicateOf(PortraitType.LIBRARY, slot),
            value = value,
            source = source,
            confidence = confidence,
            sessionId = sessionId,
        )

        return buildList {
            add(draft(PortraitType.LIKED_SHARE, shareBandOf(state.likedCount, state.totalSongs)))
            add(draft(PortraitType.DISLIKED_SHARE, shareBandOf(state.dislikedCount, state.totalSongs)))
            add(draft(PortraitType.HIDDEN_FOLDERS, shareBandOf(state.hiddenFolderCount, state.totalSongs)))
            add(draft(PortraitType.USER_TAG_FIX, shareBandOf(state.userCorrectedLabelCount, state.totalSongs)))
            val names = sanitizeNames(state.playlistNames)
            if (names.isNotEmpty()) add(draft(PortraitType.PLAYLIST_NAMES, names.joinToString(",")))
        }
    }

    private fun shareBandOf(count: Int, total: Int): String = when {
        count <= 0 -> SHARE_NONE
        total <= 0 -> SHARE_SOME
        count.toDouble() / total >= SHARE_MANY_THRESHOLD -> SHARE_MANY
        else -> SHARE_SOME
    }

    /**
     * 歌单名脱敏 —— 与路径同一条纪律（契约 §7.2）：**用户手写的文本要进 system prompt，得先收紧**。
     * 含分隔符 / 过长 / 空的一律丢弃。
     */
    fun sanitizeNames(raw: List<String>): List<String> = raw
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.length <= PLAYLIST_NAME_MAX_LEN }
        .filter { name -> name.none { it == '/' || it == '\\' || it == ':' || it == '~' || it == '\n' } }
        .distinct()
        .take(MAX_PLAYLIST_NAMES)

    /** 供测试与日志用的整数百分比（避免浮点比较的脆弱断言）。 */
    internal fun percent(part: Int, total: Int): Long =
        if (total <= 0) 0L else (part.toDouble() / total * 100).roundToLong()
}
