package com.hmp.data.database

import com.hmp.test.db.createTestDatabase
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var playlistItemDao: PlaylistItemDao
    private lateinit var musicDao: MusicDao

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        playlistDao = db.playlistDao()
        playlistItemDao = db.playlistItemDao()
        musicDao = db.musicDao()
    }

    @AfterTest
    fun teardown() {
        db.close()
    }

    private fun music(id: Long, title: String = "Song$id", path: String = "/$id.mp3") =
        Music(id = id, title = title, artist = "A", album = "B", duration = 100L, path = path, albumArtUri = "")

    // ===== PlaylistDao =====

    @Test
    fun insert_returnsId() = runTest {
        val id = playlistDao.insert(Playlist(name = "My Playlist"))
        assertTrue(id > 0)
    }

    @Test
    fun getPlaylistById() = runTest {
        val id = playlistDao.insert(Playlist(name = "Test"))
        val result = playlistDao.getPlaylistById(id)
        assertNotNull(result)
        assertEquals("Test", result.name)
    }

    @Test
    fun getAllPlaylists() = runTest {
        playlistDao.insert(Playlist(name = "A"))
        playlistDao.insert(Playlist(name = "B"))
        val all = playlistDao.getAllPlaylists()
        assertEquals(2, all.size)
    }

    @Test
    fun deletePlaylist_byName() = runTest {
        playlistDao.insert(Playlist(name = "ToDelete"))
        playlistDao.insert(Playlist(name = "ToKeep"))
        playlistDao.deletePlaylist("ToDelete")
        val all = playlistDao.getAllPlaylists()
        assertEquals(1, all.size)
        assertEquals("ToKeep", all[0].name)
    }

    @Test
    fun deletePlaylistById() = runTest {
        val id = playlistDao.insert(Playlist(name = "Test"))
        playlistDao.deletePlaylistById(id)
        assertNull(playlistDao.getPlaylistById(id))
    }

    @Test
    fun renamePlaylist() = runTest {
        val id = playlistDao.insert(Playlist(name = "Old"))
        playlistDao.renamePlaylist(id, "New", 1000L)
        assertEquals("New", playlistDao.getPlaylistById(id)?.name)
    }

    @Test
    fun updateCover() = runTest {
        val id = playlistDao.insert(Playlist(name = "Test"))
        playlistDao.updateCover(id, "cover.jpg", 1000L)
        assertEquals("cover.jpg", playlistDao.getPlaylistById(id)?.coverUri)
    }

    @Test
    fun incrementPlayCount() = runTest {
        val id = playlistDao.insert(Playlist(name = "Test", playCount = 0))
        playlistDao.incrementPlayCount(id)
        playlistDao.incrementPlayCount(id)
        assertEquals(2, playlistDao.getPlaylistById(id)?.playCount)
    }

    @Test
    fun setPinned() = runTest {
        val id = playlistDao.insert(Playlist(name = "Test"))
        playlistDao.setPinned(id, true, 1000L)
        assertTrue(playlistDao.getPlaylistById(id)?.isPinned == true)
    }

    @Test
    fun updateStats() = runTest {
        val id = playlistDao.insert(Playlist(name = "Test"))
        playlistDao.updateStats(id, 10, 300000L, 1000L)
        val result = playlistDao.getPlaylistById(id)!!
        assertEquals(10, result.songCount)
        assertEquals(300000L, result.totalDurationMs)
    }

    @Test
    fun getAllPlaylistsFlow() = runTest {
        playlistDao.insert(Playlist(name = "A"))
        playlistDao.insert(Playlist(name = "B"))
        val flow = playlistDao.getAllPlaylistsFlow().first()
        assertEquals(2, flow.size)
    }

    // ===== PlaylistItemDao =====

    @Test
    fun item_insertAndRetrieve() = runTest {
        val playlistId = playlistDao.insert(Playlist(name = "Test"))
        musicDao.insert(music(1))
        playlistItemDao.insert(PlaylistItem(songId = 1, playlistId = playlistId, itemOrder = 0))
        val items = playlistItemDao.getPlaylistById(playlistId)
        assertEquals(1, items.size)
        assertEquals(1L, items[0].music.id)
    }

    /**
     * 取代原 `item_getMaxOrder`：v10 起取号与插入必须在同一条语句里完成
     * （"先读 MAX 再写"是竞态源头，DAO 也不再暴露 getMaxOrder）。
     */
    @Test
    fun item_insertWithNextOrder_isSequentialAndMovesDuplicateToLast() = runTest {
        val playlistId = playlistDao.insert(Playlist(name = "Test"))
        musicDao.insertAll(listOf(music(1), music(2), music(3)))

        playlistItemDao.insertWithNextOrder(playlistId, 1)
        playlistItemDao.insertWithNextOrder(playlistId, 2)
        assertEquals(listOf(1L, 2L), playlistItemDao.getSongIdsInOrder(playlistId))

        // 重复添加同一首：不新增行，而是移到末尾（与原 @Insert(REPLACE) 语义一致）
        playlistItemDao.insertWithNextOrder(playlistId, 1)
        assertEquals(listOf(2L, 1L), playlistItemDao.getSongIdsInOrder(playlistId))
        assertEquals(2, playlistItemDao.getPlaylistById(playlistId).size)
    }

    @Test
    fun item_deleteItemByIds() = runTest {
        val playlistId = playlistDao.insert(Playlist(name = "Test"))
        musicDao.insertAll(listOf(music(1), music(2)))
        playlistItemDao.insert(PlaylistItem(songId = 1, playlistId = playlistId, itemOrder = 0))
        playlistItemDao.insert(PlaylistItem(songId = 2, playlistId = playlistId, itemOrder = 1))
        playlistItemDao.deleteItemByIds(1, playlistId)
        val items = playlistItemDao.getPlaylistById(playlistId)
        assertEquals(1, items.size)
        assertEquals(2L, items[0].music.id)
    }

    @Test
    fun item_updateItemOrder() = runTest {
        val playlistId = playlistDao.insert(Playlist(name = "Test"))
        musicDao.insertAll(listOf(music(1), music(2)))
        playlistItemDao.insert(PlaylistItem(songId = 1, playlistId = playlistId, itemOrder = 0))
        playlistItemDao.insert(PlaylistItem(songId = 2, playlistId = playlistId, itemOrder = 1))

        // v10 的 UNIQUE(playlistId,itemOrder) 让"裸换位"当场撞号 —— 钉住这条约束，
        // 免得有人把重排改回逐条 UPDATE（置顶/重排是活的 UI 路径，撞号=崩溃）。
        val collision = runCatching { playlistItemDao.updateItemOrder(playlistId, 2, 0) }
        assertTrue(collision.isFailure, "裸 updateItemOrder 互换应撞唯一索引；不撞说明索引没了")
        // 撞号的那次 UPDATE 已回滚，原顺序保持 0/1
        assertEquals(listOf(1L, 2L), playlistItemDao.getSongIdsInOrder(playlistId))

        // 正确的做法：两阶段整体重排（同一个事务里顺带重算派生列）
        playlistItemDao.reorderAndRefresh(playlistId, listOf(2L, 1L), 1000L)
        // Order should now be: song2 (order=0), song1 (order=1)
        val items = playlistItemDao.getPlaylistById(playlistId)
        assertEquals(2L, items[0].music.id)
        assertEquals(1L, items[1].music.id)
    }

    @Test
    fun item_cascadeDelete_onPlaylistDelete() = runTest {
        val playlistId = playlistDao.insert(Playlist(name = "Test"))
        musicDao.insert(music(1))
        playlistItemDao.insert(PlaylistItem(songId = 1, playlistId = playlistId, itemOrder = 0))
        playlistDao.deletePlaylistById(playlistId)
        val items = playlistItemDao.getPlaylistById(playlistId)
        assertTrue(items.isEmpty())
    }

    @Test
    fun item_getAllPlaylistItems() = runTest {
        val p1 = playlistDao.insert(Playlist(name = "A"))
        val p2 = playlistDao.insert(Playlist(name = "B"))
        musicDao.insertAll(listOf(music(1), music(2), music(3)))
        playlistItemDao.insert(PlaylistItem(songId = 1, playlistId = p1, itemOrder = 0))
        playlistItemDao.insert(PlaylistItem(songId = 2, playlistId = p2, itemOrder = 0))
        playlistItemDao.insert(PlaylistItem(songId = 3, playlistId = p1, itemOrder = 1))
        assertEquals(3, playlistItemDao.getAllPlaylistItems().size)
    }

    /**
     * 一-2 / D3-03：v10 的 `UNIQUE(playlistId,itemOrder)` 下并发添加不能撞号。
     *
     * 旧写法是"读 MAX → +1 → insert"跨三条语句，两次并发就写出同一个序号；
     * 现在取号与插入在同一语句里完成，两次添加各自拿到不同序号。
     */
    @Test
    fun addSongAndRefresh_concurrentAddsGetDistinctOrders() = runTest {
        val playlistId = playlistDao.insert(Playlist(name = "P"))
        musicDao.insertAll(listOf(music(1), music(2)))

        coroutineScope {
            launch { playlistItemDao.addSongAndRefresh(playlistId, 1, 1000L) }
            launch { playlistItemDao.addSongAndRefresh(playlistId, 2, 1000L) }
        }

        val songIds = playlistItemDao.getSongIdsInOrder(playlistId)
        assertEquals(2, songIds.size, "两次添加都要落库，谁也不能被唯一索引挤掉")
        val orders = playlistItemDao.getAllPlaylistItems()
            .filter { it.playlistId == playlistId }
            .map { it.itemOrder }
        assertEquals(2, orders.toSet().size, "并发添加不得写出重复序号")
        assertEquals(2, playlistDao.getPlaylistById(playlistId)?.songCount, "派生计数要收敛到最后一次写入")
    }

    /** 派生列（songCount / totalDurationMs / coverUri）随加曲移曲同步 —— SQL 化后与旧 Java 实现等价。 */
    @Test
    fun addSongAndRefresh_and_removeSongAndRefresh_keepDerivedColumnsInSync() = runTest {
        val playlistId = playlistDao.insert(Playlist(name = "P"))
        musicDao.insertAll(
            listOf(
                music(1).copy(albumArtUri = "cover-1"),
                music(2).copy(albumArtUri = "cover-2"),
            )
        )

        playlistItemDao.addSongAndRefresh(playlistId, 1, 1000L)
        with(playlistDao.getPlaylistById(playlistId)!!) {
            assertEquals(1, songCount)
            assertEquals(100L, totalDurationMs)
            assertEquals("cover-1", coverUri, "空歌单的第一首封面应回填")
        }

        playlistItemDao.addSongAndRefresh(playlistId, 2, 1000L)
        with(playlistDao.getPlaylistById(playlistId)!!) {
            assertEquals(2, songCount)
            assertEquals(200L, totalDurationMs)
        }

        playlistItemDao.removeSongAndRefresh(playlistId, 1, 1000L)
        with(playlistDao.getPlaylistById(playlistId)!!) {
            assertEquals(1, songCount, "移曲后计数要跟降")
            assertEquals(100L, totalDurationMs)
            assertEquals("cover-2", coverUri, "首曲变了，封面跟着取新的首曲")
        }

        // 软删的曲目不计入（与旧实现的 getPlaylistById 过滤同语义）
        musicDao.markDeletedByIds(listOf(2L))
        playlistItemDao.refreshStats(playlistId, 2000L)
        assertEquals(0, playlistDao.getPlaylistById(playlistId)!!.songCount, "软删后计数应清零")
    }
}
