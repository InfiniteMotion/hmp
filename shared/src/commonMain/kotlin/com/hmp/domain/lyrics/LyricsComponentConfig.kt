package com.hmp.domain.lyrics

import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import com.hmp.domain.config.LyricsConfig
import kotlinx.serialization.Serializable

enum class LyricsComponent(val key: String) {
    PLAYER("player"),
    FULLSCREEN("fullscreen"),
    FLOATING("floating"),
    ;

    companion object {
        fun fromKey(key: String): LyricsComponent? = entries.find { it.key == key }
    }
}

@Serializable
data class LyricsComponentConfig(
    val originalTextSize: Int = 14,
    val translatedTextSize: Int = 14,
    val currentTimeTextSize: Int = 16,
    val lineSpacing: Int = 6,
    val karaokeEnabled: Boolean = true,
    val displayMode: DisplayMode = DisplayMode.DUAL,
    val alignment: LyricsAlignment = LyricsAlignment.CENTER,
    val linkedTo: String? = null,
) {
    fun toLyricsConfig(): LyricsConfig = LyricsConfig(
        originalTextSize = originalTextSize,
        translatedTextSize = translatedTextSize,
        currentTimeTextSize = currentTimeTextSize,
        lineSpacing = lineSpacing,
        karaokeEnabled = karaokeEnabled,
        displayMode = displayMode,
        alignment = alignment,
    )

    companion object {
        val DEFAULT = LyricsComponentConfig()
    }
}
