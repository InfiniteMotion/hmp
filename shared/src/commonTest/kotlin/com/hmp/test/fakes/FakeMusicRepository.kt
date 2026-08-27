package com.hmp.test.fakes

import com.hmp.domain.backup.ListeningStatsSnapshot
import com.hmp.domain.backup.MusicUserStateSnapshot
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.EditableMusicTags
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.model.DailyMusicInfo
import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.domain.setting.model.UserUsageAnalytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeMusicRepository : MusicRepository {

    private val musicList = mutableListOf<MusicInfo>()
    private val deletedMusic = mutableListOf<MusicInfo>()
    private val labels = mutableMapOf<Long, MutableList<MusicLabel>>()
    private val likedStatus = mutableMapOf<Long, Boolean>()
    private val lyrics = mutableMapOf<Long, String>()
    private val extras = mutableMapOf<Long, DailyMusicInfo>()
    private val playbackHistory = mutableListOf<PlaybackHistory>()
    private val listeningDurations = mutableListOf<ListeningDuration>()
    private var nextPlaybackId = 1L
    private val _isScanning = MutableStateFlow(false)
    private var playCount = mutableMapOf<Long, Int>()
    private var skippedCount = mutableMapOf<Long, Int>()
    private var lastPlayed = mutableMapOf<Long, Long>()

    fun addMusic(musicInfo: MusicInfo) {
        musicList.add(musicInfo)
    }

    override suspend fun getAllMusicInfoAsList(orderBy: String, orderType: String): List<MusicInfo> {
        val sorted = when (orderBy.lowercase()) {
            "title" -> musicList.sortedBy { it.music.title.lowercase() }
            "artist" -> musicList.sortedBy { it.music.artist.lowercase() }
            "album" -> musicList.sortedBy { it.music.album.lowercase() }
            "duration" -> musicList.sortedBy { it.music.duration }
            else -> musicList.sortedBy { it.music.title.lowercase() }
        }
        return if (orderType.uppercase() == "DESC") sorted.reversed() else sorted
    }

    override fun getMusicCount(): Flow<Int> = flowOf(musicList.size)

    override fun getMusicWithExtraCount(): Flow<Int> =
        flowOf(musicList.count { it.extra?.isGetExtraInfo == true })

    override fun getMusicWithMissingExtraCount(): Flow<Int> =
        flowOf(musicList.count { it.extra?.isGetExtraInfo != true })

    override fun getMusicInfoById(musicId: Long): Flow<MusicInfo?> =
        flowOf(musicList.find { it.music.id == musicId })

    override suspend fun getMusicListByArtist(artistName: String): List<MusicInfo> =
        musicList.filter { it.music.artist == artistName }

    override suspend fun getMusicListByAlbum(albumName: String): List<MusicInfo> =
        musicList.filter { it.music.album == albumName }

    override suspend fun searchMusic(query: String): List<MusicInfo> {
        val q = query.lowercase()
        return musicList.filter {
            it.music.title.lowercase().contains(q) ||
                it.music.artist.lowercase().contains(q) ||
                it.music.album.lowercase().contains(q)
        }
    }

    override suspend fun getRandomMusicInfoWithMissingExtra(): MusicInfo? =
        musicList.filter { it.extra?.isGetExtraInfo != true }.randomOrNull()

    override suspend fun getRandomMusicInfoWithExtra(): MusicInfo? =
        musicList.filter { it.extra?.isGetExtraInfo == true }.randomOrNull()

    override suspend fun updateLikedStatus(id: Long, liked: Boolean) {
        likedStatus[id] = liked
    }

    override suspend fun getLikedStatus(id: Long): Boolean = likedStatus[id] ?: false

    override suspend fun removeFromLibrary(ids: List<Long>) {
        val toRemove = musicList.filter { it.music.id in ids }
        musicList.removeAll(toRemove)
        deletedMusic.addAll(toRemove)
    }

    override suspend fun restoreToLibrary(ids: List<Long>) {
        val toRestore = deletedMusic.filter { it.music.id in ids }
        deletedMusic.removeAll(toRestore)
        musicList.addAll(toRestore)
    }

    override suspend fun getDeletedMusicIdsGroupedByFolder(): List<Pair<String, List<Long>>> {
        return deletedMusic
            .groupBy { it.music.path.substringBeforeLast("/", "") }
            .map { (folder, songs) -> folder to songs.map { it.music.id } }
    }

    override suspend fun addMusicLabel(label: MusicLabel) {
        labels.getOrPut(label.musicId) { mutableListOf() }.add(label)
    }

    override suspend fun addUserMusicLabel(label: MusicLabel, confidence: Double) {
        labels.getOrPut(label.musicId) { mutableListOf() }.add(label)
    }

    override fun getLabelNamesByType(type: LabelCategory): Flow<List<LabelName>> {
        val names = labels.values.flatten()
            .filter { it.type == type }
            .map { it.label }
            .distinct()
        return flowOf(names)
    }

    override suspend fun getMusicIdListByType(labelName: LabelName): List<Long> =
        labels.entries
            .filter { (_, labelList) -> labelList.any { it.label == labelName } }
            .map { it.key }

    override suspend fun getMusicLabels(musicId: Long): List<MusicLabel> =
        labels[musicId] ?: emptyList()

    override suspend fun updateMusicTags(musicId: Long, tags: EditableMusicTags): Result<Unit> {
        val index = musicList.indexOfFirst { it.music.id == musicId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("Music not found: $musicId"))
        }
        val info = musicList[index]
        val updatedMusic = info.music.copy(
            title = tags.title?.takeIf { it.isNotBlank() } ?: info.music.title,
            artist = tags.artist?.takeIf { it.isNotBlank() } ?: info.music.artist,
            album = tags.album?.takeIf { it.isNotBlank() } ?: info.music.album
        )
        musicList[index] = info.copy(music = updatedMusic)
        tags.lyrics?.takeIf { it.isNotBlank() }?.let { lyrics[musicId] = it }
        return Result.success(Unit)
    }

    override suspend fun refreshMusicTags(musicId: Long, tags: EditableMusicTags): Result<Unit> =
        updateMusicTags(musicId, tags)

    override suspend fun getSimilarSongsByWeightedLabels(musicId: Long, limit: Int): List<MusicInfo> =
        musicList.filter { it.music.id != musicId }.take(limit)

    override fun getRecentListeningDurations(limit: Int): Flow<List<ListeningDuration>> =
        flowOf(listeningDurations.takeLast(limit))

    override suspend fun getMusicLyrics(musicId: Long): String? = lyrics[musicId]

    override suspend fun insertMusicExtra(musicId: Long, musicExtraInfo: DailyMusicInfo) {
        extras[musicId] = musicExtraInfo
    }

    override suspend fun getMusicExtraById(musicId: Long): DailyMusicInfo =
        extras[musicId] ?: throw IllegalArgumentException("No extra for music $musicId")

    override suspend fun loadMusicFromDevice(): Result<Unit> = Result.success(Unit)

    override val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    override suspend fun syncMusicFromDeviceIncremental(): Result<Unit> = Result.success(Unit)

    override suspend fun fetchMusicExtraInfoWithProvider(
        config: AiEndpointConfig, title: String, artist: String
    ): Result<DailyMusicInfo> = Result.failure(NotImplementedError())

    override suspend fun validateProviderApiKey(config: AiEndpointConfig): Result<Boolean> =
        Result.success(true)

    override suspend fun fetchAvailableModels(config: AiEndpointConfig): Result<List<String>> =
        Result.success(emptyList())

    override suspend fun insertPlayback(history: PlaybackHistory): Long {
        val id = nextPlaybackId++
        playbackHistory.add(history.copy(id = id))
        return id
    }

    override suspend fun updatePlaybackRecord(id: Long, duration: Long, isCompleted: Boolean) {
        val index = playbackHistory.indexOfFirst { it.id == id }
        if (index >= 0) {
            playbackHistory[index] = playbackHistory[index].copy(
                playDuration = duration, isCompleted = isCompleted
            )
        }
    }

    override suspend fun recordListeningDuration(duration: Long) {
        listeningDurations.add(ListeningDuration(date = "2024-01-01", duration = duration))
    }

    override fun getPlaybackHistory(musicId: Long, limit: Int): Flow<List<PlaybackHistory>> =
        flowOf(playbackHistory.filter { it.musicId == musicId }.takeLast(limit))

    override suspend fun getRecentPlaybackHistoryGlobal(limit: Int): List<PlaybackHistory> =
        playbackHistory.takeLast(limit)

    override suspend fun getUserUsageAnalytics(): UserUsageAnalytics = UserUsageAnalytics(
        totalPlayCount = 0, totalSkipCount = 0, likedCount = 0,
        totalListeningMinutes = 0, averageSessionMinutes = 0.0,
        completionRate = 0f, skipRate = 0f, thisWeekMinutes = 0, lastWeekMinutes = 0,
        topPlayedSongs = emptyList(), recentPlaybackWithTitle = emptyList()
    )

    override suspend fun incrementPlayCount(musicId: Long) {
        playCount[musicId] = (playCount[musicId] ?: 0) + 1
    }

    override suspend fun incrementSkippedCount(musicId: Long) {
        skippedCount[musicId] = (skippedCount[musicId] ?: 0) + 1
    }

    override suspend fun updateLastPlayed(musicId: Long, timestamp: Long) {
        lastPlayed[musicId] = timestamp
    }

    override suspend fun exportMusicUserStateSnapshot(): MusicUserStateSnapshot =
        MusicUserStateSnapshot()

    override suspend fun restoreMusicUserState(snapshot: MusicUserStateSnapshot) {}

    override suspend fun exportListeningStatsSnapshot(): ListeningStatsSnapshot =
        ListeningStatsSnapshot()

    override suspend fun restoreListeningStats(snapshot: ListeningStatsSnapshot) {}
}
