package com.hmp.domain.lyrics

import kotlin.math.max

data class LyricLineData(
    val timestamp: Long,
    val originalText: String,
    val translatedText: String? = null,
    /** 原文逐字（卡拉 OK）时间片段；普通 LRC 为空，由 LyricsTimingGenerator 生成 */
    val charTimings: List<CharTiming> = emptyList(),
    /** 译文逐字时间片段；普通 LRC 为空，由 LyricsTimingGenerator 按同一行区间生成 */
    val translatedCharTimings: List<CharTiming> = emptyList(),
)

fun findCurrentLyricIndex(parsedLyrics: List<LyricLineData>, currentPosition: Long): Int {
    if (parsedLyrics.isEmpty()) return -1
    return parsedLyrics.binarySearch { it.timestamp.compareTo(currentPosition) }.let { index ->
        if (index >= 0) index else max(0, -index - 2)
    }.coerceIn(0, parsedLyrics.size - 1)
}

object LrcParser {

    private val timeRegex = """^\[(\d{2}):(\d{2})\.(\d{2,3})]""".toRegex()

    /** 非锚定版本：用于逐段吃行首连续时间戳（`[t1][t2]text`）。 */
    private val anyTimeRegex = """\[(\d{2}):(\d{2})\.(\d{2,3})]""".toRegex()
    private val inlineTimeRegex =
        """<(\d{2}):(\d{2})\.(\d{2,3})(?:,(\d{2}):(\d{2})\.(\d{2,3}))?>""".toRegex()

    /** 行内原始内容：文本 + 可能携带的逐字时间片段 */
    private data class RawLine(val text: String, val charTimings: List<CharTiming>)

    fun parse(lrcText: String): List<LyricLineData> {
        if (lrcText.isBlank()) return emptyList()

        val lines = lrcText.lines()
        val groupedLines = mutableMapOf<Long, MutableList<RawLine>>()

        lines.forEach { line ->
            // D4-02：标准 LRC 允许 `[00:12.00][00:40.00]hello` —— 同一句在多个时间点重复出现。
            // 旧实现只取第一个时间戳，第二个留在正文里被当成逐字标记，于是 40s 那遍整体丢失、
            // 12s 那遍的逐字时序还错乱。这里把行首连续的时间戳逐个展开成独立条目。
            val timestamps = leadingTimestamps(line)
            if (timestamps.isEmpty()) return@forEach
            val content = line.substring(timestamps.last().second).trim()
            if (content.isEmpty()) return@forEach

            timestamps.forEach { (timestamp, _) ->
                groupedLines.getOrPut(timestamp) { mutableListOf() }
                    .add(parseTimedContent(content, timestamp))
            }
        }

        val parsedLines = mutableListOf<LyricLineData>()
        groupedLines.entries.sortedBy { it.key }.forEach { (timestamp, lineGroup) ->
            if (lineGroup.any { it.charTimings.isNotEmpty() }) {
                // 增强 LRC：拼接片段文本并保留逐字时间戳，不做双语拆分
                val text = lineGroup.joinToString(" ") { it.text }.trim()
                val timings = lineGroup.flatMap { it.charTimings }
                parsedLines.add(LyricLineData(timestamp, text, null, timings))
            } else {
                when (lineGroup.size) {
                    1 -> {
                        val line = lineGroup[0]
                        val result = DualLanguageAnalyzer.analyzeSingleLine(line.text)
                        parsedLines.add(LyricLineData(timestamp, result.first, result.second))
                    }

                    2 -> {
                        val result = DualLanguageAnalyzer.analyzeDualLines(lineGroup[0].text, lineGroup[1].text)
                        parsedLines.add(LyricLineData(timestamp, result.first, result.second))
                    }

                    else -> {
                        val result = MultiLineProcessor.extractBestPair(lineGroup.map { it.text })
                        parsedLines.add(LyricLineData(timestamp, result.first, result.second))
                    }
                }
            }
        }

        return parsedLines
    }

    private fun parseTime(minutes: String, seconds: String, millis: String): Long = minutes.toLong() * 60000 +
        seconds.toLong() * 1000 +
        millis.padEnd(3, '0').take(3).toLong()

    /**
     * 行首连续的 `[mm:ss.xx]` 前缀，返回 `(时间戳, 该前缀之后的字符下标)`。
     * 只吃**从行首开始、紧邻无空隙**的那一段；正文里出现的 `[..]` 交给 [parseTimedContent]。
     */
    private fun leadingTimestamps(line: String): List<Pair<Long, Int>> {
        val result = mutableListOf<Pair<Long, Int>>()
        var cursor = 0
        while (cursor < line.length) {
            val match = anyTimeRegex.find(line, cursor)
            if (match == null || match.range.first != cursor) break
            val timestamp = parseTime(
                minutes = match.groupValues[1],
                seconds = match.groupValues[2],
                millis = match.groupValues[3],
            )
            val next = match.range.last + 1
            result.add(timestamp to next)
            cursor = next
        }
        return result
    }

    /**
     * 解析可能包含逐字时间戳的内容。支持：
     *  - `[mm:ss.xx]<mm:ss.xx>片段`（网易云/QQ 导出）
     *  - `[mm:ss.xx]<mm:ss.xx,mm:ss.xx>片段`（A2 显式区间）
     *  - `[mm:ss.xx][mm:ss.xx]片段`（双时间戳）
     *  - `[mm:ss.xx]片段<mm:ss.xx>`（结尾时间戳作为上一片段结束）
     * 无任何内联时间戳时返回普通文本（charTimings 为空）。
     */
    private fun parseTimedContent(content: String, lineTimestamp: Long): RawLine {
        val tokens = tokenize(content)
        if (tokens.none { it.isMarker }) return RawLine(content, emptyList())

        val segments = mutableListOf<CharTiming>()
        val textBuffer = StringBuilder()
        var segStart: Long? = null
        var segEnd: Long? = null

        fun flush(endMs: Long) {
            if (segStart != null) {
                val text = textBuffer.toString()
                if (text.isNotEmpty()) {
                    segments.add(CharTiming(text, segStart!!, segEnd ?: endMs))
                }
                textBuffer.clear()
                segStart = null
                segEnd = null
            }
        }

        tokens.forEach { token ->
            if (token.isMarker) {
                if (segStart == null) {
                    if (textBuffer.isNotEmpty()) {
                        // 标记前的文本属于从行时间戳开始的片段，此标记作为其结束
                        segments.add(CharTiming(textBuffer.toString(), lineTimestamp, token.startMs))
                        textBuffer.clear()
                    }
                    segStart = token.startMs
                    segEnd = token.endMs
                } else {
                    flush(token.startMs)
                    segStart = token.startMs
                    segEnd = token.endMs
                }
            } else {
                textBuffer.append(token.text)
            }
        }

        if (segStart != null && textBuffer.isNotEmpty()) {
            segments.add(CharTiming(textBuffer.toString(), segStart!!, segEnd ?: -1L))
        }

        if (segments.isEmpty()) return RawLine(content, emptyList())
        return RawLine(segments.joinToString("") { it.text }.trim(), segments)
    }

    private data class Token(
        val isMarker: Boolean,
        val text: String = "",
        val startMs: Long = 0L,
        val endMs: Long? = null,
    )

    private fun tokenize(content: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val text = StringBuilder()
        var i = 0

        fun flushText() {
            if (text.isNotEmpty()) {
                tokens.add(Token(isMarker = false, text = text.toString()))
                text.clear()
            }
        }

        while (i < content.length) {
            val c = content[i]
            if (c == '<') {
                val match = inlineTimeRegex.find(content, i)
                if (match != null && match.range.first == i) {
                    flushText()
                    val groups = match.destructured
                    val start = parseTime(groups.component1(), groups.component2(), groups.component3())
                    val end = if (groups.component4().isNotEmpty()) {
                        parseTime(groups.component4(), groups.component5(), groups.component6())
                    } else {
                        null
                    }
                    tokens.add(Token(isMarker = true, startMs = start, endMs = end))
                    i = match.range.last + 1
                    continue
                }
            } else if (c == '[') {
                val match = timeRegex.find(content, i)
                if (match != null && match.range.first == i) {
                    flushText()
                    tokens.add(
                        Token(
                            isMarker = true,
                            startMs = parseTime(
                                minutes = match.groupValues[1],
                                seconds = match.groupValues[2],
                                millis = match.groupValues[3],
                            ),
                        ),
                    )
                    i = match.range.last + 1
                    continue
                }
            }
            text.append(c)
            i++
        }
        flushText()
        return tokens
    }
}

object DualLanguageAnalyzer {

    fun analyzeSingleLine(text: String): Pair<String, String?> {
        if (!mayContainDualLanguages(text)) return Pair(text, null)
        return splitTwoScriptSegments(text) ?: Pair(text, null)
    }

    fun analyzeDualLines(text1: String, text2: String): Pair<String, String?> {
        val lang1 = detectDominantLanguage(text1)
        val lang2 = detectDominantLanguage(text2)

        if (lang1 != lang2 && lang1 != Language.UNKNOWN && lang2 != Language.UNKNOWN) {
            return if (lang1 == Language.ENGLISH) {
                Pair(text1, text2)
            } else if (lang2 == Language.ENGLISH) {
                Pair(text2, text1)
            } else {
                if (text1 < text2) Pair(text1, text2) else Pair(text2, text1)
            }
        }

        return Pair("$text1 $text2", null)
    }

    private fun mayContainDualLanguages(text: String): Boolean {
        val chineseCount = text.count { it.code in 0x4E00..0x9FFF }
        val englishCount = text.count { it.isLetter() && it.code < 128 }
        val japaneseCount = text.count { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF }
        val koreanCount = text.count { it.code in 0xAC00..0xD7AF }
        val cyrillicCount = text.count { it.code in 0x0400..0x04FF }
        val languageCounts = listOf(chineseCount, englishCount, japaneseCount, koreanCount, cyrillicCount)
        return languageCounts.count { it > 0 } >= 2
    }

    /**
     * 只有"两种文字各占一段、且恰好一次切换"才拆（D4-05）。
     *
     * 旧实现按字符过滤重组：`"Hello, 你好! 2026"` 会变成 `("Hello", "你好")` —— 数字和标点被静默丢掉；
     * `"今天 weather 很好"` 这种交错混排也会被拆成两半、语序丢失。现在这两种都返回 `null`（不拆），
     * 因为"猜错"比"不拆"更伤：错拆会把原文/译文弄反，还会让用户看到少字的歌词。
     */
    private fun splitTwoScriptSegments(text: String): Pair<String, String?>? {
        val trimmed = text.trim()
        val firstCjk = trimmed.indexOfFirst { it.isCjk() }
        val firstLatin = trimmed.indexOfFirst { it.isLatinLetter() }
        if (firstCjk < 0 || firstLatin < 0) return null

        return if (firstCjk < firstLatin) {
            val boundary = indexOfFirstFrom(trimmed, firstCjk) { it.isLatinLetter() }
            if (boundary <= 0) return null
            val tail = trimmed.substring(boundary)
            if (tail.any { it.isCjk() }) return null
            val cjk = trimmed.substring(0, boundary).trim()
            val latin = tail.trim()
            if (cjk.isEmpty() || latin.isEmpty()) null else Pair(latin, cjk)
        } else {
            val boundary = indexOfFirstFrom(trimmed, firstLatin) { it.isCjk() }
            if (boundary <= 0) return null
            val tail = trimmed.substring(boundary)
            if (tail.any { it.isLatinLetter() }) return null
            val latin = trimmed.substring(0, boundary).trim()
            val cjk = tail.trim()
            if (latin.isEmpty() || cjk.isEmpty()) null else Pair(latin, cjk)
        }
    }

    private fun indexOfFirstFrom(text: String, start: Int, predicate: (Char) -> Boolean): Int {
        for (i in start until text.length) if (predicate(text[i])) return i
        return -1
    }

    private fun Char.isCjk(): Boolean = code in 0x4E00..0x9FFF
    private fun Char.isLatinLetter(): Boolean = isLetter() && code < 128

    fun detectDominantLanguage(text: String): Language {
        val chineseChars = text.count { it.code in 0x4E00..0x9FFF }
        val englishChars = text.count { it.isLetter() && it.code < 128 }
        val japaneseChars = text.count { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF }
        val koreanChars = text.count { it.code in 0xAC00..0xD7AF }
        val cyrillicChars = text.count { it.code in 0x0400..0x04FF }
        val totalChars = chineseChars + englishChars + japaneseChars + koreanChars + cyrillicChars
        if (totalChars == 0) return Language.UNKNOWN

        val chineseRatio = chineseChars.toFloat() / totalChars
        val englishRatio = englishChars.toFloat() / totalChars
        val japaneseRatio = japaneseChars.toFloat() / totalChars
        val koreanRatio = koreanChars.toFloat() / totalChars
        val cyrillicRatio = cyrillicChars.toFloat() / totalChars

        return when {
            chineseRatio > 0.6 -> Language.CHINESE
            englishRatio > 0.6 -> Language.ENGLISH
            japaneseRatio > 0.6 -> Language.JAPANESE
            koreanRatio > 0.6 -> Language.KOREAN
            cyrillicRatio > 0.6 -> Language.RUSSIAN
            else -> Language.MIXED
        }
    }
}

object MultiLineProcessor {

    fun extractBestPair(lines: List<String>): Pair<String, String?> {
        if (lines.size < 2) return Pair(lines.firstOrNull() ?: "", null)

        val firstLine = lines.first()
        val lastLine = lines.last()

        if (firstLine != lastLine) {
            val result = DualLanguageAnalyzer.analyzeDualLines(firstLine, lastLine)
            if (result.second != null) return result
        }

        for (i in lines.indices) {
            for (j in i + 1 until lines.size) {
                val result = DualLanguageAnalyzer.analyzeDualLines(lines[i], lines[j])
                if (result.second != null) return result
            }
        }

        return Pair(lines.joinToString(" "), null)
    }
}

enum class Language {
    ENGLISH,
    CHINESE,
    JAPANESE,
    KOREAN,
    RUSSIAN,
    MIXED,
    UNKNOWN,
}
