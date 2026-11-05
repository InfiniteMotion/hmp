package com.hearablemusic.player.ui.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIFeedbackGenerator
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

/** iOS actual：Taptic Engine 实现手势语义级触觉（契约见 commonMain HapticFeedbackHelper.kt）。 */
private class IosHapticFeedbackHelper : HapticFeedbackHelper {

    private val selection = UISelectionFeedbackGenerator()
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notification = UINotificationFeedbackGenerator()

    private fun UIFeedbackGenerator.gentle(block: () -> Unit) {
        prepare()
        block()
    }

    override fun performLightClick() = light.gentle { light.impactOccurred() }
    override fun performClick() = light.gentle { light.impactOccurred() }
    override fun performLongPress() = heavy.gentle { heavy.impactOccurred() }
    override fun performContextClick() = medium.gentle { medium.impactOccurred() }
    override fun performKeyboardPress() = light.gentle { light.impactOccurred() }
    override fun performConfirm() = notification.gentle { notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess) }
    override fun performReject() = notification.gentle { notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError) }
    override fun performDragStart() = medium.gentle { medium.impactOccurred() }
    override fun performGestureStart() = selection.gentle { selection.selectionChanged() }
    override fun performGestureEnd() = selection.gentle { selection.selectionChanged() }
}

@Composable
actual fun rememberHapticFeedback(): HapticFeedbackHelper = remember { IosHapticFeedbackHelper() }
