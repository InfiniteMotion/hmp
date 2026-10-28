package com.hearablemusic.player.ui.library.viewmodel

import com.hmp.domain.agent.port.LibraryMutatedNotifier
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.GetDeletedMusicIdsGroupedByFolderUseCase
import com.hmp.domain.music.usecase.LoadMusicFromDeviceUseCase
import com.hmp.domain.music.usecase.RemoveFromLibraryUseCase
import com.hmp.domain.music.usecase.RestoreToLibraryUseCase
import com.hmp.domain.music.usecase.SyncMusicFromDeviceIncrementalUseCase
import com.hmp.domain.setting.model.ScanDirectoryConfig
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.slot
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 一-2 / D2-03 判据：改扫描目录 / 屏蔽目录**不得**触发破坏性全量重扫。
 *
 * 事故形状：用户只是想屏蔽一个铃声文件夹，`updateScanDirectoryConfig` 却调 `fullRescan()`，
 * 把收藏、播放数、歌词、已富化文案一起清掉（旧落库是 `deleteAll()` 后重灌）。
 * 现在目录变更走增量那条，判据就是"全量那次的调用次数为 0"。
 *
 * 用 mockk 打桩 use case：`LibraryViewModel` 的依赖全是 final 类，而 D2-03 的前置动作已经把
 * 唯一挡路的具体类依赖（`MasterAgent`）换成 [LibraryMutatedNotifier]，剩下的都能按类型打桩。
 * 触发链是 `viewModelScope.launch`（Main = 测试调度器）里读设置，再 `launch(Dispatchers.Default)` 跑扫描：
 * 所以每次都先 `advanceUntilIdle()` 把 Main 那一层推进，落库的断言再用带超时的 `coVerify` 等 Default。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryScanPolicyTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val configFlow = MutableStateFlow(ScanDirectoryConfig())

    private val loadFull = mockk<LoadMusicFromDeviceUseCase>(relaxed = true)
    private val loadIncremental = mockk<SyncMusicFromDeviceIncrementalUseCase>(relaxed = true)
    private val getAllMusic = mockk<GetAllMusicUseCase>(relaxed = true)
    private val removeFromLibrary = mockk<RemoveFromLibraryUseCase>(relaxed = true)
    private val restoreToLibrary = mockk<RestoreToLibraryUseCase>(relaxed = true)
    private val hiddenFoldersUseCase = mockk<GetDeletedMusicIdsGroupedByFolderUseCase>(relaxed = true)
    private val settings = mockk<UserSettingsUseCase>(relaxed = true)
    private val notifier = mockk<LibraryMutatedNotifier>(relaxed = true)

    private fun newViewModel(): LibraryViewModel {
        // VM 在构造期就订阅这些流，必须先桩好再装配
        every { settings.scanDirectoryConfig } returns configFlow
        every { getAllMusic.getMusicCount() } returns flowOf(0)
        every { getAllMusic.getMusicWithExtraCount() } returns flowOf(0)
        every { loadFull.isScanning() } returns flowOf(false)
        coEvery { getAllMusic(any(), any()) } returns emptyList()
        coEvery { hiddenFoldersUseCase() } returns emptyList()
        coEvery { loadIncremental() } returns Result.success(Unit)
        coEvery { loadFull() } returns Result.success(Unit)
        return LibraryViewModel(
            getAllMusic, loadFull, loadIncremental,
            removeFromLibrary, restoreToLibrary, hiddenFoldersUseCase,
            settings, notifier
        )
    }

    @Test
    fun addScanDirectory_syncsIncrementally_andNeverFullRescans() = runTest(dispatcher) {
        configFlow.value = ScanDirectoryConfig(scanDirectories = listOf("/music"))
        val vm = newViewModel()

        vm.addScanDirectory("/music/incoming")
        advanceUntilIdle()

        coVerify(timeout = 5000, exactly = 1) { loadIncremental() }
        coVerify(exactly = 0) { loadFull() }
        // 配置真被写成"含新目录"这一条要落在保存的那份值上：configFlow 只是读侧替身，
        // VM 写的是 use case（真机上才回流到 DataStore）
        val saved = slot<ScanDirectoryConfig>()
        coVerify(exactly = 1) { settings.saveScanDirectoryConfig(capture(saved)) }
        assertTrue("/music/incoming" in saved.captured.scanDirectories)
        assertTrue(
            "原有目录不能被覆盖掉",
            saved.captured.scanDirectories.containsAll(configFlow.value.scanDirectories - "/music/incoming")
        )
    }

    @Test
    fun addBlockedDirectory_syncsIncrementally_andNeverFullRescans() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.addBlockedDirectory("/ringtones")
        advanceUntilIdle()

        coVerify(timeout = 5000, exactly = 1) { loadIncremental() }
        coVerify(exactly = 0) { loadFull() }
    }

    @Test
    fun removeScanDirectory_syncsIncrementally_andNeverFullRescans() = runTest(dispatcher) {
        configFlow.value = ScanDirectoryConfig(scanDirectories = listOf("/music/incoming"))
        val vm = newViewModel()

        vm.removeScanDirectory("/music/incoming")
        advanceUntilIdle()

        coVerify(timeout = 5000, exactly = 1) { loadIncremental() }
        coVerify(exactly = 0) { loadFull() }
    }

    /**
     * 「全量重建」那张 destructive 卡片仍走全量 —— 收口不能把用户显式要的那条路也堵掉。
     * 确认弹窗在 `LibrarySettingsScreen` 侧，本条只钉住"显式调用 = 全量"这个映射。
     */
    @Test
    fun explicitFullRescan_stillUsesFullPipeline() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.fullRescan()
        advanceUntilIdle()

        coVerify(timeout = 5000, exactly = 1) { loadFull() }
        coVerify(exactly = 0) { loadIncremental() }
    }
}
