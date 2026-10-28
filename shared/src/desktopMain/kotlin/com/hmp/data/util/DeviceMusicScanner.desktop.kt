package com.hmp.data.util

import com.hmp.domain.music.AudioFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop 的文件遍历助手（**不是** expect/actual 成员，D2-10）。
 *
 * 共享抽象是 `MusicRepositoryBase.performMusicScan()`；本对象只服务 desktopMain 这一端，
 * 与 iOS 那份是"同名的两份平台内实现"，不是"同一契约的两个 actual"。
 * 扫描进行中的状态由仓库那份（`MusicRepository.isScanning`）负责，这里不再自备一份。
 */
object DeviceMusicScanner {

    private val scanDirectories = mutableListOf<File>()
    private val blockedDirectories = mutableSetOf<String>()

    fun addScanDirectory(dir: File) {
        scanDirectories.add(dir)
    }

    fun setScanDirectories(dirs: List<File>) {
        scanDirectories.clear()
        scanDirectories.addAll(dirs)
    }

    fun setBlockedDirectories(dirs: List<String>) {
        blockedDirectories.clear()
        blockedDirectories.addAll(dirs.map {
            try { File(it).canonicalPath } catch (_: Exception) { File(it).absolutePath }
        })
    }

    suspend fun scanMusic(): List<ScannedMusicFile> = withContext(Dispatchers.IO) {
        val dirsToScan = if (scanDirectories.isEmpty()) {
            getDefaultMusicDirectories()
        } else {
            scanDirectories
        }
        val results = mutableListOf<ScannedMusicFile>()
        for (dir in dirsToScan) {
            if (dir.exists() && dir.isDirectory) {
                scanDirectory(dir, results)
            }
        }
        results
    }

    private fun getDefaultMusicDirectories(): List<File> {
        val home = File(System.getProperty("user.home"))
        return listOfNotNull(
            File(home, "Music"),
            File(home, "Downloads"),
        ).filter { it.exists() && it.isDirectory }
    }

    private fun scanDirectory(dir: File, results: MutableList<ScannedMusicFile>) {
        val canonicalPath = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
        if (blockedDirectories.any { canonicalPath.startsWith(it) }) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanDirectory(file, results)
            } else {
                createScannedMusicFile(file)?.let { results.add(it) }
            }
        }
    }

    private val coverArtWriter = FileCoverArtWriter()

    /**
     * 一个文件 → 一条扫描结果；**不属于曲库的返回 null**（D2-09）。
     *
     * 白名单与时长下限都用 commonMain 那一份谓词 [AudioFormats.isIncludedInLibrary]：
     * 原先 Desktop 只有白名单、没有下限，几秒的碎片文件也收进来；而 Android 那时带着一分钟的硬下限，
     * 于是同一个文件夹在三端扫出不同数量。时长未知（元数据解析不出来）不因下限被丢 —— 见该函数注释。
     */
    private fun createScannedMusicFile(file: File): ScannedMusicFile? {
        val metadata = MusicTagParser.parseMetadata(file.absolutePath)
        if (!AudioFormats.isIncludedInLibrary(file.name, metadata?.duration)) return null
        // 封面名字由内容指纹决定（D2-17）：原先按音频文件路径命名 + "存在即跳过"，
        // 于是换过封面的文件永远显示旧图。
        val albumArtUri = metadata?.albumArt?.let { CoverArtStore.store(it, coverArtWriter) } ?: ""
        return ScannedMusicFile(
            id = stableIdFor(file.absolutePath),
            title = metadata?.title ?: file.nameWithoutExtension,
            artist = metadata?.artist ?: "Unknown Artist",
            album = metadata?.album ?: "Unknown Album",
            duration = metadata?.duration ?: 0L,
            path = file.absolutePath,
            albumArtUri = albumArtUri,
            bitRate = metadata?.bitRate,
            sampleRate = metadata?.sampleRate,
            fileSize = file.length(),
            format = metadata?.format ?: file.extension.uppercase(),
            lyrics = metadata?.lyrics
        )
    }
}
