package com.hmp.domain.music.usecase

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import com.hmp.test.fakes.FakeMusicRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestoreToLibraryUseCaseTest {

    private val repository = FakeMusicRepository()
    private val removeUseCase = RemoveFromLibraryUseCase(repository)
    private val restoreUseCase = RestoreToLibraryUseCase(repository)

    private fun music(id: Long) = MusicInfo(
        music = Music(id = id, title = "Song$id", artist = "A", album = "B", duration = 100, path = "/$id.mp3", albumArtUri = ""),
        extra = null, userInfo = null
    )

    @Test
    fun invoke_emptyList_doesNothing() = runTest {
        repository.addMusic(music(1))
        restoreUseCase(emptyList())
        assertEquals(1, repository.getAllMusicInfoAsList(MusicSortKey.TITLE, SortDirection.ASC).size)
    }

    @Test
    fun invoke_restoresRemovedMusic() = runTest {
        repository.addMusic(music(1))
        repository.addMusic(music(2))
        removeUseCase(listOf(1))
        assertEquals(1, repository.getAllMusicInfoAsList(MusicSortKey.TITLE, SortDirection.ASC).size)
        restoreUseCase(listOf(1))
        assertEquals(2, repository.getAllMusicInfoAsList(MusicSortKey.TITLE, SortDirection.ASC).size)
    }

    @Test
    fun invoke_restoresOnlySpecified() = runTest {
        repository.addMusic(music(1))
        repository.addMusic(music(2))
        repository.addMusic(music(3))
        removeUseCase(listOf(1, 2, 3))
        restoreUseCase(listOf(2))
        val remaining = repository.getAllMusicInfoAsList(MusicSortKey.TITLE, SortDirection.ASC)
        assertEquals(1, remaining.size)
        assertEquals(2, remaining[0].music.id)
    }
}
