package com.hmp.domain.agent.sub

import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════════
// W0 HelloSubAgent 卡片模型
// 放在 agent/sub/ 同级，与 RadioSubAgent / EnrichSubAgent 同级
// ═══════════════════════════════════════════════════════════════════

/** 一天中的时段（minuteTickLoop 每分钟检测，用于 ANCHOR 卡 + RECOMMEND 时段匹配） */
@Serializable
enum class TimePhase {
    NIGHT,           // 0-6  深夜
    MORNING_COMMUTE, // 7-9  早高峰
    WORK,            // 9-12 / 14-18 工作
    LUNCH,           // 12-14 午休
    EVENING_COMMUTE, // 18-20 晚高峰
    EVENING_LEISURE, // 20-23 晚间休闲
    UNKNOWN,
}

/** 根据小时数推算时段 */
fun detectTimePhase(hour: Int): TimePhase = when (hour) {
    in 0..6 -> TimePhase.NIGHT
    in 7..9 -> TimePhase.MORNING_COMMUTE
    in 9..11 -> TimePhase.WORK
    in 12..13 -> TimePhase.LUNCH
    in 14..17 -> TimePhase.WORK
    in 18..19 -> TimePhase.EVENING_COMMUTE
    in 20..23 -> TimePhase.EVENING_LEISURE
    else -> TimePhase.UNKNOWN
}

/** 报告叙事段的时间维度 */
@Serializable
enum class NarrativeTimeRange { ALL, DAY, WEEK, MONTH, YEAR }

// ═══════════════════════════════════════════════════════════════════
// SlideType + SlideContent 密封接口 + SlideCard
// ═══════════════════════════════════════════════════════════════════

/** 卡的类型枚举 */
@Serializable
enum class SlideType {
    ANCHOR,         // 正在听（常驻）
    RADIO_STATUS,   // 电台运行态（0=常驻，Radio 停止时 pop）
    GREETING,       // 问候 + DJ 衔接语（10s）
    RECOMMEND,      // 可解释推荐（15s）
    DISCOVER,       // 歌手/风格探索（12s）
    FORGOTTEN,      // 遗忘唤醒（12s）
    ANNIVERSARY,    // 纪念日（15s）
    ENRICH_TRACKING,// Enrich 富化进度（活跃时显示，完成后隐藏）
}

// ═══════════════════════════════════════════════════════════════════

/** 每种卡的内容 sealed interface——可序列化，存 DAO */
@Serializable
sealed interface SlideContent

// ────────────────────────────────────────────────────────────────────
// 家族 A：ANCHOR —— 播放器快照（正在听）
// ────────────────────────────────────────────────────────────────────

@Serializable
data class AnchorContent(
    /** 歌曲名 */
    val trackTitle: String?,
    /** 艺术家 */
    val artistName: String?,
    /** BPM（null = 未知）*/
    val bpm: Int?,
    /** 当前时段 */
    val phase: TimePhase?,
    /** 封面 URI（null 表示无封面）*/
    val albumArtUri: String?,
    /** 播放状态：true=正在播放 */
    val isPlaying: Boolean,
    /** 总时长（秒），0=未知 */
    val durationSec: Int,
    /** 当前进度百分比 0-100 */
    val progressPercent: Int,
    /** 来源标签名（歌单/电台/手动播放——null=手动播放）*/
    val sourceLabel: String?,
) : SlideContent

// ────────────────────────────────────────────────────────────────────
// 家族 B1：RECOMMEND —— AI 推荐单曲
// ────────────────────────────────────────────────────────────────────

@Serializable
data class RecommendContent(
    val trackId: Long,
    val trackTitle: String,
    /** AI 推荐理由 */
    val reason: String,
    /** 结合的时段 */
    val currentPhase: TimePhase,
    /** 来源（歌单/电台名，null=AI 直接推荐）*/
    val sourceLabel: String?,
    /** 时长（秒，0=未知） */
    val durationSec: Int,
) : SlideContent

// ────────────────────────────────────────────────────────────────────
// 家族 B2：FORGOTTEN —— 遗忘唤醒
// ────────────────────────────────────────────────────────────────────

@Serializable
data class ForgottenContent(
    val trackId: Long,
    val trackTitle: String,
    /** 上次播放距今天数 */
    val daysSince: Int,
    /** 总播放次数 */
    val playCount: Int,
    /** 情感化文案（如"那年你循环了 N 遍"，null=兜底）*/
    val emotionText: String?,
    /** 时长（秒，0=未知） */
    val durationSec: Int,
) : SlideContent

// ────────────────────────────────────────────────────────────────────
// 家族 B3：ANNIVERSARY —— 纪念日
// ────────────────────────────────────────────────────────────────────

/** ANNIVERSARY 子类型：决定 UI 文案和展示差异。 */
@Serializable
enum class AnniversarySubtype {
    /** N 年前的今天，第一次播放这首歌 */
    FIRST_PLAY,
    /** 累计播放次数跨过里程碑（第 100/500/1000 次） */
    PLAY_MILESTONE,
    /** 累计听歌时长跨过里程碑（10h/50h/100h） */
    DURATION_MILESTONE,
    /** N 年前的今天，创建了这个歌单 */
    PLAYLIST_CREATE,
}

@Serializable
data class AnniversaryContent(
    /** 子类型——决定文案分支；默认 FIRST_PLAY 兼容旧版 Room JSON 无 subtype 字段 */
    val subtype: AnniversarySubtype = AnniversarySubtype.FIRST_PLAY,
    /** 单曲 ID（FIRST_PLAY/PLAY_MILESTONE/DURATION_MILESTONE 用；PLAYLIST_CREATE 为 0） */
    val trackId: Long,
    /** 歌名（PLAYLIST_CREATE 时换成歌单名） */
    val trackTitle: String,
    /** 几年前（FIRST_PLAY/PLAYLIST_CREATE 用；里程碑类型为 null） */
    val yearsAgo: Int? = null,
    /** 里程碑值（PLAY_MILESTONE=第 N 次；DURATION_MILESTONE=N 小时） */
    val milestoneValue: Int? = null,
    /** 累计总播放次数（单曲 / 歌单） */
    val totalPlays: Int,
    /** 累计听歌总时长（小时，0.1 精度） */
    val totalListenHours: Float? = null,
    /** 具体日期（如 "09.08 · 6 年前"） */
    val specificDate: String? = null,
    /** 首次播放那天的播放次数（仅 FIRST_PLAY） */
    val thatDayPlays: Int? = null,
    /** 情感化文案（null=兜底） */
    val emotionText: String?,
    /** 歌曲时长（秒，PLAYLIST_CREATE 为 0） */
    val durationSec: Int = 0,
) : SlideContent

// ────────────────────────────────────────────────────────────────────
// 家族 C：DISCOVER —— 多曲目探索
// ────────────────────────────────────────────────────────────────────

@Serializable
data class DiscoverContent(
    /** 探索主题（如 "POP"）*/
    val target: String,
    /** 探索理由 */
    val reason: String,
    /** 完整曲目 ID 列表（UI 取前 3 首展示）*/
    val trackIds: List<Long>,
    /** 预取的曲目标题（与 trackIds 同序，用于 UI 零延迟渲染）。空表示 UI 需异步查。 */
    val trackTitles: List<String> = emptyList(),
    /** 预取的曲目艺术家（与 trackIds 同序）。空表示 UI 需异步查。 */
    val trackArtists: List<String> = emptyList(),
    /** 预取的曲目时长（秒，与 trackIds 同序） */
    val trackDurations: List<Int> = emptyList(),
) : SlideContent

// ────────────────────────────────────────────────────────────────────
// 家族 D：RADIO_STATUS —— 电台运行态（左 ANCHOR + 右 Radio 状态）
// ────────────────────────────────────────────────────────────────────

@Serializable
data class RadioStatusContent(
    /** 电台主题（"蓝调" / seed 提取的风格关键词，null=自动电台） */
    val stationTheme: String?,
    /** 状态文字："播放中" / "已暂停" / BUILDING 时面向用户的提示 */
    val actionText: String,

    // —— 左侧简化 ANCHOR ——
    /** 当前正在播的歌名 */
    val nowPlayingTitle: String?,
    /** 当前正在播的歌手 */
    val nowPlayingArtist: String?,
    /** 封面 URI（null=无封面，UI 显示 fallback 图标） */
    val albumArtUri: String?,

    // —— 右侧常驻核心区 ——
    /** 当前播放曲目的 LLM 推荐理由（🌟 电台核心差异化） */
    val nowPlayingWhy: String?,
    /** 下一首歌名 */
    val nextTrackTitle: String?,
    /** 下一首的 LLM 推荐理由 */
    val nextTrackWhy: String?,
    /** playlist 总曲目数 */
    val playlistCount: Int?,

    // —— BUILDING 专属（PLAYING 时 null）——
    /** 构建进度 0-100 */
    val progressPercent: Int?,
    /** 目标曲目数（构建时显示） */
    val targetCount: Int?,
) : SlideContent

// ────────────────────────────────────────────────────────────────────
// 家族 E：GREETING —— 音乐百科小卡片
// ────────────────────────────────────────────────────────────────────

/** GREETING 内容类型——6 种音乐内容方向 */
@Serializable
enum class GreetingType {
    /** 音乐名句/哲思 */
    QUOTE,
    /** 歌词金句 */
    LYRIC_GOLD,
    /** 音乐冷知识 */
    FACT,
    /** 歌曲/专辑/音乐家故事 */
    STORY,
    /** 音乐家轶事 */
    ARTIST,
    /** 听歌引导/应景推荐 */
    LISTEN,
}

@Serializable
data class GreetingContent(
    /** 问候主文本 */
    val text: String,
    /** 是否为兜底（非 LLM 生成）*/
    val fromFallback: Boolean,
    /** 当前时段（用于时段感知文案）*/
    val phase: TimePhase,
    /** 内容类型 */
    val type: GreetingType = GreetingType.QUOTE,
    /** 上下文简述（如 "来自《晴天》"） */
    val contextHint: String? = null,
) : SlideContent

/** GreetingType → 中文显示名（domain + ui 共用） */
fun GreetingType.zhName(): String = when (this) {
    GreetingType.QUOTE -> "音乐名句"
    GreetingType.LYRIC_GOLD -> "歌词金句"
    GreetingType.FACT -> "音乐冷知识"
    GreetingType.STORY -> "歌曲故事"
    GreetingType.ARTIST -> "音乐家轶事"
    GreetingType.LISTEN -> "听歌引导"
}

// ────────────────────────────────────────────────────────────────────
// SlideCard 主数据类
// ────────────────────────────────────────────────────────────────────

/** SlideCard 主数据类 */
@Serializable
data class SlideCard(
    val cardId: String,
    val type: SlideType,
    val content: SlideContent,
    val visible: Boolean = true,     // 引擎层决定：这张卡今天是否在 Pager 中展示
    val focusedAt: Long = 0L,        // replace 时引擎层设为当前时间；>0 时 UI 聚焦展示并暂停轮播
) {
    companion object {
        /** KMP commonMain 没有 java.util.UUID，用计数器 + 时间戳 + 随机数生成唯一 ID */
        private var seqCounter = 0L

        fun newId(): String {
            val seq = ++seqCounter
            val ts = com.hmp.data.database.currentTimeMillis()
            val rnd = kotlin.random.Random.nextInt(1_000_000)
            return "card_${ts}_${seq}_$rnd"
        }
    }
}

// ────────────────────────────────────────────────────────────────────
// 家族 E：ENRICH_TRACKING —— 富化进度追踪
// ────────────────────────────────────────────────────────────────────

@Serializable
data class EnrichTrackingContent(
    /** 当前状态名：RUNNING / PAUSED / UNREGISTERED */
    val state: String,
    /** 已处理歌曲数 */
    val processed: Int,
    /** 成功富化歌曲数 */
    val success: Int,
    /** 失败歌曲数 */
    val failed: Int,
    /** 当前工作单元大小 */
    val currentUnitSize: Int,
    /** 是否活跃（RUNNING 或 PAUSED = true） */
    val active: Boolean,
    /** 当前处理的 artist 名（混合组为 GROUP_KEY_MIXED） */
    val currentArtist: String?,
    /** 当前 chunk 序号（1-based） */
    val chunkIndex: Int,
    /** 当前 workUnit 总 chunk 数 */
    val chunkTotal: Int,
    /** 当前阶段文本（Round 1/2a/2b/3 等） */
    val phase: String,
) : SlideContent
