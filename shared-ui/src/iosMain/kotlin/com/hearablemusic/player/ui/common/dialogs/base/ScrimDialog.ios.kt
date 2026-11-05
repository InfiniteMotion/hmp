package com.hearablemusic.player.ui.common.dialogs.base

import androidx.compose.ui.window.DialogProperties

/** iOS actual：无系统栏嵌合概念，仅禁用平台默认宽度以实现全宽遮罩（与 Desktop actual 同）。 */
internal actual fun scrimDialogProperties(): DialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
)
