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
import com.hmp.domain.backup.TransactionRunner
import com.hmp.domain.music.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

/**
 * iOS 播放器仓库实现（B0 去重后仅保留平台分叉）：NSFileManager 目录扫描（DeviceMusicScanner）/
 * 排序语义差异方法。其余 DAO/AI/统计/备份逻辑在 [MusicRepositoryBase]。
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
    transactionRunner: TransactionRunner
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

    // region Query（排序与"按文件夹分组"都已上收 `MusicRepositoryBase`，本端无分叉）

    // `getAllMusicInfoAsList` 已上收（一-5 / D2-08，与 Desktop 那份逐字相同）；
    // `getDeletedMusicIdsGroupedByFolder` 已上收（一-5 / D2-13）—— iOS 是 POSIX 路径，
    // 走基类默认的 `/`，不需要再手写 `substringBeforeLast("/")`。

    // endregion

}
