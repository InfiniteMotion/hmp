package com.hmp.data.util

expect object MusicTagParser {
    fun parseLyrics(filePath: String): String?
    fun parseMetadata(filePath: String): MusicMetadata?
}

data class MusicMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val track: String? = null,
    val duration: Long? = null,
    val bitRate: Int? = null,
    val sampleRate: Int? = null,
    val format: String? = null,
    val lyrics: String? = null,
    val albumArt: ByteArray? = null,
)
