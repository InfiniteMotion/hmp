package com.hmp.data.util

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * D2-09 判据：Desktop 扫出来的曲目集合**恰好等于**共用白名单里的那些格式。
 *
 * 基线给的验证条件是"混入 mp3 / flac / opus / wma / ape / txt 的临时目录里，
 * 产出的条目恰好是受支持那几条（含 opus），txt 与未放行的 ape 不出现"——这里照它写，
 * 并额外钉两条容易各自跑偏的地方：大小写（`E.MP3`）与递归子目录。
 *
 * 走的是真实扫描路径（不设钩子、不注入集合），因为它现在读的就是 commonMain 那一份常量。
 */
class DeviceMusicScannerFormatFilterTest {

    private lateinit var root: File

    @Before
    fun createTree() {
        root = Files.createTempDirectory("hmp-scan-").toFile()
        listOf("a.mp3", "b.flac", "c.opus", "d.wma", "e.MP3", "f.ape", "g.txt", "h", "i.dsf", "j.wv")
            .forEach { File(root, it).writeText("not audio") }
        val sub = File(root, "sub").apply { mkdir() }
        listOf("k.m4a", "l.ape").forEach { File(sub, it).writeText("not audio") }
    }

    @After
    fun cleanUp() {
        // 扫描器是 object，状态是全局的；不清就会把临时目录漏给同一次构建里的其他用例
        DeviceMusicScanner.setScanDirectories(emptyList())
        DeviceMusicScanner.setBlockedDirectories(emptyList())
        root.deleteRecursively()
    }

    @Test
    fun scanMusic_yieldsExactlyTheWhitelistedFormats() = runTest {
        DeviceMusicScanner.setScanDirectories(listOf(root))

        val names = DeviceMusicScanner.scanMusic().map { File(it.path).name }.toSet()

        assertEquals(
            "ape / dsf / wv 未放行（FFmpeg 能解与否是前件）、txt 与无扩展名不收、大小写都要认",
            setOf("a.mp3", "b.flac", "c.opus", "d.wma", "e.MP3", "k.m4a"),
            names,
        )
    }
}
