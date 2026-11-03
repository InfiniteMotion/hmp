package com.hearablemusic.player.ui.settings.viewmodel

import com.hmp.domain.lyrics.LyricsComponentConfig
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.LyricsSettingsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * D4-01 的 UI 半边在 VM 层的判据（`shared-ui` 没有 Compose 渲染测试基建，
 * 所以"打开 LyricsScreen 后字号跟着变"退到 VM 状态：渲染那半仍靠人看）。
 *
 * 钉住两件事：
 * 1. 播放页读的是 PLAYER 组件配置（不是 legacy 扁平键）；
 * 2. 页内面板只改 FULLSCREEN 的一个字段，同组件其它字段（含 linkedTo）不被覆盖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LyricsSettingsViewModelTest {

    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val playerFlow = MutableStateFlow("")
    private val fullscreenFlow = MutableStateFlow("")
    private val floatingFlow = MutableStateFlow("")
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        every { repository.lyricsPlayerConfig } returns playerFlow
        every { repository.lyricsFullscreenConfig } returns fullscreenFlow
        every { repository.lyricsFloatingConfig } returns floatingFlow
        every { repository.floatingLyricsEnabled } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun encode(config: LyricsComponentConfig) =
        json.encodeToString(LyricsComponentConfig.serializer(), config)

    @Test
    fun playerConfig_reflectsComponentConfig() = runTest(dispatcher) {
        playerFlow.value = encode(LyricsComponentConfig(originalTextSize = 20, lineSpacing = 11))

        val vm = LyricsSettingsViewModel(LyricsSettingsUseCase(repository))
        val job = launch { vm.playerLyricsConfig.collect { } }
        advanceUntilIdle()

        assertEquals(20, vm.playerLyricsConfig.value.originalTextSize)
        assertEquals(11, vm.playerLyricsConfig.value.lineSpacing)
        job.cancel()
    }

    @Test
    fun playerConfig_followsLinkedTo() = runTest(dispatcher) {
        playerFlow.value = encode(LyricsComponentConfig(linkedTo = "fullscreen"))
        fullscreenFlow.value = encode(LyricsComponentConfig(originalTextSize = 26))

        val vm = LyricsSettingsViewModel(LyricsSettingsUseCase(repository))
        val job = launch { vm.playerLyricsConfig.collect { } }
        advanceUntilIdle()

        assertEquals(26, vm.playerLyricsConfig.value.originalTextSize)
        job.cancel()
    }

    @Test
    fun updateFullscreenConfig_isReadModifyWrite() = runTest(dispatcher) {
        fullscreenFlow.value = encode(
            LyricsComponentConfig(lineSpacing = 9, karaokeEnabled = false, linkedTo = "floating")
        )
        coEvery { repository.getLyricsFullscreenConfig() } answers { fullscreenFlow.value }
        val saved = slot<String>()
        coEvery { repository.saveLyricsFullscreenConfig(capture(saved)) } answers {
            fullscreenFlow.value = saved.captured
        }

        val vm = LyricsSettingsViewModel(LyricsSettingsUseCase(repository))
        vm.updateFullscreenConfig { it.copy(originalTextSize = 30) }
        advanceUntilIdle()

        val persisted = json.decodeFromString(LyricsComponentConfig.serializer(), fullscreenFlow.value)
        assertEquals(30, persisted.originalTextSize)
        assertEquals(9, persisted.lineSpacing)
        assertEquals(false, persisted.karaokeEnabled)
        assertEquals("floating", persisted.linkedTo)
        coVerify(exactly = 1) { repository.saveLyricsFullscreenConfig(any()) }
    }
}
