package com.hmp.domain.music.usecase

import com.hmp.domain.music.EditableMusicTags
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.test.fakes.FakeMusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditMusicTagsUseCaseTest {

    private val repository = FakeMusicRepository()
    private val useCase = EditMusicTagsUseCase(repository)

    private fun music(id: Long) = MusicInfo(
        music = Music(
            id = id,
            title = "Old Title",
            artist = "Old Artist",
            album = "Old Album",
            duration = 180000,
            path = "/music/$id.mp3",
            albumArtUri = "",
        ),
        extra = null,
        userInfo = null,
    )

    @Test
    fun invoke_updatesAllFields() = runTest {
        repository.addMusic(music(1))
        val result = useCase(
            1,
            EditableMusicTags(
                title = "New Title",
                artist = "New Artist",
                album = "New Album",
                year = "2020",
                genre = "Rock",
                track = "3",
                lyrics = "new lyrics",
            ),
        )
        assertTrue(result.isSuccess)
        val updated = repository.getMusicInfoById(1).first()!!
        assertEquals("New Title", updated.music.title)
        assertEquals("New Artist", updated.music.artist)
        assertEquals("New Album", updated.music.album)
        assertEquals("new lyrics", repository.getMusicLyrics(1))
    }

    @Test
    fun invoke_partialUpdate_keepsUnsetFields() = runTest {
        repository.addMusic(music(1))
        val result = useCase(1, EditableMusicTags(title = "T2"))
        assertTrue(result.isSuccess)
        val updated = repository.getMusicInfoById(1).first()!!
        assertEquals("T2", updated.music.title)
        assertEquals("Old Artist", updated.music.artist)
        assertEquals("Old Album", updated.music.album)
    }

    @Test
    fun invoke_blankValues_keepExistingFields() = runTest {
        repository.addMusic(music(1))
        val result = useCase(1, EditableMusicTags(title = "   ", artist = "  "))
        assertTrue(result.isSuccess)
        val updated = repository.getMusicInfoById(1).first()!!
        assertEquals("Old Title", updated.music.title)
        assertEquals("Old Artist", updated.music.artist)
    }

    @Test
    fun invoke_missingMusic_returnsFailure() = runTest {
        val result = useCase(999, EditableMusicTags(title = "T"))
        assertTrue(result.isFailure)
    }

    @Test
    fun refreshAfterFileWrite_updatesLibraryWithoutRecheckingFile() = runTest {
        repository.addMusic(music(1))
        val result = useCase.refreshAfterFileWrite(
            1,
            EditableMusicTags(title = "New Title", lyrics = "new lyrics"),
        )
        assertTrue(result.isSuccess)
        val updated = repository.getMusicInfoById(1).first()!!
        assertEquals("New Title", updated.music.title)
        assertEquals("new lyrics", repository.getMusicLyrics(1))
    }

    @Test
    fun editableMusicTags_hasChanges_reflectsNullFields() {
        assertFalse(EditableMusicTags().hasChanges)
        assertTrue(EditableMusicTags(title = "T").hasChanges)
        assertTrue(EditableMusicTags(artist = "A").hasChanges)
        assertTrue(EditableMusicTags(album = "B").hasChanges)
        assertTrue(EditableMusicTags(year = "2020").hasChanges)
        assertTrue(EditableMusicTags(genre = "Rock").hasChanges)
        assertTrue(EditableMusicTags(track = "1").hasChanges)
        assertTrue(EditableMusicTags(lyrics = "x").hasChanges)
        assertTrue(EditableMusicTags(albumArt = ByteArray(0)).hasChanges)
    }
}
