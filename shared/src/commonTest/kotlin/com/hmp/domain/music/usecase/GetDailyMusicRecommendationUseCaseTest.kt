package com.hmp.domain.music.usecase

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicExtra
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.UserInfo
import com.hmp.domain.setting.model.DailyMusicInfo
import com.hmp.test.fakes.FakeMusicRepository
import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetDailyMusicRecommendationUseCaseTest {

    private val musicRepository = FakeMusicRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val useCase = GetDailyMusicRecommendationUseCase(musicRepository, settingsRepository)

    private fun musicInfo(id: Long, hasExtra: Boolean = true) = MusicInfo(
        music = Music(id = id, title = "Song$id", artist = "Artist${id % 5}", album = "Album${id % 3}", duration = 100, path = "/$id.mp3", albumArtUri = ""),
        extra = MusicExtra(id = id, isGetExtraInfo = hasExtra),
        userInfo = UserInfo(id = id)
    )

    private fun dailyMusicInfo() = DailyMusicInfo(
        genre = listOf("Pop"), mood = listOf("Happy"), scenario = listOf("Workout"),
        language = "English", era = "2020s", rewards = "", lyric = "",
        singerIntroduce = "", backgroundIntroduce = "", description = "",
        relevantMusic = "", errorInfo = ""
    )

    private suspend fun addMusicWithExtra(id: Long) {
        musicRepository.addMusic(musicInfo(id))
        musicRepository.insertMusicExtra(id, dailyMusicInfo())
    }

    // ===== getRandomMusicWithExtra =====

    @Test
    fun getRandomMusicWithExtra_emptyRepository_returnsNullRecommendation() = runTest {
        val result = useCase.getRandomMusicWithExtra()
        assertNull(result.musicInfo)
        assertNull(result.dailyMusicInfo)
        assertTrue(result.labels.isEmpty())
    }

    @Test
    fun getRandomMusicWithExtra_withMusicAndExtra_returnsRecommendation() = runTest {
        addMusicWithExtra(1)

        val result = useCase.getRandomMusicWithExtra()
        assertNotNull(result.musicInfo)
        assertEquals(1L, result.musicInfo!!.music.id)
        assertNotNull(result.dailyMusicInfo)
    }

    @Test
    fun getRandomMusicWithExtra_onlyNoExtraMusic_returnsNull() = runTest {
        musicRepository.addMusic(musicInfo(1, hasExtra = false))
        musicRepository.addMusic(musicInfo(2, hasExtra = false))

        val result = useCase.getRandomMusicWithExtra()
        assertNull(result.musicInfo)
    }

    // ===== getMusicWithExtraById =====

    @Test
    fun getMusicWithExtraById_existing_returnsRecommendation() = runTest {
        addMusicWithExtra(42)

        val result = useCase.getMusicWithExtraById(42)
        assertNotNull(result)
        assertEquals(42L, result.musicInfo!!.music.id)
        assertNotNull(result.dailyMusicInfo)
    }

    @Test
    fun getMusicWithExtraById_nonExisting_returnsNull() = runTest {
        val result = useCase.getMusicWithExtraById(999)
        assertNull(result)
    }

    @Test
    fun getMusicWithExtraById_musicExistsButNoExtra_returnsNull() = runTest {
        musicRepository.addMusic(musicInfo(10, hasExtra = true))
        // No insertMusicExtra for id=10 -> fake throws -> caught -> returns null
        val result = useCase.getMusicWithExtraById(10)
        assertNull(result)
    }

    // ===== MusicRecommendation data class =====

    @Test
    fun musicRecommendation_labels_defaultEmpty() = runTest {
        val result = useCase.getRandomMusicWithExtra()
        assertTrue(result.labels.isEmpty())
    }

    // ===== validateProviderApiKey =====

    @Test
    fun validateProviderApiKey_fakeReturnsTrue() = runTest {
        val result = useCase.validateProviderApiKey()
        assertTrue(result)
    }

    // ===== getRecentListeningDurations =====

    @Test
    fun getRecentListeningDurations_empty_returnsEmpty() = runTest {
        val durations = useCase.getRecentListeningDurations().first()
        assertTrue(durations.isEmpty())
    }
}
