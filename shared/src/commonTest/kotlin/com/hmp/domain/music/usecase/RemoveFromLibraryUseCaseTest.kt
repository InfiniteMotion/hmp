package com.hmp.domain.music.usecase

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import com.hmp.domain.music.MusicInfo
import com.hmp.test.fakes.FakeMusicRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoveFromLibraryUseCaseTest {

    private val repository = FakeMusicRepository()
    private val useCase = RemoveFromLibraryUseCase(repository)

    private fun music(id: Long) = MusicInfo(
        music = Music(id = id, title = "Song$id", artist = "A", album = "B", duration = 100, path = "/$id.mp3", albumArtUri = ""),
        extra = null, userInfo = null
    )

    @Test
    fun invoke_emptyList_doesNothing() = runTest {
        repository.addMusic(music(1))
        useCase(emptyList())
        assertEquals(1, repository.searchMusic("").size)
    }

    @Test
    fun invoke_removesSpecifiedIds() = runTest {
        repository.addMusic(music(1))
        repository.addMusic(music(2))
        repository.addMusic(music(3))
        useCase(listOf(1, 3))
        val remaining = repository.getAllMusicInfoAsList(MusicSortKey.TITLE, SortDirection.ASC)
        assertEquals(1, remaining.size)
        assertEquals(2, remaining[0].music.id)
    }

    @Test
    fun invoke_removesAll_clearsRepository() = runTest {
        repository.addMusic(music(1))
        repository.addMusic(music(2))
        useCase(listOf(1, 2))
        assertTrue(repository.getAllMusicInfoAsList(MusicSortKey.TITLE, SortDirection.ASC).isEmpty())
    }
}
