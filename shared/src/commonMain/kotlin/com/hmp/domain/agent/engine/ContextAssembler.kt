package com.hmp.domain.agent.engine

/**
 * R-T1 首轮上下文注入装配器——把「第一次对话时该注入 agent 的内容」格式化成系统提示片段。
 *
 * 纯格式化、无副作用、可测。数据由接缝侧（gateway）从 repository / now-playing provider /
 * TimeProvider 取到后喂入；本对象只负责拼装（总纲 2.4「记忆 ≠ 上下文」的装配纪律）。
 */

/** 曲库概况（概览法世界模型，总纲 2.4 层 2）。 */
data class LibraryOverview(
    val totalCount: Int,
    /** 语言 → 数量。 */
    val languageDistribution: Map<String, Int> = emptyMap(),
    /** 流派 → 数量（label top，前 N）。 */
    val topGenres: List<Pair<String, Int>> = emptyList(),
    /** 年代（如「80s」「90s」「2010s」）→ 数量。 */
    val eraDistribution: Map<String, Int> = emptyMap(),
    /** 常听歌 ""title · artist"" 清单。 */
    val topPlayedSongs: List<Pair<String, String>> = emptyList(),
)

object ContextAssembler {

    /** 曲库概况（概览法）：规模 / 语言 / 流派 top / 年代 / 常听。 */
    fun buildLibraryOverview(
        o: LibraryOverview,
        maxGenres: Int = 5,
        maxSongs: Int = 5,
        maxChars: Int = EngineDefaults.MAX_LIBRARY_LIST_CHARS,
    ): String {
        val sb = StringBuilder()
        sb.append("曲库共 ${o.totalCount} 首。")
        if (o.languageDistribution.isNotEmpty()) {
            sb.append(" 语言分布：")
            sb.append(o.languageDistribution.entries.joinToString("、") { "${it.key} ${it.value} 首" })
            sb.append("。")
        }
        if (o.topGenres.isNotEmpty()) {
            sb.append(" 流派 top${o.topGenres.size}：")
            sb.append(o.topGenres.joinToString("、") { "${it.first}(${it.second})" })
            sb.append("。")
        }
        if (o.eraDistribution.isNotEmpty()) {
            sb.append(" 年代分布：")
            sb.append(o.eraDistribution.entries.joinToString("、") { "${it.key} ${it.value} 首" })
            sb.append("。")
        }
        if (o.topPlayedSongs.isNotEmpty()) {
            sb.append(" 常听（top${o.topPlayedSongs.size}）：")
            sb.append(o.topPlayedSongs.joinToString("、") { "${it.first} · ${it.second}" })
            sb.append("。")
        }
        val out = sb.toString()
        return if (out.length <= maxChars) out else out.take(maxChars - 3) + "…"
    }

    /** 认识进度（总纲 2.2：伙伴的阅历）。 */
    fun buildRecognitionProgress(knownCount: Int, totalCount: Int): String =
        if (totalCount == 0) "曲库为空，我还在等你导入音乐。"
        else "我已经认识了你的 $knownCount / $totalCount 首歌。"

    /** 时段（总纲 3.4 层 1「语境包：时段」）。取本地小时 0..23。 */
    fun buildTimeOfDay(hourOfDay: Int): String = when (hourOfDay) {
        in 5..8 -> "清晨"
        in 9..11 -> "上午"
        in 12..14 -> "中午"
        in 15..18 -> "下午"
        in 19..22 -> "晚上"
        else -> "深夜"
    }

    /** 当前曲目（总纲 3.4 层 1「语境包：当前曲目」）。 */
    fun buildNowPlaying(
        currentTitle: String?,
        currentArtist: String?,
        isPlaying: Boolean,
        currentPositionMs: Long,
        durationMs: Long,
    ): String {
        if (currentTitle.isNullOrBlank()) return "当前没有在播放的曲目。"
        val state = if (isPlaying) "正在播放" else "已暂停"
        val progress = if (durationMs > 0) {
            val pct = (currentPositionMs * 100 / durationMs).coerceIn(0, 100)
            "，已播放约 $pct%"
        } else ""
        return "当前$state：$currentTitle · $currentArtist$progress。"
    }

    /** 把上述片段拼成可注入 system prompt 的上下文块（仅非空部分）。 */
    fun assembleFirstTurnBlock(
        personaText: String,
        libraryOverview: String?,
        recognition: String?,
        timeOfDay: String?,
        nowPlaying: String?,
        userTitle: String? = null,
    ): String = buildString {
        append(personaText.trim())
        userTitle?.takeIf { it.isNotBlank() }?.let { append("\n称呼为「$it」。") }
        if (!nowPlaying.isNullOrBlank()) append("\n【当前曲目】\n").append(nowPlaying.trim())
        if (!timeOfDay.isNullOrBlank()) append("\n【时段】\n").append(timeOfDay.trim())
        if (!libraryOverview.isNullOrBlank()) append("\n【曲库概况】\n").append(libraryOverview.trim())
        if (!recognition.isNullOrBlank()) append("\n【认识进度】\n").append(recognition.trim())
    }
}
