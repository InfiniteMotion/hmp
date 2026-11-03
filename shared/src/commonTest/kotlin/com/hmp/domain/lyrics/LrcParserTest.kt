package com.hmp.domain.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LrcParserTest {

    @Test
    fun parse_emptyInput_returnsEmptyList() {
        val result = LrcParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parse_blankInput_returnsEmptyList() {
        val result = LrcParser.parse("   \n  \n  ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parse_singleLine_parsesCorrectly() {
        val lrc = "[00:12.34]Hello World"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals(12340L, result[0].timestamp)
        assertEquals("Hello World", result[0].originalText)
    }

    @Test
    fun parse_millisTwoDigits_parsesCorrectly() {
        val lrc = "[00:05.50]Test"
        val result = LrcParser.parse(lrc)
        assertEquals(5500L, result[0].timestamp)
    }

    @Test
    fun parse_millisThreeDigits_parsesCorrectly() {
        val lrc = "[00:05.500]Test"
        val result = LrcParser.parse(lrc)
        assertEquals(5500L, result[0].timestamp)
    }

    @Test
    fun parse_multipleLines_sortedByTimestamp() {
        val lrc = "[00:10.00]Second\n[00:05.00]First\n[00:15.00]Third"
        val result = LrcParser.parse(lrc)
        assertEquals(3, result.size)
        assertEquals("First", result[0].originalText)
        assertEquals("Second", result[1].originalText)
        assertEquals("Third", result[2].originalText)
    }

    @Test
    fun parse_linesWithSameTimestamp_grouped() {
        val lrc = "[00:10.00]English Line\n[00:10.00]中文歌词"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals(10000L, result[0].timestamp)
    }

    @Test
    fun parse_translatedLine_setsTranslatedText() {
        val lrc = "[00:10.00]Hello World\n[00:10.00]你好世界"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals("Hello World", result[0].originalText)
        assertEquals("你好世界", result[0].translatedText)
    }

    @Test
    fun parse_emptyContentLine_skipped() {
        val lrc = "[00:05.00]\n[00:10.00]Valid"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals("Valid", result[0].originalText)
    }

    @Test
    fun parse_metadataTags_ignored() {
        val lrc = "[ti:Song Title]\n[ar:Artist]\n[00:05.00]Lyrics"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals("Lyrics", result[0].originalText)
    }

    @Test
    fun parse_longTimestamp_parsesCorrectly() {
        val lrc = "[05:30.00]Five thirty"
        val result = LrcParser.parse(lrc)
        assertEquals(330000L, result[0].timestamp)
    }

    @Test
    fun parse_enhancedNetEaseStyle_buildsCharTimings() {
        val lrc = "[00:12.34]<00:12.34>我 <00:12.78>爱"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals(12340L, result[0].timestamp)
        assertEquals("我 爱", result[0].originalText)
        assertEquals(2, result[0].charTimings.size)
        assertEquals(12340L, result[0].charTimings[0].startMs)
        assertEquals(12780L, result[0].charTimings[0].endMs)
        assertEquals("我 ", result[0].charTimings[0].text)
        assertEquals(12780L, result[0].charTimings[1].startMs)
        assertEquals(-1L, result[0].charTimings[1].endMs)
    }

    @Test
    fun parse_enhancedA2Range_usesExplicitEnd() {
        val lrc = "[00:12.34]<00:12.34,00:12.78>我爱你<00:13.00>"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals("我爱你", result[0].originalText)
        assertEquals(1, result[0].charTimings.size)
        assertEquals(12340L, result[0].charTimings[0].startMs)
        assertEquals(12780L, result[0].charTimings[0].endMs)
    }

    @Test
    fun parse_doubleTimestampPrefix_isRepeatNotCharTiming() {
        // D4-02：行首连续时间戳是标准 LRC 的"同一句重复出现"，不是逐字结束标记。
        // 旧实现把它当逐字标记，第二段时间点整体丢失；逐字时序请用 `<mm:ss.xx>`。
        val lrc = "[00:12.34][00:12.78]我 爱"
        val result = LrcParser.parse(lrc)
        assertEquals(2, result.size)
        assertEquals(12340L, result[0].timestamp)
        assertEquals(12780L, result[1].timestamp)
        assertEquals("我 爱", result[0].originalText)
        assertEquals("我 爱", result[1].originalText)
        assertTrue(result[0].charTimings.isEmpty())
        assertTrue(result[1].charTimings.isEmpty())
    }

    @Test
    fun parse_repeatedTimestamps_expandsEveryTimestamp() {
        val lrc = "[00:12.00][00:40.00]hello"
        val result = LrcParser.parse(lrc)
        assertEquals(2, result.size)
        assertEquals(12000L, result[0].timestamp)
        assertEquals(40000L, result[1].timestamp)
        assertEquals("hello", result[0].originalText)
        assertEquals("hello", result[1].originalText)
        assertTrue(result[0].charTimings.isEmpty())
        assertTrue(result[1].charTimings.isEmpty())
    }

    @Test
    fun parse_repeatedTimestamps_stillSupportsInlineCharMarkers() {
        // 重复时间戳展开后，正文里的 `<...>` 逐字标记仍按原逻辑解析
        val lrc = "[00:12.00][00:40.00]<00:12.00>你<00:12.50>好"
        val result = LrcParser.parse(lrc)
        assertEquals(2, result.size)
        assertEquals("你好", result[0].originalText)
        assertEquals(40000L, result[1].timestamp)
        assertEquals(2, result[0].charTimings.size)
        assertEquals(2, result[1].charTimings.size)
    }

    @Test
    fun parse_enhancedTrailingEndTimestamp_closesPreviousSegment() {
        val lrc = "[00:12.34]我爱你<00:12.78>"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.size)
        assertEquals("我爱你", result[0].originalText)
        assertEquals(1, result[0].charTimings.size)
        assertEquals(12340L, result[0].charTimings[0].startMs)
        assertEquals(12780L, result[0].charTimings[0].endMs)
    }

    @Test
    fun parse_plainLine_hasEmptyCharTimings() {
        val lrc = "[00:12.34]Hello World"
        val result = LrcParser.parse(lrc)
        assertTrue(result[0].charTimings.isEmpty())
        assertTrue(result[0].translatedCharTimings.isEmpty())
    }

    @Test
    fun parse_enhancedWithMalformedMarker_fallsBackToPlainText() {
        val lrc = "[00:12.34]<12:34>bad marker"
        val result = LrcParser.parse(lrc)
        assertEquals(1, result.size)
        assertTrue(result[0].charTimings.isEmpty())
    }
}

class FindCurrentLyricIndexTest {

    @Test
    fun emptyList_returnsNegativeOne() {
        val result = findCurrentLyricIndex(emptyList(), 5000L)
        assertEquals(-1, result)
    }

    @Test
    fun exactMatch_returnsCorrectIndex() {
        val lyrics = listOf(
            LyricLineData(1000L, "Line 1"),
            LyricLineData(5000L, "Line 2"),
            LyricLineData(10000L, "Line 3")
        )
        val result = findCurrentLyricIndex(lyrics, 5000L)
        assertEquals(1, result)
    }

    @Test
    fun betweenTimestamps_returnsPreviousIndex() {
        val lyrics = listOf(
            LyricLineData(1000L, "Line 1"),
            LyricLineData(5000L, "Line 2"),
            LyricLineData(10000L, "Line 3")
        )
        val result = findCurrentLyricIndex(lyrics, 7000L)
        assertEquals(1, result)
    }

    @Test
    fun beforeFirstTimestamp_returnsZero() {
        val lyrics = listOf(
            LyricLineData(1000L, "Line 1"),
            LyricLineData(5000L, "Line 2")
        )
        val result = findCurrentLyricIndex(lyrics, 500L)
        assertEquals(0, result)
    }

    @Test
    fun afterLastTimestamp_returnsLastIndex() {
        val lyrics = listOf(
            LyricLineData(1000L, "Line 1"),
            LyricLineData(5000L, "Line 2")
        )
        val result = findCurrentLyricIndex(lyrics, 99999L)
        assertEquals(1, result)
    }

    @Test
    fun singleLyric_alwaysReturnsZero() {
        val lyrics = listOf(LyricLineData(5000L, "Only line"))
        assertEquals(0, findCurrentLyricIndex(lyrics, 1000L))
        assertEquals(0, findCurrentLyricIndex(lyrics, 5000L))
        assertEquals(0, findCurrentLyricIndex(lyrics, 99999L))
    }
}

class DualLanguageAnalyzerTest {

    @Test
    fun analyzeSingleLine_pureEnglish_noTranslation() {
        val result = DualLanguageAnalyzer.analyzeSingleLine("Hello World")
        assertEquals("Hello World", result.first)
        assertNull(result.second)
    }

    @Test
    fun analyzeSingleLine_pureChinese_noTranslation() {
        val result = DualLanguageAnalyzer.analyzeSingleLine("你好世界")
        assertEquals("你好世界", result.first)
        assertNull(result.second)
    }

    /**
     * D4-05：旧实现按字符过滤重组，数字与标点会被静默丢掉（2026 直接消失）。
     * 现在按"段"切，两侧原文原样保留。
     */
    @Test
    fun analyzeSingleLine_splitKeepsDigitsAndPunctuation() {
        val result = DualLanguageAnalyzer.analyzeSingleLine("Hello, world! 你好，世界 2026")
        assertEquals("Hello, world!", result.first)
        assertEquals("你好，世界 2026", result.second)
    }

    /** 交错混排不是"原文 + 译文"，拆了会丢语序 —— 一律不拆。 */
    @Test
    fun analyzeSingleLine_interleavedScripts_notSplit() {
        val result = DualLanguageAnalyzer.analyzeSingleLine("今天 weather 很好")
        assertEquals("今天 weather 很好", result.first)
        assertNull(result.second)
    }

    @Test
    fun analyzeSingleLine_chineseFirst_englishStillOriginal() {
        val result = DualLanguageAnalyzer.analyzeSingleLine("你好，世界 Hello, world!")
        assertEquals("Hello, world!", result.first)
        assertEquals("你好，世界", result.second)
    }

    @Test
    fun analyzeDualLines_englishAndChinese_separates() {
        val result = DualLanguageAnalyzer.analyzeDualLines("Hello World", "你好世界")
        assertEquals("Hello World", result.first)
        assertEquals("你好世界", result.second)
    }

    @Test
    fun analyzeDualLines_chineseAndEnglish_separates() {
        val result = DualLanguageAnalyzer.analyzeDualLines("你好世界", "Hello World")
        assertEquals("Hello World", result.first)
        assertEquals("你好世界", result.second)
    }

    @Test
    fun analyzeDualLines_sameLanguage_merged() {
        val result = DualLanguageAnalyzer.analyzeDualLines("Hello", "World")
        assertEquals("Hello World", result.first)
        assertNull(result.second)
    }

    @Test
    fun detectDominantLanguage_pureEnglish_returnsEnglish() {
        val result = DualLanguageAnalyzer.detectDominantLanguage("Hello World Test")
        assertEquals(Language.ENGLISH, result)
    }

    @Test
    fun detectDominantLanguage_pureChinese_returnsChinese() {
        val result = DualLanguageAnalyzer.detectDominantLanguage("你好世界测试")
        assertEquals(Language.CHINESE, result)
    }

    @Test
    fun detectDominantLanguage_empty_returnsUnknown() {
        val result = DualLanguageAnalyzer.detectDominantLanguage("")
        assertEquals(Language.UNKNOWN, result)
    }

    @Test
    fun detectDominantLanguage_mixed_returnsMixed() {
        val result = DualLanguageAnalyzer.detectDominantLanguage("Hello 你好世界测试")
        assertEquals(Language.MIXED, result)
    }
}

class MultiLineProcessorTest {

    @Test
    fun extractBestPair_emptyList_returnsEmpty() {
        val result = MultiLineProcessor.extractBestPair(emptyList())
        assertEquals("", result.first)
        assertNull(result.second)
    }

    @Test
    fun extractBestPair_singleLine_returnsLine() {
        val result = MultiLineProcessor.extractBestPair(listOf("Hello"))
        assertEquals("Hello", result.first)
        assertNull(result.second)
    }

    @Test
    fun extractBestPair_twoDifferentLines_analyzes() {
        val result = MultiLineProcessor.extractBestPair(listOf("Hello World", "你好世界"))
        assertEquals("Hello World", result.first)
        assertEquals("你好世界", result.second)
    }

    @Test
    fun extractBestPair_threeLines_findsBestPair() {
        val lines = listOf("Hello World", "你好世界", "Additional")
        val result = MultiLineProcessor.extractBestPair(lines)
        // Should find the English/Chinese pair
        assertTrue(result.second != null || result.first.contains("Hello"))
    }
}
