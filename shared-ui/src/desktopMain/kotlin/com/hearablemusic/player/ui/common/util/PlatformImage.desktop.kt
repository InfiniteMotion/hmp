package com.hearablemusic.player.ui.common.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/** Desktop actual：skiko Image.makeFromEncoded 解码（失败抛异常，契约要求返回 null 故捕获）。 */
actual fun ByteArray.decodeToImageBitmap(): ImageBitmap? = try {
    Image.makeFromEncoded(this).toComposeImageBitmap()
} catch (_: Exception) {
    null
}
