package com.hmp.data.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * X-06 / R47：`stringToPinyinSortKey` 曾是三端逐字相同的 `expect`/`actual`，
 * 合并进 commonMain 后，这一份断言在 **desktop 与 iOS 两个目标上都跑**。
 *
 * 断言只依赖"折叠成什么形状"，不硬编码具体拼音（表是从 Unihan 生成的，读音可能随表更新）——
 * 硬编码 `"周" -> "zhou"` 会把测试绑死在数据文件上，改表就红，而那并不代表行为错了。
 */
class PinyinSortKeyTest {

    @Test
    fun asciiIsLowercased() {
        assertEquals("hello", stringToPinyinSortKey("Hello"))
        assertEquals("track 01", stringToPinyinSortKey("Track 01"))
    }

    @Test
    fun hanziFoldsToLatin() {
        val key = stringToPinyinSortKey("周杰伦")
        assertTrue(key.isNotEmpty(), "汉字应有拼音键")
        assertTrue(key.all { it in 'a'..'z' }, "汉字应折叠为纯小写拉丁：$key")
    }

    @Test
    fun mixedTextKeepsLatinAndDropsPunctuation() {
        // 全角标点不是 ASCII、也不是汉字 → 丢弃
        assertEquals("ab", stringToPinyinSortKey("A，B"))
    }

    @Test
    fun everythingDroppedFallsBackToLowercase() {
        // 否则这些标题会共享同一个空键，排序时被并成一组
        assertEquals("，。", stringToPinyinSortKey("，。"))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", stringToPinyinSortKey(""))
    }
}
