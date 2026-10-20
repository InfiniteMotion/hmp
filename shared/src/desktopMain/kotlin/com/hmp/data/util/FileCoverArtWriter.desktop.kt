package com.hmp.data.util

import java.io.File

/**
 * Desktop 的封面落盘：`~/.hmp/covers/<fileName>`。
 *
 * 名字已经由 [CoverArtStore.fileNameFor] 从内容算出，所以这里**不需要**再判"存在即跳过"来表达
 * "封面换没换" —— 内容变了名字就变，同名就是同一份字节。留一次存在性判断只是为了少写一遍盘。
 */
class FileCoverArtWriter(
    private val coversDir: File = defaultCoversDirectory(),
) : CoverArtWriter {

    override fun write(fileName: String, bytes: ByteArray): String? = try {
        if (!coversDir.exists()) coversDir.mkdirs()
        val target = File(coversDir, fileName)
        if (!target.exists() || target.length() != bytes.size.toLong()) {
            target.writeBytes(bytes)
        }
        target.absolutePath
    } catch (_: Exception) {
        null
    }

    companion object {
        fun defaultCoversDirectory(): File = File(System.getProperty("user.home"), ".hmp/covers")
    }
}
