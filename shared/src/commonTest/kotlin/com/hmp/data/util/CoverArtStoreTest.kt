package com.hmp.data.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * D2-16 / D2-17：封面命名规则收成一份之后的两条口径钉住。
 *
 * ① **文件名来自封面字节的内容指纹**，不是音频文件的路径 ——
 *    修前是"路径 hash + 存在即跳过"，于是换了封面文件名也不变、写被跳过，
 *    用户保存成功却永远看到旧图（D2-17）。
 * ② **渲染是无符号十六进制**，不是 `toLong()` ——
 *    修前 Kotlin 侧 `hash.toLong()`（可为负，`cover_-123.jpg`）与 Swift 侧 `abs(...)`（恒非负）
 *    是同一条规则的两份实现、两种符号处理（D2-16）。这里用无符号渲染让这类分歧在源头消失。
 *
 * 另外钉住 [stableIdFor] 仍是**有符号 Long**：那个数已经是三端真实库里的 `music.id`，
 * 改渲染方式等于给全部曲目换主键（那是数据迁移，不是命名规则）。
 * 两个具体值都是实测的 FNV-1a 64 位结果，改动实现就会红。
 */
class CoverArtStoreTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun coverFileName_isContentFingerprint_notPathHash() {
        val a = bytes(1, 2, 3)
        val b = bytes(1, 2, 4)

        assertEquals("cover_d0aa6218672cf5ab.jpg", CoverArtStore.fileNameFor(a))
        // 同一个音频文件的两版封面 → 两个名字（修前同名，于是"存在即跳过"把新版吞掉）
        assertNotEquals(CoverArtStore.fileNameFor(b), CoverArtStore.fileNameFor(a))
        // 同一份字节重复算 → 同名，落盘因此是幂等的
        assertEquals(CoverArtStore.fileNameFor(a), CoverArtStore.fileNameFor(bytes(1, 2, 3)))
    }

    @Test
    fun coverFileName_isUnsignedSoNoSignDivergence() {
        // 找一个 FNV 高位为 1 的字节序列：按有符号渲染就是负数（`-` 出现在文件名里）
        val highBitSample = (0..4096).asSequence()
            .map { bytes(it, it + 1, it + 2, it + 3) }
            .first { fnv1a64(it) >= 0x8000000000000000UL }

        val name = CoverArtStore.fileNameFor(highBitSample)
        assertTrue(fnv1a64(highBitSample).toLong() < 0, "这条样本的前提是有符号后为负")
        assertFalse('-' in name, "文件名不得带符号 —— 修前两端一个带、一个 abs 掉，就分叉了：$name")
        assertTrue(name.startsWith("cover_") && name.endsWith(".jpg"))
    }

    @Test
    fun stableId_keepsTheSignedLongAlreadyStoredInRealLibraries() {
        // 实测值：这三条路径的 id 已经在真实库里，改渲染 = 改主键
        assertEquals(-3595404055474716728L, stableIdFor("/Music/b.mp3"))
        assertEquals(2880582060820965013L, stableIdFor("/Music/a.mp3"))
        assertEquals(8412373389730265186L, stableIdFor("/x/y/keep.mp3"))
    }
}
