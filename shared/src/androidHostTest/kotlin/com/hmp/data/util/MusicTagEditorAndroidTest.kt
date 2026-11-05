package com.hmp.data.util

import android.content.Context
import com.hmp.domain.music.EditableMusicTags
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * X-06：`MusicTagEditor.android`（190 行）此前零测试。
 *
 * 桌面侧同判据已有 `MusicTagEditorTest`（`:shared:desktopTest`，6 例，WAV 夹具）。
 * Android 份在文件路径分支上多三件事：`init(context)` 门卫、`canWrite()` 检查
 * （分区存储下不可写要给 [NeedsStorageAccessException]）、写完调 MediaScanner 刷新索引。
 * 这三条正是 Android 独有的出错面，这里逐条钉住。
 *
 * 夹具沿用 desktop 那份最小 WAV（44 字节头 + 100 字节静默）：jaudiotagger 对 `.wav`
 * 支持标签读写，桌面 6 例已证明这套夹具可用，不在这里再造一份。
 *
 * 没测的（如实记）：
 * - **`writeTags(Uri, …)` SAF 重载**：Robolectric 的 `ContentResolver.update` 对非 content://
 *   Uri 会直接抛，模拟 SAF 授权流要起一个完整 provider，超出本批收益；
 * - **「未 init 就调用」**：`appContext` 是 object 上的静态位且无重置入口，用例执行顺序
 *   不保证（同类内其他用例会先 init），断言"未初始化失败"不可靠 —— 该分支只有 4 行，风险可接受。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicTagEditorAndroidTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        MusicTagEditor.init(context)
        tempDir = kotlin.io.path.createTempDirectory("music_tag_editor_android_test-").toFile()
    }

    @After
    fun teardown() {
        tempDir.walkBottomUp().forEach { it.setWritable(true) } // 只读用例不改回来会删不掉
        tempDir.deleteRecursively()
    }

    private fun createMinimalWavFile(file: File) {
        val sampleRate = 44100
        val channels = 1
        val bitsPerSample = 16
        val dataLength = 100
        val totalLength = 44 + dataLength

        fun intLe(value: Int) = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        )
        fun shortLe(value: Int) = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

        file.outputStream().buffered().use { out ->
            out.write("RIFF".toByteArray())
            out.write(intLe(totalLength - 8))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intLe(16))
            out.write(shortLe(1))
            out.write(shortLe(channels))
            out.write(intLe(sampleRate))
            out.write(intLe(sampleRate * channels * bitsPerSample / 8))
            out.write(shortLe(channels * bitsPerSample / 8))
            out.write(shortLe(bitsPerSample))
            out.write("data".toByteArray())
            out.write(intLe(dataLength))
            out.write(ByteArray(dataLength))
        }
    }

    private fun readTag(file: File) = AudioFileIO.read(file).tag

    /** 主路径：文本标签落进文件，读回来字段一致（这条同时证明 init 门卫放行、canWrite 通过、MediaScanner 不抛）。 */
    @Test
    fun writeTags_persistsTitleArtistAlbumAndLyrics() {
        val wav = File(tempDir, "song.wav")
        createMinimalWavFile(wav)

        val result = MusicTagEditor.writeTags(
            wav.absolutePath,
            EditableMusicTags(
                title = "新标题",
                artist = "新歌手",
                album = "新专辑",
                lyrics = "渐暗的舞台",
            ),
        )

        assertTrue(result.isSuccess, "写入失败：${result.exceptionOrNull()}")
        val tag = readTag(wav)
        assertEquals("新标题", tag.getFirst(FieldKey.TITLE))
        assertEquals("新歌手", tag.getFirst(FieldKey.ARTIST))
        assertEquals("新专辑", tag.getFirst(FieldKey.ALBUM))
        assertEquals("渐暗的舞台", tag.getFirst(FieldKey.LYRICS))
    }

    /** 文件不存在 → Result.failure，不许抛出（调用方按 Result 分支提示用户）。 */
    @Test
    fun writeTags_missingFile_failsWithResult() {
        val missing = File(tempDir, "ghost.wav")

        val result = MusicTagEditor.writeTags(missing.absolutePath, EditableMusicTags(title = "x"))

        assertTrue(result.isFailure)
    }

    /** 分区存储的核心分支：文件只读（≈ 无直接写权限）必须给 [NeedsStorageAccessException]，而不是半写半不写。 */
    @Test
    fun writeTags_readOnlyFile_failsWithNeedsStorageAccess() {
        val wav = File(tempDir, "readonly.wav")
        createMinimalWavFile(wav)
        assertTrue(wav.setReadOnly(), "测试前置：setReadOnly 应成功")

        val result = MusicTagEditor.writeTags(wav.absolutePath, EditableMusicTags(title = "x"))

        assertTrue(result.isFailure)
        assertFailsWith<NeedsStorageAccessException> { result.getOrThrow() }
        // 文件内容不能被半途改坏：标签解析仍应能读
        assertNotNull(AudioFileIO.read(wav).audioHeader)
    }

    /** 封面替换：PNG 字节进去，读回来的 artwork 是同一批字节（PNG 魔数分支由 detectImageMimeType 走到）。 */
    @Test
    fun writeTags_albumArt_pngBytes_becomesReadableArtwork() {
        val wav = File(tempDir, "cover.wav")
        createMinimalWavFile(wav)
        val png = pngBytes()

        val result = MusicTagEditor.writeTags(wav.absolutePath, EditableMusicTags(albumArt = png))

        assertTrue(result.isSuccess, "写入失败：${result.exceptionOrNull()}")
        val artwork = readTag(wav).firstArtwork
        assertNotNull(artwork, "封面应已写入")
        assertTrue(artwork.binaryData.contentEquals(png), "读回的封面字节应与写入一致")
        assertEquals("image/png", artwork.mimeType)
    }

    private fun pngBytes(): ByteArray {
        // 最小 PNG 魔数 + 若干填充：detectImageMimeType 只看前 4 字节
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(32)
        val out = ByteArrayOutputStream()
        out.write(bytes)
        return out.toByteArray()
    }
}
