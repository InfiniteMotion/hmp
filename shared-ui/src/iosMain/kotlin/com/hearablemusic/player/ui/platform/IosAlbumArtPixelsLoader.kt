package com.hearablemusic.player.ui.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.posix.memcpy

/**
 * AlbumArtPixelsLoader 的 iOS 实现（A3）。
 *
 * 与 Desktop 的 SkiaAlbumArtPixelsLoader 同构：封面貌似文件路径（iOS 沙盒内），
 * skiko Image.makeFromEncoded 解码 → Bitmap/Canvas 读像素 → 步进采样，
 * 供 commonMain ThemeViewModel 的直方图取色分析。
 */
class IosAlbumArtPixelsLoader : AlbumArtPixelsLoader {

    override suspend fun loadPixels(albumArtUri: String): IntArray? = withContext(Dispatchers.Default) {
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(albumArtUri)) return@withContext null

        val bytes = runCatching {
            fm.contentsAtPath(albumArtUri)?.toByteArray()
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

/** NSData → ByteArray（K/N 无内建转换，memcpy 拷贝）。 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}
