package com.hmp.data.util

import com.hmp.domain.music.EditableMusicTags
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

actual object MusicTagEditor {

    actual fun writeTags(filePath: String, tags: EditableMusicTags): Result<Unit> {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canWrite()) {
                return Result.failure(
                    IllegalStateException("Music file is not writable: $filePath"),
                )
            }
            writeTagsToFile(file, tags)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeTagsToFile(file: File, tags: EditableMusicTags) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        tags.title?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.TITLE, it) }
        tags.artist?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ARTIST, it) }
        tags.album?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ALBUM, it) }
        tags.year?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.YEAR, it) }
        tags.genre?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.GENRE, it) }
        tags.track?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.TRACK, it) }
        tags.lyrics?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.LYRICS, it) }
        tags.albumArt?.let { art ->
            tag.deleteArtworkField()
            if (art.isNotEmpty()) {
                tag.setField(artworkFromBytes(art))
            }
        }
        AudioFileIO.write(audioFile)
    }

    private fun artworkFromBytes(bytes: ByteArray): Artwork = ArtworkFactory.getNew().apply {
        binaryData = bytes
        mimeType = detectImageMimeType(bytes)
        description = ""
    }

    private fun detectImageMimeType(bytes: ByteArray): String = when {
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() -> "image/png"

        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "image/jpeg"

        else -> "image/jpeg"
    }
}
