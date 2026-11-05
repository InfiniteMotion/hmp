package com.hearablemusic.player.ui.common.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/** iOS actual：skiko Image.makeFromEncoded 解码（与 Desktop actual 同实现，CMP iOS 亦基于 skiko）。 */
actual fun ByteArray.decodeToImageBitmap(): ImageBitmap? = try {
    Image.makeFromEncoded(this).toComposeImageBitmap()
} catch (_: Exception) {
    null
}
