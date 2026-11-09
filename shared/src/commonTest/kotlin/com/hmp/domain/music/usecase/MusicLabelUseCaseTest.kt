package com.hmp.domain.music.usecase

import com.hmp.domain.enum.LabelCategory
import com.hmp.domain.enum.LabelName
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.test.fakes.FakeMusicRepository
import com.hmp.test.fakes.FakePlaylistRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicLabelUseCaseTest {

    private val musicRepository = FakeMusicRepository()
    private val playlistRepository = FakePlaylistRepository()
    private val useCase = MusicLabelUseCase(musicRepository, playlistRepository)

    private fun music(id: Long) = MusicInfo(
        music = Music(id = id, title = "Song$id", artist = "A", album = "B", duration = 100, path = "/$id.mp3", albumArtUri = ""),
        extra = null,
        userInfo = null,
    )

    @Test
    fun addMusicLabels_addsGenreLabels() = runTest {
        musicRepository.addMusic(music(1))
        val labels = MusicLabels(genres = listOf("Rock", "Pop"))
        useCase.addMusicLabels(1, labels)
        val musicLabels = useCase.getMusicLabels(1)
        assertEquals(2, musicLabels.size)
        assertTrue(musicLabels.all { it?.type == LabelCategory.GENRE })
    }

    @Test
    fun addMusicLabels_addsMoodLabels() = runTest {
        musicRepository.addMusic(music(1))
        val labels = MusicLabels(moods = listOf("Happy", "Energetic"))
        useCase.addMusicLabels(1, labels)
        val musicLabels = useCase.getMusicLabels(1)
        assertEquals(2, musicLabels.size)
        assertTrue(musicLabels.all { it?.type == LabelCategory.MOOD })
    }

    @Test
    fun addMusicLabels_addsScenarioLabels() = runTest {
        musicRepository.addMusic(music(1))
        val labels = MusicLabels(scenarios = listOf("Workout"))
        useCase.addMusicLabels(1, labels)
        val musicLabels = useCase.getMusicLabels(1)
        assertEquals(1, musicLabels.size)
        assertEquals(LabelCategory.SCENARIO, musicLabels[0]?.type)
    }

    @Test
    fun addMusicLabels_addsAllCategories() = runTest {
        musicRepository.addMusic(music(1))
        val labels = MusicLabels(
            genres = listOf("Rock"),
            moods = listOf("Happy"),
            scenarios = listOf("Workout"),
            language = "Chinese",
            era = "2020s",
        )
        useCase.addMusicLabels(1, labels)
        val musicLabels = useCase.getMusicLabels(1)
        assertEquals(5, musicLabels.size)
    }

    @Test
    fun getMusicLabels_returnsEmptyForNoLabels() = runTest {
        musicRepository.addMusic(music(1))
        val labels = useCase.getMusicLabels(1)
        assertTrue(labels.isEmpty())
    }

    @Test
    fun getLabelNamesByType_returnsDistinctNames() = runTest {
        musicRepository.addMusic(music(1))
        musicRepository.addMusic(music(2))
        useCase.addMusicLabels(1, MusicLabels(genres = listOf("Rock")))
        useCase.addMusicLabels(2, MusicLabels(genres = listOf("Rock", "Pop")))
        val names = useCase.getLabelNamesByType(LabelCategory.GENRE).first()
        assertEquals(2, names.size)
    }

    @Test
    fun getMusicIdListByLabel_returnsMatchingIds() = runTest {
        musicRepository.addMusic(music(1))
        musicRepository.addMusic(music(2))
        musicRepository.addMusic(music(3))
        useCase.addMusicLabels(1, MusicLabels(genres = listOf("Rock")))
        useCase.addMusicLabels(2, MusicLabels(genres = listOf("Pop")))
        useCase.addMusicLabels(3, MusicLabels(genres = listOf("Rock")))
        val ids = useCase.getMusicIdListByLabel(LabelName.ROCK)
        assertEquals(2, ids.size)
    }
}
