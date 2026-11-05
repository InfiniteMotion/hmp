package com.hmp.domain.setting.usecase

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.test.fakes.FakeMusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackHistoryUseCaseTest {

    private val musicRepository = FakeMusicRepository()
    private val useCase = PlaybackHistoryUseCase(musicRepository)

    private fun musicInfo(id: Long) = MusicInfo(
        music = Music(id = id, title = "Song$id", artist = "A", album = "B", duration = 100, path = "/$id.mp3", albumArtUri = ""),
        extra = null,
        userInfo = null,
    )

    @Test
    fun insertPlayback_returnsId() = runTest {
        musicRepository.addMusic(musicInfo(1))
        val id = useCase.insertPlayback(PlaybackHistory(musicId = 1, playedAt = 1000L))
        assertTrue(id > 0)
    }

    @Test
    fun startPlaybackSession_recordsHistory() = runTest {
        musicRepository.addMusic(musicInfo(1))
        val id = useCase.startPlaybackSession(1L, "test")
        assertTrue(id > 0)
        val history = useCase.getPlaybackHistory(1L).first()
        assertEquals(1, history.size)
        assertEquals(1L, history[0].musicId)
        assertEquals("test", history[0].source)
    }

    @Test
    fun completePlaybackSession_updatesRecord() = runTest {
        musicRepository.addMusic(musicInfo(1))
        val id = useCase.startPlaybackSession(1L)
        useCase.completePlaybackSession(id, 1L, 5000L)
        val history = useCase.getPlaybackHistory(1L).first()
        assertEquals(5000L, history[0].playDuration)
        assertEquals(true, history[0].isCompleted)
    }

    @Test
    fun skipPlaybackSession_marksNotCompleted() = runTest {
        musicRepository.addMusic(musicInfo(1))
        val id = useCase.startPlaybackSession(1L)
        useCase.skipPlaybackSession(id, 1L, 2000L, isSkip = true)
        val history = useCase.getPlaybackHistory(1L).first()
        assertEquals(2000L, history[0].playDuration)
        assertEquals(false, history[0].isCompleted)
    }

    @Test
    fun getPlaybackHistory_returnsLimitedResults() = runTest {
        musicRepository.addMusic(musicInfo(1))
        repeat(10) { useCase.startPlaybackSession(1L) }
        val history = useCase.getPlaybackHistory(1L, limit = 3).first()
        assertEquals(3, history.size)
    }

    @Test
    fun recordListeningDuration_doesNotThrow() = runTest {
        useCase.recordListeningDuration(60000L)
        // No assertion needed - just verify it doesn't throw
    }
}
