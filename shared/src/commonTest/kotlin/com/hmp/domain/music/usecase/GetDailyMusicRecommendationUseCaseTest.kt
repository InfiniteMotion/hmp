package com.hmp.domain.music.usecase

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicExtra
import com.hmp.domain.music.MusicExtraTexts
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.UserInfo
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

    /** Enrich 管道落库的 6 列文本（写端形状）。 */
    private fun extraTexts(id: Long) = MusicExtraTexts(
        rewards = "格莱美$id", popLyric = "歌词$id", singerIntroduce = "歌手介绍$id",
        backgroundIntroduce = "创作背景$id", description = "歌曲简介$id",
        relevantMusic = "相似歌曲$id",
    )

    private suspend fun addMusicWithExtra(id: Long) {
        musicRepository.addMusic(musicInfo(id))
        musicRepository.updateMusicExtraTexts(id, extraTexts(id))
    }

    // ===== getMusicWithExtraById =====

    @Test
    fun getMusicWithExtraById_existing_returnsRecommendation() = runTest {
        addMusicWithExtra(42)

        val result = useCase.getMusicWithExtraById(42)
        assertNotNull(result)
        assertEquals(42L, result.musicInfo!!.music.id)
        assertEquals("创作背景42", result.musicInfo!!.extra?.backgroundIntroduce)
        assertEquals("歌手介绍42", result.musicInfo!!.extra?.singerIntroduce)
        assertEquals("格莱美42", result.musicInfo!!.extra?.rewards)
        assertEquals("相似歌曲42", result.musicInfo!!.extra?.relevantMusic)
    }

    @Test
    fun getMusicWithExtraById_nonExisting_returnsNull() = runTest {
        val result = useCase.getMusicWithExtraById(999)
        assertNull(result)
    }

    /**
     * 「曲目存在但未富化」：仍返回该曲目，富化文案为 null（由 UI 侧判空决定是否渲染）。
     *
     * 旧版此用例断言 null，依据是 `FakeMusicRepository.getMusicExtraById` 会抛异常；
     * 而生产 `MusicRepositoryBase.getMusicExtraById` **从不抛异常**（缺行时返回空壳）。
     * 把断言建立在 Fake 特有的行为上，是这个用例长期"绿得没道理"的原因，已改为断言真实行为。
     */
    @Test
    fun getMusicWithExtraById_musicExistsButNoExtra_stillReturnsMusic() = runTest {
        musicRepository.addMusic(musicInfo(10, hasExtra = false))

        val result = useCase.getMusicWithExtraById(10)
        assertNotNull(result)
        assertEquals(10L, result.musicInfo!!.music.id)
        assertNull(result.musicInfo!!.extra?.description)
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
