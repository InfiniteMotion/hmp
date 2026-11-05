package com.hmp.data.util

import com.hmp.domain.music.EditableMusicTags
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicTagEditorTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = kotlin.io.path.createTempDirectory("music_tag_editor_test" + "-").toFile()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    private fun createMinimalWavFile(file: File) {
        val sampleRate = 44100
        val channels = 1
        val bitsPerSample = 16
        val dataLength = 100
        val totalLength = 44 + dataLength

        file.outputStream().buffered().use { out ->
            out.write("RIFF".toByteArray())
            out.write(intToLittleEndian(totalLength - 8))
            out.write("WAVE".toByteArray())

            out.write("fmt ".toByteArray())
            out.write(intToLittleEndian(16))
            out.write(shortToLittleEndian(1))
            out.write(shortToLittleEndian(channels))
            out.write(intToLittleEndian(sampleRate))
            out.write(intToLittleEndian(sampleRate * channels * bitsPerSample / 8))
            out.write(shortToLittleEndian(channels * bitsPerSample / 8))
            out.write(shortToLittleEndian(bitsPerSample))

            out.write("data".toByteArray())
            out.write(intToLittleEndian(dataLength))
            out.write(ByteArray(dataLength))
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun shortToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )

    // ===== writeTags =====

    @Test
    fun writeTags_wavFile_persistsTitleArtistAlbum() {
        val wavFile = File(tempDir, "song.wav")
        createMinimalWavFile(wavFile)

        val result = MusicTagEditor.writeTags(
            wavFile.absolutePath,
            EditableMusicTags(title = "新标题", artist = "New Artist", album = "New Album"),
        )
        assertTrue(result.isSuccess, "writeTags should succeed: $result")

        val metadata = MusicTagParser.parseMetadata(wavFile.absolutePath)
        assertNotNull(metadata, "metadata should be readable after writing tags")
        assertEquals("新标题", metadata.title)
        assertEquals("New Artist", metadata.artist)
        assertEquals("New Album", metadata.album)
    }

    @Test
    fun writeTags_partialUpdate_keepsUnsetFields() {
        val wavFile = File(tempDir, "partial.wav")
        createMinimalWavFile(wavFile)
        MusicTagEditor.writeTags(
            wavFile.absolutePath,
            EditableMusicTags(title = "T", artist = "A", album = "B"),
        )

        // 只更新标题，artist/album 保持原值
        val result = MusicTagEditor.writeTags(
            wavFile.absolutePath,
            EditableMusicTags(title = "T2"),
        )
        assertTrue(result.isSuccess)

        val metadata = MusicTagParser.parseMetadata(wavFile.absolutePath)
        assertNotNull(metadata)
        assertEquals("T2", metadata.title)
        assertEquals("A", metadata.artist)
        assertEquals("B", metadata.album)
    }

    @Test
    fun writeTags_nonexistentFile_returnsFailure() {
        val result = MusicTagEditor.writeTags(
            File(tempDir, "missing.wav").absolutePath,
            EditableMusicTags(title = "T"),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun writeTags_blankValues_keepExistingTags() {
        val wavFile = File(tempDir, "blank.wav")
        createMinimalWavFile(wavFile)
        MusicTagEditor.writeTags(
            wavFile.absolutePath,
            EditableMusicTags(title = "Keep", artist = "Artist", album = "Album"),
        )

        val result = MusicTagEditor.writeTags(
            wavFile.absolutePath,
            EditableMusicTags(title = "   ", artist = "  "),
        )
        assertTrue(result.isSuccess)

        val metadata = MusicTagParser.parseMetadata(wavFile.absolutePath)
        assertNotNull(metadata)
        assertEquals("Keep", metadata.title)
        assertEquals("Artist", metadata.artist)
    }

    @Test
    fun writeTags_persistsExtendedFieldsAndArtwork() {
        val wavFile = File(tempDir, "extended.wav")
        createMinimalWavFile(wavFile)
        val artwork = Base64.getDecoder().decode(PNG_1X1_BASE64)

        val result = MusicTagEditor.writeTags(
            wavFile.absolutePath,
            EditableMusicTags(
                title = "T",
                artist = "A",
                album = "B",
                year = "2020",
                genre = "Rock",
                track = "3",
                lyrics = "la la la",
                albumArt = artwork,
            ),
        )
        assertTrue(result.isSuccess, "writeTags should succeed: $result")

        val metadata = MusicTagParser.parseMetadata(wavFile.absolutePath)
        assertNotNull(metadata)
        assertEquals("2020", metadata.year)
        assertEquals("Rock", metadata.genre)
        assertEquals("3", metadata.track)
        assertEquals("la la la", metadata.lyrics)
        assertContentEquals(artwork, metadata.albumArt)
    }

    @Test
    fun writeTags_emptyAlbumArt_removesArtwork() {
        val wavFile = File(tempDir, "remove_art.wav")
        createMinimalWavFile(wavFile)
        val artwork = Base64.getDecoder().decode(PNG_1X1_BASE64)
        MusicTagEditor.writeTags(
            wavFile.absolutePath,
            EditableMusicTags(title = "T", albumArt = artwork),
        )

        val result = MusicTagEditor.writeTags(
            wavFile.absolutePath,
            EditableMusicTags(title = "T2", albumArt = ByteArray(0)),
        )
        assertTrue(result.isSuccess)

        val metadata = MusicTagParser.parseMetadata(wavFile.absolutePath)
        assertNotNull(metadata)
        assertEquals("T2", metadata.title)
        assertNull(metadata.albumArt)
    }

    private companion object {
        const val PNG_1X1_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNgYAAAAAMAASsJTYQAAAAASUVORK5CYII="
    }
}
