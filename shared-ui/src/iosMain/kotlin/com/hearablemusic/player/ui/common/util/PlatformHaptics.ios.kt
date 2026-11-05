package com.hearablemusic.player.ui.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hearablemusic.player.ui.platform.HapticEffect
import com.hearablemusic.player.ui.platform.HapticService
import platform.UIKit.UIFeedbackGenerator
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * iOS actual：UIFeedbackGenerator 触觉反馈（Taptic Engine）。
 *
 * 与 Android performHapticFeedback / Desktop 空实现对应；Generator 每次 perform 前
 * prepare() 以降低首触延迟（Apple 推荐模式）。
 */
private class IosHapticService : HapticService {

    private val selection = UISelectionFeedbackGenerator()
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notification = UINotificationFeedbackGenerator()

    private fun UIFeedbackGenerator.gentle(block: () -> Unit) {
        prepare()
        block()
    }

    override fun perform(effect: HapticEffect) {
        when (effect) {
            HapticEffect.TICK -> selection.gentle { selection.selectionChanged() }
            HapticEffect.VIRTUAL_KEY -> light.gentle { light.impactOccurred() }
            HapticEffect.LONG_PRESS -> heavy.gentle { heavy.impactOccurred() }
            HapticEffect.CONTEXT_CLICK -> medium.gentle { medium.impactOccurred() }
            HapticEffect.KEYBOARD_PRESS -> light.gentle { light.impactOccurred() }
            HapticEffect.CONFIRM -> notification.gentle { notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess) }
            HapticEffect.REJECT -> notification.gentle { notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError) }
            HapticEffect.DRAG_START -> medium.gentle { medium.impactOccurred() }
        }
    }
}

@Composable
actual fun rememberPlatformHaptics(): HapticService = remember { IosHapticServiceInstance }

/** PlatformServices.haptic 与 rememberPlatformHaptics 共享的触觉单例（A3）。 */
internal val IosHapticServiceInstance: HapticService = IosHapticService()
