package com.hmp.data.database

import com.hmp.test.db.createTestDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 一-2 / D7-02 判据：恢复流程的跨仓库写收进一个事务后，中途失败要回滚。
 *
 * 用**真 Room + 真 DAO 写**而不是假仓库计数器：判据问的是"第 3 步撞约束后前 2 步在库里查不查得到"，
 * 只有真引擎能答。第 3 步用歌单重名撞 v10 的 `UNIQUE(name)` —— 该索引由迁移链自己建，不依赖外键开关。
 */
class BackupRestoreTransactionTest {

    private lateinit var db: AppDatabase

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
    }

    @AfterTest
    fun teardown() {
        db.close()
    }

    @Test
    fun runner_rollsBackEveryWrite_whenBlockFails() = runTest {
        // 前置数据：用来区分"回滚"和"压根没写过"
        db.listeningDurationDao().insertAll(
            listOf(ListeningDuration(date = "2026-01-01", duration = 100L, updatedAt = 1L))
        )

        val error = assertFailsWith<Exception> {
            RoomTransactionRunner(db).run {
                // 第 1 步（曲目用户态）
                db.userInfoDao().insertAll(
                    listOf(UserInfo(id = 42L, liked = true, playCount = 7))
                )
                // 第 2 步（听歌统计，整表替换）
                db.listeningDurationDao().replaceAll(
                    listOf(ListeningDuration(date = "2026-01-02", duration = 200L, updatedAt = 2L))
                )
                // 第 3 步（歌单）：重名撞唯一索引
                db.playlistDao().insert(Playlist(name = "重复"))
                db.playlistDao().insert(Playlist(name = "重复"))
            }
        }
        assertTrue(
            error.message?.contains("constraint") == true || error.message?.contains("UNIQUE") == true,
            "第 3 步应因唯一约束失败，实际：${error.message}"
        )

        assertNull(db.userInfoDao().getUserInfoById(42L), "第 1 步应已回滚")
        assertEquals(
            listOf("2026-01-01"),
            db.listeningDurationDao().getAllDurations().map { it.date },
            "第 2 步的整表替换应已回滚"
        )
        assertTrue(db.playlistDao().getAllPlaylists().isEmpty(), "第 3 步自己也要回滚")
    }

    /**
     * 另一半：DAO 自带的 `@Transaction`（[PlaylistItemDao.reorderAndRefresh]）被包在外层事务里时
     * 不能各交各的 —— 外层回滚它也得跟着回滚。
     */
    @Test
    fun daoTransaction_insideOuterRollback_leavesNoTrace() = runTest {
        val playlistId = db.playlistDao().insert(Playlist(name = "P"))
        db.musicDao().insertAll(
            listOf(
                Music(id = 1L, title = "a", artist = "A", album = "B", duration = 1L, path = "/1.mp3", albumArtUri = ""),
                Music(id = 2L, title = "b", artist = "A", album = "B", duration = 1L, path = "/2.mp3", albumArtUri = "")
            )
        )
        db.playlistItemDao().insertPlaylist(
            listOf(
                PlaylistItem(songId = 1L, playlistId = playlistId, itemOrder = 0),
                PlaylistItem(songId = 2L, playlistId = playlistId, itemOrder = 1)
            )
        )

        assertFailsWith<Exception> {
            RoomTransactionRunner(db).run {
                db.playlistItemDao().reorderAndRefresh(playlistId, listOf(2L, 1L), 999L)
                error("第 3 步失败")
            }
        }

        assertEquals(
            listOf(1L, 2L),
            db.playlistItemDao().getSongIdsInOrder(playlistId),
            "外层回滚后 DAO 事务的写入不该留下痕迹"
        )
    }
}
