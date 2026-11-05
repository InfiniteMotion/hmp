package com.hmp.data.util

import platform.Foundation.NSURL

actual object MusicTagParser {

    actual fun parseLyrics(filePath: String): String? {
        val bridge = MetadataParserBridge.registered
        if (bridge != null) {
            return bridge.parse(filePath)?.lyrics
        }
        return null
    }

    actual fun parseMetadata(filePath: String): MusicMetadata? {
        val bridge = MetadataParserBridge.registered

        if (bridge != null) {
            return bridge.parse(filePath)
        }

        return try {
            val url = NSURL.fileURLWithPath(filePath)
            val fallbackTitle = url.lastPathComponent?.substringBeforeLast(".") ?: "Unknown"
            MusicMetadata(
                title = fallbackTitle,
                artist = "Unknown Artist",
                album = "Unknown Album",
                format = url.pathExtension?.uppercase(),
            )
        } catch (_: Exception) {
            null
        }
    }
}

object MetadataParserBridge {
    var registered: Parser? = null

    fun register(parser: Parser) {
        registered = parser
    }

    interface Parser {
        fun parse(filePath: String): MusicMetadata?
    }
}
