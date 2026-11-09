package com.hmp.data.util

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicTagParserTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = kotlin.io.path.createTempDirectory("music_tag_test" + "-").toFile()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    /**
     * Creates a minimal valid WAV file (44-byte header + silence).
     * jaudiotagger can read WAV files but they won't have metadata tags.
     */
    private fun createMinimalWavFile(file: File) {
        val sampleRate = 44100
        val channels = 1
        val bitsPerSample = 16
        val dataLength = 100 // 100 bytes of silence
        val totalLength = 44 + dataLength

        file.outputStream().buffered().use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            out.write(intToLittleEndian(totalLength - 8))
            out.write("WAVE".toByteArray())

            // fmt subchunk
            out.write("fmt ".toByteArray())
            out.write(intToLittleEndian(16)) // subchunk1 size
            out.write(shortToLittleEndian(1)) // PCM format
            out.write(shortToLittleEndian(channels))
            out.write(intToLittleEndian(sampleRate))
            out.write(intToLittleEndian(sampleRate * channels * bitsPerSample / 8))
            out.write(shortToLittleEndian(channels * bitsPerSample / 8))
            out.write(shortToLittleEndian(bitsPerSample))

            // data subchunk
            out.write("data".toByteArray())
            out.write(intToLittleEndian(dataLength))
            out.write(ByteArray(dataLength)) // silence
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

    // ===== parseLyrics =====

    @Test
    fun parseLyrics_nonExistentFile_returnsNull() {
        assertNull(MusicTagParser.parseLyrics("/nonexistent/path.mp3"))
    }

    @Test
    fun parseLyrics_emptyPath_returnsNull() {
        assertNull(MusicTagParser.parseLyrics(""))
    }

    @Test
    fun parseLyrics_wavFile_noLyrics_returnsNullOrEmpty() {
        val wavFile = File(tempDir, "test.wav")
        createMinimalWavFile(wavFile)
        // WAV files typically don't have lyrics tags
        val lyrics = MusicTagParser.parseLyrics(wavFile.absolutePath)
        // WAV files may return null or empty string for lyrics
        assertTrue(lyrics == null || lyrics.isEmpty())
    }

    @Test
    fun parseLyrics_invalidFile_returnsNull() {
        val invalidFile = File(tempDir, "invalid.mp3")
        invalidFile.writeText("this is not an audio file")
        assertNull(MusicTagParser.parseLyrics(invalidFile.absolutePath))
    }

    @Test
    fun parseLyrics_directory_returnsNull() {
        assertNull(MusicTagParser.parseLyrics(tempDir.absolutePath))
    }

    // ===== parseMetadata =====

    @Test
    fun parseMetadata_nonExistentFile_returnsNull() {
        assertNull(MusicTagParser.parseMetadata("/nonexistent/path.mp3"))
    }

    @Test
    fun parseMetadata_emptyPath_returnsNull() {
        assertNull(MusicTagParser.parseMetadata(""))
    }

    @Test
    fun parseMetadata_wavFile_returnsMetadataWithFormat() {
        val wavFile = File(tempDir, "test.wav")
        createMinimalWavFile(wavFile)
        val metadata = MusicTagParser.parseMetadata(wavFile.absolutePath)
        assertNotNull(metadata)
        // WAV files should have format info
        assertNotNull(metadata.format)
        // Duration should be > 0 (even if very small)
        assertNotNull(metadata.duration)
    }

    @Test
    fun parseMetadata_invalidFile_returnsNull() {
        val invalidFile = File(tempDir, "invalid.mp3")
        invalidFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        assertNull(MusicTagParser.parseMetadata(invalidFile.absolutePath))
    }

    @Test
    fun parseMetadata_directory_returnsNull() {
        assertNull(MusicTagParser.parseMetadata(tempDir.absolutePath))
    }

    // ===== MusicMetadata data class =====

    @Test
    fun musicMetadata_defaultValues() {
        val metadata = MusicMetadata()
        assertNull(metadata.title)
        assertNull(metadata.artist)
        assertNull(metadata.album)
        assertNull(metadata.duration)
        assertNull(metadata.bitRate)
        assertNull(metadata.sampleRate)
        assertNull(metadata.format)
        assertNull(metadata.lyrics)
        assertNull(metadata.albumArt)
    }

    @Test
    fun musicMetadata_equality() {
        val m1 = MusicMetadata(title = "Test", artist = "Artist")
        val m2 = MusicMetadata(title = "Test", artist = "Artist")
        assertEquals(m1, m2)
    }

    @Test
    fun musicMetadata_copy() {
        val original = MusicMetadata(title = "Original", artist = "Artist")
        val copy = original.copy(title = "Modified")
        assertEquals("Modified", copy.title)
        assertEquals("Artist", copy.artist)
    }
}
