package com.hmp.data.util

import com.hmp.domain.music.AudioFormats
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * D2-09 的尾巴：三端统一的**时长下限**（5 秒，只挡碎片）要在真实扫描路径上验。
 *
 * 为什么用真 WAV 而不是伪造时长：下限谓词本身在 `AudioFormatsTest` 里已经逐值钉过了，
 * 这一条要验的是"扫描器确实在问它" —— 用桩或直接用 List 断言的话，
 * 扫描器改成不过滤也照样绿（D2-06 那类"替身比真身宽容"的同型错误）。
 *
 * WAV 是无损头信息格式，JAudioTagger 按 `dataLength / byteRate` 算时长，
 * 所以能用 8kHz 单声道 16bit 造出精确的 1s / 5s / 6s 样本（每个不超过 100KB）。
 */
class DeviceMusicScannerDurationFloorTest {

    private lateinit var root: File

    private val bytesPerSecond = 8000L * 1 * 2   // 采样率 × 声道 × 每样本字节 = 16000 B/s

    @Before
    fun createTree() {
        root = Files.createTempDirectory("hmp-duration-").toFile()
        writeWav(File(root, "short.wav"), seconds = 1)     // 1s < 5s → 该被挡掉
        writeWav(File(root, "exact.wav"), seconds = 5)     // 恰好 5s → 收（下限是 >=）
        writeWav(File(root, "long.wav"), seconds = 6)      // 6s → 收
        // 解析不出元数据的文件：时长**未知**而不是 0 —— 不因下限被丢（见 AudioFormats.isPlausibleTrack）
        File(root, "unparsable.mp3").writeText("not audio at all")
        File(root, "notes.txt").writeText("not audio")     // 白名单外
    }

    @After
    fun cleanUp() {
        DeviceMusicScanner.setScanDirectories(emptyList())
        DeviceMusicScanner.setBlockedDirectories(emptyList())
        root.deleteRecursively()
    }

    /** 最小 WAV：RIFF/fmt/data，数据段全静音。 */
    private fun writeWav(file: File, seconds: Int) {
        val dataLength = (bytesPerSecond * seconds).toInt()
        file.outputStream().buffered().use { out ->
            out.write("RIFF".toByteArray())
            out.write(le32(4 + 16 + 8 + dataLength))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(le32(16))
            out.write(le16(1))                    // PCM
            out.write(le16(1))                    // 声道
            out.write(le32(8000))                 // 采样率
            out.write(le32(bytesPerSecond.toInt()))  // 每秒字节数（时长就是靠它算的）
            out.write(le16(2))                    // 块对齐
            out.write(le16(16))                   // 位深
            out.write("data".toByteArray())
            out.write(le32(dataLength))
            out.write(ByteArray(dataLength))      // 静音
        }
    }

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())

    @Test
    fun scanMusic_dropsSubFiveSecondTracks_andKeepsUnknownDurationOnes() = runTest {
        DeviceMusicScanner.setScanDirectories(listOf(root))

        val names = DeviceMusicScanner.scanMusic().map { File(it.path).name }.toSet()

        assertEquals(setOf("exact.wav", "long.wav", "unparsable.mp3"), names)
    }

    /** 下限生效的前提是"时长真读得出来"，所以这里把读到的值也钉一下（避免上一条是空转）。 */
    @Test
    fun scannedWavDurations_areParsedNotZero() = runTest {
        DeviceMusicScanner.setScanDirectories(listOf(root))

        val long = DeviceMusicScanner.scanMusic().first { File(it.path).name == "long.wav" }

        assertTrue(
            "JAudioTagger 要是读不出时长，上面那条断言就失去意义了（实际读到 ${long.duration}）",
            long.duration in 5_500L..6_500L,
        )
    }

    @Test
    fun theFloorComesFromTheSharedConstant_notAPlatformLocalNumber() {
        // 三端共用同一个数：这条不是形式主义 —— 修前 Android 硬编码 60_000L（一分钟），
        // 而 Desktop / iOS 根本没有下限，"同一份音乐三台机器不一样"就是这么来的。
        assertEquals(5_000L, AudioFormats.MIN_TRACK_DURATION_MS)
    }
}
