package com.hmp.desktop.player

import com.hmp.domain.backup.ListeningStatsSnapshot
import com.hmp.domain.backup.MusicUserStateSnapshot
import com.hmp.domain.backup.PlaylistsSnapshot
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.domain.setting.model.ScanDirectoryConfig
import com.hmp.domain.setting.model.UserUsageAnalytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for FakeAudioEngine and DesktopMusicController dependencies.
 * Note: Full DesktopMusicController integration tests require Dispatchers.Main
 * which needs platform-specific test support (Swing EDT). These tests verify
 * the building blocks used by the controller.
 */
class DesktopMusicControllerTest {

    // ===== FakeAudioEngine tests =====

    @Test
    fun fakeAudioEngine_initialState() {
        val engine = FakeAudioEngine()
        assertFalse(engine.isPlaying())
        assertFalse(engine.isPaused())
        assertFalse(engine.isLoaded())
        assertEquals(0L, engine.getCurrentPosition())
        assertEquals(180000L, engine.getDuration())
    }

    @Test
    fun fakeAudioEngine_play_setsPlaying() {
        val engine = FakeAudioEngine()
        engine.play("/test.mp3")
        assertTrue(engine.isPlaying())
        assertTrue(engine.isLoaded())
        assertFalse(engine.isPaused())
    }

    @Test
    fun fakeAudioEngine_pause_setsPaused() {
        val engine = FakeAudioEngine()
        engine.play("/test.mp3")
        engine.pause()
        assertFalse(engine.isPlaying())
        assertTrue(engine.isPaused())
    }

    @Test
    fun fakeAudioEngine_resume_afterPause() {
        val engine = FakeAudioEngine()
        engine.play("/test.mp3")
        engine.pause()
        engine.resume()
        assertTrue(engine.isPlaying())
        assertFalse(engine.isPaused())
    }

    @Test
    fun fakeAudioEngine_stop_resetsState() {
        val engine = FakeAudioEngine()
        engine.play("/test.mp3")
        engine.stop()
        assertFalse(engine.isPlaying())
        assertFalse(engine.isPaused())
    }

    @Test
    fun fakeAudioEngine_seekTo_updatesPosition() {
        val engine = FakeAudioEngine()
        engine.seekTo(5000L)
        assertEquals(5000L, engine.getCurrentPosition())
        assertEquals(5000L, engine.seekToPosition)
    }

    @Test
    fun fakeAudioEngine_setVolume() {
        val engine = FakeAudioEngine()
        engine.setVolume(0.5f)
        assertEquals(0.5f, engine.lastVolume)
    }

    @Test
    fun fakeAudioEngine_release() {
        val engine = FakeAudioEngine()
        engine.play("/test.mp3")
        engine.release()
        assertTrue(engine.releaseCalled)
        assertFalse(engine.isPlaying())
        assertFalse(engine.isLoaded())
    }

    @Test
    fun fakeAudioEngine_simulatePlaybackComplete() {
        var completed = false
        val engine = FakeAudioEngine()
        engine.onPlaybackComplete = { completed = true }
        engine.simulatePlaybackComplete()
        assertTrue(completed)
    }

    @Test
    fun fakeAudioEngine_simulateError() {
        var receivedError: Exception? = null
        val engine = FakeAudioEngine()
        engine.onError = { receivedError = it }
        engine.simulateError(RuntimeException("test error"))
        assertEquals("test error", receivedError?.message)
    }

    @Test
    fun fakeAudioEngine_setDuration() {
        val engine = FakeAudioEngine()
        engine.setDuration(300000L)
        assertEquals(300000L, engine.getDuration())
    }

    @Test
    fun fakeAudioEngine_setCurrentPosition() {
        val engine = FakeAudioEngine()
        engine.setCurrentPosition(10000L)
        assertEquals(10000L, engine.getCurrentPosition())
    }

    @Test
    fun fakeAudioEngine_setLoaded() {
        val engine = FakeAudioEngine()
        assertFalse(engine.isLoaded())
        engine.setLoaded(true)
        assertTrue(engine.isLoaded())
    }
}
