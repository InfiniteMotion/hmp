package com.hmp.desktop.player

import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `DesktopMusicController` 的**队列 / DB 同步契约**回归测试。
 *
 * 契约（「胶囊闪一下消失」事故后的重建）：内存队列是播放的唯一事实源，
 * DB 只是持久化副本——
 * 1. 启动时从 DB 恢复一次（`loadPlaylistFromSettings`）；
 * 2. 运行中**任何** DB 侧变化都不得覆写内存队列（结构断言：没有常驻回流收集器）；
 * 3. 每次队列变更写回 DB 的必须是**变更那一刻的快照**。
 *
 * 历史根因（供考古）：常驻回流收集器用 DB 快照覆写内存队列。写库正常时表现为
 * 偶发竞态（回流早于写入落地）；系统歌单行被 destructive migration 清掉、
 * DataStore 残留旧 id 时写库 FK 必败且被静默吞掉，回流永远带空快照——每次回流
 * 都清空队列。配套修复：系统歌单启动自愈见 `DefaultPlaylistGuard`（shared-ui）。
 *
 * 与 `DesktopPlaybackQueueTest` 的分工：后者测**队列操作语义**（追加/去重/替换），
 * 本类测**同步契约**，两者的 fake 约定不同，故分文件。
 *
 * 本类走 [runTestWithMain] 入口 —— controller 的 `scope` 绑定 `Dispatchers.Main`，
 * 而它在测试里是真实的 Swing EDT（见该函数 KDoc），不接管就测不到 `scope.launch`
 * 里的效果。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopQueueDbReflowTest {

    private fun newController(
        playlistRepository: FakePlaylistRepository,
        settings: SettingsRepository,
    ): DesktopMusicController = DesktopMusicController(
        audioEngine = FakeAudioEngine(),
        currentPlaybackUseCase = CurrentPlaybackUseCase(
            stubInterface<MusicRepository>(),
            playlistRepository,
            settings,
        ),
        playbackHistoryUseCase = PlaybackHistoryUseCase(stubInterface<MusicRepository>()),
        timerUseCase = TimerUseCase(settings),
        managePlaylistUseCase = ManagePlaylistUseCase(playlistRepository, settings),
        settingsRepository = settings,
    )

    @Test
    fun runtimeDbChanges_neverOverwriteInMemoryQueue() = runTestWithMain {
        val repo = FakePlaylistRepository(initialDbPlaylist = listOf(testMusicInfo(9)))
        val controller = newController(repo, settingsRepositoryWithPlaylistId(MutableStateFlow(1L)))
        advanceUntilIdle()

        // 启动恢复：内存队列 = DB 快照（DB 作为副本唯一被采纳的时刻）
        assertEquals(listOf(9L), controller.currentPlaylist.value.map { it.music.id })

        // 入队，并卡住写盘：制造「DB 侧仍是旧快照」的时序
        val gate = CompletableDeferred<Unit>()
        repo.writeGate = gate
        controller.addToPlaylist(testMusicInfo(1))
        runCurrent() // 写入协程跑到挂起点
        assertEquals(listOf(9L, 1L), controller.currentPlaylist.value.map { it.music.id })

        // 写入仍未落地，DB 快照流变出一个不含新曲目的旧列表
        repo.dbPlaylist.value = listOf(testMusicInfo(9), testMusicInfo(8))
        advanceUntilIdle()

        // 钉子①：运行中 DB 变化不得覆写内存队列（若有回流收集器这里必挂）
        assertEquals(listOf(9L, 1L), controller.currentPlaylist.value.map { it.music.id })

        gate.complete(Unit)
        advanceUntilIdle()

        // 钉子②：写回 DB 的必须是入队那一刻的快照
        assertEquals(listOf(listOf(9L, 1L)), repo.writes.map { write -> write.map { it.music.id } })
    }

    @Test
    fun playlistIdReplayOrChange_doesNotTouchRunningQueue() = runTestWithMain {
        val repo = FakePlaylistRepository(initialDbPlaylist = listOf(testMusicInfo(9)))
        // SharedFlow 可重放同一个值，模拟 DataStore 因其它设置项写入而重发 playlistId
        val idFlow = MutableSharedFlow<Long?>(replay = 1, extraBufferCapacity = 1)
        val controller = newController(repo, settingsRepositoryWithPlaylistId(idFlow))

        idFlow.emit(1L)
        advanceUntilIdle()
        assertEquals(listOf(9L), controller.currentPlaylist.value.map { it.music.id })

        controller.addToPlaylist(testMusicInfo(1))
        advanceUntilIdle()
        assertEquals(listOf(9L, 1L), controller.currentPlaylist.value.map { it.music.id })

        // 同一 id 重放（保存进度等设置项写入），甚至 id 变化（Guard 自愈写入新歌单 id）
        idFlow.emit(1L)
        idFlow.emit(7L)
        advanceUntilIdle()

        // 钉子：队列纹丝不动 —— 运行中不存在会把队列回滚到 DB 快照的收集器
        assertEquals(listOf(9L, 1L), controller.currentPlaylist.value.map { it.music.id })
    }
}
