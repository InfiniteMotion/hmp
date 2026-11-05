package com.hearablemusic.player.ui.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File

/**
 * AlbumArtPixelsLoader 的 Desktop 实现。
 *
 * skiko Image.makeFromEncoded 解码 → Bitmap/Canvas 读像素 → 步进采样
 * （min 边 / 32，约 32×32 个采样点，量级与 Android 侧 150×150 缩略图同档），
 * 供 commonMain ThemeViewModel 的直方图取色分析。
 */
class SkiaAlbumArtPixelsLoader : AlbumArtPixelsLoader {

    override suspend fun loadPixels(albumArtUri: String): IntArray? = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            val file = File(albumArtUri)
            if (file.exists() && file.length() > 0) file.readBytes() else null
        }.getOrNull() ?: return@withContext null

        runCatching {
            val image = Image.makeFromEncoded(bytes)
            val bitmap = Bitmap()
            bitmap.allocPixels(
                ImageInfo.makeN32Premul(image.width, image.height, null),
            )
            Canvas(bitmap).drawImage(image, 0f, 0f)

            val step = maxOf(1, minOf(image.width, image.height) / 32)
            val pixels = mutableListOf<Int>()
            for (y in 0 until image.height step step) {
                for (x in 0 until image.width step step) {
                    pixels.add(bitmap.getColor(x, y))
                }
            }
            pixels.toIntArray()
        }.getOrNull()
    }
}
