package com.hmp.data.util

import com.hmp.domain.music.AudioFormats
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSFileSize

/**
 * iOS 的 NSFileManager 目录遍历（**不是** expect/actual 成员，D2-10）。
 *
 * 与 Desktop 那份是"同名的两份平台内实现"，共享抽象在 `MusicRepositoryBase.performMusicScan()`；
 * 扫描进行中的状态由仓库那份（`MusicRepository.isScanning`）负责。
 */
@OptIn(ExperimentalForeignApi::class)
object DeviceMusicScanner {

    suspend fun scanMusic(): List<ScannedMusicFile> = withContext(Dispatchers.Default) {
        performScan()
    }

    private fun performScan(): List<ScannedMusicFile> {
        val musicList = mutableListOf<ScannedMusicFile>()
        val fileManager = NSFileManager.defaultManager

        val documentsURL = fileManager.URLForDirectory(
            NSDocumentDirectory,
            NSUserDomainMask,
            null,
            true,
            null
        ) ?: return musicList

        // 白名单的唯一真源在 commonMain（D2-09）：原先 iOS 自备一份 7 项、少 opus / wma，
        // 同一个文件夹在 Desktop 与 iOS 上会扫出不同数量的歌。
        scanDirectory(documentsURL.path!!, musicList, fileManager)

        return musicList
    }

    private fun scanDirectory(
        directory: String,
        results: MutableList<ScannedMusicFile>,
        fileManager: NSFileManager
    ) {
        val contents = fileManager.contentsOfDirectoryAtPath(directory, null) ?: return

        for (item in contents) {
            val fullPath = "$directory/$item"
            val url = NSURL.fileURLWithPath(fullPath)
            if (url.hasDirectoryPath) {
                scanDirectory(fullPath, results, fileManager)
            } else {
                createScannedMusicFile(fullPath, item.toString())?.let { results.add(it) }
            }
        }
    }

    /**
     * 一个文件 → 一条扫描结果；不属于曲库的返回 null（D2-09）。
     *
     * 白名单与时长下限都走 commonMain 那一份谓词 `AudioFormats.isIncludedInLibrary` ——
     * 这里原先是本地再判一遍扩展名，且**没有任何时长下限**。
     */
    private fun createScannedMusicFile(path: String, filename: String): ScannedMusicFile? {
        val fallbackTitle = filename.substringBeforeLast(".")

        val metadata = MusicTagParser.parseMetadata(path)
        if (!AudioFormats.isIncludedInLibrary(filename, metadata?.duration)) return null

        val fileSize: Long? = try {
            val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)
            if (attributes != null) {
                val sizeValue = attributes[NSFileSize]
                when (sizeValue) {
                    is Long -> sizeValue
                    is Int -> sizeValue.toLong()
                    is Double -> sizeValue.toLong()
                    else -> null
                }
            } else null
        } catch (_: Exception) {
            null
        }

        // Extract album artwork via registered bridge
        val albumArtUri = ArtworkBridge.registered?.extractAndSave(path) ?: ""

        return ScannedMusicFile(
            id = stableIdFor(path),
            title = metadata?.title ?: fallbackTitle,
            artist = metadata?.artist ?: "Unknown Artist",
            album = metadata?.album ?: "Unknown Album",
            duration = metadata?.duration ?: 0L,
            path = path,
            albumArtUri = albumArtUri,
            bitRate = metadata?.bitRate,
            sampleRate = metadata?.sampleRate,
            fileSize = fileSize,
            format = metadata?.format ?: path.substringAfterLast(".").uppercase(),
            lyrics = metadata?.lyrics
        )
    }
}

/**
 * Bridge for Swift to register artwork extraction implementation.
 * Same pattern as MetadataParserBridge.
 */
object ArtworkBridge {
    var registered: ArtworkExtractor? = null

    fun register(extractor: ArtworkExtractor) {
        registered = extractor
    }

    interface ArtworkExtractor {
        fun extractAndSave(filePath: String): String?
    }
}
