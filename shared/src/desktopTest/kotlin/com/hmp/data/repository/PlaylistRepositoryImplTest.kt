package com.hmp.data.repository

import com.hmp.data.database.AppDatabase
import com.hmp.data.database.Music
import com.hmp.data.database.MusicExtra
import com.hmp.data.database.UserInfo
import com.hmp.test.db.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaylistRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PlaylistRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = PlaylistRepositoryImpl(db.playlistDao(), db.playlistItemDao(), db.musicAllDao())
    }

    @AfterTest
    fun teardown() { db.close() }

    private suspend fun insertMusicWithCover(id: Long, cover: String) {
        db.musicDao().insert(
            Music(
                id = id, title = "Song$id", artist = "A", album = "B",
                duration = 100000L, path = "/$id.mp3", albumArtUri = cover
            )
        )
        db.musicExtraDao().insert(MusicExtra(id = id, isGetExtraInfo = true))
        db.userInfoDao().insert(UserInfo(id = id))
    }

    private suspend fun insertMusic(id: Long, title: String = "Song$id", duration: Long = 100000L) {
        db.musicDao().insert(Music(id = id, title = title, artist = "A", album = "B", duration = duration, path = "/$id.mp3", albumArtUri = ""))
        db.musicExtraDao().insert(MusicExtra(id = id, isGetExtraInfo = true))
        db.userInfoDao().insert(UserInfo(id = id))
    }

    @Test fun createPlaylist_returnsId() = runTest { assertTrue(repo.createPlaylist("P") > 0) }

    @Test fun createAndGetAll() = runTest {
        repo.createPlaylist("A"); repo.createPlaylist("B")
        assertEquals(2, repo.getAllPlaylists().size)
    }

    @Test fun removeByName() = runTest {
        repo.createPlaylist("Del"); repo.createPlaylist("Keep")
        repo.removePlaylist("Del")
        assertEquals(1, repo.getAllPlaylists().size)
        assertEquals("Keep", repo.getAllPlaylists()[0].name)
    }

    @Test fun removeById() = runTest {
        val id = repo.createPlaylist("X"); repo.removePlaylistById(id)
        assertTrue(repo.getAllPlaylists().isEmpty())
    }

    @Test fun rename() = runTest {
        val id = repo.createPlaylist("Old"); repo.renamePlaylist(id, "New")
        assertEquals("New", repo.getPlaylistMeta(id)?.name)
    }

    @Test fun updateCover() = runTest {
        val id = repo.createPlaylist("P"); repo.updatePlaylistCover(id, "c.jpg")
        assertEquals("c.jpg", repo.getPlaylistMeta(id)?.coverUri)
    }

    @Test fun updateDescription() = runTest {
        val id = repo.createPlaylist("P"); repo.updatePlaylistDescription(id, "desc")
        assertEquals("desc", repo.getPlaylistMeta(id)?.description)
    }

    @Test fun setPinned() = runTest {
        val id = repo.createPlaylist("P"); repo.setPlaylistPinned(id, true)
        assertTrue(repo.getPlaylistMeta(id)?.isPinned == true)
    }

    @Test fun addToPlaylist_updatesSongCount() = runTest {
        val pid = repo.createPlaylist("P")
        insertMusic(1); insertMusic(2)
        repo.addToPlaylist(pid, 1); repo.addToPlaylist(pid, 2)
        assertEquals(2, repo.getPlaylistMeta(pid)?.songCount)
    }

    @Test fun addToPlaylist_updatesTotalDuration() = runTest {
        val pid = repo.createPlaylist("P")
        insertMusic(1, duration = 60000L); insertMusic(2, duration = 120000L)
        repo.addToPlaylist(pid, 1); repo.addToPlaylist(pid, 2)
        assertEquals(180000L, repo.getPlaylistMeta(pid)?.totalDurationMs)
    }

    @Test fun removeItem_updatesCount() = runTest {
        val pid = repo.createPlaylist("P")
        insertMusic(1); insertMusic(2)
        repo.addToPlaylist(pid, 1); repo.addToPlaylist(pid, 2)
        repo.removeItemFromPlaylist(1, pid)
        assertEquals(1, repo.getPlaylistMeta(pid)?.songCount)
    }

    @Test fun getMusicInfoInPlaylist() = runTest {
        val pid = repo.createPlaylist("P")
        insertMusic(1, "First"); insertMusic(2, "Second")
        repo.addToPlaylist(pid, 1); repo.addToPlaylist(pid, 2)
        val music = repo.getMusicInfoInPlaylist(pid).first()
        assertEquals(2, music.size); assertEquals("First", music[0].music.title)
    }

    @Test fun reorderItems() = runTest {
        val pid = repo.createPlaylist("P")
        insertMusic(1); insertMusic(2); insertMusic(3)
        repo.addToPlaylist(pid, 1); repo.addToPlaylist(pid, 2); repo.addToPlaylist(pid, 3)
        repo.reorderPlaylistItems(pid, listOf(3, 1, 2))
        val music = repo.getPlaylistById(pid)
        assertEquals(3L, music[0].music.id); assertEquals(1L, music[1].music.id); assertEquals(2L, music[2].music.id)
    }

    /**
     * D3-04：重排入参不是"当前可见曲目的完整排列"时**必须拒绝，且不留部分写入**。
     *
     * 修前的行为是"没列出的接在后面 + 照样返回成功"，Agent 传个子集过来，
     * 用户看到的顺序与模型宣称的结果不一致。这条要真 Room：
     * "无部分写入"是事务语义，替身给不出这个信息（它在自己的列表上随便改）。
     */
    @Test fun reorderItems_incompleteInput_rejectedWithoutPartialWrite() = runTest {
        val pid = repo.createPlaylist("P")
        listOf(1L, 2L, 3L).forEach { insertMusic(it); repo.addToPlaylist(pid, it) }
        val before = repo.getPlaylistById(pid).map { it.music.id }
        assertEquals(listOf(1L, 2L, 3L), before)

        assertFalse(repo.reorderPlaylistItems(pid, listOf(1L, 2L)), "少一首应当被拒绝")
        assertFalse(repo.reorderPlaylistItems(pid, listOf(1L, 2L, 3L, 3L)), "重复 id 应当被拒绝")
        assertFalse(repo.reorderPlaylistItems(pid, listOf(1L, 2L, 9L)), "多一首（9 不在歌单里）应当被拒绝")
        assertFalse(repo.reorderPlaylistItems(999L, listOf(1L)), "歌单不存在应当被拒绝")

        assertEquals(before, repo.getPlaylistById(pid).map { it.music.id }, "被拒的几次不能留下半截顺序")

        assertTrue(repo.reorderPlaylistItems(pid, listOf(3L, 1L, 2L)))
        assertEquals(listOf(3L, 1L, 2L), repo.getPlaylistById(pid).map { it.music.id })
    }

    /**
     * D3-04：校验的基准是**可见**曲目，不是全部条目。
     *
     * UI 与 Agent 拿到的列表都过滤了软删的曲目；如果按全量比，
     * "歌单里有一首被隐藏的歌"会让每一次正常重排都被误拒。
     */
    @Test fun reorderItems_againstVisibleSongs_ignoresSoftDeletedOnes() = runTest {
        val pid = repo.createPlaylist("P")
        listOf(1L, 2L, 3L).forEach { insertMusic(it); repo.addToPlaylist(pid, it) }
        db.musicDao().markDeletedByIds(listOf(2L))

        assertTrue(repo.reorderPlaylistItems(pid, listOf(3L, 1L)), "软删的那首不该被算进『你没列出来』")
        assertEquals(listOf(3L, 1L), repo.getPlaylistById(pid).map { it.music.id })
        // 被软删的条目仍占着序号，且不能与新的序号撞（撞了 UNIQUE(playlistId,itemOrder) 会直接抛）
        assertEquals(3, db.playlistItemDao().getSongIdsInOrder(pid).size)
    }

    /**
     * D3-07：手动设过的封面，不能在下一次加/删/排时被首曲专辑图抹掉。
     *
     * 这条之所以以前"看起来没事"：`updatePlaylistCover` 全仓没有 UI 调用方，
     * 覆写只在没人走过的路上发生。API 齐备 + 入口缺失 + 被后台逻辑静默抹掉，
     * 三件一起放着，接上 UI 的那天就会变成"设完封面一加歌就没了"。
     */
    @Test fun customCover_survivesItemChanges() = runTest {
        val pid = repo.createPlaylist("P")
        insertMusicWithCover(1L, "cover-1")
        repo.addToPlaylist(pid, 1L)
        assertEquals("cover-1", repo.getPlaylistMeta(pid)?.coverUri, "空歌单的第一首封面应回填")

        repo.updatePlaylistCover(pid, "custom.jpg")
        insertMusicWithCover(2L, "cover-2")
        repo.addToPlaylist(pid, 2L)
        assertEquals("custom.jpg", repo.getPlaylistMeta(pid)?.coverUri, "加曲不得覆写已有封面")

        repo.reorderPlaylistItems(pid, listOf(2L, 1L))
        assertEquals("custom.jpg", repo.getPlaylistMeta(pid)?.coverUri, "重排不得覆写已有封面")

        repo.removeItemFromPlaylist(2L, pid)
        assertEquals("custom.jpg", repo.getPlaylistMeta(pid)?.coverUri, "移曲不得覆写已有封面")
    }

    @Test fun incrementPlayCount() = runTest {
        val id = repo.createPlaylist("P")
        repo.incrementPlaylistPlayCount(id); repo.incrementPlaylistPlayCount(id)
        assertEquals(2, repo.getPlaylistMeta(id)?.playCount)
    }

    @Test fun exportAndRestore_roundTrip() = runTest {
        val id = repo.createPlaylist("P")
        insertMusic(1); repo.addToPlaylist(id, 1)
        val snap = repo.exportPlaylistsSnapshot()
        assertEquals(1, snap.playlists.size); assertEquals(1, snap.playlistItems.size)
        repo.removePlaylistById(id); assertTrue(repo.getAllPlaylists().isEmpty())
        repo.restoreFromSnapshot(snap)
        assertEquals(1, repo.getAllPlaylists().size); assertEquals("P", repo.getAllPlaylists()[0].name)
    }

    /**
     * D3-02：歌单内**用户排出来的顺序**要能穿过"导出 → 删 → 恢复"活着回来。
     *
     * 修前三处都在丢它：领域 `PlaylistItem` 没有 `itemOrder` 字段（导出即丢）、
     * `getAllPlaylistItems()` 没有 `ORDER BY`（拿到的就是 SQLite 的行序）、
     * 恢复时按那个任意行序 `mapIndexed` 重编（把丢失固化成"新顺序"）。
     * 现有那条 `exportAndRestore_roundTrip` 只有 1 首歌，顺序对它没有意义 —— 测不出这个 bug。
     */
    @Test fun exportAndRestore_keepsUserOrder() = runTest {
        val id = repo.createPlaylist("Ordered")
        listOf(1L, 2L, 3L).forEach { insertMusic(it); repo.addToPlaylist(id, it) }
        repo.reorderPlaylistItems(id, listOf(3L, 1L, 2L))
        assertEquals(listOf(3L, 1L, 2L), repo.getPlaylistById(id).map { it.music.id })

        val snap = repo.exportPlaylistsSnapshot()
        // 快照里就得带着顺序，否则后面的一切都无从恢复（这一条同时钉住"领域模型承载 itemOrder"
        // 与"导出查询按 itemOrder 排"，两者缺一个这里就红）
        assertEquals(listOf(3L to 0, 1L to 1, 2L to 2), snap.playlistItems.map { it.songId to it.itemOrder })

        repo.removePlaylistById(id)
        repo.restoreFromSnapshot(snap)

        assertEquals(listOf(3L, 1L, 2L), repo.getPlaylistById(id).map { it.music.id })
    }

    /** D3-02 的降级分支：旧快照没有 `itemOrder`（全 0）时按导出顺序编号，而不是撞唯一索引。 */
    @Test fun restoreFromLegacySnapshot_withoutItemOrder() = runTest {
        insertMusic(1); insertMusic(2); insertMusic(3)
        val legacy = com.hmp.domain.backup.PlaylistsSnapshot(
            playlists = listOf(com.hmp.domain.playlist.Playlist(id = 900L, name = "Legacy")),
            playlistItems = listOf(1L, 2L, 3L).map {
                com.hmp.domain.playlist.PlaylistItem(songId = it, playlistId = 900L)
            }
        )

        repo.restoreFromSnapshot(legacy)

        assertEquals(listOf(1L, 2L, 3L), repo.getPlaylistById(900L).map { it.music.id })
    }

    @Test fun getAllPlaylistsFlow() = runTest {
        repo.createPlaylist("A"); repo.createPlaylist("B")
        val flow = repo.getAllPlaylistsFlow().first()
        assertEquals(2, flow.size)
    }

    @Test fun getPlaylistMeta_nonExisting_returnsNull() = runTest {
        assertEquals(null, repo.getPlaylistMeta(999))
    }
}
