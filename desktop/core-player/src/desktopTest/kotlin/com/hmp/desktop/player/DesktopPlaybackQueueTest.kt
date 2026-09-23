package com.hmp.desktop.player

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `DesktopMusicController` 的**播放队列语义**测试。
 *
 * 为什么单独成类：`DesktopMusicControllerTest` 只覆盖 `FakeAudioEngine` 自身，
 * 从未触碰 controller 的队列行为（`addToPlaylist` / `playAt` / `playWith` /
 * `clearPlaylist` / `addAllToPlaylistInOrder`）。而 UI 侧"点击播放"是否正确，
 * **完全取决于这几个方法的契约** —— 例如「点推荐列表不该清空用户已有待播队列」，
 * 其成立的前提是 `addToPlaylist` 为**尾部追加**且 `playAt` **不重建队列**。
 *
 * 这些契约此前**零测试**：一旦有人在实现里"顺手优化"，UI 层会静默退化，
 * 且编译绿、既有测试也全绿。
 *
 * 契约来源（`PlaybackController` 接口 KDoc + 实现）：
 * - `addToPlaylist`  ：追加到队列尾部（**内含整队列去重**，已存在则不重复入队）
 * - `playAt`         ：从当前下标处开始播放指定曲目（**不重建队列**）
 * - `playWith`       ：= `addToPlaylist` + 播放（**不调 clearPlaylist**）
 *
 * 技术前提：controller 的 `scope` 绑定 `Dispatchers.Main`；`runTest {}` 会自动把
 * `Dispatchers.Main` 指向一个**隔离的 TestDispatcher**，且每个用例独立调度器，
 * 天然杜绝跨用例污染。其中 `playMusic` 在 `scope.launch` 内更新 `currentIndex`，
 * 故断言前须 `advanceUntilIdle()`。
 *
 * Fake 约定（`QueueTestFakes.stubInterface`）：所有 `Flow` 默认「什么也不发射」，
 * 使 `init` 里的响应式恢复逻辑（`currentPlayListId` 收集器、`loadPlaylistFromSettings`）
 * 永不触发，不会覆写手动添加的播放列表；相关 `.first()` 抛的异常也都被 `catch` 兜住。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlaybackQueueTest {

    private fun newController(audio: FakeAudioEngine = FakeAudioEngine()): Pair<DesktopMusicController, FakeAudioEngine> {
        val settings: SettingsRepository = stubInterface()
        val playlistRepo: PlaylistRepository = stubInterface()
        // MusicRepository 由 CurrentPlaybackUseCase / PlaybackHistoryUseCase 间接持有
        val musicRepository: MusicRepository = stubInterface()

        val controller = DesktopMusicController(
            audioEngine = audio,
            currentPlaybackUseCase = CurrentPlaybackUseCase(musicRepository, playlistRepo, settings),
            playbackHistoryUseCase = PlaybackHistoryUseCase(musicRepository),
            timerUseCase = TimerUseCase(settings),
            managePlaylistUseCase = ManagePlaylistUseCase(playlistRepo, settings),
            settingsRepository = settings,
        )
        return controller to audio
    }

    @Test
    fun addToPlaylist_appendsToTail() = runTest {
        val (controller, _) = newController()
        controller.addToPlaylist(testMusicInfo(1))
        controller.addToPlaylist(testMusicInfo(2))
        controller.addToPlaylist(testMusicInfo(3))

        assertEquals(listOf(1L, 2L, 3L), controller.currentPlaylist.value.map { it.music.id })
    }

    @Test
    fun addToPlaylist_deduplicatesWholeQueue() = runTest {
        val (controller, _) = newController()
        controller.addToPlaylist(testMusicInfo(1))
        controller.addToPlaylist(testMusicInfo(2))
        controller.addToPlaylist(testMusicInfo(1)) // 重复

        assertEquals(listOf(1L, 2L), controller.currentPlaylist.value.map { it.music.id })
    }

    @Test
    fun addToPlaylist_doesNotClearExistingQueue() = runTest {
        val (controller, _) = newController()
        controller.addToPlaylist(testMusicInfo(1))
        controller.addToPlaylist(testMusicInfo(2))

        controller.addToPlaylist(testMusicInfo(3))

        // 本次 bug 的回归钉子：追加不得丢掉已有队列
        assertEquals(3, controller.currentPlaylist.value.size)
        assertTrue(controller.currentPlaylist.value.any { it.music.id == 1L })
        assertTrue(controller.currentPlaylist.value.any { it.music.id == 2L })
    }

    @Test
    fun playAt_locatesWithinExistingQueueWithoutRebuilding() = runTest {
        val (controller, _) = newController()
        controller.addToPlaylist(testMusicInfo(1))
        controller.addToPlaylist(testMusicInfo(2))
        controller.addToPlaylist(testMusicInfo(3))
        val sizeBefore = controller.currentPlaylist.value.size

        controller.playAt(testMusicInfo(2))
        advanceUntilIdle()

        // playAt 定位到既有队列中的下标，队列长度不变
        assertEquals(sizeBefore, controller.currentPlaylist.value.size)
        assertEquals(1, controller.currentIndex.value)
    }

    @Test
    fun playAt_notInQueue_isNoOp() = runTest {
        val (controller, _) = newController()
        controller.addToPlaylist(testMusicInfo(1))

        controller.playAt(testMusicInfo(99)) // 不在队列中
        advanceUntilIdle()

        // 记录当前语义：静默 no-op（不追加、不改索引）。接口 KDoc 未定义此情形。
        assertEquals(listOf(1L), controller.currentPlaylist.value.map { it.music.id })
        assertEquals(0, controller.currentIndex.value)
    }

    @Test
    fun playWith_appendsAndPlays_withoutClearingQueue() = runTest {
        val (controller, _) = newController()
        controller.addToPlaylist(testMusicInfo(1))

        controller.playWith(testMusicInfo(2))
        advanceUntilIdle()

        // playWith = addToPlaylist + 播放；队列应含旧曲 + 新曲，且索引指向新曲（证明已播放）
        assertEquals(listOf(1L, 2L), controller.currentPlaylist.value.map { it.music.id })
        assertEquals(1, controller.currentIndex.value)
    }

    @Test
    fun clearPlaylist_emptiesQueueAndResetsIndex() = runTest {
        val (controller, _) = newController()
        controller.addToPlaylist(testMusicInfo(1))
        controller.addToPlaylist(testMusicInfo(2))

        controller.clearPlaylist()
        advanceUntilIdle()

        assertTrue(controller.currentPlaylist.value.isEmpty())
        assertEquals(0, controller.currentIndex.value)
    }

    @Test
    fun addAllToPlaylistInOrder_replacesEntireQueue_contraryToItsName() = runTest {
        val (controller, _) = newController()
        controller.addToPlaylist(testMusicInfo(1))
        controller.addToPlaylist(testMusicInfo(2))

        controller.addAllToPlaylistInOrder(listOf(testMusicInfo(7), testMusicInfo(8)))
        advanceUntilIdle()

        // 记录「名不副实」的实际语义：整条替换 + 索引归零。
        // UI 侧曾误以为它是"按顺序追加"，用它做列表点播 —— 那会清掉用户队列，
        // 且使 playFrom(index) 的 index 失效（永远从 0 播）。见 RecommendListScreen 注释。
        assertEquals(listOf(7L, 8L), controller.currentPlaylist.value.map { it.music.id })
        assertEquals(0, controller.currentIndex.value)
        assertFalse(controller.currentPlaylist.value.any { it.music.id == 1L })
    }

    private fun testMusicInfo(id: Long): MusicInfo {
        val music = Music(
            id = id,
            title = "song-$id",
            artist = "artist-$id",
            album = "album-$id",
            duration = 1000L,
            path = "/path/$id",
            albumArtUri = "",
        )
        return MusicInfo(music = music, extra = null, userInfo = null)
    }
}
