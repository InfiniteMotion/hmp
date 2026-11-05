package com.hmp.data.util

import com.hmp.domain.music.EditableMusicTags

actual object MusicTagEditor {

    actual fun writeTags(filePath: String, tags: EditableMusicTags): Result<Unit> {
        val bridge = TagWriterBridge.registered
        if (bridge != null) {
            return if (bridge.write(filePath, tags)) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Failed to write tags for: $filePath"))
            }
        }
        return Result.failure(
            UnsupportedOperationException("Tag editing is not supported on this platform yet"),
        )
    }
}

/**
 * iOS 端标签写入桥。Swift 侧通过 [register] 注册基于 AVAsset/AVAssetExportSession 的实现。
 */
object TagWriterBridge {
    var registered: Writer? = null

    fun register(writer: Writer) {
        registered = writer
    }

    interface Writer {
        fun write(filePath: String, tags: EditableMusicTags): Boolean
    }
}
