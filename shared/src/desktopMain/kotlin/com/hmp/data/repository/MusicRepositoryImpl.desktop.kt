package com.hmp.data.repository

import com.hmp.data.database.ListeningDuration
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
import com.hmp.data.util.DeviceMusicScanner
import com.hmp.data.util.stringToPinyinSortKey
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.backup.TransactionRunner
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

/**
 * Desktop 播放器仓库实现（B0 去重后仅保留平台分叉）：文件系统扫描（DeviceMusicScanner +
 * scanDirectoryConfig）/ 页面排序语义差异方法。其余 DAO/AI/统计/备份逻辑在 [MusicRepositoryBase]。
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
    transactionRunner: TransactionRunner,
    private val settingsRepository: SettingsRepository
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
    json = json,
    transactionRunner = transactionRunner
) {


    // region Scan & Persist

    override suspend fun performMusicScan(): Triple<List<Music>, List<MusicExtra>, List<UserInfo>> =
        withContext(Dispatchers.Default) {
            val config = settingsRepository.scanDirectoryConfig.first()
            if (config.scanDirectories.isNotEmpty()) {
                DeviceMusicScanner.setScanDirectories(config.scanDirectories.map { File(it) })
            }
            DeviceMusicScanner.setBlockedDirectories(config.blockedDirectories)
            val scannedFiles = DeviceMusicScanner.scanMusic()

            val musicList = mutableListOf<Music>()
            val musicExtraList = mutableListOf<MusicExtra>()
            val userInfoList = mutableListOf<UserInfo>()

            for (file in scannedFiles) {
                try {
                    musicList.add(
                        Music(
                            id = file.id,
                            title = file.title,
                            artist = file.artist,
                            album = file.album,
                            duration = file.duration,
                            path = file.path,
                            albumArtUri = file.albumArtUri
                        )
                    )

                    musicExtraList.add(
                        MusicExtra(
                            id = file.id,
                            lyrics = file.lyrics,
                            bitRate = file.bitRate,
                            sampleRate = file.sampleRate,
                            fileSize = file.fileSize,
                            format = file.format,
                            isGetExtraInfo = false
                        )
                    )

                    userInfoList.add(UserInfo(id = file.id))
                } catch (e: Exception) {
                    HmpLog.e(LogTag.DataMusicRepo, e) { "🎵 MusicRepository: Error processing music item: ${e.message}" }
                }
            }

            val existingDates = musicExtraDao.getAllIdAndDate().associate { it.id to it.date }
            val extrasWithDate = musicExtraList.map { e ->
                e.copy(date = existingDates[e.id] ?: currentTimeMillis())
            }

            Triple(musicList, extrasWithDate, userInfoList)
        }

    // endregion

    // region Query（本端排序语义与 Android/iOS 刻意不同，留在平台层）

    override suspend fun getAllMusicInfoAsList(orderBy: String, orderType: String): List<MusicInfo> {
        val safeOrderType = if (orderType.uppercase() == "DESC") "DESC" else "ASC"
        if (orderBy == "title" || orderBy == "artist" || orderBy == "album") {
            val list = musicAllDao.getAllMusicInfoAsListById().map { it.toDomain() }
            val keyFn: (MusicInfo) -> String = when (orderBy) {
                "title" -> { info -> stringToPinyinSortKey(info.music.title) }
                "artist" -> { info -> stringToPinyinSortKey(info.music.artist) }
                else -> { info -> stringToPinyinSortKey(info.music.album) }
            }
            val sorted = list.sortedWith(compareBy(keyFn))
            return if (safeOrderType == "DESC") sorted.reversed() else sorted
        }
        val list = musicAllDao.getAllMusicInfoAsListById().map { it.toDomain() }
        return if (safeOrderType == "DESC") {
            when (orderBy) {
                "duration" -> list.sortedByDescending { it.music.duration }
                "playCount" -> list.sortedByDescending { it.userInfo?.playCount ?: 0 }
                else -> list.sortedByDescending { it.music.id }
            }
        } else {
            when (orderBy) {
                "duration" -> list.sortedBy { it.music.duration }
                "playCount" -> list.sortedBy { it.userInfo?.playCount ?: 0 }
                else -> list
            }
        }
    }

    override suspend fun getDeletedMusicIdsGroupedByFolder(): List<Pair<String, List<Long>>> {
        val list = musicDao.getRemovedMusicIdAndPath()
        return list
            .groupBy { (_, path) ->
                try {
                    path.substringBeforeLast("/", "Unknown")
                } catch (e: Exception) {
                    "Unknown"
                }
            }
            .map { (path, entries) -> path to entries.map { it.id } }
            .sortedByDescending { it.second.size }
    }

    // endregion

}
