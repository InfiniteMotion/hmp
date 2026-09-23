package com.hmp.data.util

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

actual object MusicTagParser {

    actual fun parseLyrics(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            tag?.getFirst(FieldKey.LYRICS)
                ?: tag?.getFirst("UNSYNCEDLYRICS")
                ?: tag?.getFirst("USLT")
        } catch (_: Exception) {
            null
        }
    }

    actual fun parseMetadata(filePath: String): MusicMetadata? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader
            val artwork = tag?.firstArtwork?.binaryData
            MusicMetadata(
                title = tag?.getFirst(FieldKey.TITLE),
                artist = tag?.getFirst(FieldKey.ARTIST),
                album = tag?.getFirst(FieldKey.ALBUM),
                year = tag?.getFirst(FieldKey.YEAR),
                genre = tag?.getFirst(FieldKey.GENRE),
                track = tag?.getFirst(FieldKey.TRACK),
                duration = (header?.trackLength?.toLong() ?: 0L) * 1000,
                // jaudiotagger getBitRateAsNumber() 单位即 kbps（接口 Javadoc），无需再 /1000
                bitRate = header?.bitRateAsNumber?.toInt(),
                sampleRate = header?.sampleRateAsNumber?.toInt(),
                format = header?.format,
                lyrics = tag?.getFirst(FieldKey.LYRICS)
                    ?: tag?.getFirst("UNSYNCEDLYRICS")
                    ?: tag?.getFirst("USLT"),
                albumArt = artwork
            )
        } catch (_: Exception) {
            null
        }
    }
}
