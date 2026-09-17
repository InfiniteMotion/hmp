package com.hmp.domain.music

import com.hmp.domain.agent.enrich.EnrichBatchResult
import com.hmp.domain.agent.enrich.EnrichHealth
import com.hmp.domain.agent.enrich.EnrichWorkUnit
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.model.DailyMusicInfo
import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.domain.agent.profile.BehaviorSnapshot
import com.hmp.domain.agent.profile.LibraryContentSnapshot
import com.hmp.domain.agent.profile.LibraryStateSnapshot
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.domain.setting.model.UserUsageAnalytics
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    // Music Query
    suspend fun getAllMusicInfoAsList(orderBy: String, orderType: String): List<MusicInfo>
    fun getMusicCount(): Flow<Int>
    fun getMusicWithExtraCount(): Flow<Int>
    fun getMusicWithMissingExtraCount(): Flow<Int>
    fun getMusicInfoById(musicId: Long): Flow<MusicInfo?>
    suspend fun getMusicListByArtist(artistName: String): List<MusicInfo>
    suspend fun getMusicListByAlbum(albumName: String): List<MusicInfo>

    /** 按歌手聚合：返回 (歌手名, 作品数) 列表，按作品数降序。 */
    suspend fun getAllArtistsSummary(limit: Int): List<Pair<String, Int>>

    /** 按专辑聚合：返回 (专辑名, 曲目数) 列表，按曲目数降序。 */
    suspend fun getAllAlbumsSummary(limit: Int): List<Pair<String, Int>>
    suspend fun searchMusic(query: String): List<MusicInfo>

    // Music Random
    suspend fun getRandomMusicInfoWithMissingExtra(): MusicInfo?
    suspend fun getRandomMusicInfoWithExtra(): MusicInfo?

    // Music Action (Like/Dislike)
    suspend fun updateLikedStatus(id: Long, liked: Boolean)
    suspend fun getLikedStatus(id: Long): Boolean

    /** G6：批量取全部已收藏曲目 id（私人推荐种子源） */
    suspend fun getLikedMusicIds(): List<Long>

    /** 从曲库软删除：标记指定 id 的 music / musicExtra / userInfo 为已删除，列表查询将不再返回。 */
    suspend fun removeFromLibrary(ids: List<Long>)

    /** 恢复：将指定 id 的 music / musicExtra / userInfo 标记为未删除，列表查询将重新返回。 */
    suspend fun restoreToLibrary(ids: List<Long>)

    /** 已软删除的歌曲按父文件夹路径分组，用于「隐藏文件夹」的恢复。返回 (文件夹路径, 该文件夹下已删除歌曲的 id 列表)。 */
    suspend fun getDeletedMusicIdsGroupedByFolder(): List<Pair<String, List<Long>>>

    // Labels
    suspend fun addMusicLabel(label: MusicLabel)

    /**
     * 用户修正标签（T1 路径，UI/agent 修正调用）——source=USER，永不被模型认识覆盖
     * （设计总纲 3.2 规则 ①）。
     */
    suspend fun addUserMusicLabel(label: MusicLabel, confidence: Double = 1.0)

    fun getLabelNamesByType(type: LabelCategory): Flow<List<LabelName>>
    suspend fun getMusicIdListByType(label: LabelName, limit: Int = 100): List<Long>

    /** 删除某首歌的 USER 源标签（只删 source=USER 的；LLM 富化标签不受影响）。 */
    suspend fun removeUserMusicLabel(musicId: Long, label: LabelName)
    suspend fun getMusicLabels(musicId: Long): List<MusicLabel>

    /** 编辑单曲标签（ID3 元数据），写入文件成功后同步更新本地曲库记录。 */
    suspend fun updateMusicTags(musicId: Long, tags: EditableMusicTags): Result<Unit>

    /**
     * 标签文件已由调用方写入（如 Android SAF 授权后直接写入），仅同步本地曲库记录。
     */
    suspend fun refreshMusicTags(musicId: Long, tags: EditableMusicTags): Result<Unit>

    // 相似度推荐 (Similarity)
    suspend fun getSimilarSongsByWeightedLabels(musicId: Long, limit: Int = 10): List<MusicInfo>

    // 收听时长统计
    fun getRecentListeningDurations(limit: Int = 7): Flow<List<ListeningDuration>>

    // 额外信息 / AI (Extra Info / AI)
    suspend fun getMusicLyrics(musicId: Long): String?
    suspend fun insertMusicExtra(musicId: Long, musicExtraInfo: DailyMusicInfo)
    suspend fun getMusicExtraById(musicId: Long): DailyMusicInfo

    // Device Scan
    suspend fun loadMusicFromDevice(): Result<Unit>
    val isScanning: Flow<Boolean>

    // Device Scan - Incremental
    suspend fun syncMusicFromDeviceIncremental(): Result<Unit>

    // AI / Extra Fetching
    suspend fun validateProviderApiKey(config: AiEndpointConfig): Result<Boolean>

    suspend fun fetchAvailableModels(config: AiEndpointConfig): Result<List<String>>

    // Listening Duration
    suspend fun insertPlayback(history: PlaybackHistory): Long
    suspend fun updatePlaybackRecord(id: Long, duration: Long, isCompleted: Boolean)
    suspend fun recordListeningDuration(duration: Long)
    fun getPlaybackHistory(musicId: Long, limit: Int = 5): Flow<List<PlaybackHistory>>
    suspend fun getRecentPlaybackHistoryGlobal(limit: Int): List<PlaybackHistory>

    // User Usage Analytics
    suspend fun getUserUsageAnalytics(): UserUsageAnalytics

    // User Stats
    suspend fun incrementPlayCount(musicId: Long)
    suspend fun incrementSkippedCount(musicId: Long)
    suspend fun updateLastPlayed(musicId: Long, timestamp: Long)

    // Snapshot Export/Import
    suspend fun exportMusicUserStateSnapshot(): com.hmp.domain.backup.MusicUserStateSnapshot
    suspend fun restoreMusicUserState(snapshot: com.hmp.domain.backup.MusicUserStateSnapshot)
    
    suspend fun exportListeningStatsSnapshot(): com.hmp.domain.backup.ListeningStatsSnapshot
    suspend fun restoreListeningStats(snapshot: com.hmp.domain.backup.ListeningStatsSnapshot)

    // ===== Agent T2: 富化健康度查询 =====

    /** 富化健康度快照（Master 启动时检测覆盖率） */
    suspend fun getEnrichHealth(): EnrichHealth

    /** 获取未富化的歌曲（没有任何 LLM/AGENT 源标签的），返回前 limit 首 */
    suspend fun getUnenrichedSongs(limit: Int): List<MusicInfo>

    /**
     * 取下一个富化工作单元：
     * - 大歌手（≥ bigArtistThreshold 首未富化）→ ArtistGroup（可预热）
     * - 小歌手累计到 mixGroupSize → MixedGroup（跳过 Round 0）
     * - 全部富化完 → null
     *
     * 内部逻辑：取全部 unenriched → groupBy artist → count DESC → 依次吃大歌手 + 攒小歌手。
     */
    suspend fun fetchNextEnrichWorkUnit(
        bigArtistThreshold: Int = 3,
        mixGroupSize: Int = 10,
    ): EnrichWorkUnit?

    /** 获取之前富化失败的歌曲重试批次（简化版：low confidence 的） */
    suspend fun getFailedEnrichSongs(limit: Int): List<MusicInfo>

    /** 最近富化结果验收：自 since 时间戳以来的成功/失败统计 */
    suspend fun getRecentEnrichResults(since: Long): EnrichBatchResult

    // ═══ W0 HelloSubAgent 新增依赖 ═══

    /** 7 天内跳过率最高的 N 首歌 ID（RECOMMEND 反推不该推荐什么） */
    suspend fun getRecentSkipRate(limit: Int, days: Int = 7): List<Long>

    /** 7 天内播放率最高的 N 首歌 ID（RECOMMEND 正推该推荐什么） */
    suspend fun getRecentPlayRate(limit: Int, days: Int = 7): List<Long>

    /** days 天内未播放的曲目 (musicId, lastPlayedMs)。lastPlayedMs 为 null 表示从未播放过。按最久未播排序。 */
    suspend fun getForgottenTracks(days: Int, limit: Int = 10): List<Pair<Long, Long?>>

    /** N 年前的今天首次播放的曲目（ANNIVERSARY 卡；date 参数格式 yyyy-MM-dd，和 todayDateString() 一致）。
     * 按 firstPlayedAt 升序（最久的在前），返回 (musicId, firstPlayedAtMs, thatDayPlays)。 */
    suspend fun getAnniversaryTracks(date: String): List<Triple<Long, Long, Int>>;

    /** 歌单创建纪念日候选（ANNIVERSARY 卡）。按 createdAt 升序（最久在前）。 */
    suspend fun getAnniversaryPlaylists(date: String): List<PlaylistAnniversaryRow>;

    /** 所有歌的累计时长 + 播放次数视图（播放里程碑 + 时长里程碑候选）。 */
    suspend fun getAllMusicDurations(): List<MusicDurationRow>;

    /** 今日有新播放的 musicId 集合（用于检查今天是否跨过播放里程碑）。 */
    suspend fun getMusicIdsPlayedOn(date: String): List<Long>;

    /** 某歌单累计被播放次数。 */
    suspend fun getPlaybackCountForPlaylist(playlistName: String): Int;

    /** 某风格 label 下的曲目（DISCOVER 兜底 + RECOMMEND 时段匹配；等价于 getMusicIdListByType，额外提供别名） */
    suspend fun getRecentTracksByLabel(label: LabelName, limit: Int): List<Long> =
        getMusicIdListByType(label, limit)

    /** 最近 30 天活跃过的风格 label 排序（DISCOVER 兜底 + RadioSubAgent 共用） */
    suspend fun getGlobalTopLabels(limit: Int): List<LabelName>

    /** 按 ID 批量查 MusicInfo（RadioSubAgent + HelloSubAgent 共用，替代 private 扩展函数） */
    suspend fun getMusicInfoByIds(ids: List<Long>): List<MusicInfo>

    /** 最近 days 天的日均听歌时长（分钟），报告叙事段自适应频率判断用 */
    suspend fun getAvgDailyListeningMinutes(days: Int = 30): Float

    // ── F9-T1：时段分布 + 遗忘唤醒送达标记 ──

    /**
     * 时段分布：窗口内按小时分桶的播放次数 + 累计时长。
     * 给报告页时段分布 chart 用。**只返回实际有数据的桶**（UI 层补零位）。
     */
    suspend fun getHourlyDistribution(windowDays: Int): List<HourlyDistributionRow>

    /**
     * 遗忘唤醒送达标记：把一首被推送过的"遗忘曲目"标记为已送达，
     * 后续 getForgottenTracks 会排除它（或 30 天内不再推送同一首）。
     */
    suspend fun markForgottenDelivered(musicId: Long)

    /** 查询某首歌是否在送达标记里（排除查询辅助）。 */
    suspend fun isForgottenDelivered(musicId: Long): Boolean

    // ── 用户认识模块（画像）的三个快照 ── 见 design/agent-profile.md §4.1 / §4.2 / §4.3 ──

    /**
     * 曲库内容快照（阶段二建模输入）：标签分布 + 覆盖率。
     * 覆盖率同时用于判断"该不该跑内容建模"与给置信度打折。
     */
    suspend fun getLibraryContentSnapshot(): LibraryContentSnapshot

    /** 曲库状态快照：收藏 / 明确不喜欢 / 手工歌单 / 隐藏文件夹 / 用户修正标签。 */
    suspend fun getLibraryStateSnapshot(): LibraryStateSnapshot

    /** 行为快照：窗口内的播放明细聚合（时段 / 完播 / 跳过点 / 重复度 / 新歌比）。 */
    suspend fun getBehaviorSnapshot(windowDays: Int = 90): BehaviorSnapshot

    // ═══════════════════════════════════════════════════════════════
    // 带时间窗口的统计查询（方案 B：维度拆分，每条独立 SQL）
    // days=-1 表示"全部"
    // ═══════════════════════════════════════════════════════════════

    /** 窗口内总时长 / 完播率 / 跳过率 / 播放次数 / 跳过次数。 */
    suspend fun getWindowedAnalytics(days: Int): com.hmp.domain.setting.model.WindowedUsageAnalytics

    /** 窗口内播放来源分布（source → 次数）。 */
    suspend fun getWindowedSourceBreakdown(days: Int): Map<String, Int>

    /** 窗口内 Top N 标签（按播放次数加权）。category 为 GENRE/MOOD/SCENARIO。 */
    suspend fun getWindowedTopLabels(
        days: Int,
        category: com.hmp.data.database.myenum.LabelCategory,
        limit: Int = 5
    ): List<com.hmp.domain.setting.model.LabelCountEntry>

    /** 窗口内 Top N 歌曲（歌名 + 歌手 + 播放次数）。 */
    suspend fun getWindowedTopSongs(days: Int, limit: Int = 5): List<com.hmp.domain.setting.model.TopPlayedEntry>

    /** 窗口内最近 N 条播放（歌名 + 歌手 + 播放时间）。 */
    suspend fun getWindowedRecentPlayback(days: Int, limit: Int = 10): List<com.hmp.domain.setting.model.RecentPlaybackEntry>
}

/** 歌单创建纪念日候选。 */
data class PlaylistAnniversaryRow(
    val playlistId: Long,
    val playlistName: String,
    val createdAt: Long,
    val songCount: Int,
    val playbackCount: Int,
)

/** 单首歌累计时长 + 播放次数（用于播放/时长里程碑候选）。 */
data class MusicDurationRow(
    val musicId: Long,
    val totalMs: Long,
    val playCount: Int,
)

/** F9-T1：时段分布行（窗口内某小时的播放次数 + 累计时长）。 */
data class HourlyDistributionRow(
    val hour: Int,      // 0-23
    val playCount: Int,
    val totalMs: Long,
)
