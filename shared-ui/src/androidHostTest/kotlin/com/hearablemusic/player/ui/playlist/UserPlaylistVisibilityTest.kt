package com.hearablemusic.player.ui.playlist

import com.hmp.domain.playlist.Playlist
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D3-06 判据：空歌单必须留在列表里。
 *
 * 为什么测的是 [userVisiblePlaylists] 而不是 `PlaylistViewModel`：VM 的 `init` 里
 * `initializeDefaultPlaylists()` 会解析 compose 资源名，而本测试源集没接 Robolectric
 * （`Resources.getSystem` 未 mock，构造 VM 必抛），这正是 D3-06 判据预先写下的退路。
 * 三个消费点（列表页横滑区、管理页、「加入歌单」候选）都从 VM 这一个入口拿数据，
 * 所以钉住这个函数就钉住了全部三处。
 */
class UserPlaylistVisibilityTest {

    @Test
    fun emptyPlaylistStaysVisible() {
        val all = listOf(
            Playlist(id = 1L, name = "有歌的", songCount = 3),
            Playlist(id = 2L, name = "新建没选曲", songCount = 0),
        )

        val visible = userVisiblePlaylists(all)

        // 修前这里是 `filter { it.songCount > 0 }` → id=2 会被整条滤掉
        assertEquals(listOf(1L, 2L), visible.map { it.id })
    }

    @Test
    fun orderIsPassedThroughUntouched() {
        val all = listOf(
            Playlist(id = 5L, name = "B", songCount = 1),
            Playlist(id = 3L, name = "A", songCount = 0),
        )
        assertEquals(listOf(5L, 3L), userVisiblePlaylists(all).map { it.id })
    }
}
