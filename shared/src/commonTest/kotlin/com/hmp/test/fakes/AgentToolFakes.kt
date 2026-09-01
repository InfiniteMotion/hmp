package com.hmp.test.fakes

import com.hmp.domain.agent.port.AiExtraEnrichPort
import com.hmp.domain.agent.enrich.EnrichBatchResult
import com.hmp.domain.agent.enrich.EnrichHealth
import com.hmp.domain.backup.ListeningStatsSnapshot
import com.hmp.domain.backup.MusicUserStateSnapshot
import com.hmp.domain.music.EditableMusicTags
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.model.DailyMusicInfo
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.domain.setting.model.UserUsageAnalytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** M3 工具层专用内存 Fake：MusicRepository。
 *  （命名加 Agent 前缀，避免与既有的 FakeMusicRepository 重名冲突。仅 stub 工具用到的方法。） */
class FakeAgentMusicRepository : MusicRepository {
    val songs = LinkedHashMap<Long, MusicInfo>()
    val history = mutableListOf<PlaybackHistory>()
    val recentHistoryResult = mutableListOf<PlaybackHistory>()
    val playbacks: MutableList<PlaybackHistory> = history
    /** 标签 → 曲目 id（供 searchLibrary 标签过滤测试）。 */
    val musicIdsByLabel = mutableMapOf<LabelName, List<Long>>()

    override suspend fun getAllMusicInfoAsList(orderBy: String, orderType: String): List<MusicInfo> = songs.values.toList()
    override fun getMusicCount(): Flow<Int> = flowOf(songs.size)
    override fun getMusicWithExtraCount(): Flow<Int> = flowOf(0)
    override fun getMusicWithMissingExtraCount(): Flow<Int> = flowOf(songs.size)
    override fun getMusicInfoById(musicId: Long): Flow<MusicInfo?> = flowOf(songs[musicId])
    override suspend fun getMusicListByArtist(artistName: String): List<MusicInfo> = songs.values.filter { it.music.artist == artistName }
    override suspend fun getMusicListByAlbum(albumName: String): List<MusicInfo> = songs.values.filter { it.music.album == albumName }
    override suspend fun getAllArtistsSummary(limit: Int): List<Pair<String, Int>> =
        songs.values.filter { !it.music.artist.isBlank() }
            .groupBy { it.music.artist }.mapValues { it.value.size }.toList()
            .sortedByDescending { it.second }.take(limit)
    override suspend fun getAllAlbumsSummary(limit: Int): List<Pair<String, Int>> =
        songs.values.filter { !it.music.album.isBlank() }
            .groupBy { it.music.album }.mapValues { it.value.size }.toList()
            .sortedByDescending { it.second }.take(limit)
    override suspend fun searchMusic(query: String): List<MusicInfo> =
        songs.values.filter {
            it.music.title.contains(query, ignoreCase = true) || it.music.artist.contains(query, ignoreCase = true)
        }
    override suspend fun getRandomMusicInfoWithMissingExtra(): MusicInfo? = null
    override suspend fun getRandomMusicInfoWithExtra(): MusicInfo? = null
    override suspend fun updateLikedStatus(id: Long, liked: Boolean) {}
    override suspend fun getLikedStatus(id: Long): Boolean = false
    override suspend fun removeFromLibrary(ids: List<Long>) {}
    override suspend fun restoreToLibrary(ids: List<Long>) {}
    override suspend fun getDeletedMusicIdsGroupedByFolder(): List<Pair<String, List<Long>>> = emptyList()
    override suspend fun addMusicLabel(label: MusicLabel) {}
    override suspend fun addUserMusicLabel(label: MusicLabel, confidence: Double) {}
    override fun getLabelNamesByType(type: LabelCategory): Flow<List<LabelName>> = flowOf(emptyList())
    override suspend fun getMusicIdListByType(label: LabelName): List<Long> = musicIdsByLabel[label] ?: emptyList()
    override suspend fun getMusicLabels(musicId: Long): List<MusicLabel> = emptyList()
    override suspend fun removeUserMusicLabel(musicId: Long, label: LabelName) {}
    override suspend fun updateMusicTags(musicId: Long, tags: EditableMusicTags): Result<Unit> = Result.success(Unit)
    override suspend fun refreshMusicTags(musicId: Long, tags: EditableMusicTags): Result<Unit> = Result.success(Unit)
    override suspend fun getSimilarSongsByWeightedLabels(musicId: Long, limit: Int): List<MusicInfo> =
        songs.values.take(limit)
    override fun getRecentListeningDurations(limit: Int): Flow<List<ListeningDuration>> = flowOf(emptyList())
    override suspend fun getMusicLyrics(musicId: Long): String? = null
    override suspend fun insertMusicExtra(musicId: Long, musicExtraInfo: DailyMusicInfo) {}
    override suspend fun getMusicExtraById(musicId: Long): DailyMusicInfo =
        dailyMusicExtra[songs[musicId]?.music?.id] ?: DailyMusicInfo(
            genre = listOf("摇滚"), mood = listOf("激昂"), scenario = listOf("通勤"),
            language = "中文", era = "现代", rewards = "", lyric = "", singerIntroduce = "",
            backgroundIntroduce = "", description = "描述", relevantMusic = "", errorInfo = "",
        )
    // 允许测试注入"富化未就绪"（errorInfo 非空）
    val dailyMusicExtra = HashMap<Long, DailyMusicInfo>()
    override suspend fun loadMusicFromDevice(): Result<Unit> = Result.success(Unit)
    override val isScanning: Flow<Boolean> = flowOf(false)
    override suspend fun syncMusicFromDeviceIncremental(): Result<Unit> = Result.success(Unit)
    override suspend fun fetchMusicExtraInfoWithProvider(config: AiEndpointConfig, title: String, artist: String): Result<DailyMusicInfo> =
        Result.failure(IllegalStateException("测试不应直达 fetch"))
    override suspend fun validateProviderApiKey(config: AiEndpointConfig): Result<Boolean> = Result.success(true)
    override suspend fun fetchAvailableModels(config: AiEndpointConfig): Result<List<String>> = Result.success(emptyList())
    override suspend fun insertPlayback(newRecord: PlaybackHistory): Long { history += newRecord; return newRecord.id.takeIf { it != 0L } ?: history.size.toLong() }
    override suspend fun updatePlaybackRecord(id: Long, duration: Long, isCompleted: Boolean) {}
    override suspend fun recordListeningDuration(duration: Long) {}
    override fun getPlaybackHistory(musicId: Long, limit: Int): Flow<List<PlaybackHistory>> = flowOf(emptyList())
    override suspend fun getRecentPlaybackHistoryGlobal(limit: Int): List<PlaybackHistory> = recentHistoryResult.take(limit)
    override suspend fun getUserUsageAnalytics(): UserUsageAnalytics = UserUsageAnalytics(
        totalPlayCount = 120, totalSkipCount = 9, likedCount = 5, totalListeningMinutes = 320,
        averageSessionMinutes = 25.0, completionRate = 0.8f, skipRate = 0.07f,
        thisWeekMinutes = 40, lastWeekMinutes = 35,
        topPlayedSongs = listOf(
            com.hmp.domain.setting.model.TopPlayedEntry(1L, "SongA", "ArtistA", 30),
            com.hmp.domain.setting.model.TopPlayedEntry(2L, "SongB", "ArtistB", 20),
        ),
        recentPlaybackWithTitle = emptyList(),
    )
    override suspend fun incrementPlayCount(musicId: Long) {}
    override suspend fun incrementSkippedCount(musicId: Long) {}
    override suspend fun updateLastPlayed(musicId: Long, timestamp: Long) {}
    override suspend fun exportMusicUserStateSnapshot(): MusicUserStateSnapshot = MusicUserStateSnapshot()
    override suspend fun restoreMusicUserState(snapshot: MusicUserStateSnapshot) {}
    override suspend fun exportListeningStatsSnapshot(): ListeningStatsSnapshot = ListeningStatsSnapshot()
    override suspend fun restoreListeningStats(snapshot: ListeningStatsSnapshot) {}

    // region Agent T2: 富化健康度 stub（Agent 工具层测试暂不涉及）
    override suspend fun getEnrichHealth(): EnrichHealth = EnrichHealth(0, songs.size, 0)
    override suspend fun getUnenrichedSongs(limit: Int): List<MusicInfo> = songs.values.take(limit)
    override suspend fun getFailedEnrichSongs(limit: Int): List<MusicInfo> = emptyList()
    override suspend fun getRecentEnrichResults(since: Long): EnrichBatchResult = EnrichBatchResult(0, 0)
    // endregion
}

/** M3 工具层专用内存 Fake：PlaylistRepository。 */
class FakeAgentPlaylistRepository : PlaylistRepository {
    val playlists = LinkedHashMap<Long, Playlist>()
    val playlistItems = HashMap<Long, MutableList<Long>>() // playlistId -> musicIds 顺序
    private var nextId = 1L

    override suspend fun createPlaylist(name: String): Long {
        val id = nextId++
        playlists[id] = Playlist(id = id, name = name)
        playlistItems[id] = mutableListOf()
        return id
    }
    override suspend fun removePlaylist(name: String) { playlists.values.removeIf { it.name == name } }
    override suspend fun removePlaylistById(id: Long) { playlists.remove(id); playlistItems.remove(id) }
    override suspend fun getAllPlaylists(): List<Playlist> = playlists.values.toList()
    override suspend fun getPlaylistMeta(id: Long): Playlist? = playlists[id]
    override suspend fun renamePlaylist(id: Long, newName: String) { playlists[id]?.let { playlists[id] = it.copy(name = newName) } }
    override suspend fun updatePlaylistCover(id: Long, coverUri: String?) { playlists[id]?.let { playlists[id] = it.copy(coverUri = coverUri) } }
    override suspend fun updatePlaylistDescription(id: Long, description: String?) { playlists[id]?.let { playlists[id] = it.copy(description = description) } }
    override suspend fun setPlaylistPinned(id: Long, isPinned: Boolean) { playlists[id]?.let { playlists[id] = it.copy(isPinned = isPinned) } }
    override suspend fun incrementPlaylistPlayCount(id: Long) { playlists[id]?.let { playlists[id] = it.copy(playCount = it.playCount + 1) } }
    override suspend fun setPlaylistLastPlayedAt(id: Long, timestamp: Long) { playlists[id]?.let { playlists[id] = it.copy(lastPlayedAt = timestamp) } }
    override suspend fun addToPlaylist(playlistId: Long, musicId: Long, musicPath: String) { playlistItems.getOrPut(playlistId) { mutableListOf() } += musicId }
    override suspend fun removeItemFromPlaylist(musicId: Long, playlistId: Long) { playlistItems[playlistId]?.remove(musicId) }
    override suspend fun resetPlaylistItems(playlistId: Long, musicList: List<com.hmp.domain.music.MusicInfo>) { }
    override suspend fun reorderPlaylistItems(playlistId: Long, orderedMusicIds: List<Long>) { playlistItems[playlistId] = orderedMusicIds.toMutableList() }
    override fun getMusicInfoInPlaylist(playlistId: Long): Flow<List<MusicInfo>> = flowOf(emptyList())
    override suspend fun getPlaylistById(playlistId: Long): List<MusicInfo> = emptyList()
    override suspend fun getPlaylistByIdList(playlistIdList: List<Long>): List<MusicInfo> = emptyList()
    override fun getAllPlaylistsFlow(): Flow<List<Playlist>> = flowOf(playlists.values.toList())
    override suspend fun exportPlaylistsSnapshot(): com.hmp.domain.backup.PlaylistsSnapshot = com.hmp.domain.backup.PlaylistsSnapshot()
    override suspend fun restoreFromSnapshot(snapshot: com.hmp.domain.backup.PlaylistsSnapshot) {}
}

/** M3 enrichSong 测试 Fake：配置成功/失败。 */
class FakeAiExtraEnrichPort(
    var result: Result<DailyMusicInfo> = Result.success(
        DailyMusicInfo(
            genre = listOf("电子", "氛围"), mood = listOf("放松"), scenario = listOf("工作"),
            language = "英文", era = "2020s", rewards = "", lyric = "", singerIntroduce = "",
            backgroundIntroduce = "", description = "富化描述", relevantMusic = "", errorInfo = "",
        )
    ),
) : AiExtraEnrichPort {
    override suspend fun enrich(title: String, artist: String): Result<DailyMusicInfo> = result
}