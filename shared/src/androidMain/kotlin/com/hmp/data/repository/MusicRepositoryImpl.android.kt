package com.hmp.data.repository

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.core.net.toUri
import com.hmp.data.database.AgentAuditLogDao
import com.hmp.data.database.ForgottenDeliveryDao
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
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

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
    forgottenDeliveryDao: ForgottenDeliveryDao,
    private val settingsRepository: SettingsRepository,
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
    forgottenDeliveryDao = forgottenDeliveryDao,
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

        /**
         * 逐文件标签解析的并发上限。单文件工作是「小 IO + 轻解析」，
         * 4 路已能把大库扫描提速约一个数量级；更高并发对机械存储/低端机反而增加争抢。
         */
        private const val TAG_PARSE_PARALLELISM = 4
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
            HmpLog.e(LogTag.DataMusicRepo, e) { "🎵 MusicRepository: Music scan failed: ${e.message}" }
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
            HmpLog.e(LogTag.DataMusicRepo, e) { "🎵 MusicRepository: Incremental music scan failed: ${e.message}" }
            kotlin.Result.failure(e)
        } finally {
            _isScanning.value = false
        }
    }

    /** 逐文件解析产物：[MusicExtra] 的三个需要读文件才能拿到的字段。 */
    private data class FileExtra(
        val lyrics: String?,
        val bitRate: Int?,
        val sampleRate: Int?,
    )

    /** MediaStore 查询结果行（纯元数据，无逐文件 IO，可单线程快速收集）。 */
    private data class ScanRow(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val path: String,
        val albumArtUri: String,
        val mimeType: String?,
        val fileSize: Long,
    )

    /**
     * 单次文件解析：歌词 + 比特率 + 采样率（[FileExtra]）。
     *
     * 旧实现每首歌做**两次**全文件解析且**全程单线程**：MediaMetadataRetriever（走 media
     * 服务 IPC + demux，单次约 20–60ms）取比特率/采样率，再 jaudiotagger AudioFileIO.read
     * 取歌词——大库扫描因此要数分钟。现合并为 jaudiotagger **单次**解析（纯文件 seek 读取，
     * 约几 ms）+ 调用方并行化。
     *
     * - jaudiotagger 首选：歌词取自 tag；比特率取自 header（其单位本就是 **kbps**，
     *   与旧 retriever 返回 bps 后 `/1000` 同单位，不再换算）；采样率 Hz。
     * - jaudiotagger 不支持的格式（wma/opus 等）回退 MediaMetadataRetriever。
     * - 两者皆失败时字段留空，与旧行为一致。
     */
    private fun parseFileExtra(path: String): FileExtra {
        var lyrics: String? = null
        var bitRate: Int? = null
        var sampleRate: Int? = null
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tag
                val header = audioFile.audioHeader
                lyrics = tag?.getFirst(FieldKey.LYRICS)
                    ?: tag?.getFirst("UNSYNCEDLYRICS")
                    ?: tag?.getFirst("USLT")
                    ?: tag?.getFirst("LYRICS:SYNCED")
                // jaudiotagger 3.0.1：getBitRateAsNumber() 单位即 kbps（接口 Javadoc 明确），直接取整
                bitRate = header?.bitRateAsNumber?.toInt()
                sampleRate = header?.sampleRateAsNumber
            }
        } catch (e: Exception) {
            try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()?.div(1000)
                    sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()
                } finally {
                    retriever.release()
                }
            } catch (e2: Exception) {
                // ignore：字段留空
            }
        }
        return FileExtra(lyrics, bitRate, sampleRate)
    }

    /**
     * 目录路径 → MediaStore `LIKE` 模式（前缀匹配该目录下的文件）。
     *
     * 转义 LIKE 元字符（`\` / `%` / `_`），避免路径中的下划线被当作单字符通配符
     * （如 `My_Music` 会误匹配 `MyXMusic`）。配合 SQL 侧的 `ESCAPE '\'` 使用。
     */
    private fun String.toMediaStoreLikePattern(): String {
        val escaped = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "$escaped/%"
    }

    private suspend fun performMusicScan(): Triple<List<Music>, List<MusicExtra>, List<UserInfo>> = withContext(Dispatchers.IO) {
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

        // 目录管理（R3 恢复）：Android 无「扫描根」概念（MediaStore 已索引全部可读媒体），
        // 故此处语义为**过滤**：scanDirectories 作 include、blockedDirectories 作 exclude，
        // 以 DATA 前缀匹配施加到查询上。此前该配置在 Android 侧既不持久化也不被读取。
        val dirConfig = settingsRepository.scanDirectoryConfig.first()
        val selectionBuilder = StringBuilder(
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ?"
        )
        val selectionArgs = mutableListOf(MIN_DURATION_MS.toString())
        if (dirConfig.scanDirectories.isNotEmpty()) {
            selectionBuilder.append(" AND (")
            dirConfig.scanDirectories.forEachIndexed { index, dir ->
                if (index > 0) selectionBuilder.append(" OR ")
                selectionBuilder.append("${MediaStore.Audio.Media.DATA} LIKE ? ESCAPE '\\'")
                selectionArgs.add(dir.toMediaStoreLikePattern())
            }
            selectionBuilder.append(")")
        }
        if (dirConfig.blockedDirectories.isNotEmpty()) {
            dirConfig.blockedDirectories.forEach { dir ->
                selectionBuilder.append(" AND ${MediaStore.Audio.Media.DATA} NOT LIKE ? ESCAPE '\\'")
                selectionArgs.add(dir.toMediaStoreLikePattern())
            }
        }
        if (dirConfig.scanDirectories.isNotEmpty() || dirConfig.blockedDirectories.isNotEmpty()) {
            HmpLog.d(LogTag.DataMusicRepo) {
                "🎵 目录过滤生效 | include=${dirConfig.scanDirectories.size} | exclude=${dirConfig.blockedDirectories.size}"
            }
        }
        val selection = selectionBuilder.toString()
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"

        // ── 第一步：MediaStore 查询（纯元数据，单线程，毫秒级）──
        val scanRows = mutableListOf<ScanRow>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs.toTypedArray(),
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

                    scanRows.add(
                        ScanRow(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            path = path,
                            albumArtUri = albumArtUri,
                            mimeType = mimeType,
                            fileSize = fileSize
                        )
                    )
                } catch (e: Exception) {
                    HmpLog.e(LogTag.DataMusicRepo, e) { "🎵 MusicRepository: Error processing music item: ${e.message}" }
                }
            }
        }

        // ── 第二步：逐文件标签解析（[TAG_PARSE_PARALLELISM] 路并行，单文件单次读取）──
        val fileExtras: List<FileExtra> = coroutineScope {
            val semaphore = Semaphore(permits = TAG_PARSE_PARALLELISM)
            scanRows.map { row ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { parseFileExtra(row.path) }
                }
            }.awaitAll()
        }

        val musicList = mutableListOf<Music>()
        val musicExtraList = mutableListOf<MusicExtra>()
        val userInfoList = mutableListOf<UserInfo>()
        scanRows.forEachIndexed { index, row ->
            val extra = fileExtras[index]
            musicList.add(
                Music(
                    id = row.id,
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    duration = row.duration,
                    path = row.path,
                    albumArtUri = row.albumArtUri
                )
            )
            musicExtraList.add(
                MusicExtra(
                    id = row.id,
                    lyrics = extra.lyrics,
                    bitRate = extra.bitRate,
                    sampleRate = extra.sampleRate,
                    fileSize = row.fileSize,
                    format = row.mimeType,
                    isGetExtraInfo = false
                )
            )
            userInfoList.add(
                UserInfo(
                    id = row.id,
                )
            )
        }

        val existingDates = musicExtraDao.getAllIdAndDate().associate { it.id to it.date }
        val extrasWithDate = musicExtraList.map { e ->
            e.copy(date = existingDates[e.id] ?: currentTimeMillis())
        }
        Triple(musicList, extrasWithDate, userInfoList)
    }

}
