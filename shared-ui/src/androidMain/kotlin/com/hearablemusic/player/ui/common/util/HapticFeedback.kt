package com.hearablemusic.player.ui.common.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 触觉反馈 Android actual：包装 LocalView.performHapticFeedback。
 * 契约见 commonMain HapticFeedbackHelper.kt。
 */
class AndroidHapticFeedbackHelper(private val view: View) : HapticFeedbackHelper {

    override fun performLightClick() {
        view.performHapticFeedback(
            HapticFeedbackConstants.CLOCK_TICK,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    override fun performClick() {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    override fun performLongPress() {
        view.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    override fun performContextClick() {
        view.performHapticFeedback(
            HapticFeedbackConstants.CONTEXT_CLICK,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    override fun performKeyboardPress() {
        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    override fun performConfirm() {
        view.performHapticFeedback(
            HapticFeedbackConstants.CONFIRM,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    override fun performReject() {
        view.performHapticFeedback(
            HapticFeedbackConstants.REJECT,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    override fun performDragStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(
                HapticFeedbackConstants.DRAG_START,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        } else {
            performLightClick()
        }
    }

    override fun performGestureStart() {
        view.performHapticFeedback(
            HapticFeedbackConstants.GESTURE_START,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    override fun performGestureEnd() {
        view.performHapticFeedback(
            HapticFeedbackConstants.GESTURE_END,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }
}

@Composable
actual fun rememberHapticFeedback(): HapticFeedbackHelper {
    val view = LocalView.current
    return remember(view) { AndroidHapticFeedbackHelper(view) }
}
