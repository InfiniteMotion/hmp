package com.hmp.domain.music.usecase

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.music.MusicSortKey
import com.hmp.domain.music.SortDirection
import com.hmp.test.fakes.FakeMusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetAllMusicUseCaseTest {

    private val repository = FakeMusicRepository()
    private val useCase = GetAllMusicUseCase(repository)

    private fun music(id: Long, title: String, artist: String = "Artist", album: String = "Album", duration: Long = 180000) =
        MusicInfo(music = Music(id = id, title = title, artist = artist, album = album, duration = duration, path = "/music/$id.mp3", albumArtUri = ""), extra = null, userInfo = null)

    @Test
    fun invoke_emptyRepository_returnsEmpty() = runTest {
        val result = useCase()
        assertEquals(0, result.size)
    }

    @Test
    fun invoke_defaultSortByTitleAsc() = runTest {
        repository.addMusic(music(1, "Cherry"))
        repository.addMusic(music(2, "Apple"))
        repository.addMusic(music(3, "Banana"))
        val result = useCase()
        assertEquals("Apple", result[0].music.title)
        assertEquals("Banana", result[1].music.title)
        assertEquals("Cherry", result[2].music.title)
    }

    @Test
    fun invoke_sortByArtistDesc() = runTest {
        repository.addMusic(music(1, "S1", artist = "Alice"))
        repository.addMusic(music(2, "S2", artist = "Charlie"))
        repository.addMusic(music(3, "S3", artist = "Bob"))
        val result = useCase(orderBy = MusicSortKey.ARTIST, orderType = SortDirection.DESC)
        assertEquals("Charlie", result[0].music.artist)
        assertEquals("Bob", result[1].music.artist)
        assertEquals("Alice", result[2].music.artist)
    }

    @Test
    fun invoke_sortByDuration() = runTest {
        repository.addMusic(music(1, "Short", duration = 60000))
        repository.addMusic(music(2, "Long", duration = 300000))
        repository.addMusic(music(3, "Medium", duration = 180000))
        val result = useCase(orderBy = MusicSortKey.DURATION)
        assertEquals(60000, result[0].music.duration)
        assertEquals(180000, result[1].music.duration)
        assertEquals(300000, result[2].music.duration)
    }

    @Test
    fun getMusicCount_returnsCorrectCount() = runTest {
        repository.addMusic(music(1, "A"))
        repository.addMusic(music(2, "B"))
        assertEquals(2, useCase.getMusicCount().first())
    }

    @Test
    fun getMusicListByArtist_filtersCorrectly() = runTest {
        repository.addMusic(music(1, "S1", artist = "X"))
        repository.addMusic(music(2, "S2", artist = "Y"))
        repository.addMusic(music(3, "S3", artist = "X"))
        val result = useCase.getMusicListByArtist("X")
        assertEquals(2, result.size)
    }

    @Test
    fun getMusicListByAlbum_filtersCorrectly() = runTest {
        repository.addMusic(music(1, "S1", album = "AlbumA"))
        repository.addMusic(music(2, "S2", album = "AlbumB"))
        val result = useCase.getMusicListByAlbum("AlbumA")
        assertEquals(1, result.size)
    }

    @Test
    fun getMusicById_existingId_returnsMusic() = runTest {
        repository.addMusic(music(42, "My Song"))
        val result = useCase.getMusicById(42)
        assertEquals("My Song", result?.music?.title)
    }

    @Test
    fun getMusicById_nonExisting_returnsNull() = runTest {
        val result = useCase.getMusicById(999)
        assertNull(result)
    }
}
