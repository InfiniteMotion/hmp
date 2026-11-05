package com.hearablemusic.player.ui.common.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.hearablemusic.player.ui.platform.HapticEffect
import com.hearablemusic.player.ui.platform.HapticService

/** Android 触觉实现：View.performHapticFeedback（与旧 HapticFeedbackHelper 等价）。 */
private class ViewHapticService(private val view: View) : HapticService {
    override fun perform(effect: HapticEffect) {
        val constant = when (effect) {
            HapticEffect.TICK -> HapticFeedbackConstants.CLOCK_TICK

            HapticEffect.VIRTUAL_KEY -> HapticFeedbackConstants.VIRTUAL_KEY

            HapticEffect.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS

            HapticEffect.CONTEXT_CLICK -> HapticFeedbackConstants.CONTEXT_CLICK

            HapticEffect.KEYBOARD_PRESS -> HapticFeedbackConstants.KEYBOARD_PRESS

            HapticEffect.CONFIRM -> HapticFeedbackConstants.CONFIRM

            HapticEffect.REJECT -> HapticFeedbackConstants.REJECT

            HapticEffect.DRAG_START ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HapticFeedbackConstants.DRAG_START
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
        }
        view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }
}

@Composable
actual fun rememberPlatformHaptics(): HapticService {
    val view = LocalView.current
    return remember(view) { ViewHapticService(view) }
}
