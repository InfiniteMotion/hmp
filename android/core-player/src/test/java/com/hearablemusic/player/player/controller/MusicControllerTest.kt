package com.hearablemusic.player.player.controller

import android.content.Context
import com.hmp.domain.enum.PlaybackMode
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.usecase.CurrentPlaybackUseCase
import com.hmp.domain.setting.usecase.PlaybackHistoryUseCase
import com.hmp.domain.setting.usecase.TimerUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MusicControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var controller: MusicController
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()

        controller = MusicController(
            context = context,
            currentPlaybackUseCase = mockk(relaxed = true),
            playbackHistoryUseCase = mockk(relaxed = true),
            timerUseCase = mockk(relaxed = true),
            managePlaylistUseCase = mockk(relaxed = true),
            settingsRepository = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ===== Mini Player Visibility =====

    @Test
    fun miniPlayerVisible_defaultIsTrue() {
        assertTrue(controller.isMiniPlayerVisible.value)
    }

    @Test
    fun setMiniPlayerVisible_false_updatesState() {
        controller.setMiniPlayerVisible(false)
        assertFalse(controller.isMiniPlayerVisible.value)
    }

    @Test
    fun setMiniPlayerVisible_toggleBackAndForth() {
        controller.setMiniPlayerVisible(false)
        assertFalse(controller.isMiniPlayerVisible.value)
        controller.setMiniPlayerVisible(true)
        assertTrue(controller.isMiniPlayerVisible.value)
    }

    // ===== Playback Mode =====

    @Test
    fun playbackMode_defaultIsSequential() {
        assertEquals(PlaybackMode.SEQUENTIAL, controller.playbackMode.value)
    }

    @Test
    fun togglePlaybackModeByOrder_sequential_toRepeatOne() {
        controller.togglePlaybackModeByOrder()
        assertEquals(PlaybackMode.REPEAT_ONE, controller.playbackMode.value)
    }

    @Test
    fun togglePlaybackModeByOrder_repeatOne_toShuffle() {
        controller.togglePlaybackModeByOrder()
        controller.togglePlaybackModeByOrder()
        assertEquals(PlaybackMode.SHUFFLE, controller.playbackMode.value)
    }

    @Test
    fun togglePlaybackModeByOrder_fullCycle_returnsSequential() {
        controller.togglePlaybackModeByOrder()
        controller.togglePlaybackModeByOrder()
        controller.togglePlaybackModeByOrder()
        assertEquals(PlaybackMode.SEQUENTIAL, controller.playbackMode.value)
    }

    @Test
    fun togglePlaybackModeByOrder_multipleCycles() {
        repeat(6) { controller.togglePlaybackModeByOrder() }
        assertEquals(PlaybackMode.SEQUENTIAL, controller.playbackMode.value)
    }

    // ===== Is Playing =====

    @Test
    fun isPlaying_defaultIsFalse() {
        assertFalse(controller.isPlaying.value)
    }

    // ===== Current Position =====

    @Test
    fun currentPosition_defaultIsZero() {
        assertEquals(0L, controller.currentPosition.value)
    }

    // ===== Current Playlist =====

    @Test
    fun currentPlaylist_defaultIsEmpty() {
        assertTrue(controller.currentPlaylist.value.isEmpty())
    }

    // ===== Current Index =====

    @Test
    fun currentIndex_defaultIsZero() {
        assertEquals(0, controller.currentIndex.value)
    }

    // ===== Bind Play Control =====

    @Test
    fun bindPlayControl_null_doesNotCrash() {
        controller.bindPlayControl(null)
    }

    // ===== Set Target Activity Class =====

    @Test
    fun setTargetActivityClass_doesNotCrash() {
        controller.setTargetActivityClass(String::class.java)
    }

    // ===== UiEvent =====

    @Test
    fun uiEvent_showToast_dataClass() {
        val event = MusicController.UiEvent.ShowToast("test message")
        assertEquals("test message", event.message)
    }

    @Test
    fun uiEvent_showToast_equality() {
        val event1 = MusicController.UiEvent.ShowToast("msg")
        val event2 = MusicController.UiEvent.ShowToast("msg")
        assertEquals(event1, event2)
    }

    // ===== Audio Effect Defaults (no PlayControl bound) =====

    @Test
    fun getCurrentEqualizerPreset_noPlayControl_returnsZero() {
        assertEquals(0, controller.getCurrentEqualizerPreset())
    }

    @Test
    fun getBassBoostLevel_noPlayControl_returnsZero() {
        assertEquals(0, controller.getBassBoostLevel())
    }

    @Test
    fun isSurroundSoundEnabled_noPlayControl_returnsFalse() {
        assertFalse(controller.isSurroundSoundEnabled())
    }

    @Test
    fun getReverbPreset_noPlayControl_returnsZero() {
        assertEquals(0, controller.getReverbPreset())
    }

    @Test
    fun getCurrentEqualizerBandLevels_noPlayControl_returnsEmpty() {
        val levels = controller.getCurrentEqualizerBandLevels()
        assertTrue(levels.isEmpty())
    }

    // ===== Audio Effect setters (no PlayControl) =====

    @Test
    fun setEqualizerPreset_noPlayControl_doesNotCrash() {
        controller.setEqualizerPreset(2)
    }

    @Test
    fun setBassBoost_noPlayControl_doesNotCrash() {
        controller.setBassBoost(5)
    }

    @Test
    fun setSurroundSound_noPlayControl_doesNotCrash() {
        controller.setSurroundSound(true)
    }

    @Test
    fun setReverb_noPlayControl_doesNotCrash() {
        controller.setReverb(1)
    }

    @Test
    fun setCustomEqualizer_noPlayControl_doesNotCrash() {
        controller.setCustomEqualizer(floatArrayOf(1f, 2f, 3f))
    }

    // ===== isMusicLoaded =====

    @Test
    fun isMusicLoaded_noPlayControl_returnsNull() {
        assertNull(controller.isMusicLoaded("/test.mp3"))
    }

    // ===== 启动恢复失败计数（D1-08）=====
    //
    // init 块里 loadPlaylistFromSettings() / restoreLastPosition() 会在构造时立即跑完，
    // 所以计数断言必须用「构造前就 stub 好」的实例，不能复用 setup 里那个。

    private fun buildController(
        playlistIdFlow: Flow<Long?> = flowOf(null),
        positionFlow: Flow<Long> = flowOf(0L),
        managePlaylist: ManagePlaylistUseCase = mockk(relaxed = true),
    ): MusicController {
        val settings = mockk<SettingsRepository>(relaxed = true)
        every { settings.currentPlaylistId } returns playlistIdFlow
        every { settings.likedPlaylistId } returns flowOf(null)
        every { settings.recentPlaylistId } returns flowOf(null)
        every { settings.currentPosition } returns positionFlow

        val playback = mockk<CurrentPlaybackUseCase>(relaxed = true)
        every { playback.getCurrentMusicId() } returns flowOf(null)

        return MusicController(
            context = context,
            currentPlaybackUseCase = playback,
            playbackHistoryUseCase = mockk(relaxed = true),
            timerUseCase = mockk(relaxed = true),
            managePlaylistUseCase = managePlaylist,
            settingsRepository = settings,
        )
    }

    @Test
    fun restoreFailures_noCurrentPlaylist_isNotCounted() {
        // 首次启动没有当前歌单是正常态，不该被 filterNotNull().first() 的
        // NoSuchElementException 计成一次恢复失败。
        val fresh = buildController(playlistIdFlow = flowOf(null))
        assertEquals(0, fresh.restoreFailures.value)
    }

    @Test
    fun restoreFailures_positionThrows_isCounted() {
        val fresh = buildController(positionFlow = flow { error("boom: currentPosition") })
        assertEquals(1, fresh.restoreFailures.value)
    }

    @Test
    fun restoreFailures_playlistThrows_isCounted() {
        val managePlaylist = mockk<ManagePlaylistUseCase>(relaxed = true)
        every { managePlaylist.getMusicInfoInPlaylist(any()) } returns flow { error("boom: playlist") }

        val fresh = buildController(playlistIdFlow = flowOf(1L), managePlaylist = managePlaylist)
        assertEquals(1, fresh.restoreFailures.value)
    }
}
