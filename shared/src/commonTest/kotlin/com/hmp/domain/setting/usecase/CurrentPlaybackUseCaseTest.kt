package com.hmp.domain.setting.usecase

import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicLabel
import com.hmp.test.fakes.FakeMusicRepository
import com.hmp.test.fakes.FakePlaylistRepository
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrentPlaybackUseCaseTest {

    private val musicRepository = FakeMusicRepository()
    private val playlistRepository = FakePlaylistRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val useCase = CurrentPlaybackUseCase(musicRepository, playlistRepository, settingsRepository)

    private fun musicInfo(id: Long) = MusicInfo(
        music = Music(id = id, title = "Song$id", artist = "A", album = "B", duration = 100, path = "/$id.mp3", albumArtUri = ""),
        extra = null,
        userInfo = null,
    )

    @Test
    fun getCurrentMusicId_initiallyNull() = runTest {
        val id = useCase.getCurrentMusicId().first()
        assertNull(id)
    }

    @Test
    fun saveAndGetCurrentMusicId() = runTest {
        useCase.saveCurrentMusicId(42L)
        val id = useCase.getCurrentMusicId().first()
        assertEquals(42L, id)
    }

    @Test
    fun updateAndGetLikedStatus() = runTest {
        musicRepository.addMusic(musicInfo(1L))
        assertFalse(useCase.getLikedStatus(1L))
        useCase.updateLikedStatus(1L, true)
        assertTrue(useCase.getLikedStatus(1L))
    }

    @Test
    fun getMusicLyrics_returnsNull_whenNotSet() = runTest {
        musicRepository.addMusic(musicInfo(1L))
        val lyrics = useCase.getMusicLyrics(1L)
        assertNull(lyrics)
    }

    @Test
    fun getMusicLabels_returnsLabels() = runTest {
        musicRepository.addMusic(musicInfo(1L))
        musicRepository.addMusicLabel(MusicLabel(1L, LabelCategory.GENRE, LabelName.ROCK))
        val labels = useCase.getMusicLabels(1L)
        assertEquals(1, labels.size)
    }

    @Test
    fun getSimilarSongs_returnsResults() = runTest {
        for (i in 1L..5L) musicRepository.addMusic(musicInfo(i))
        val similar = useCase.getSimilarSongsByWeightedLabels(1L, 3)
        assertEquals(3, similar.size)
    }
}
