package com.hearablemusic.player.ui.settings.pages

import com.hearablemusic.player.ui.common.navigation.Routes
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** §九 决策 5：不支持音效的平台从设置页撤下入口。 */
class SettingsEntryVisibilityTest {

    @Test
    fun unsupported_hidesAudioEffectsEntry() {
        val entries = visibleSettingsEntries(audioEffectsSupported = false)

        assertFalse(entries.any { it.route == Routes.Player.AudioEffects })
        assertTrue(entries.isNotEmpty(), "其他设置项不能被一起过滤掉")
    }

    @Test
    fun supported_keepsAudioEffectsEntry() {
        val entries = visibleSettingsEntries(audioEffectsSupported = true)

        assertTrue(entries.any { it.route == Routes.Player.AudioEffects })
    }
}
