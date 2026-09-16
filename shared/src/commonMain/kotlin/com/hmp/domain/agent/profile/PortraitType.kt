package com.hmp.domain.agent.profile

/**
 * 侧写类型与槽位**闭集**。
 *
 * 契约：`docs/7_x/B agent-build/design/agent-profile.md` v3.1 §5
 *
 * 两条不能破的纪律：
 *
 * 1. **槽位是闭集** —— 开放槽位会让侧写退化成自由文本：UI 没有稳定形态、
 *    LLM 没有稳定输入、设置页无法逐项展示与删除。
 * 2. **谓词就是"类型.槽位"** —— 不另设一套谓词表。两套闭集互相映射只会漂移
 *    （v1 曾定「谓词闭集 7 条」，已被此替代）。
 *
 * 要加类型或槽位，先改契约，再改这里。
 */

/** 分级记忆的落层。L1 当下态是会话内存（`SessionStore`），不落库，故不在此枚举内。 */
enum class PortraitTier(val id: String) {
    /** 情境态（短时人格）：天~周，带衰减 */
    L2("L2"),

    /** 性情态（人性）：月~年，需长期背离才降权，不自动删除 */
    L3("L3"),
}

/**
 * 侧写的成熟机制 —— 决定它靠什么变得可信。
 *
 * 这个区分不是为了分类，而是因为**三类侧写的晋升路径不同**：
 * 曲库是长期资产（导入即全量，与时间无关），行为靠累积，对话靠被行为确证。
 */
enum class PortraitMaturity {
    /** 曲库：按**认知深度**成熟（形态 → 内容）。导入即全量，**不靠跨会话晋升**，直接落 L3 */
    LIBRARY,

    /** 行为与状态：按**时间**成熟（L2 → L3，见契约 §3.1 跨层流动规则） */
    BEHAVIOR,

    /** 对话推断：默认落 L2、**不自动升 L3**，须被播放行为确证 */
    DIALOGUE,
}

/**
 * 槽位键的**唯一事实来源**。
 *
 * ⚠️ **为什么这些常量不在 [PortraitType] 的 companion 里**：
 * 枚举条目的构造函数在 companion 初始化**之前**执行，条目若引用 companion 的成员，
 * 编译器会直接报 `Companion object ... is uninitialized here`（`const val` 也救不了，
 * 这条路上没有内联）。放进一个独立的顶层对象即可 —— 条目引用它没有顺序依赖。
 */
object PortraitSlots {
    // —— 曲库侧写（形态阶段）——
    const val SCALE = "scale"
    const val ARTIST_CONCENTRATION = "artistConcentration"
    const val ALBUM_COMPLETENESS = "albumCompleteness"
    const val PATH_STRUCTURE = "pathStructure"
    const val PATH_GROUPS = "pathGroups"
    const val DURATION_TENDENCY = "durationTendency"

    // —— 曲库侧写（状态快照族：用户显式给的东西，T1_USER 0.9）——
    const val LIKED_SHARE = "likedShare"
    const val DISLIKED_SHARE = "dislikedShare"
    const val PLAYLIST_NAMES = "playlistNames"
    const val HIDDEN_FOLDERS = "hiddenFolders"
    const val USER_TAG_FIX = "userTagFix"

    // —— 曲库侧写（内容阶段，阶段二补）——
    const val GENRE_BREADTH = "genreBreadth"
    const val LANGUAGE_MIX = "languageMix"
    const val ERA_MIX = "eraMix"
    const val MOOD_MIX = "moodMix"
    const val COVERAGE = "coverage"

    // —— 时间 / 口味 / 听法 / 探索 ——
    const val PRIMARY_PART = "primaryPart"
    const val SECONDARY_PART = "secondaryPart"
    const val PART_STABILITY = "partStability"
    const val LEADING_GENRE = "leadingGenre"
    const val TOP_ARTISTS = "topArtists"
    /** 用户不喜欢听的风格（T0b 新增，供对话抽取；value 是自由短语，如"快歌"） */
    const val DISLIKED_STYLES = "dislikedStyles"
    const val COMPLETION_RATE = "completionRate"
    const val SKIP_POINT = "skipPoint"
    const val REPEAT_RATE = "repeatRate"
    const val NEW_RATIO = "newRatio"
    const val RETURN_RATE = "returnRate"

    // —— B 类（只能降级表达）——
    const val DETERMINISM = "determinism"
    const val STRUCTURE_TOLERANCE = "structureTolerance"
    const val ENERGY_TENDENCY = "energyTendency"
    const val MOOD_CONSISTENCY = "moodConsistency"
    const val SESSION_LENGTH = "sessionLength"
    const val FRAGMENTATION = "fragmentation"

    /** 类型 id 之外的**独立谓词**：用户否决了某个侧写，value = 被否决的 [PortraitType.id] */
    const val DENIED_PREDICATE = "portrait.denied"
}

/**
 * 侧写类型闭集（契约 §5 的 A 类 5 个 + B 类 3 个）。
 *
 * [weakEvidence] 为 true 的 B 类**只能降级表达** —— 渲染时必须以
 * 「你在音乐里表现出的…」起头，不得直接下人格断言（契约 §5.2）。
 */
enum class PortraitType(
    val id: String,
    val displayName: String,
    val maturity: PortraitMaturity,
    val weakEvidence: Boolean,
    slotKeys: List<String>,
) {
    // ── A 类 · 可硬确证 ──────────────────────────────────────────────
    LIBRARY(
        id = "library_portrait",
        displayName = "曲库侧写",
        maturity = PortraitMaturity.LIBRARY,
        weakEvidence = false,
        slotKeys = listOf(
            PortraitSlots.SCALE,
            PortraitSlots.ARTIST_CONCENTRATION,
            PortraitSlots.ALBUM_COMPLETENESS,
            PortraitSlots.PATH_STRUCTURE,
            PortraitSlots.PATH_GROUPS,
            PortraitSlots.DURATION_TENDENCY,
            // 状态快照族（用户显式给的，来源 T1_USER）
            PortraitSlots.LIKED_SHARE,
            PortraitSlots.DISLIKED_SHARE,
            PortraitSlots.PLAYLIST_NAMES,
            PortraitSlots.HIDDEN_FOLDERS,
            PortraitSlots.USER_TAG_FIX,
            // 阶段二（内容建模）追加：
            PortraitSlots.GENRE_BREADTH,
            PortraitSlots.LANGUAGE_MIX,
            PortraitSlots.ERA_MIX,
            PortraitSlots.MOOD_MIX,
            PortraitSlots.COVERAGE,
        ),
    ),
    TIME(
        id = "time_portrait",
        displayName = "时间侧写",
        maturity = PortraitMaturity.BEHAVIOR,
        weakEvidence = false,
        slotKeys = listOf(
            PortraitSlots.PRIMARY_PART,
            PortraitSlots.SECONDARY_PART,
            PortraitSlots.PART_STABILITY,
        ),
    ),
    TASTE(
        id = "taste_portrait",
        displayName = "口味侧写",
        maturity = PortraitMaturity.BEHAVIOR,
        weakEvidence = false,
        slotKeys = listOf(
            PortraitSlots.LEADING_GENRE,
            PortraitSlots.GENRE_BREADTH,
            PortraitSlots.LANGUAGE_MIX,
            PortraitSlots.ERA_MIX,
            PortraitSlots.TOP_ARTISTS,
            PortraitSlots.DISLIKED_STYLES,
        ),
    ),
    HABITS(
        id = "habits_portrait",
        displayName = "听法侧写",
        maturity = PortraitMaturity.BEHAVIOR,
        weakEvidence = false,
        slotKeys = listOf(
            PortraitSlots.COMPLETION_RATE,
            PortraitSlots.SKIP_POINT,
            PortraitSlots.REPEAT_RATE,
        ),
    ),
    EXPLORATION(
        id = "exploration_portrait",
        displayName = "探索侧写",
        maturity = PortraitMaturity.BEHAVIOR,
        weakEvidence = false,
        // 「来源构成」已随契约 v3.1 §4.2 取消（PlaybackHistory.source 是切换原因，不是来源）
        slotKeys = listOf(
            PortraitSlots.NEW_RATIO,
            PortraitSlots.RETURN_RATE,
        ),
    ),

    // ── B 类 · 可弱确证（只能降级表达）──────────────────────────────
    ORDER(
        id = "order_portrait",
        displayName = "秩序偏好",
        maturity = PortraitMaturity.BEHAVIOR,
        weakEvidence = true,
        slotKeys = listOf(
            PortraitSlots.DETERMINISM,
            PortraitSlots.STRUCTURE_TOLERANCE,
        ),
    ),
    INTENSITY(
        id = "intensity_portrait",
        displayName = "情绪强度",
        maturity = PortraitMaturity.BEHAVIOR,
        weakEvidence = true,
        slotKeys = listOf(
            PortraitSlots.ENERGY_TENDENCY,
            PortraitSlots.MOOD_CONSISTENCY,
        ),
    ),
    ATTENTION(
        id = "attention_portrait",
        displayName = "注意力模式",
        maturity = PortraitMaturity.BEHAVIOR,
        weakEvidence = true,
        slotKeys = listOf(
            PortraitSlots.SESSION_LENGTH,
            PortraitSlots.FRAGMENTATION,
        ),
    ),
    ;

    /** 该类型的槽位闭集（构造期注入，实例上可直接读） */
    val slots: List<String> = slotKeys

    companion object {
        // 转发到 PortraitSlots —— 让调用方可以统一写 `PortraitType.XXX`。
        // 这些是**属性**而非 `const val`，不参与初始化，故不受上面那个顺序问题影响。
        val SCALE: String get() = PortraitSlots.SCALE
        val ARTIST_CONCENTRATION: String get() = PortraitSlots.ARTIST_CONCENTRATION
        val ALBUM_COMPLETENESS: String get() = PortraitSlots.ALBUM_COMPLETENESS
        val PATH_STRUCTURE: String get() = PortraitSlots.PATH_STRUCTURE
        val PATH_GROUPS: String get() = PortraitSlots.PATH_GROUPS
        val DURATION_TENDENCY: String get() = PortraitSlots.DURATION_TENDENCY
        val LIKED_SHARE: String get() = PortraitSlots.LIKED_SHARE
        val DISLIKED_SHARE: String get() = PortraitSlots.DISLIKED_SHARE
        val PLAYLIST_NAMES: String get() = PortraitSlots.PLAYLIST_NAMES
        val HIDDEN_FOLDERS: String get() = PortraitSlots.HIDDEN_FOLDERS
        val USER_TAG_FIX: String get() = PortraitSlots.USER_TAG_FIX
        val GENRE_BREADTH: String get() = PortraitSlots.GENRE_BREADTH
        val LANGUAGE_MIX: String get() = PortraitSlots.LANGUAGE_MIX
        val ERA_MIX: String get() = PortraitSlots.ERA_MIX
        val MOOD_MIX: String get() = PortraitSlots.MOOD_MIX
        val COVERAGE: String get() = PortraitSlots.COVERAGE
        val PRIMARY_PART: String get() = PortraitSlots.PRIMARY_PART
        val SECONDARY_PART: String get() = PortraitSlots.SECONDARY_PART
        val PART_STABILITY: String get() = PortraitSlots.PART_STABILITY
        val LEADING_GENRE: String get() = PortraitSlots.LEADING_GENRE
        val TOP_ARTISTS: String get() = PortraitSlots.TOP_ARTISTS
        val DISLIKED_STYLES: String get() = PortraitSlots.DISLIKED_STYLES
        val COMPLETION_RATE: String get() = PortraitSlots.COMPLETION_RATE
        val SKIP_POINT: String get() = PortraitSlots.SKIP_POINT
        val REPEAT_RATE: String get() = PortraitSlots.REPEAT_RATE
        val NEW_RATIO: String get() = PortraitSlots.NEW_RATIO
        val RETURN_RATE: String get() = PortraitSlots.RETURN_RATE
        val DETERMINISM: String get() = PortraitSlots.DETERMINISM
        val STRUCTURE_TOLERANCE: String get() = PortraitSlots.STRUCTURE_TOLERANCE
        val ENERGY_TENDENCY: String get() = PortraitSlots.ENERGY_TENDENCY
        val MOOD_CONSISTENCY: String get() = PortraitSlots.MOOD_CONSISTENCY
        val SESSION_LENGTH: String get() = PortraitSlots.SESSION_LENGTH
        val FRAGMENTATION: String get() = PortraitSlots.FRAGMENTATION

        /** 「用户否决了某个侧写」的谓词（value = 类型 id） */
        const val DENIED: String = PortraitSlots.DENIED_PREDICATE

        /** 全部已知谓词（闭集）。未登记的谓词一律拒绝写入。 */
        val knownPredicates: Set<String> = entries
            .flatMap { type -> type.slots.map { predicateOf(type, it) } }
            .toSet() + DENIED

        fun byId(id: String): PortraitType? = entries.firstOrNull { it.id == id }

        /** 谓词名 = `"<portraitType>.<slotKey>"` */
        fun predicateOf(type: PortraitType, slot: String): String = "${type.id}.$slot"

        /** 解析谓词；返回 null 表示不在闭集内。 */
        fun parse(predicate: String): Pair<PortraitType, String>? {
            val typeId = predicate.substringBefore('.', missingDelimiterValue = "")
            val slot = predicate.substringAfter('.', missingDelimiterValue = "")
            val type = byId(typeId) ?: return null
            return if (slot in type.slots) type to slot else null
        }

        /** 该谓词是否属于闭集 —— 写入前的闸门（"不能自由发明谓词"，契约 §4.4）。 */
        fun isKnown(predicate: String): Boolean = predicate in knownPredicates
    }
}
