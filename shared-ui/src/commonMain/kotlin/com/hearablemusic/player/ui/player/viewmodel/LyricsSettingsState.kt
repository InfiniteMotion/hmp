package com.hearablemusic.player.ui.player.viewmodel

import androidx.compose.runtime.Stable
import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment

@Stable
data class LyricsSettingsState(
    val lyricsOriginalTextSize: Int = 14,
    val lyricsTranslatedTextSize: Int = 14,
    val lyricsCurrentTimeTextSize: Int = 16,
    val lyricsLineSpacing: Int = 6,
    val lyricsKaraokeEnabled: Boolean = true,
    val lyricsDisplayMode: DisplayMode = DisplayMode.DUAL,
    val lyricsAlignment: LyricsAlignment = LyricsAlignment.CENTER,
)
