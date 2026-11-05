package com.hearablemusic.player.ui.platform

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AlbumArtPixelsLoader 的 Android 实现。
 *
 * Coil3 软件位图（allowHardware(false)，取像素必须软件位图）→
 * BitmapDrawable 整块 getPixels。
 */
class CoilAlbumArtPixelsLoader(
    private val context: Context,
) : AlbumArtPixelsLoader {

    override suspend fun loadPixels(albumArtUri: String): IntArray? = withContext(Dispatchers.IO) {
        val loader = ImageLoader.Builder(context).build()
        val request = ImageRequest.Builder(context)
            .data(albumArtUri)
            .size(150, 150)
            .allowHardware(false)
            .build()

        // Coil3：SuccessResult 属性为 image（Image 接口），经 asDrawable 转 BitmapDrawable 取像素
        val result = (loader.execute(request) as? SuccessResult)?.image
        val bitmap = (result?.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
            ?: return@withContext null

        val w = bitmap.width
        val h = bitmap.height
        IntArray(w * h).also { pixels ->
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        }
    }
}
