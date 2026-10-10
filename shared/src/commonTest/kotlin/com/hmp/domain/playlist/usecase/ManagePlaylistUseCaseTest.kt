package com.hmp.domain.playlist.usecase

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.test.fakes.FakeMusicRepository
import com.hmp.test.fakes.FakePlaylistRepository
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagePlaylistUseCaseTest {

    private val playlistRepository = FakePlaylistRepository()
    private val musicRepository = FakeMusicRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val useCase = ManagePlaylistUseCase(playlistRepository, settingsRepository)

    private fun musicInfo(id: Long, title: String = "Song$id") = MusicInfo(
        music = Music(id = id, title = title, artist = "A", album = "B", duration = 100, path = "/$id.mp3", albumArtUri = ""),
        extra = null, userInfo = null
    )

    @Test
    fun createPlaylist_returnsId() = runTest {
        val id = useCase.createPlaylist("My Playlist")
        assertTrue(id > 0)
    }

    @Test
    fun createPlaylist_canRetrieveByName() = runTest {
        useCase.createPlaylist("Favorites")
        val all = useCase.getAllPlaylists()
        assertEquals(1, all.size)
        assertEquals("Favorites", all[0].name)
    }

    /** D3-05②：按名删除这条路整个不存在了；能做的只有"按名找到它，再按 id 走带保护的删除"。 */
    @Test
    fun findPlaylistByName_locatesTheRow_andThereIsNoByNameDelete() = runTest {
        val id = useCase.createPlaylist("Only")
        assertEquals(id, useCase.findPlaylistByName("Only")?.id)
        assertNull(useCase.findPlaylistByName("不存在"))

        useCase.removePlaylistById(id)
        assertTrue(useCase.getAllPlaylists().isEmpty())
    }

    /**
     * D3-17③：删除保护从"抛 IllegalArgumentException"改成返回 false。
     *
     * 原先那个异常会一路穿出 `viewModelScope.launch`，而全仓没有协程异常兜底 ——
     * 管理页恰好列出了系统歌单，于是"点删除心动"在 Android 上就是一次崩溃。
     */
    @Test
    fun removePlaylistById_systemPlaylist_returnsFalseAndKeepsRow() = runTest {
        val likedId = useCase.createPlaylist("红心")
        val recentId = useCase.createPlaylist("最近播放")
        val userId = useCase.createPlaylist("我的")
        settingsRepository.saveLikedPlaylistId(likedId)
        settingsRepository.saveRecentPlaylistId(recentId)

        assertTrue(useCase.isSystemPlaylist(likedId))
        assertTrue(useCase.isSystemPlaylist(recentId))
        assertFalse(useCase.isSystemPlaylist(userId))

        assertFalse(useCase.removePlaylistById(likedId), "系统歌单不可删，应当返回 false 而不是抛")
        assertNotNull(useCase.getPlaylistMeta(likedId), "被拒时一行都不该删")

        assertTrue(useCase.removePlaylistById(userId))
        assertNull(useCase.getPlaylistMeta(userId))
    }

    @Test
    fun renamePlaylist_updatesName() = runTest {
        val id = useCase.createPlaylist("Old Name")
        useCase.renamePlaylist(id, "New Name")
        val meta = useCase.getPlaylistMeta(id)
        assertEquals("New Name", meta?.name)
    }

    @Test
    fun addToPlaylist_addsMusic() = runTest {
        val playlistId = useCase.createPlaylist("Test")
        useCase.addToPlaylist(playlistId, 1L)
        val items = useCase.getPlaylistById(playlistId)
        assertEquals(1, items.size)
    }

    @Test
    fun removeItemFromPlaylist_removesMusic() = runTest {
        val playlistId = useCase.createPlaylist("Test")
        useCase.addToPlaylist(playlistId, 1L)
        useCase.addToPlaylist(playlistId, 2L)
        useCase.removeItemFromPlaylist(1L, playlistId)
        val items = useCase.getPlaylistById(playlistId)
        assertEquals(1, items.size)
        assertEquals(2L, items[0].music.id)
    }

    @Test
    fun setPlaylistPinned_updatesPinnedStatus() = runTest {
        val id = useCase.createPlaylist("Pinned")
        useCase.setPlaylistPinned(id, true)
        val meta = useCase.getPlaylistMeta(id)
        assertEquals(true, meta?.isPinned)
    }

    @Test
    fun updatePlaylistCover_updatesCover() = runTest {
        val id = useCase.createPlaylist("With Cover")
        useCase.updatePlaylistCover(id, "cover.jpg")
        val meta = useCase.getPlaylistMeta(id)
        assertEquals("cover.jpg", meta?.coverUri)
    }

    @Test
    fun updatePlaylistDescription_updatesDescription() = runTest {
        val id = useCase.createPlaylist("With Desc")
        useCase.updatePlaylistDescription(id, "My description")
        val meta = useCase.getPlaylistMeta(id)
        assertEquals("My description", meta?.description)
    }

    @Test
    fun incrementPlaylistPlayCount_increments() = runTest {
        val id = useCase.createPlaylist("Popular")
        useCase.incrementPlaylistPlayCount(id)
        useCase.incrementPlaylistPlayCount(id)
        val meta = useCase.getPlaylistMeta(id)
        assertEquals(2, meta?.playCount)
    }

    @Test
    fun setPlaylistLastPlayedAt_updatesTimestamp() = runTest {
        val id = useCase.createPlaylist("Recent")
        useCase.setPlaylistLastPlayedAt(id, 12345L)
        val meta = useCase.getPlaylistMeta(id)
        assertEquals(12345L, meta?.lastPlayedAt)
    }

    @Test
    fun getAllPlaylistsFlow_emitsUpdates() = runTest {
        val flow = useCase.getAllPlaylistsFlow()
        val initial = flow.first()
        assertTrue(initial.isEmpty())
        useCase.createPlaylist("New")
        val updated = flow.first()
        assertEquals(1, updated.size)
    }

    @Test
    fun getPlaylistMeta_nonExisting_returnsNull() = runTest {
        val meta = useCase.getPlaylistMeta(999)
        assertNull(meta)
    }
}
