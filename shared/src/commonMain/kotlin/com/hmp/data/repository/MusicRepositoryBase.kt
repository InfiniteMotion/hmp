package com.hmp.data.repository

import com.hmp.data.database.AgentAuditLog
import com.hmp.data.database.AgentAuditLogDao
import com.hmp.data.database.ListeningDuration
import com.hmp.data.database.ListeningDurationDao
import com.hmp.data.database.MusicAllDao
import com.hmp.data.database.MusicDao
import com.hmp.data.database.MusicExtra
import com.hmp.data.database.MusicExtraDao
import com.hmp.data.database.MusicLabelDao
import com.hmp.data.database.PlaybackHistoryDao
import com.hmp.data.database.PlaylistDao
import com.hmp.data.database.PlaylistItemDao
import com.hmp.data.database.UserInfo
import com.hmp.data.database.UserInfoDao
import com.hmp.data.database.currentTimeMillis
import com.hmp.data.database.myenum.LabelCategory as DataLabelCategory
import com.hmp.data.database.myenum.LabelName as DataLabelName
import com.hmp.data.mapper.toDomain
import com.hmp.data.mapper.toEntity
import com.hmp.data.network.AiApiResult
import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.data.network.dto.MusicInfoResponse
import com.hmp.data.util.MusicTagEditor
import com.hmp.data.util.parseDateToMillis
import com.hmp.data.util.stringToPinyinSortKey
import com.hmp.data.util.todayDateString
import com.hmp.domain.agent.enrich.EnrichBatchResult
import com.hmp.domain.agent.enrich.EnrichHealth
import com.hmp.domain.backup.ListeningStatsSnapshot
import com.hmp.domain.backup.MusicExtraUserSnapshot
import com.hmp.domain.backup.MusicLabelSnapshot
import com.hmp.domain.backup.MusicUserStateSnapshot
import com.hmp.domain.backup.UserInfoSnapshot
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.EditableMusicTags
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.model.ArtistCountEntry
import com.hmp.domain.setting.model.DailyMusicInfo
import com.hmp.domain.setting.model.LabelCountEntry
import com.hmp.domain.setting.model.ListeningDuration as ListeningDurationDomain
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.domain.setting.model.RecentPlaybackEntry
import com.hmp.domain.setting.model.TopPlayedEntry
import com.hmp.domain.setting.model.UserUsageAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * MusicRepository 三端共享基类（设计总纲 B0 去重）。
 *
 * 上提 commonMain 的全部方法在 android/desktop/ios 三份实现中逐字一致（2026-08-27 复核）；
 * 平台层仅保留真正分叉的能力：
 * - 设备扫描（loadMusicFromDevice / syncMusicFromDeviceIncremental）——MediaStore vs 文件系统 vs NSFileManager；
 * - `getAllMusicInfoAsList`——三端排序语义刻意不同（android 含 raw 分键与 SQL 前缀路径）；
 * - `getRandomMusicInfoWithExtra`——desktop 手动构建绕过 Room @Relation 桌面端问题；
 * - `getDeletedMusicIdsGroupedByFolder`——File.parent vs substringBeforeLast。
 *
 * 新增 expect/actual 日期工具（todayDateString / parseDateToMillis）把三端日期差异收口，
 * 使统计类方法（recordListeningDuration / getUserUsageAnalytics）可公共化。
 */
abstract class MusicRepositoryBase(
    protected val musicDao: MusicDao,
    protected val musicExtraDao: MusicExtraDao,
    protected val userInfoDao: UserInfoDao,
    protected val musicAllDao: MusicAllDao,
    protected val musicLabelDao: MusicLabelDao,
    protected val playbackHistoryDao: PlaybackHistoryDao,
    protected val listeningDurationDao: ListeningDurationDao,
    protected val playlistDao: PlaylistDao,
    protected val playlistItemDao: PlaylistItemDao,
    protected val openAiCompatibleAdapter: OpenAiCompatibleAdapter,
    protected val json: Json,
    protected val agentAuditLogDao: AgentAuditLogDao,
) : MusicRepository {

    // region Query

    override suspend fun getRandomMusicInfoWithMissingExtra(): MusicInfo? =
        musicAllDao.getRandomMusicInfoWithMissingExtra()?.toDomain()

    override fun getMusicCount(): Flow<Int> = musicDao.getMusicCount()

    override fun getMusicWithExtraCount(): Flow<Int> = musicExtraDao.getExtraInfoNum()

    override fun getMusicWithMissingExtraCount(): Flow<Int> = musicAllDao.getMusicWithMissingExtraCount()

    override fun getMusicInfoById(musicId: Long): Flow<MusicInfo?> =
        musicAllDao.getMusicInfoById(musicId).map { it?.toDomain() }

    override suspend fun getMusicListByArtist(artistName: String): List<MusicInfo> {
        val list = musicAllDao.getMusicInfoByArtist(artistName).map { it.toDomain() }
        return list.sortedWith(
            compareBy<MusicInfo> { stringToPinyinSortKey(it.music.title) }.thenBy { it.music.title }
        )
    }

    override suspend fun getMusicListByAlbum(albumName: String): List<MusicInfo> {
        val list = musicAllDao.getMusicInfoByAlbum(albumName).map { it.toDomain() }
        return list.sortedWith(
            compareBy<MusicInfo> { stringToPinyinSortKey(it.music.title) }.thenBy { it.music.title }
        )
    }

    override suspend fun getAllArtistsSummary(limit: Int): List<Pair<String, Int>> =
        getAllMusicInfoAsList("playCount", "DESC")
            .filter { !it.music.artist.isBlank() }
            .groupBy { it.music.artist }
            .mapValues { (_, list) -> list.size }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)

    override suspend fun getAllAlbumsSummary(limit: Int): List<Pair<String, Int>> =
        getAllMusicInfoAsList("playCount", "DESC")
            .filter { !it.music.album.isBlank() }
            .groupBy { it.music.album }
            .mapValues { (_, list) -> list.size }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)

    override suspend fun searchMusic(query: String): List<MusicInfo> =
        musicAllDao.searchMusic("%$query%").map { it.toDomain() }

    // endregion

    // region Like / Delete / Restore

    override suspend fun updateLikedStatus(id: Long, liked: Boolean) {
        userInfoDao.updateLikedStatus(id, liked)
    }

    override suspend fun getLikedStatus(id: Long): Boolean = userInfoDao.getLikedStatus(id)

    override suspend fun removeFromLibrary(ids: List<Long>) {
        if (ids.isEmpty()) return
        musicDao.markDeletedByIds(ids)
        musicExtraDao.markDeletedByIds(ids)
        userInfoDao.markDeletedByIds(ids)
    }

    override suspend fun restoreToLibrary(ids: List<Long>) {
        if (ids.isEmpty()) return
        musicDao.markActiveByIds(ids)
        musicExtraDao.markActiveByIds(ids)
        userInfoDao.markActiveByIds(ids)
    }

    // endregion

    // region Labels

    /**
     * 模型认识写入（LLM 来源，富化管道主路径）。
     * 规则①（总纲 3.2）：`source=USER` 的标签永不被模型覆盖——命中 USER 记录直接拒写；
     * 规则③：T2 可被 T2 更新，`createdAt` 保留初建时间、`updatedAt` 滚动，被覆盖的旧认识
     * 同步写 `agent_audit_log`（tool=label_correction，reason 含旧值快照）——旧的进历史不消失。
     *
     * 并发注记：check-then-insert 非原子（先查 USER 再 REPLACE）。富化管道为单协程顺序执行、
     * 未来 Orchestrator 单实例串行，实际不存在同槽位并发写；如未来引入并发写入，需将
     * 该规则下沉为 DAO 事务（INSERT OR IGNORE WHERE source=USER）。
     */
    override suspend fun addMusicLabel(label: MusicLabel) {
        // UNKNOWN 为占位非知识，静默丢弃（review 2026-08-28：移除 println 调试残留）
        if (label.label == LabelName.UNKNOWN) return
        val existing = findExistingLabel(label)
        if (existing?.source == SOURCE_USER) return
        val now = currentTimeMillis()
        musicLabelDao.insert(
            label.toEntity().copy(
                source = SOURCE_LLM,
                confidence = existing?.confidence ?: DEFAULT_MODEL_CONFIDENCE,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        if (existing != null) {
            // 主数据先落库、审计后留痕：insert 失败时不再产生孤儿留痕（review 2026-08-28）
            recordLabelSuperseded(label, existing, reason = "T2 被 T2 更新（模型重新认识）")
        }
    }

    /** 用户修正写入（T1 来源）：任何来源均可覆盖，置信度默认 1.0（用户显式判断 = ground truth）。被覆盖旧认识留痕。 */
    override suspend fun addUserMusicLabel(label: MusicLabel, confidence: Double) {
        // UNKNOWN 为占位非知识，静默丢弃（review 2026-08-28：移除 println 调试残留）
        if (label.label == LabelName.UNKNOWN) return
        val existing = findExistingLabel(label)
        val now = currentTimeMillis()
        musicLabelDao.insert(
            label.toEntity().copy(
                source = SOURCE_USER,
                confidence = confidence,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        if (existing != null) {
            // 主数据先落库、审计后留痕：insert 失败时不再产生孤儿留痕（review 2026-08-28）
            recordLabelSuperseded(label, existing, reason = "T1 用户修正覆盖（USER > 一切）")
        }
    }

    private suspend fun findExistingLabel(label: MusicLabel): com.hmp.data.database.MusicLabel? =
        musicLabelDao.getLabelsById(label.musicId).firstOrNull {
            it.type.name == label.type.name && it.label.name == label.label.name
        }

    /** 规则③留痕：被覆盖的旧认识写入 agent_audit_log（label_correction），快照放 reason（args_hash 待 M4 审计体系接入）。 */
    private suspend fun recordLabelSuperseded(
        label: MusicLabel,
        old: com.hmp.data.database.MusicLabel,
        reason: String,
    ) {
        val snapshot = "musicId=${old.musicId},type=${old.type.name},label=${old.label.name}," +
            "source=${old.source},confidence=${old.confidence}," +
            "createdAt=${old.createdAt},updatedAt=${old.updatedAt}"
        agentAuditLogDao.insert(
            AgentAuditLog(
                tool = LABEL_CORRECTION_TOOL,
                outcome = "superseded",
                reason = "$reason；旧认识快照：$snapshot",
                createdAt = currentTimeMillis(),
            )
        )
    }

    override fun getLabelNamesByType(type: LabelCategory): Flow<List<LabelName>> {
        val dataType = try {
            DataLabelCategory.valueOf(type.name)
        } catch (e: Exception) {
            return flowOf(emptyList())
        }
        return musicLabelDao.getLabelsByType(dataType).map { list ->
            list.mapNotNull { dataLabel ->
                try {
                    LabelName.valueOf(dataLabel.name)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override suspend fun getMusicIdListByType(label: LabelName): List<Long> {
        val dataLabel = try {
            DataLabelName.valueOf(label.name)
        } catch (e: Exception) {
            return emptyList()
        }
        return musicLabelDao.getMusicIdListByType(dataLabel)
    }

    override suspend fun getMusicLabels(musicId: Long): List<MusicLabel> =
        musicLabelDao.getLabelsById(musicId).map { it.toDomain() }

    override suspend fun removeUserMusicLabel(musicId: Long, label: LabelName) {
        val dataLabel = try {
            DataLabelName.valueOf(label.name)
        } catch (e: Exception) {
            return
        }
        musicLabelDao.deleteUserLabel(musicId, dataLabel)
    }

    /** 编辑单曲标签（ID3 元数据）：文件写入委托平台 [MusicTagEditor]（expect/actual），本地曲库记录同步在此。 */
    override suspend fun updateMusicTags(musicId: Long, tags: EditableMusicTags): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val music = musicDao.getMusicById(musicId).firstOrNull()
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Music not found: $musicId")
                    )
                MusicTagEditor.writeTags(music.path, tags).fold(
                    onSuccess = {
                        val newTitle = tags.title?.takeIf { it.isNotBlank() } ?: music.title
                        val newArtist = tags.artist?.takeIf { it.isNotBlank() } ?: music.artist
                        val newAlbum = tags.album?.takeIf { it.isNotBlank() } ?: music.album
                        musicDao.updateMusicTags(musicId, newTitle, newArtist, newAlbum)
                        tags.lyrics?.takeIf { it.isNotBlank() }?.let { newLyrics ->
                            val existing = musicExtraDao.getExtraFieldsById(musicId)
                            musicExtraDao.insert(
                                (existing ?: MusicExtra(id = musicId, isGetExtraInfo = false))
                                    .copy(lyrics = newLyrics)
                            )
                        }
                        Result.success(Unit)
                    },
                    onFailure = { Result.failure(it) }
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** 标签文件已由调用方写入（如 Android SAF 授权），仅同步本地曲库记录。 */
    override suspend fun refreshMusicTags(musicId: Long, tags: EditableMusicTags): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                val music = musicDao.getMusicById(musicId).firstOrNull()
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Music not found: $musicId")
                    )
                val newTitle = tags.title?.takeIf { it.isNotBlank() } ?: music.title
                val newArtist = tags.artist?.takeIf { it.isNotBlank() } ?: music.artist
                val newAlbum = tags.album?.takeIf { it.isNotBlank() } ?: music.album
                musicDao.updateMusicTags(musicId, newTitle, newArtist, newAlbum)
                tags.lyrics?.takeIf { it.isNotBlank() }?.let { newLyrics ->
                    val existing = musicExtraDao.getExtraFieldsById(musicId)
                    musicExtraDao.insert(
                        (existing ?: MusicExtra(id = musicId, isGetExtraInfo = false))
                            .copy(lyrics = newLyrics)
                    )
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // endregion

    // region Extra Info / AI

    override suspend fun getMusicLyrics(musicId: Long): String? = musicExtraDao.getLyricsById(musicId)

    override suspend fun insertMusicExtra(musicId: Long, musicExtraInfo: DailyMusicInfo) {
        musicExtraDao.updateExtraFieldsById(
            id = musicId,
            rewards = musicExtraInfo.rewards,
            popLyric = musicExtraInfo.lyric,
            singerIntroduce = musicExtraInfo.singerIntroduce,
            backgroundIntroduce = musicExtraInfo.backgroundIntroduce,
            description = musicExtraInfo.description,
            relevantMusic = musicExtraInfo.relevantMusic
        )
    }

    override suspend fun getMusicExtraById(musicId: Long): DailyMusicInfo {
        val info = musicExtraDao.getExtraFieldsById(musicId)
        return DailyMusicInfo(
            genre = emptyList(),
            mood = emptyList(),
            scenario = emptyList(),
            language = "",
            era = "",
            rewards = info?.rewards ?: "",
            lyric = info?.popLyric ?: "",
            singerIntroduce = info?.singerIntroduce ?: "",
            backgroundIntroduce = info?.backgroundIntroduce ?: "",
            description = info?.description ?: "",
            relevantMusic = info?.relevantMusic ?: "",
            errorInfo = "None"
        )
    }

    override suspend fun fetchMusicExtraInfoWithProvider(
        config: AiEndpointConfig,
        title: String,
        artist: String
    ): Result<DailyMusicInfo> {
        val prompt = buildMusicInfoPrompt(title, artist)

        return when (val result = openAiCompatibleAdapter.callChatApi(config, prompt)) {
            is AiApiResult.Success -> {
                try {
                    val response = json.decodeFromString<MusicInfoResponse>(result.data)
                    val info = DailyMusicInfo(
                        genre = response.genre,
                        mood = response.mood,
                        scenario = response.scenario,
                        language = response.language,
                        era = response.era,
                        rewards = response.rewards,
                        lyric = response.lyric,
                        singerIntroduce = response.singerIntroduce,
                        backgroundIntroduce = response.backgroundIntroduce,
                        description = response.description,
                        relevantMusic = response.relevantMusic,
                        errorInfo = response.errorInfo
                    )
                    Result.success(info)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            is AiApiResult.Error -> {
                Result.failure(Exception(result.error.toDisplayMessage()))
            }
        }
    }

    override suspend fun validateProviderApiKey(config: AiEndpointConfig): Result<Boolean> {
        return when (val result = openAiCompatibleAdapter.testConnection(config)) {
            is AiApiResult.Success -> Result.success(true)
            is AiApiResult.Error -> Result.failure(Exception(result.error.toDisplayMessage()))
        }
    }

    override suspend fun fetchAvailableModels(config: AiEndpointConfig): Result<List<String>> {
        return when (val result = openAiCompatibleAdapter.fetchModels(config)) {
            is AiApiResult.Success -> Result.success(result.data)
            is AiApiResult.Error -> Result.failure(Exception(result.error.toDisplayMessage()))
        }
    }

    private fun buildMusicInfoPrompt(title: String, artist: String): String {
        return """
                你是一位专业的音乐编辑，精通各类音乐风格、流派发展历史和艺术家背景。

                请分析以下歌曲信息，如果需要可以通过网络搜索获取更准确的信息。

                请严格按照以下JSON格式回复，不要包含任何解释、注释或其他额外文本：
                {
                "genre": ["ROCK", "POP", "JAZZ", "CLASSICAL", "HIPHOP", "ELECTRONIC", "FOLK", "RNB", "METAL", "COUNTRY", "BLUES", "REGGAE", "PUNK", "FUNK", "SOUL", "INDIE"],
                "mood": ["HAPPY", "SAD", "ENERGETIC", "CALM", "ROMANTIC", "ANGRY", "LONELY", "UPLIFTING", "MYSTERIOUS", "DARK", "MELANCHOLY", "HOPEFUL"],
                "scenario": ["WORKOUT", "SLEEP", "PARTY", "DRIVING", "STUDY", "RELAX", "DINNER", "MEDITATION", "FOCUS", "TRAVEL", "MORNING", "NIGHT"],
                "language":"ENGLISH/CHINESE/JAPANESE/KOREAN/OTHERS",
                "era":"SIXTIES/SEVENTIES/EIGHTIES/NINETIES/TWO_THOUSANDS/TWENTY_TENS/TWENTY_TWENTIES",
                "rewards": "歌手或歌曲获得的重要奖项，如格莱美、金曲奖等，若无则返回-暂无",
                "lyric": "该歌曲最广为人知的一到两句歌词，若无则返回-暂无",
                "singerIntroduce": "歌手的背景、主要成就、音乐风格和代表作品介绍",
                "backgroundIntroduce": "歌曲的创作背景、灵感来源、发布时的反响或背后的故事",
                "description": "歌曲表达的主题、情感、核心内容或想要传达的信息",
                "relevantMusic": "与该歌曲风格、流派或情感相似的其他知名歌曲，用逗号分隔，不可返回数组",
                "errorInfo": "None"
                }

                要求：
                1. 只返回上述JSON格式，不要添加任何markdown标记或解释
                2. genre、mood、scenario 为多选，返回JSON数组，从候选值中选择
                3. relevantMusic 为多选，用逗号分隔为字符串，禁止返回JSON数组
                4. language和era为单选，直接输出选项值
                4. 如果无法确定某字段，回复"UNKNOWN"
                5. 歌词必须是该歌曲的真实热门歌词，不要编造
                6. 相似歌曲推荐必须是真正与该歌曲风格相似的知名歌曲
                7. 优先通过网络搜索确认信息的准确性

                歌曲信息：$artist 演唱的《$title》
        """.trimIndent()
    }

    // endregion

    // region Similarity

    private val labelCategoryWeight = mapOf(
        LabelCategory.GENRE to 3,
        LabelCategory.MOOD to 4,
        LabelCategory.SCENARIO to 2,
        LabelCategory.LANGUAGE to 1,
        LabelCategory.ERA to 1
    )

    private fun calcSimilarity(
        baseLabels: List<MusicLabel>,
        targetLabels: List<MusicLabel>
    ): Int {
        var score = 0
        for (base in baseLabels) {
            for (target in targetLabels) {
                if (base.label == target.label) {
                    score += labelCategoryWeight[base.type] ?: 1
                }
            }
        }
        return score
    }

    override suspend fun getSimilarSongsByWeightedLabels(
        musicId: Long,
        limit: Int
    ): List<MusicInfo> {
        val baseLabels = getMusicLabels(musicId)
        if (baseLabels.isEmpty()) return emptyList()

        val allMusic = getAllMusicInfoAsList("id", "ASC")
        return allMusic
            .filter { it.music.id != musicId }
            .map { musicInfo ->
                val targetLabels = getMusicLabels(musicInfo.music.id)
                val similarity = calcSimilarity(baseLabels, targetLabels)
                musicInfo to similarity
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    // endregion

    // region Playback & Stats

    override suspend fun insertPlayback(history: PlaybackHistory): Long =
        playbackHistoryDao.insert(history.toEntity())

    override suspend fun updatePlaybackRecord(id: Long, duration: Long, isCompleted: Boolean) {
        playbackHistoryDao.updatePlaybackRecord(id, duration, isCompleted)
    }

    override suspend fun recordListeningDuration(duration: Long) {
        val today = todayDateString()
        val existing = listeningDurationDao.getDurationByDate(today)

        if (existing == null) {
            listeningDurationDao.insert(
                ListeningDuration(
                    date = today,
                    duration = duration,
                    updatedAt = currentTimeMillis()
                )
            )
        } else {
            listeningDurationDao.updateDuration(
                date = today,
                additionalDuration = duration,
                updateTime = currentTimeMillis()
            )
        }
    }

    override fun getRecentListeningDurations(limit: Int): Flow<List<ListeningDurationDomain>> =
        listeningDurationDao.getRecentDurations(limit).map { list -> list.map { it.toDomain() } }

    override fun getPlaybackHistory(musicId: Long, limit: Int): Flow<List<PlaybackHistory>> =
        playbackHistoryDao.getHistoryForMusic(musicId, limit).map { list -> list.map { it.toDomain() } }

    override suspend fun getRecentPlaybackHistoryGlobal(limit: Int): List<PlaybackHistory> =
        playbackHistoryDao.getRecentHistory(limit).map { it.toDomain() }

    override suspend fun getUserUsageAnalytics(): UserUsageAnalytics = withContext(Dispatchers.Default) {
        val userInfos = userInfoDao.getAllUserInfos().filter { !it.isDeleted }
        val allHistory = playbackHistoryDao.getAllHistory()
        val recentHistory = playbackHistoryDao.getRecentHistory(10)
        val allDurations = listeningDurationDao.getAllDurations()
        val allLabels = musicLabelDao.getAllLabels()
        val allMusic = getAllMusicInfoAsList("playCount", "DESC")
        val playlists = playlistDao.getAllPlaylists()
        val playlistItems = playlistItemDao.getAllPlaylistItems()

        val totalPlayCount = userInfos.sumOf { (it.playCount ?: 0).toLong() }.toInt()
        val totalSkipCount = userInfos.sumOf { (it.skippedCount ?: 0).toLong() }.toInt()
        val totalPlayPlusSkip = totalPlayCount + totalSkipCount
        val skipRate = if (totalPlayPlusSkip > 0) totalSkipCount.toFloat() / totalPlayPlusSkip else 0f
        val likedCount = userInfos.count { it.liked }

        val completedCount = allHistory.count { it.isCompleted }
        val totalSessions = allHistory.size
        val completionRate = if (totalSessions > 0) completedCount.toFloat() / totalSessions else 0f
        val totalListeningMs = allHistory.sumOf { it.playDuration }
        val totalListeningMinutes = totalListeningMs / 60_000
        val averageSessionMinutes = if (totalSessions > 0) totalListeningMs / 60_000.0 / totalSessions else 0.0

        val playSourceBreakdown = allHistory
            .mapNotNull { it.source }
            .groupingBy { it }
            .eachCount()

        val weekMs = 7 * 24 * 60 * 60 * 1000L
        val now = currentTimeMillis()
        val thisWeekStart = now - weekMs
        val lastWeekStart = now - 2 * weekMs
        val thisWeekMinutes = allDurations
            .filter { d -> (parseDateToMillis(d.date) ?: 0L).let { t -> t in thisWeekStart..now } }
            .sumOf { it.duration } / 60_000
        val lastWeekMinutes = allDurations
            .filter { d -> (parseDateToMillis(d.date) ?: 0L).let { t -> t in lastWeekStart until thisWeekStart } }
            .sumOf { it.duration } / 60_000

        val userInfoMap = userInfos.associateBy { it.id }
        val topByPlay = userInfos
            .sortedByDescending { it.playCount ?: 0 }
            .take(5)
            .mapNotNull { ui -> ui.id }
        val topMusicIds = topByPlay
        val topMusicInfos = if (topMusicIds.isEmpty()) emptyList() else musicAllDao.getPlaylistByIdList(topMusicIds)
        val topMusicInfoMap = topMusicInfos.associateBy { it.music.id }
        val topPlayedSongs = topMusicIds.mapNotNull { id ->
            val info = topMusicInfoMap[id] ?: return@mapNotNull null
            TopPlayedEntry(
                musicId = id,
                title = info.music.title,
                artist = info.music.artist,
                playCount = userInfoMap[id]?.playCount ?: 0
            )
        }

        val recentMusicIds = recentHistory.map { it.musicId }.distinct()
        val recentMusicInfos = if (recentMusicIds.isEmpty()) emptyList() else musicAllDao.getPlaylistByIdList(recentMusicIds)
        val recentMusicInfoMap = recentMusicInfos.associateBy { it.music.id }
        val recentPlaybackWithTitle = recentHistory.map { h ->
            val info = recentMusicInfoMap[h.musicId]
            RecentPlaybackEntry(
                musicId = h.musicId,
                title = info?.music?.title ?: "",
                artist = info?.music?.artist ?: "",
                playedAt = h.playedAt,
                playDuration = h.playDuration,
                isCompleted = h.isCompleted,
                source = h.source
            )
        }

        val playCountByMusicId = userInfos.associate { it.id to (it.playCount ?: 0).toLong() }
        val labelToCount = mutableMapOf<Pair<DataLabelCategory, DataLabelName>, Long>()
        allLabels.forEach { ml ->
            val key = ml.type to ml.label
            val add = playCountByMusicId[ml.musicId] ?: 0L
            labelToCount[key] = (labelToCount[key] ?: 0L) + add
        }
        val topGenres = labelToCount
            .filter { it.key.first == DataLabelCategory.GENRE }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { LabelCountEntry(labelDisplayName = it.first.second.name, count = it.second.toInt()) }
        val topMoods = labelToCount
            .filter { it.key.first == DataLabelCategory.MOOD }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { LabelCountEntry(labelDisplayName = it.first.second.name, count = it.second.toInt()) }
        val topScenarios = labelToCount
            .filter { it.key.first == DataLabelCategory.SCENARIO }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { LabelCountEntry(labelDisplayName = it.first.second.name, count = it.second.toInt()) }

        val artistToCount = allMusic
            .filter { it.userInfo != null }
            .groupBy { it.music.artist }
            .mapValues { (_, list) -> list.sumOf { (it.userInfo?.playCount ?: 0).toLong() } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        val topArtists = artistToCount.map { ArtistCountEntry(artistName = it.first, playCount = it.second.toInt()) }

        val customPlaylistCount = playlists.size

        val songIdToPlaylistCount = playlistItems.groupingBy { it.songId }.eachCount()
        val topSongIdsInPlaylists = songIdToPlaylistCount.toList().sortedByDescending { it.second }.take(5).map { it.first }
        val topSongsInPlaylistsInfos = if (topSongIdsInPlaylists.isEmpty()) emptyList() else musicAllDao.getPlaylistByIdList(topSongIdsInPlaylists)
        val topSongsInPlaylistsMap = topSongsInPlaylistsInfos.associateBy { it.music.id }
        val topSongsInPlaylists = topSongIdsInPlaylists.mapNotNull { id ->
            val info = topSongsInPlaylistsMap[id] ?: return@mapNotNull null
            TopPlayedEntry(
                musicId = id,
                title = info.music.title,
                artist = info.music.artist,
                playCount = songIdToPlaylistCount[id] ?: 0
            )
        }

        UserUsageAnalytics(
            totalPlayCount = totalPlayCount,
            totalSkipCount = totalSkipCount,
            likedCount = likedCount,
            totalListeningMinutes = totalListeningMinutes,
            averageSessionMinutes = averageSessionMinutes,
            completionRate = completionRate,
            skipRate = skipRate,
            thisWeekMinutes = thisWeekMinutes,
            lastWeekMinutes = lastWeekMinutes,
            topPlayedSongs = topPlayedSongs,
            recentPlaybackWithTitle = recentPlaybackWithTitle,
            playSourceBreakdown = playSourceBreakdown,
            topGenres = topGenres,
            topMoods = topMoods,
            topScenarios = topScenarios,
            topArtists = topArtists,
            customPlaylistCount = customPlaylistCount,
            topSongsInPlaylists = topSongsInPlaylists
        )
    }

    override suspend fun incrementPlayCount(musicId: Long) {
        userInfoDao.incrementPlayCount(musicId)
    }

    override suspend fun incrementSkippedCount(musicId: Long) {
        userInfoDao.incrementSkippedCount(musicId)
    }

    override suspend fun updateLastPlayed(musicId: Long, timestamp: Long) {
        userInfoDao.updateLastPlayed(musicId, timestamp)
    }

    // endregion

    // region Snapshot Export/Import

    override suspend fun exportMusicUserStateSnapshot(): MusicUserStateSnapshot {
        val userInfos = userInfoDao.getAllUserInfos().map {
            UserInfoSnapshot(
                id = it.id,
                liked = it.liked,
                disLiked = it.disLiked,
                lastPlayed = it.lastPlayed,
                playCount = it.playCount,
                skippedCount = it.skippedCount,
                userRating = it.userRating,
                inCustomPlaylistCount = it.inCustomPlaylistCount
            )
        }

        val extras = musicExtraDao.getAllExtras().map {
            MusicExtraUserSnapshot(
                id = it.id,
                isGetExtraInfo = it.isGetExtraInfo,
                rewards = it.rewards,
                popLyric = it.popLyric,
                singerIntroduce = it.singerIntroduce,
                backgroundIntroduce = it.backgroundIntroduce,
                description = it.description,
                relevantMusic = it.relevantMusic
            )
        }

        val labels = musicLabelDao.getAllLabels().map {
            MusicLabelSnapshot(
                musicId = it.musicId,
                label = LabelName.valueOf(it.label.name),
                category = LabelCategory.valueOf(it.type.name),
                source = it.source,
                confidence = it.confidence,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }

        return MusicUserStateSnapshot(
            userInfos = userInfos,
            extras = extras,
            labels = labels
        )
    }

    override suspend fun restoreMusicUserState(snapshot: MusicUserStateSnapshot) {
        val userInfos = snapshot.userInfos.map {
            UserInfo(
                id = it.id,
                liked = it.liked,
                disLiked = it.disLiked,
                lastPlayed = it.lastPlayed,
                playCount = it.playCount,
                skippedCount = it.skippedCount,
                userRating = it.userRating,
                inCustomPlaylistCount = it.inCustomPlaylistCount,
                isDeleted = false
            )
        }
        userInfoDao.insertAll(userInfos)

        val existingExtrasMap = musicExtraDao.getAllExtras().associateBy { it.id }

        val mergedExtras = snapshot.extras.map { snapshotExtra ->
            val existing = existingExtrasMap[snapshotExtra.id]
            existing?.copy(
                isGetExtraInfo = snapshotExtra.isGetExtraInfo,
                rewards = snapshotExtra.rewards,
                popLyric = snapshotExtra.popLyric,
                singerIntroduce = snapshotExtra.singerIntroduce,
                backgroundIntroduce = snapshotExtra.backgroundIntroduce,
                description = snapshotExtra.description,
                relevantMusic = snapshotExtra.relevantMusic
            )
                ?: MusicExtra(
                    id = snapshotExtra.id,
                    isGetExtraInfo = snapshotExtra.isGetExtraInfo,
                    rewards = snapshotExtra.rewards,
                    popLyric = snapshotExtra.popLyric,
                    singerIntroduce = snapshotExtra.singerIntroduce,
                    backgroundIntroduce = snapshotExtra.backgroundIntroduce,
                    description = snapshotExtra.description,
                    relevantMusic = snapshotExtra.relevantMusic,
                    isDeleted = false
                )
        }
        musicExtraDao.insertAll(mergedExtras)

        val now = currentTimeMillis()
        val musicLabels = snapshot.labels.map {
            com.hmp.data.database.MusicLabel(
                musicId = it.musicId,
                label = DataLabelName.valueOf(it.label.name),
                type = DataLabelCategory.valueOf(it.category.name),
                // v1 存量备份缺溯源字段 → null（规则①不可用）；v2 备份原样保留 USER/LLM 溯源（review 修复 2026-08-28）
                source = it.source,
                confidence = it.confidence,
                createdAt = it.createdAt ?: now,
                updatedAt = it.updatedAt ?: now
            )
        }
        musicLabelDao.insertAll(musicLabels)
    }

    override suspend fun exportListeningStatsSnapshot(): ListeningStatsSnapshot {
        val durations = listeningDurationDao.getAllDurations().map {
            ListeningDurationDomain(
                date = it.date,
                duration = it.duration
            )
        }

        val history = playbackHistoryDao.getAllHistory().map {
            PlaybackHistory(
                musicId = it.musicId,
                playedAt = it.playedAt,
                playDuration = it.playDuration,
                isCompleted = it.isCompleted,
                source = it.source
            )
        }

        return ListeningStatsSnapshot(
            listeningDurations = durations,
            playbackHistories = history
        )
    }

    override suspend fun restoreListeningStats(snapshot: ListeningStatsSnapshot) {
        val durations = snapshot.listeningDurations.map {
            ListeningDuration(
                date = it.date,
                duration = it.duration,
                updatedAt = currentTimeMillis()
            )
        }
        listeningDurationDao.insertAll(durations)

        val history = snapshot.playbackHistories.map {
            com.hmp.data.database.PlaybackHistory(
                musicId = it.musicId,
                playedAt = it.playedAt,
                playDuration = it.playDuration,
                isCompleted = it.isCompleted,
                source = it.source
            )
        }
        playbackHistoryDao.insertAll(history)
    }

    // endregion

    // region Agent T2: 富化健康度查询

    /** 富化健康度快照：Kotlin 侧 groupBy 实现，避免新增 DAO SQL。 */
    override suspend fun getEnrichHealth(): EnrichHealth {
        val allIds = musicDao.getAllActiveIds()
        val allLabels = musicLabelDao.getAllLabels()

        val enrichedIds = allLabels
            .filter { it.source == SOURCE_LLM || it.source == SOURCE_AGENT }
            .map { it.musicId }
            .toSet()

        val lowConfCount = allLabels
            .filter { (it.source == SOURCE_LLM || it.source == SOURCE_AGENT) && (it.confidence != null && it.confidence < 0.5) }
            .map { it.musicId }
            .distinct()
            .size

        return EnrichHealth(
            enrichedSongCount = enrichedIds.size,
            totalSongCount = allIds.size,
            lowConfidenceCount = lowConfCount,
        )
    }

    /** 获取未富化的歌曲：没有任何 LLM/AGENT 源标签的。 */
    override suspend fun getUnenrichedSongs(limit: Int): List<MusicInfo> {
        val allIds = musicDao.getAllActiveIds()
        val enrichedIds = musicLabelDao.getAllLabels()
            .filter { it.source == SOURCE_LLM || it.source == SOURCE_AGENT }
            .map { it.musicId }
            .toSet()

        val unenrichedIds = allIds.filter { it !in enrichedIds }.take(limit)
        val result = mutableListOf<MusicInfo>()
        for (id in unenrichedIds) {
            val info = musicAllDao.getMusicInfoById(id).firstOrNull()
            if (info != null) result.add(info.toDomain())
        }
        return result
    }

    /** 获取之前富化失败的歌曲：low confidence 的。 */
    override suspend fun getFailedEnrichSongs(limit: Int): List<MusicInfo> {
        val allLabels = musicLabelDao.getAllLabels()
        val lowConfIds = allLabels
            .filter { (it.source == SOURCE_LLM || it.source == SOURCE_AGENT) && (it.confidence != null && it.confidence < 0.5) }
            .map { it.musicId }
            .distinct()
            .take(limit)

        val result = mutableListOf<MusicInfo>()
        for (id in lowConfIds) {
            val info = musicAllDao.getMusicInfoById(id).firstOrNull()
            if (info != null) result.add(info.toDomain())
        }
        return result
    }

    /** 最近富化结果验收：since 后新增/更新的 LLM/AGENT 标签数 ≈ success；无标签产出 ≈ failure。 */
    override suspend fun getRecentEnrichResults(since: Long): EnrichBatchResult {
        val allLabels = musicLabelDao.getAllLabels()
        val recentLabels = allLabels.filter { label ->
            (label.source == SOURCE_LLM || label.source == SOURCE_AGENT) &&
                (label.createdAt != null && label.createdAt >= since)
        }
        // 简化验收：recentLabels 的 musicId 去重数 = success；没有专门的 failure 记录，用 0
        val successMusicIds = recentLabels.map { it.musicId }.distinct().size
        return EnrichBatchResult(
            successCount = successMusicIds,
            failureCount = 0, // 简化实现：失败率由 successRate=1.0 推断
        )
    }

    // endregion

    companion object {
        /** 认识来源（musicLabel.source 取值，设计总纲 7.3）：模型富化。 */
        const val SOURCE_LLM = "LLM"

        /** 认识来源：用户修正（T1，永不被模型覆盖——规则①）。 */
        const val SOURCE_USER = "USER"

        /** 认识来源：agent 主动发起认识（enrichSong 等工具路径）。 */
        const val SOURCE_AGENT = "AGENT"

        /** 模型认识的初始可信度（行为确证/证伪后动态调整）。 */
        const val DEFAULT_MODEL_CONFIDENCE = 0.6

        /** 审计工具名：标签被覆盖/修正的留痕事件（设计总纲 7.3）。 */
        const val LABEL_CORRECTION_TOOL = "label_correction"
    }
}