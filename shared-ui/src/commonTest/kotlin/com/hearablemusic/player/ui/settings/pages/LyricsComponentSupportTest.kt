package com.hearablemusic.player.ui.settings.pages

import com.hmp.domain.lyrics.LyricsComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D4-04：不支持悬浮歌词的平台隐藏该 tab。 */
class LyricsComponentSupportTest {

    @Test
    fun unsupported_hidesFloatingTab() {
        val components = supportedLyricsComponents(floatingSupported = false)

        assertFalse(LyricsComponent.FLOATING in components)
        assertTrue(LyricsComponent.PLAYER in components)
        assertTrue(LyricsComponent.FULLSCREEN in components)
    }

    @Test
    fun supported_keepsAllTabs() {
        assertEquals(LyricsComponent.entries, supportedLyricsComponents(floatingSupported = true))
    }
}
