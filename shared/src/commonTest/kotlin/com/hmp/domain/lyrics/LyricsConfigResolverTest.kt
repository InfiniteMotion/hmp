package com.hmp.domain.lyrics

import com.hmp.domain.config.DisplayMode
import com.hmp.domain.config.LyricsAlignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricsConfigResolverTest {

    @Test
    fun resolve_noConfig_returnsDefault() {
        val result = LyricsConfigResolver.resolve(LyricsComponent.PLAYER, emptyMap())
        assertEquals(LyricsComponentConfig.DEFAULT.toLyricsConfig(), result)
    }

    @Test
    fun resolve_withOwnConfig_returnsOwnConfig() {
        val config = LyricsComponentConfig(
            originalTextSize = 20,
            translatedTextSize = 16,
            displayMode = DisplayMode.LANG1,
            alignment = LyricsAlignment.LEFT,
        )
        val configs = mapOf(LyricsComponent.PLAYER to config)
        val result = LyricsConfigResolver.resolve(LyricsComponent.PLAYER, configs)
        assertEquals(20, result.originalTextSize)
        assertEquals(16, result.translatedTextSize)
        assertEquals(DisplayMode.LANG1, result.displayMode)
        assertEquals(LyricsAlignment.LEFT, result.alignment)
    }

    @Test
    fun resolve_linkedToOther_usesLinkedConfig() {
        val playerConfig = LyricsComponentConfig(
            originalTextSize = 20,
            linkedTo = "fullscreen",
        )
        val fullscreenConfig = LyricsComponentConfig(
            originalTextSize = 30,
            displayMode = DisplayMode.DUAL,
        )
        val configs = mapOf(
            LyricsComponent.PLAYER to playerConfig,
            LyricsComponent.FULLSCREEN to fullscreenConfig,
        )
        val result = LyricsConfigResolver.resolve(LyricsComponent.PLAYER, configs)
        // Should use fullscreen config since player links to fullscreen
        assertEquals(30, result.originalTextSize)
        assertEquals(DisplayMode.DUAL, result.displayMode)
    }

    @Test
    fun resolve_linkedToChain_ignoresChainUsesOwn() {
        val playerConfig = LyricsComponentConfig(
            originalTextSize = 20,
            linkedTo = "fullscreen",
        )
        // fullscreen links to floating → chain, should fallback to own
        val fullscreenConfig = LyricsComponentConfig(
            originalTextSize = 30,
            linkedTo = "floating",
        )
        val configs = mapOf(
            LyricsComponent.PLAYER to playerConfig,
            LyricsComponent.FULLSCREEN to fullscreenConfig,
        )
        val result = LyricsConfigResolver.resolve(LyricsComponent.PLAYER, configs)
        // Chain detected → use own config (playerConfig → fullscreenConfig → floating, chain)
        // Actually the logic: player links to fullscreen, fullscreen has linkedTo (not null),
        // so it's a chain A→B→C, returns own config
        assertEquals(20, result.originalTextSize)
    }

    @Test
    fun resolve_linkedToSelf_returnsOwn() {
        val config = LyricsComponentConfig(
            originalTextSize = 25,
            linkedTo = "player",
        )
        val configs = mapOf(LyricsComponent.PLAYER to config)
        val result = LyricsConfigResolver.resolve(LyricsComponent.PLAYER, configs)
        assertEquals(25, result.originalTextSize)
    }

    @Test
    fun resolveAll_allComponents_resolved() {
        val configs = mapOf(
            LyricsComponent.PLAYER to LyricsComponentConfig(originalTextSize = 16),
            LyricsComponent.FULLSCREEN to LyricsComponentConfig(originalTextSize = 24),
            LyricsComponent.FLOATING to LyricsComponentConfig(originalTextSize = 12),
        )
        val result = LyricsConfigResolver.resolveAll(configs)
        assertEquals(3, result.size)
        assertEquals(16, result[LyricsComponent.PLAYER]?.originalTextSize)
        assertEquals(24, result[LyricsComponent.FULLSCREEN]?.originalTextSize)
        assertEquals(12, result[LyricsComponent.FLOATING]?.originalTextSize)
    }

    @Test
    fun resolveAll_emptyConfigs_allGetDefaults() {
        val result = LyricsConfigResolver.resolveAll(emptyMap())
        assertEquals(3, result.size)
        result.values.forEach { config ->
            assertEquals(LyricsComponentConfig.DEFAULT.toLyricsConfig(), config)
        }
    }
}

class LyricsComponentTest {

    @Test
    fun fromKey_player_returnsPlayer() {
        assertEquals(LyricsComponent.PLAYER, LyricsComponent.fromKey("player"))
    }

    @Test
    fun fromKey_fullscreen_returnsFullscreen() {
        assertEquals(LyricsComponent.FULLSCREEN, LyricsComponent.fromKey("fullscreen"))
    }

    @Test
    fun fromKey_floating_returnsFloating() {
        assertEquals(LyricsComponent.FLOATING, LyricsComponent.fromKey("floating"))
    }

    @Test
    fun fromKey_unknown_returnsNull() {
        assertNull(LyricsComponent.fromKey("unknown"))
    }
}

class LyricsComponentConfigTest {

    @Test
    fun default_values() {
        val config = LyricsComponentConfig.DEFAULT
        assertEquals(14, config.originalTextSize)
        assertEquals(14, config.translatedTextSize)
        assertEquals(16, config.currentTimeTextSize)
        assertEquals(6, config.lineSpacing)
        assertEquals(DisplayMode.DUAL, config.displayMode)
        assertEquals(LyricsAlignment.CENTER, config.alignment)
        assertNull(config.linkedTo)
    }

    @Test
    fun toLyricsConfig_convertsCorrectly() {
        val config = LyricsComponentConfig(
            originalTextSize = 20,
            translatedTextSize = 18,
            currentTimeTextSize = 22,
            lineSpacing = 10,
            displayMode = DisplayMode.LANG1,
            alignment = LyricsAlignment.LEFT,
        )
        val lyricsConfig = config.toLyricsConfig()
        assertEquals(20, lyricsConfig.originalTextSize)
        assertEquals(18, lyricsConfig.translatedTextSize)
        assertEquals(22, lyricsConfig.currentTimeTextSize)
        assertEquals(10, lyricsConfig.lineSpacing)
        assertEquals(DisplayMode.LANG1, lyricsConfig.displayMode)
        assertEquals(LyricsAlignment.LEFT, lyricsConfig.alignment)
    }
}
