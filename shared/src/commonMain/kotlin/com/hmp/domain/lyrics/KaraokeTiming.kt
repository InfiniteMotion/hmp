package com.hmp.domain.lyrics

import kotlin.math.max

/**
 * 逐字（卡拉 OK）时间片段。
 * [startMs]/[endMs] 为该片段起止毫秒时间；[endMs] 为 -1 表示"行结束"，
 * 由 [LyricsTimingGenerator] 解析为具体的行结束时刻。
 */
data class CharTiming(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/**
 * 卡拉 OK 字符焦点：当前唱到的字符位置与字符内进度（0..1）。
 * 渲染层据此绘制「当前字符放大/提亮」浮层。
 */
data class CharFocus(
    val index: Int = 0,
    val withinProgress: Float = 0f,
)

/**
 * 为普通 LRC（只有行级时间戳）生成逐字时间，并规整增强 LRC 的片段时间。
 *
 * 模拟策略（S2 行尾对齐等分）：行的有效字符在行时长的前 k 比例内匀速亮起，
 * 剩余时间整行保持高亮，符合主流播放器的卡拉 OK 观感。
 */
object LyricsTimingGenerator {

    /** 行内演唱区间占整行时长的比例 */
    const val DEFAULT_TRAILING_K = 0.8f

    /** 最后一行没有下一行时间戳时的兜底时长（毫秒） */
    const val DEFAULT_LAST_LINE_FALLBACK_MS = 10_000L

    /** 计算第 [index] 行的结束时刻：下一行时间戳，最后一行用歌曲时长/兜底时长 */
    fun lineEndMs(lines: List<LyricLineData>, index: Int, totalDurationMs: Long? = null): Long {
        if (index < 0 || index >= lines.size) return 0L
        val next = lines.getOrNull(index + 1)
        if (next != null) return next.timestamp
        val start = lines[index].timestamp
        return if (totalDurationMs != null && totalDurationMs > start) {
            minOf(totalDurationMs, start + DEFAULT_LAST_LINE_FALLBACK_MS)
        } else {
            start + DEFAULT_LAST_LINE_FALLBACK_MS
        }
    }

    /**
     * 为每行补全 [LyricLineData.charTimings] 与 [LyricLineData.translatedCharTimings]：
     * 增强 LRC 片段优先（仅规整起止），普通 LRC 走 S2 等分；译文按同一行区间等分。
     */
    fun resolve(
        lines: List<LyricLineData>,
        totalDurationMs: Long? = null,
        k: Float = DEFAULT_TRAILING_K,
    ): List<LyricLineData> {
        if (lines.isEmpty()) return emptyList()
        return lines.mapIndexed { index, line ->
            val end = lineEndMs(lines, index, totalDurationMs)
            val original = if (line.charTimings.isNotEmpty()) {
                normalize(line.charTimings, line.timestamp, end)
            } else {
                generateUniform(line.originalText, line.timestamp, end, k)
            }
            val translated = line.translatedText
                ?.let { generateUniform(it, line.timestamp, end, k) }
                ?: emptyList()
            line.copy(charTimings = original, translatedCharTimings = translated)
        }
    }

    /**
     * S2 行尾对齐等分：非空白字符在 `[lineStartMs, lineStartMs + k*(lineEndMs-lineStartMs)]`
     * 内匀速亮起，每个字符一个片段。
     */
    fun generateUniform(
        text: String,
        lineStartMs: Long,
        lineEndMs: Long,
        k: Float = DEFAULT_TRAILING_K,
    ): List<CharTiming> {
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) return emptyList()

        val lineDuration = (lineEndMs - lineStartMs).coerceAtLeast(0L)
        if (lineDuration <= 0L) {
            return listOf(CharTiming(chars.toString(), lineStartMs, lineEndMs))
        }

        val singMs = (lineDuration * k).toLong().coerceAtLeast(0L)
        if (singMs <= 0L) return emptyList()

        val count = chars.length
        return chars.mapIndexed { index, char ->
            CharTiming(
                text = char.toString(),
                startMs = lineStartMs + (singMs * index / count),
                endMs = lineStartMs + (singMs * (index + 1) / count),
            )
        }
    }

    /**
     * 规整增强 LRC 片段：将起止钳制在行区间内；无显式结束的片段
     * （endMs <= startMs，含 -1）取下一片段起点，最后一个取行结束。
     */
    internal fun normalize(
        timings: List<CharTiming>,
        lineStartMs: Long,
        lineEndMs: Long,
    ): List<CharTiming> {
        if (timings.isEmpty()) return emptyList()
        return timings.mapIndexed { index, timing ->
            val start = timing.startMs.coerceIn(lineStartMs, lineEndMs)
            val end = if (timing.endMs > timing.startMs) {
                timing.endMs.coerceIn(lineStartMs, lineEndMs)
            } else {
                val nextStart = timings.getOrNull(index + 1)?.startMs ?: lineEndMs
                nextStart.coerceIn(lineStartMs, lineEndMs)
            }
            CharTiming(timing.text, start, max(start, end))
        }
    }
}

/**
 * 计算卡拉 OK 渐变进度（0..1）。
 *
 * 片段存在时按片段起止做分段线性插值（第 i 个片段对应进度区间
 * [i/N, (i+1)/N]）；片段为空时退化为整行线性进度。进度只由时间驱动，
 * 渲染层将其映射到文本宽度，不做逐字符测量。
 */
fun findKaraokeProgress(
    charTimings: List<CharTiming>,
    lineStartMs: Long,
    lineEndMs: Long,
    position: Long,
): Float {
    if (lineEndMs <= lineStartMs) return 1f
    if (position <= lineStartMs) return 0f
    if (position >= lineEndMs) return 1f

    if (charTimings.isEmpty()) {
        return ((position - lineStartMs).toFloat() / (lineEndMs - lineStartMs)).coerceIn(0f, 1f)
    }

    val first = charTimings.first()
    if (position <= first.startMs) return 0f
    val last = charTimings.last()
    if (position >= last.endMs) return 1f

    val count = charTimings.size
    var segmentIndex = count - 1
    for (i in 0 until count) {
        val timing = charTimings[i]
        if (position < timing.startMs) break
        if (i == count - 1 || position < charTimings[i + 1].startMs) {
            segmentIndex = i
            break
        }
    }

    val timing = charTimings[segmentIndex]
    val segmentDuration = (timing.endMs - timing.startMs).coerceAtLeast(1L)
    val within = ((position - timing.startMs).toFloat() / segmentDuration).coerceIn(0f, 1f)
    return ((segmentIndex + within) / count).coerceIn(0f, 1f)
}

/**
 * 计算卡拉 OK 当前字符焦点。
 *
 * 与 [findKaraokeProgress] 共用同一时间轴：增强 LRC 按片段定位，
 * S2 等分片段（每片段一个字符）同样适用。片段为空或行区间无效时返回
 * 索引 0、进度 0（渲染层应在此情况下不放大）。
 */
fun findKaraokeCharFocus(
    charTimings: List<CharTiming>,
    lineStartMs: Long,
    lineEndMs: Long,
    position: Long,
): CharFocus {
    if (charTimings.isEmpty() || lineEndMs <= lineStartMs) return CharFocus()
    if (position <= lineStartMs) return CharFocus(0, 0f)

    val last = charTimings.last()
    if (position >= last.endMs) return CharFocus(charTimings.lastIndex, 1f)

    var segmentIndex = 0
    for (i in charTimings.indices) {
        val timing = charTimings[i]
        if (position < timing.startMs) break
        if (i == charTimings.lastIndex || position < charTimings[i + 1].startMs) {
            segmentIndex = i
            break
        }
    }

    val timing = charTimings[segmentIndex]
    val segmentDuration = (timing.endMs - timing.startMs).coerceAtLeast(1L)
    val within = ((position - timing.startMs).toFloat() / segmentDuration).coerceIn(0f, 1f)
    return CharFocus(segmentIndex, within)
}

/**
 * 将逐字片段展开为与 [text] 逐字符对齐的时间列表。
 *
 * 展开规则：按非空白字符序消费片段；S2 等分（每片段一字符）时逐字符一一对应；
 * 增强 LRC 的多字符片段内，各字符共享同一片段时间。空白字符对应 null（不参与点亮）。
 * 渲染层据此做逐字符渲染（当前字符在原节点上放大，而非叠加副本）。
 */
fun expandCharTimings(
    text: String,
    charTimings: List<CharTiming>,
): List<CharTiming?> {
    val result = arrayOfNulls<CharTiming>(text.length)
    if (charTimings.isEmpty()) return result.toList()

    var timingIndex = 0
    var remainingInSegment = 0
    for (i in text.indices) {
        if (text[i].isWhitespace()) continue
        if (timingIndex >= charTimings.size) continue
        if (remainingInSegment == 0) {
            remainingInSegment = charTimings[timingIndex].text
                .count { !it.isWhitespace() }
                .coerceAtLeast(1)
        }
        result[i] = charTimings[timingIndex]
        remainingInSegment--
        if (remainingInSegment == 0) timingIndex++
    }
    return result.toList()
}

/**
 * 逐字进度帧间外推器：在两次底层位置采样之间，用单调时钟平滑推进播放位置，
 * 使卡拉 OK 渐变以渲染帧率连续运动，而不是等低频采样阶跃。
 *
 * 用法：收到新采样时以 `(采样值, 当前帧时间)` 作为基准，之后每帧调用本函数
 * 计算外推位置；播放暂停时冻结，恢复/seek 时由调用方重置基准。
 */
object KaraokePositionInterpolator {

    /** 纳秒转毫秒 */
    private const val NANOS_PER_MILLI = 1_000_000L

    /**
     * 由上一帧基准计算当前外推位置。
     * @param lastPositionMs 最近一次采样（或上一帧外推）的位置，毫秒
     * @param lastTimestampNanos 该位置对应的帧时间戳（纳秒）
     * @param nowNanos 当前帧时间戳（纳秒）
     * @param isPlaying 是否正在播放；暂停时返回 [lastPositionMs] 原值
     * @param speed 播放速率（默认 1.0；预留变速支持）
     */
    fun interpolatePosition(
        lastPositionMs: Long,
        lastTimestampNanos: Long,
        nowNanos: Long,
        isPlaying: Boolean,
        speed: Float = 1f,
    ): Long {
        if (!isPlaying) return lastPositionMs
        val deltaNanos = (nowNanos - lastTimestampNanos).coerceAtLeast(0L)
        val deltaMs = (deltaNanos * speed.toDouble() / NANOS_PER_MILLI).toLong()
        return lastPositionMs + deltaMs
    }
}
