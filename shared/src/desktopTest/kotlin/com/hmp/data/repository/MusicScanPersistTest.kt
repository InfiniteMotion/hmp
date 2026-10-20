package com.hmp.data.repository

import com.hmp.data.database.AppDatabase
import com.hmp.data.database.Music
import com.hmp.data.database.MusicDao
import com.hmp.data.database.MusicExtra
import com.hmp.data.database.RoomTransactionRunner
import com.hmp.data.database.UserInfo
import com.hmp.data.database.UserInfoDao
import com.hmp.data.network.OpenAiCompatibleAdapter
import com.hmp.domain.music.MusicInfo
import com.hmp.test.db.createTestDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 一-2 / D2-01 + D2-02 判据：一次扫描的落库必须**事务内**且**按 id upsert**。
 *
 * 装配用真 Room（`createTestDatabase()`）+ 真基类，只把平台分叉的 `performMusicScan` 换成定好的数据 ——
 * 判据问的是"库里最后剩下什么"，用假仓库就只剩"调用了几次"，那种断言证不出用户资产没被抹掉。
 */
class MusicScanPersistTest {

    private lateinit var db: AppDatabase

    /** 测试可控的扫描结果。三端各自的 `performMusicScan` 在真机上读设备，这里就是它的替身。 */
    private var scanResult = Triple<List<Music>, List<MusicExtra>, List<UserInfo>>(
        emptyList(), emptyList(), emptyList()
    )

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
    }

    @AfterTest
    fun teardown() {
        db.close()
    }

    private fun repo() = ScanRepo()

    /** 只实现"扫描"这一件事的仓库替身，落库走 [MusicRepositoryBase] 的真实现。 */
    private inner class ScanRepo(private val musicDaoOverride: MusicDao? = null) : MusicRepositoryBase(
        musicDao = musicDaoOverride ?: db.musicDao(),
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
        override suspend fun performMusicScan() = scanResult
        override suspend fun getAllMusicInfoAsList(orderBy: String, orderType: String): List<MusicInfo> = emptyList()
        override suspend fun getDeletedMusicIdsGroupedByFolder(): List<Pair<String, List<Long>>> = emptyList()
    }

    private fun music(id: Long, deleted: Boolean = false) = Music(
        id = id,
        title = "Song$id",
        artist = "A",
        album = "B",
        duration = 100L,
        path = "/lib/$id.mp3",
        albumArtUri = "",
        isDeleted = deleted,
    )

    private fun scannedIds(vararg ids: Long) = ids.map { music(it) }

    private fun seedScan(vararg ids: Long) {
        scanResult = Triple(
            scannedIds(*ids),
            ids.map { MusicExtra(id = it, lyrics = "tag-lyrics-$it", isGetExtraInfo = false) },
            ids.map { UserInfo(id = it) },
        )
    }

    // ═══ D2-01 判据 ①：重扫不得抹掉用户资产 ═══

    @Test
    fun fullRescan_keepsPlayCountLyricsAndEnrichment() = runTest {
        db.musicDao().insertAll(scannedIds(1, 2, 3))
        // 用户在库里的三样资产：播放数、收藏、歌词 + 已富化标记与文案
        db.userInfoDao().upsertAll(listOf(UserInfo(id = 1, liked = true, playCount = 7)))
        db.musicExtraDao().upsertAll(
            listOf(
                MusicExtra(
                    id = 1,
                    lyrics = "用户改过的歌词",
                    isGetExtraInfo = true,
                    rewards = "已富化的文案",
                )
            )
        )

        // 同三首文件重扫一遍；扫描侧的 extra 是"标签里读到的东西"，不该盖掉库里的用户内容
        seedScan(1, 2, 3)
        assertTrue(repo().loadMusicFromDevice().isSuccess)

        val user = db.userInfoDao().getUserInfoById(1)!!
        assertEquals(7, user.playCount, "播放数被重扫抹掉了")
        assertTrue(user.liked, "收藏被重扫抹掉了")

        val extra = db.musicExtraDao().getExtraById(1)!!
        assertEquals("已富化的文案", extra.rewards, "富化结果被重扫抹掉了")
        assertTrue(extra.isGetExtraInfo, "已富化标记被重扫改回未富化 —— Enrich 管道会把整库重新判为待处理")
        // 歌词列是扫描自己读的列：标签读到就以标签为准（与旧的增量实现同口径）
        assertEquals("tag-lyrics-1", extra.lyrics)
    }

    @Test
    fun rescan_absentFile_isMarkedDeleted_notErased() = runTest {
        db.musicDao().insertAll(scannedIds(1, 2, 3))
        db.userInfoDao().upsertAll(listOf(UserInfo(id = 3, playCount = 4)))

        // 第 3 首这次没扫到（文件被移走）
        seedScan(1, 2)
        assertTrue(repo().syncMusicFromDeviceIncremental().isSuccess)

        assertEquals(2, db.musicDao().getMusicCount().first(), "没扫到的歌应转为不可见，而不是从库里消失")
        assertTrue(db.musicDao().getDeletedMusicIdAndPath().any { it.id == 3L })
        // 行还在，用户数据也还在 —— 文件挪回来就能恢复
        val gone = db.userInfoDao().getUserInfoById(3)!!
        assertEquals(4, gone.playCount)
        assertTrue(gone.isDeleted)
    }

    @Test
    fun rescan_fileCameBack_becomesVisibleAgain() = runTest {
        db.musicDao().insertAll(listOf(music(1), music(2, deleted = true)))
        db.userInfoDao().upsertAll(listOf(UserInfo(id = 2, isDeleted = true)))

        seedScan(1, 2)
        assertTrue(repo().syncMusicFromDeviceIncremental().isSuccess)

        assertEquals(2, db.musicDao().getMusicCount().first(), "文件回来的歌要重新可见")
    }

    // ═══ D2-01 判据 ②：中途取消不得留下半空库 ═══

    /**
     * 让第 2 批 `music` 写入抛 [CancellationException]，断言第 1 批也查不到 ——
     * 即整条落库确实在一个事务里。旧写法是 `deleteAll()` 后逐批 autocommit，
     * 取消点落在中间就对外表现为空曲库。
     */
    @Test
    fun cancellationMidWrite_rollsBackWholeScan() = runTest {
        val before = scannedIds(1, 2, 3).map { it.copy(isDeleted = false) }
        db.musicDao().insertAll(before)
        val countBefore = db.musicDao().getMusicCount().first()
        assertEquals(3, countBefore, "前置条件")

        // 51 首 → 批 1（50 首）写成功、批 2 抛；加上一个不存在的旧行来验"删除"也没留下
        val many = (1L..51L).map { music(it) }
        scanResult = Triple(many, many.map { MusicExtra(id = it.id, isGetExtraInfo = false) }, emptyList())

        assertFailsWith<CancellationException> {
            ScanRepo(CancelOnSecondBatchDao(db.musicDao())).loadMusicFromDevice()
        }

        assertEquals(
            countBefore,
            db.musicDao().getMusicCount().first(),
            "第 1 批写进去又没回滚 —— 落库没有真的包在一个事务里"
        )
        assertEquals(3, db.musicDao().getAllIds().size, "回滚后连『标记缺失』的写也不能留下")
    }

    /** 只拦 `insertAll`，其余方法原样转发给真 DAO（Kotlin 接口委托，不用 mock 库）。 */
    private class CancelOnSecondBatchDao(private val real: MusicDao) : MusicDao by real {
        private var batches = 0

        override suspend fun insertAll(musics: List<Music>) {
            batches++
            if (batches == 2) throw CancellationException("用户在扫描中途取消了")
            real.insertAll(musics)
        }
    }

    // ═══ D2-02 判据：用户隐藏的意图要活过一次重扫 ═══

    @Test
    fun userRemovedTrack_staysHiddenAfterRescanAndSurvivesInRemovedList() = runTest {
        db.musicDao().insertAll(scannedIds(1, 2))
        db.userInfoDao().upsertAll(listOf(UserInfo(id = 1), UserInfo(id = 2)))

        // 用户把第 1 首从曲库移除（文件还在盘上）
        repo().removeFromLibrary(listOf(1L))
        assertEquals(1, db.musicDao().getMusicCount().first())

        // 再跑一次全量：文件仍在 → 旧实现会把 isDeleted 一律写回 false（＝隐藏失效）
        seedScan(1, 2)
        assertTrue(repo().loadMusicFromDevice().isSuccess)

        assertEquals(1, db.musicDao().getMusicCount().first(), "用户隐藏的歌在重扫后复活了")
        assertEquals(
            listOf(1L),
            db.musicDao().getRemovedMusicIdAndPath().map { it.id },
            "「已移除」列表要按用户意图列，不能把『文件不在了』也算进来"
        )
    }

    @Test
    fun restoreToLibrary_clearsIntentAndVisibility() = runTest {
        db.musicDao().insertAll(scannedIds(1))
        db.userInfoDao().upsertAll(listOf(UserInfo(id = 1)))
        repo().removeFromLibrary(listOf(1L))

        repo().restoreToLibrary(listOf(1L))
        assertEquals(1, db.musicDao().getMusicCount().first())
        assertTrue(db.userInfoDao().getRemovedByUserIds().isEmpty(), "恢复后意图位要清掉")

        // 之后再重扫一次，仍然是可见的
        seedScan(1)
        repo().loadMusicFromDevice()
        assertEquals(1, db.musicDao().getMusicCount().first())
    }

    /** 文件缺失与用户移除都不可见，但只有后者进"已移除"列表 —— 两成因必须能分开。 */
    @Test
    fun missingFile_and_userRemoval_areDistinguishable() = runTest {
        db.musicDao().insertAll(scannedIds(1, 2, 3))
        db.userInfoDao().upsertAll(listOf(UserInfo(id = 1), UserInfo(id = 2), UserInfo(id = 3)))
        repo().removeFromLibrary(listOf(1L))

        // 第 2 首只是没扫到
        seedScan(1, 3)
        repo().syncMusicFromDeviceIncremental()

        assertEquals(1, db.musicDao().getMusicCount().first())
        assertEquals(2, db.musicDao().getDeletedMusicIdAndPath().size, "两成因都记在 isDeleted 上")
        assertEquals(listOf(1L), db.musicDao().getRemovedMusicIdAndPath().map { it.id })
    }
}
