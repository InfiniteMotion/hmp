package com.hmp.test.repo

import com.hmp.data.database.AppDatabase
import com.hmp.data.database.MusicExtra
import com.hmp.data.database.RoomTransactionRunner
import com.hmp.data.database.UserInfo
import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.data.repository.MusicRepositoryBase
import com.hmp.domain.music.MusicInfo
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

/**
 * 用真 Room（`createTestDatabase()`）装配的 [MusicRepositoryBase] 测试替身：
 * 平台分叉方法置为桩，其余走基类真实现。
 *
 * 一-3 把它从 `MusicRepositoryBaseTest` 里抽出来放这一处 —— 时间口径那组判据要用同一份装配，
 * 复制两份就会出现在一份里改了、另一份还在测老行为的可能。
 * 顺带删掉了原先对 `getAvgDailyListeningMinutes` 的桩覆写：基类有真实现（D5-03 的判据要跑它）。
 */
class InMemoryMusicRepository(db: AppDatabase) : MusicRepositoryBase(
    musicDao = db.musicDao(),
    musicExtraDao = db.musicExtraDao(),
    userInfoDao = db.userInfoDao(),
    musicAllDao = db.musicAllDao(),
    musicLabelDao = db.musicLabelDao(),
    playbackHistoryDao = db.playbackHistoryDao(),
    listeningDurationDao = db.listeningDurationDao(),
    playlistDao = db.playlistDao(),
    playlistItemDao = db.playlistItemDao(),
    openAiCompatibleAdapter = OpenAiCompatibleAdapter(HttpClient(), Json),
    json = Json,
    agentAuditLogDao = db.agentAuditLogDao(),
    forgottenDeliveryDao = db.forgottenDeliveryDao(),
    transactionRunner = RoomTransactionRunner(db),
) {
    // 扫描落库已在基类（D2-01），本类不用它：给一个空扫描结果即可
    override suspend fun performMusicScan() =
        Triple(emptyList<com.hmp.data.database.Music>(), emptyList<MusicExtra>(), emptyList<UserInfo>())
    override suspend fun getAllMusicInfoAsList(orderBy: String, orderType: String): List<MusicInfo> = emptyList()
    override suspend fun getDeletedMusicIdsGroupedByFolder(): List<Pair<String, List<Long>>> = emptyList()

    // ═══ W0 HelloSubAgent stub ═══
    override suspend fun getRecentSkipRate(limit: Int, days: Int): List<Long> = emptyList()
    override suspend fun getRecentPlayRate(limit: Int, days: Int): List<Long> = emptyList()
    override suspend fun getForgottenTracks(days: Int, limit: Int): List<Pair<Long, Long?>> = emptyList()
    override suspend fun getAnniversaryTracks(date: String): List<Triple<Long, Long, Int>> = emptyList()
    override suspend fun getGlobalTopLabels(limit: Int): List<com.hmp.domain.enum.LabelName> = emptyList()
    override suspend fun getMusicInfoByIds(ids: List<Long>): List<MusicInfo> = emptyList()
}
