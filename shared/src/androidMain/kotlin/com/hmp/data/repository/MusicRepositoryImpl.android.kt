package com.hmp.data.repository

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.hmp.data.database.AgentAuditLogDao
import com.hmp.data.database.ListeningDurationDao
import com.hmp.data.database.Music
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
import com.hmp.data.mapper.toDomain
import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.data.util.stringToPinyinSortKey
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * Android 播放器仓库实现（B0 去重后仅保留平台分叉）：MediaStore 扫描 / SAF 标签写入 /
 * File 路径分组 / 三端排序语义各异的 getAllMusicInfoAsList。其余 DAO/AI/统计/备份逻辑在 [MusicRepositoryBase]。
 */
class MusicRepositoryImpl(
    musicDao: MusicDao,
    musicExtraDao: MusicExtraDao,
    userInfoDao: UserInfoDao,
    musicAllDao: MusicAllDao,
    musicLabelDao: MusicLabelDao,
    playbackHistoryDao: PlaybackHistoryDao,
    listeningDurationDao: ListeningDurationDao,
    playlistDao: PlaylistDao,
    playlistItemDao: PlaylistItemDao,
    openAiCompatibleAdapter: OpenAiCompatibleAdapter,
    json: Json,
    agentAuditLogDao: AgentAuditLogDao,
    private val context: Context
) : MusicRepositoryBase(
    musicDao = musicDao,
    musicExtraDao = musicExtraDao,
    userInfoDao = userInfoDao,
    musicAllDao = musicAllDao,
    musicLabelDao = musicLabelDao,
    playbackHistoryDao = playbackHistoryDao,
    listeningDurationDao = listeningDurationDao,
    playlistDao = playlistDao,
    playlistItemDao = playlistItemDao,
    openAiCompatibleAdapter = openAiCompatibleAdapter,
    agentAuditLogDao = agentAuditLogDao,
    json = json
) {

    override suspend fun getAllMusicInfoAsList(orderBy: String, orderType: String): List<MusicInfo> {
        val safeOrderType = if (orderType.uppercase() == "DESC") "DESC" else "ASC"
        // Text-based sorts (title/artist/album) always use in-memory pinyin-aware sorting
        if (orderBy == "title" || orderBy == "artist" || orderBy == "album") {
            val list = musicAllDao.getAllMusicInfoAsListById().map { it.toDomain() }
            val keyFn: (MusicInfo) -> String = when (orderBy) {
                "title" -> { info -> stringToPinyinSortKey(info.music.title) }
                "artist" -> { info -> stringToPinyinSortKey(info.music.artist) }
                else -> { info -> stringToPinyinSortKey(info.music.album) }
            }
            val rawFn: (MusicInfo) -> String = when (orderBy) {
                "title" -> { info -> info.music.title }
                "artist" -> { info -> info.music.artist }
                else -> { info -> info.music.album }
            }
            return if (safeOrderType == "DESC") {
                list.sortedWith(compareByDescending(keyFn).thenByDescending(rawFn))
            } else {
                list.sortedWith(compareBy(keyFn).thenBy(rawFn))
            }
        }
        val baseList = musicAllDao.getAllMusicInfoAsListById()
        val mappedList = baseList.map { it.toDomain() }
        return if (safeOrderType == "DESC") {
            when (orderBy) {
                "duration" -> mappedList.sortedByDescending { it.music.duration }
                "playCount" -> mappedList.sortedByDescending { it.userInfo?.playCount ?: 0 }
                else -> mappedList.sortedByDescending { it.music.id }
            }
        } else {
            mappedList
        }
    }

    override suspend fun getRandomMusicInfoWithExtra(): MusicInfo? =
        musicAllDao.getRandomMusicInfoWithExtra()?.toDomain()

    override suspend fun getDeletedMusicIdsGroupedByFolder(): List<Pair<String, List<Long>>> {
        val list = musicDao.getDeletedMusicIdAndPath()
        return list
            .groupBy { (_, path) ->
                try {
                    File(path).parent ?: "Unknown"
                } catch (e: Exception) {
                    "Unknown"
                }
            }
            .map { (path, entries) -> path to entries.map { it.id } }
            .sortedByDescending { it.second.size }
    }

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    companion object {
        private const val BATCH_SIZE = 50
        private const val MIN_DURATION_MS = 60000L
    }

    override suspend fun loadMusicFromDevice(): kotlin.Result<Unit> = withContext(Dispatchers.IO) {
        _isScanning.value = true
        try {
            val (musicList, extraList, userInfoList) = performMusicScan()

            musicDao.deleteAll()
            musicExtraDao.deleteAll()
            userInfoDao.deleteAll()

            musicList.chunked(BATCH_SIZE).forEach { batch ->
                musicDao.insertAll(batch)
            }

            extraList.chunked(BATCH_SIZE).forEach { batch ->
                musicExtraDao.insertAll(batch)
            }

            userInfoList.chunked(BATCH_SIZE).forEach { batch ->
                userInfoDao.insertAll(batch)
            }

            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("Repo.Music", e) { "MusicRepository: Music scan failed: ${e.message}" }
            kotlin.Result.failure(e)
        } finally {
            _isScanning.value = false
        }
    }

    override suspend fun syncMusicFromDeviceIncremental(): kotlin.Result<Unit> = withContext(Dispatchers.IO) {
        _isScanning.value = true
        try {
            val (scannedMusic, scannedExtra, scannedUserInfo) = performMusicScan()

            val existingIds = musicDao.getAllActiveIds().toSet()
            val scannedIds = scannedMusic.map { it.id }.toSet()

            val newIds = scannedIds - existingIds
            val commonIds = scannedIds.intersect(existingIds)
            val missingIds = existingIds - scannedIds

            if (newIds.isNotEmpty()) {
                val newMusic = scannedMusic.filter { it.id in newIds }
                val newExtra = scannedExtra.filter { it.id in newIds }
                val newUserInfo = scannedUserInfo.filter { it.id in newIds }

                newMusic.chunked(BATCH_SIZE).forEach { batch -> musicDao.insertAll(batch) }
                newExtra.chunked(BATCH_SIZE).forEach { batch -> musicExtraDao.insertAll(batch) }
                newUserInfo.chunked(BATCH_SIZE).forEach { batch -> userInfoDao.insertAll(batch) }
            }

            if (commonIds.isNotEmpty()) {
                val commonMusicById = scannedMusic.filter { it.id in commonIds }.associateBy { it.id }
                val commonExtraById = scannedExtra.filter { it.id in commonIds }.associateBy { it.id }

                commonIds.chunked(BATCH_SIZE).forEach { idBatch ->
                    idBatch.forEach { id ->
                        val scannedMusicItem = commonMusicById[id]
                        if (scannedMusicItem != null) {
                            musicDao.insert(
                                scannedMusicItem.copy(isDeleted = false)
                            )
                        }

                        val scannedExtraItem = commonExtraById[id]
                        if (scannedExtraItem != null) {
                            val existingExtra = musicExtraDao.getExtraFieldsById(id)
                            val mergedExtra = existingExtra?.copy(
                                lyrics = scannedExtraItem.lyrics ?: existingExtra.lyrics,
                                bitRate = scannedExtraItem.bitRate ?: existingExtra.bitRate,
                                sampleRate = scannedExtraItem.sampleRate ?: existingExtra.sampleRate,
                                fileSize = scannedExtraItem.fileSize ?: existingExtra.fileSize,
                                format = scannedExtraItem.format ?: existingExtra.format,
                                isDeleted = false
                            )
                                ?: scannedExtraItem.copy(isDeleted = false)
                            musicExtraDao.insert(mergedExtra)
                        }

                        val existingUserInfo = userInfoDao.getUserInfoById(id)
                        if (existingUserInfo == null) {
                            userInfoDao.insert(UserInfo(id = id))
                        } else if (existingUserInfo.isDeleted) {
                            userInfoDao.insert(existingUserInfo.copy(isDeleted = false))
                        }
                    }
                }
            }

            if (missingIds.isNotEmpty()) {
                musicDao.markDeletedByIds(missingIds.toList())
                musicExtraDao.markDeletedByIds(missingIds.toList())
                userInfoDao.markDeletedByIds(missingIds.toList())
            }

            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("Repo.Music", e) { "MusicRepository: Incremental music scan failed: ${e.message}" }
            kotlin.Result.failure(e)
        } finally {
            _isScanning.value = false
        }
    }

    private fun getLyrics(file: File): String? {
        return try {
            if (!file.exists() || !file.canRead()) {
                return null
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            tag?.getFirst(FieldKey.LYRICS)
                ?: tag?.getFirst("UNSYNCEDLYRICS")
                ?: tag?.getFirst("USLT")
                ?: tag?.getFirst("LYRICS:SYNCED")
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun performMusicScan(): Triple<List<Music>, List<MusicExtra>, List<UserInfo>> = withContext(Dispatchers.IO) {
        val musicList = mutableListOf<Music>()
        val musicExtraList = mutableListOf<MusicExtra>()
        val userInfoList = mutableListOf<UserInfo>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString())
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"

        val retriever = MediaMetadataRetriever()

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                        val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)) ?: "Unknown Artist"
                        val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)) ?: "Unknown Album"
                        val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                        val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                        val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE))
                        val fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))

                        val albumArtUri = ContentUris.withAppendedId(
                            "content://media/external/audio/albumart".toUri(),
                            albumId
                        ).toString()

                        var bitRate: Int? = null
                        var sampleRate: Int? = null

                        try {
                            retriever.setDataSource(path)
                            bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000)
                            sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
                        } catch (e: Exception) {
                            // ignore
                        }

                        val lyrics = File(path).let { file ->
                            if (file.exists() && file.canRead()) getLyrics(file) else null
                        }

                        musicList.add(
                            Music(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                path = path,
                                albumArtUri = albumArtUri
                            )
                        )

                        musicExtraList.add(
                            MusicExtra(
                                id = id,
                                lyrics = lyrics,
                                bitRate = bitRate,
                                sampleRate = sampleRate,
                                fileSize = fileSize,
                                format = mimeType,
                                isGetExtraInfo = false
                            )
                        )

                        userInfoList.add(
                            UserInfo(
                                id = id,
                            )
                        )
                    } catch (e: Exception) {
                        Logger.e("Repo.Music", e) { "MusicRepository: Error processing music item: ${e.message}" }
                    }
                }
            }
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        val existingDates = musicExtraDao.getAllIdAndDate().associate { it.id to it.date }
        val extrasWithDate = musicExtraList.map { e ->
            e.copy(date = existingDates[e.id] ?: currentTimeMillis())
        }
        Triple(musicList, extrasWithDate, userInfoList)
    }
}