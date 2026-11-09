package com.hearablemusic.player.ui.library.viewmodel

import com.hmp.domain.agent.port.LibraryMutatedNotifier
import com.hmp.domain.music.usecase.GetAllMusicUseCase
import com.hmp.domain.music.usecase.GetDeletedMusicIdsGroupedByFolderUseCase
import com.hmp.domain.music.usecase.LibraryScanUseCase
import com.hmp.domain.music.usecase.RemoveFromLibraryUseCase
import com.hmp.domain.music.usecase.RestoreToLibraryUseCase
import com.hmp.domain.setting.model.ScanDirectoryConfig
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 一-2 / D2-03 判据：改扫描目录 / 屏蔽目录**不得**触发破坏性全量重扫。
 *
 * 事故形状：用户只是想屏蔽一个铃声文件夹，旧实现却调 `fullRescan()`，
 * 把收藏、播放数、歌词、已富化文案一起清掉（旧落库是 `deleteAll()` 后重灌）。
 * 7.5 拆分后目录动作归 [LibrarySettingsViewModel]，扫描走 [LibraryScanUseCase] ——
 * 判据仍是"增量那次的调用次数为 1、全量那次的调用次数为 0"。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySettingsScanPolicyTest {

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

    private val getAllMusic = mockk<GetAllMusicUseCase>(relaxed = true)
    private val hiddenFoldersUseCase = mockk<GetDeletedMusicIdsGroupedByFolderUseCase>(relaxed = true)
    private val removeFromLibrary = mockk<RemoveFromLibraryUseCase>(relaxed = true)
    private val restoreToLibrary = mockk<RestoreToLibraryUseCase>(relaxed = true)
    private val scanUseCase = mockk<LibraryScanUseCase>(relaxed = true)
    private val settings = mockk<UserSettingsUseCase>(relaxed = true)
    private val notifier = mockk<LibraryMutatedNotifier>(relaxed = true)

    private fun newViewModel(): LibrarySettingsViewModel {
        // VM 构造期就会组装这些流，必须先桩好再装配（未被订阅，不会真的收流）
        every { settings.scanDirectoryConfig } returns configFlow
        every { scanUseCase.isScanning } returns flowOf(false)
        every { getAllMusic.getMusicCount() } returns flowOf(0)
        every { getAllMusic.getMusicWithExtraCount() } returns flowOf(0)
        every { getAllMusic.observeAllMusic(any(), any()) } returns flowOf(emptyList())
        every { hiddenFoldersUseCase.observe() } returns flowOf(emptyList())
        coEvery { scanUseCase.incremental() } returns Result.success(Unit)
        coEvery { scanUseCase.full() } returns Result.success(Unit)
        return LibrarySettingsViewModel(
            getAllMusic,
            hiddenFoldersUseCase,
            removeFromLibrary,
            restoreToLibrary,
            scanUseCase,
            settings,
            notifier,
        )
    }

    @Test
    fun addScanDirectory_syncsIncrementally_andNeverFullRescans() = runTest(dispatcher) {
        configFlow.value = ScanDirectoryConfig(scanDirectories = listOf("/music"))
        val vm = newViewModel()

        vm.addScanDirectory("/music/incoming")
        advanceUntilIdle()

        coVerify(exactly = 1) { scanUseCase.incremental() }
        coVerify(exactly = 0) { scanUseCase.full() }
        // 配置真被写成"含新目录"这一条要落在保存的那份值上：configFlow 只是读侧替身，
        // VM 写的是 use case（真机上才回流到 DataStore）
        val saved = slot<ScanDirectoryConfig>()
        coVerify(exactly = 1) { settings.saveScanDirectoryConfig(capture(saved)) }
        assertTrue("/music/incoming" in saved.captured.scanDirectories)
        assertTrue(
            "原有目录不能被覆盖掉",
            saved.captured.scanDirectories.containsAll(configFlow.value.scanDirectories - "/music/incoming"),
        )
    }

    @Test
    fun addBlockedDirectory_syncsIncrementally_andNeverFullRescans() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.addBlockedDirectory("/ringtones")
        advanceUntilIdle()

        coVerify(exactly = 1) { scanUseCase.incremental() }
        coVerify(exactly = 0) { scanUseCase.full() }
    }

    @Test
    fun removeScanDirectory_syncsIncrementally_andNeverFullRescans() = runTest(dispatcher) {
        configFlow.value = ScanDirectoryConfig(scanDirectories = listOf("/music/incoming"))
        val vm = newViewModel()

        vm.removeScanDirectory("/music/incoming")
        advanceUntilIdle()

        coVerify(exactly = 1) { scanUseCase.incremental() }
        coVerify(exactly = 0) { scanUseCase.full() }
    }

    /**
     * 「全量重建」那张 destructive 卡片仍走全量 —— 收口不能把用户显式要的那条路也堵掉。
     * 确认弹窗的可见性在 `LibrarySettingsUiState.showFullRescanConfirm`。
     */
    @Test
    fun explicitFullRescan_stillUsesFullPipeline() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.onFullRescanConfirmed()
        advanceUntilIdle()

        coVerify(exactly = 1) { scanUseCase.full() }
        coVerify(exactly = 0) { scanUseCase.incremental() }
    }
}
