package com.hmp.domain.music.usecase

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.test.fakes.FakeMusicRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetDeletedMusicIdsGroupedByFolderUseCaseTest {

    private val repository = FakeMusicRepository()
    private val removeUseCase = RemoveFromLibraryUseCase(repository)
    private val useCase = GetDeletedMusicIdsGroupedByFolderUseCase(repository)

    private fun music(id: Long, path: String) = MusicInfo(
        music = Music(id = id, title = "Song$id", artist = "A", album = "B", duration = 100, path = path, albumArtUri = ""),
        extra = null,
        userInfo = null,
    )

    @Test
    fun invoke_noDeletedMusic_returnsEmpty() = runTest {
        repository.addMusic(music(1, "/music/song1.mp3"))
        val result = useCase()
        assertTrue(result.isEmpty())
    }

    @Test
    fun invoke_groupsByFolder() = runTest {
        repository.addMusic(music(1, "/rock/song1.mp3"))
        repository.addMusic(music(2, "/rock/song2.mp3"))
        repository.addMusic(music(3, "/pop/song3.mp3"))
        removeUseCase(listOf(1, 2, 3))
        val result = useCase()
        assertEquals(2, result.size)
        val rockGroup = result.find { it.first == "/rock" }
        val popGroup = result.find { it.first == "/pop" }
        assertEquals(2, rockGroup?.second?.size)
        assertEquals(1, popGroup?.second?.size)
    }
}
