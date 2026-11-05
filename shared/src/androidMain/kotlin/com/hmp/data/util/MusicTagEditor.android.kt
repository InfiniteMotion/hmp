package com.hmp.data.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import com.hmp.domain.music.EditableMusicTags
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

/**
 * 文件不可直接写入（Android 分区存储下的用户共享媒体），
 * 需要通过 SAF 授权后使用 [MusicTagEditor.writeTags] 的 Uri 重载写入。
 */
class NeedsStorageAccessException(
    filePath: String,
) : IllegalStateException("Storage access required to edit: $filePath")

actual object MusicTagEditor {

    @Volatile
    private var appContext: Context? = null

    /**
     * 由 Application 启动时调用，用于 SAF 读写与媒体库刷新。
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun writeTags(filePath: String, tags: EditableMusicTags): Result<Unit> {
        val context = appContext
            ?: return Result.failure(
                IllegalStateException("MusicTagEditor has not been initialized"),
            )
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(IllegalStateException("Music file not found: $filePath"))
            }
            if (!file.canWrite()) {
                // 分区存储下无直接写权限，需通过 SAF 授权后调用 writeTags(Uri, ...)
                return Result.failure(NeedsStorageAccessException(filePath))
            }
            writeTagsToFile(file, tags)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 通过 SAF 授权的内容 Uri 写入标签（Android 特有）。
     *
     * 文件内容先复制到带正确音频扩展名的临时文件，由 jaudiotagger 改写后写回 [uri]；
     * 完成后按 [scanPath]（原始文件路径）刷新媒体库索引。
     */
    fun writeTags(uri: Uri, tags: EditableMusicTags, scanPath: String? = null): Result<Unit> {
        val context = appContext
            ?: return Result.failure(
                IllegalStateException("MusicTagEditor has not been initialized"),
            )
        val resolver = context.contentResolver
        return try {
            val tempFile = createTempFileFor(scanPath, context)
            try {
                resolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return Result.failure(
                    IllegalStateException("Cannot open input stream for: $uri"),
                )
                writeTagsToFile(tempFile, tags)
                resolver.openOutputStream(uri, "w")?.use { output ->
                    tempFile.inputStream().use { input -> input.copyTo(output) }
                } ?: return Result.failure(
                    IllegalStateException("Cannot open output stream for: $uri"),
                )
            } finally {
                tempFile.delete()
            }
            // 同步 MediaStore 元数据，立即反映标题/艺术家等文本字段
            val values = ContentValues().apply {
                tags.title?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.TITLE, it) }
                tags.artist?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ARTIST, it) }
                tags.album?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                tags.year?.takeIf { it.isNotBlank() }?.let {
                    put(MediaStore.Audio.Media.YEAR, it.toIntOrNull() ?: 0)
                }
                tags.genre?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.GENRE, it) }
                tags.track?.takeIf { it.isNotBlank() }?.let {
                    put(MediaStore.Audio.Media.TRACK, it.toIntOrNull() ?: 0)
                }
            }
            if (values.size() > 0) {
                resolver.update(uri, values, null, null)
            }
            scanPath?.let { path ->
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 按文件路径反查 MediaStore 中的音频条目（Android 10+ 分区存储下 DATA 列仍可读）。
     * 用于 [android.provider.MediaStore.createWriteRequest] 请求修改指定文件。
     */
    fun queryMediaStoreUri(context: Context, filePath: String): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(filePath),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                return ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
            }
        }
        return null
    }

    /**
     * jaudiotagger 按文件扩展名识别音频格式，临时文件必须保留原扩展名。
     */
    private fun createTempFileFor(originalPath: String?, context: Context): File {
        val extension = originalPath
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() && it.length <= 8 }
            ?: "bin"
        return File.createTempFile("hmp_tag_edit_", ".$extension", context.cacheDir)
    }

    private fun writeTagsToFile(file: File, tags: EditableMusicTags) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        tags.title?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.TITLE, it) }
        tags.artist?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ARTIST, it) }
        tags.album?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.ALBUM, it) }
        tags.year?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.YEAR, it) }
        tags.genre?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.GENRE, it) }
        tags.track?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.TRACK, it) }
        tags.lyrics?.takeIf { it.isNotBlank() }?.let { tag.setField(FieldKey.LYRICS, it) }
        tags.albumArt?.let { art ->
            tag.deleteArtworkField()
            if (art.isNotEmpty()) {
                tag.setField(artworkFromBytes(art))
            }
        }
        AudioFileIO.write(audioFile)
    }

    private fun artworkFromBytes(bytes: ByteArray): Artwork = ArtworkFactory.getNew().apply {
        binaryData = bytes
        mimeType = detectImageMimeType(bytes)
        description = ""
    }

    private fun detectImageMimeType(bytes: ByteArray): String = when {
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() -> "image/png"

        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "image/jpeg"

        else -> "image/jpeg"
    }
}
