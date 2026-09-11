package com.hmp.domain.agent.sub

import com.hmp.data.database.HelloCardCache
import com.hmp.data.database.HelloCardCacheDao
import com.hmp.data.database.currentTimeMillis
import com.hmp.data.util.millisUntilNextLocalMidnight
import com.hmp.data.util.parseDateToMillis
import com.hmp.data.util.todayDateString

/**
 * HelloSubAgent 记忆协调层。
 *
 * 职责：
 * 1. 从 Room 查询历史记录，在 Kotlin 层做集合运算
 * 2. 内存缓存今日状态（避免每次生成都查 Room）
 * 3. 按当前卡片类型格式化 LLM 注入上下文
 *
 * 不存 contextBudget 的对话 history（那是单次 LLM call 内部的多轮推理用的）。
 * HelloMemory 管的是跨卡片、跨天、跨周的协调信息。
 */
class HelloMemory(
    private val dao: HelloCardCacheDao?,
) {
    // ── 今日内存缓存 ──
    // 每次 record() 更新，buildContextForCard() 优先读，避免重复 Room 查询
    private data class TodayCache(
        val recommendSongIds: Set<String> = emptySet(),
        val recommendArtists: Set<String> = emptySet(),
        val recommendLabels: Set<String> = emptySet(),
        val greetingTypes: List<String> = emptyList(),
        val greetingMentionedArtists: Set<String> = emptySet(),
        val discoverLabels: Set<String> = emptySet(),
        val forgottenArtists: Set<String> = emptySet(),
        val forgottenSongIds: Set<String> = emptySet(),
        val anniversaryQueried: Boolean = false,
        val anniversaryArtist: String? = null,
    )

    private var todayCache: TodayCache = TodayCache()
    private var cacheDate: String = ""

    /** App 启动时从 Room 恢复今日缓存 */
    suspend fun loadToday() {
        val today = todayDateString()
        val d = dao ?: return  // 局部 val 确保 lambda 内 smart cast
        val startOfDay = startOfDayMillis()
        val entities = runCatching { d.getTodayAllEntities(startOfDay) }.getOrDefault(emptyList())
        todayCache = entities.fold(TodayCache()) { acc, e -> acc.mergeWith(e) }
        cacheDate = today
    }

    /** 今日缓存是否已过期（跨天） */
    private fun ensureFresh() {
        val today = todayDateString()
        if (cacheDate != today) {
            todayCache = TodayCache()
            cacheDate = today
        }
    }

    /** 记录一张新卡片到内存缓存（Room 写入由 HelloSubAgent 负责） */
    fun record(cache: HelloCardCache) {
        ensureFresh()
        todayCache = todayCache.mergeWith(cache)
    }

    // ── 精确查询 API（硬编码层直接消费，零 token） ──

    /** 今日 ANNIVERSARY 是否已查询过（防重复 LLM call） */
    suspend fun isAnniversaryQueriedToday(): Boolean {
        val d = dao ?: return todayCache.anniversaryQueried
        val startOfDay = startOfDayMillis()
        val count = runCatching { d.countToday("ANNIVERSARY", startOfDay) }.getOrDefault(0)
        return count > 0 || todayCache.anniversaryQueried
    }

    /** 昨日 RECOMMEND songIds（硬编码选歌时排除） */
    suspend fun getYesterdayRecommendSongIds(): Set<String> {
        val d = dao ?: return emptySet()
        val (yStart, yEnd) = yesterdayRange()
        return runCatching { d.getYesterdayRecommends(yStart, yEnd) }
            .getOrDefault(emptyList())
            .flatMap { it.recommendSongIds ?: emptyList() }
            .toSet()
    }

    /** 7 天内 DISCOVER 提过的 labels（硬编码选 label 时排除） */
    suspend fun getWeeklyDiscoverLabels(): Set<String> {
        val d = dao ?: return todayCache.discoverLabels
        val sevenDaysAgo = currentTimeMillis() - 7L * 24 * 3600 * 1000
        return runCatching { d.getWeeklyDiscoverCards(sevenDaysAgo) }
            .getOrDefault(emptyList())
            .flatMap { it.discoverLabels ?: emptyList() }
            .toSet() + todayCache.discoverLabels
    }

    /** 7 天内 FORGOTTEN 随笔过的歌手（硬编码选歌时排除） */
    suspend fun getWeeklyForgottenArtists(): Set<String> {
        val d = dao ?: return todayCache.forgottenArtists
        val sevenDaysAgo = currentTimeMillis() - 7L * 24 * 3600 * 1000
        return runCatching { d.getWeeklyForgottenCards(sevenDaysAgo) }
            .getOrDefault(emptyList())
            .flatMap { it.forgottenArtists ?: emptyList() }
            .toSet() + todayCache.forgottenArtists
    }

    // ── 格式化 LLM 注入上下文 ──

    /**
     * 为指定卡片类型生成记忆协调文本，拼进 user prompt。
     * 返回空字符串表示没有可用的协调信息（直接跳过即可）。
     */
    suspend fun buildContextForCard(cardType: String): String {
        ensureFresh()
        val sb = StringBuilder()

        // ── 今日已生成的其他卡片（跨卡片去重） ──
        buildTodayCrossCardContext(sb, cardType)

        // ── 卡片类型专属协调信息 ──
        when (cardType) {
            "RECOMMEND" -> buildRecommendContext(sb)
            "DISCOVER" -> buildDiscoverContext(sb)
            "FORGOTTEN" -> buildForgottenContext(sb)
            "ANNIVERSARY" -> {
                if (isAnniversaryQueriedToday()) {
                    return "[跳过] 今日已查询过 ANNIVERSARY 事件，不需要重复查询。"
                }
            }
            "GREETING" -> buildGreetingContext(sb)
        }

        return sb.toString().trimEnd()
    }

    // ═══════════════════════════════════════════════════════════════════
    // 内部辅助
    // ═══════════════════════════════════════════════════════════════════

    private fun buildTodayCrossCardContext(sb: StringBuilder, targetType: String) {
        sb.appendLine("[今日已生成卡片（不要重复相同内容）]")
        val tc = todayCache

        if (targetType != "RECOMMEND" && tc.recommendArtists.isNotEmpty()) {
            sb.appendLine("  RECOMMEND 已推歌手: ${tc.recommendArtists.joinToString()}")
        }
        if (targetType != "DISCOVER" && tc.discoverLabels.isNotEmpty()) {
            sb.appendLine("  DISCOVER 已提 label: ${tc.discoverLabels.joinToString()}")
        }
        if (targetType != "FORGOTTEN" && tc.forgottenArtists.isNotEmpty()) {
            sb.appendLine("  FORGOTTEN 已随笔歌手: ${tc.forgottenArtists.joinToString()}")
        }
        if (targetType != "ANNIVERSARY" && tc.anniversaryArtist != null) {
            sb.appendLine("  ANNIVERSARY 主题: ${tc.anniversaryArtist}")
        }
        if (targetType != "GREETING" && tc.greetingMentionedArtists.isNotEmpty()) {
            sb.appendLine("  GREETING 提到的歌手: ${tc.greetingMentionedArtists.joinToString()}")
        }
    }

    private suspend fun buildRecommendContext(sb: StringBuilder) {
        // 昨日避开
        val yesterday = getYesterdayRecommendSongIds()
        if (yesterday.isNotEmpty()) {
            sb.appendLine("[昨日 RECOMMEND 已推歌曲 - 今日请尽量避开]")
            sb.appendLine("  songId 数量: ${yesterday.size}")
        }
        // 今日 label 避开（从 DISCOVER 注入）
        val tc = todayCache
        if (tc.discoverLabels.isNotEmpty()) {
            sb.appendLine("[今日 DISCOVER 已提 label - RECOMMEND 选歌时尽量避开]")
            sb.appendLine("  ${tc.discoverLabels.joinToString()}")
        }
    }

    private suspend fun buildDiscoverContext(sb: StringBuilder) {
        // 7 天内 label 避开
        val weekly = getWeeklyDiscoverLabels()
        if (weekly.isNotEmpty()) {
            sb.appendLine("[最近 7 天 DISCOVER 已提 label - 尽量避开]")
            sb.appendLine("  ${weekly.joinToString()}")
        }
        // 今日 RECOMMEND 已用 label 避开
        val tc = todayCache
        if (tc.recommendLabels.isNotEmpty()) {
            sb.appendLine("[今日 RECOMMEND 已用 label - 尽量避开]")
            sb.appendLine("  ${tc.recommendLabels.joinToString()}")
        }
    }

    private suspend fun buildForgottenContext(sb: StringBuilder) {
        // 7 天内歌手避开
        val weekly = getWeeklyForgottenArtists()
        if (weekly.isNotEmpty()) {
            sb.appendLine("[最近 7 天 FORGOTTEN 已随笔歌手 - 不要重复]")
            sb.appendLine("  ${weekly.joinToString()}")
        }
    }

    private fun buildGreetingContext(sb: StringBuilder) {
        val tc = todayCache
        if (tc.greetingTypes.isNotEmpty()) {
            sb.appendLine("[今日已使用的 GREETING 类型 - 尽量不要重复]")
            sb.appendLine("  ${tc.greetingTypes.joinToString()}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 时间辅助
    // ═══════════════════════════════════════════════════════════════════

    private fun startOfDayMillis(): Long {
        // KMP 兼容：用 expect/actual API，不依赖 java.util.Calendar
        return parseDateToMillis(todayDateString())
            ?: (currentTimeMillis() - (24L * 3600 * 1000 - millisUntilNextLocalMidnight()))
    }

    private fun yesterdayRange(): Pair<Long, Long> {
        val start = startOfDayMillis()
        val oneDay = 24L * 3600 * 1000
        return (start - oneDay) to start
    }

    // ═══════════════════════════════════════════════════════════════════
    // TodayCache 合并逻辑
    // ═══════════════════════════════════════════════════════════════════

    private fun TodayCache.mergeWith(e: HelloCardCache): TodayCache {
        val songIds = e.recommendSongIds?.toSet() ?: emptySet()
        val artists = e.recommendArtists?.toSet() ?: emptySet()
        val labels = e.recommendLabels?.toSet() ?: emptySet()
        val greetingArtists = e.greetingMentionedArtists?.toSet() ?: emptySet()
        val discoverLbls = e.discoverLabels?.toSet() ?: emptySet()
        val forgottenArt = e.forgottenArtists?.toSet() ?: emptySet()
        val forgottenSongs = e.forgottenSongIds?.toSet() ?: emptySet()

        return when (e.cardType) {
            "RECOMMEND" -> copy(
                recommendSongIds = recommendSongIds + songIds,
                recommendArtists = recommendArtists + artists,
                recommendLabels = recommendLabels + labels,
            )
            "GREETING" -> copy(
                greetingTypes = greetingTypes + (e.greetingType?.let { listOf(it) } ?: emptyList()),
                greetingMentionedArtists = greetingMentionedArtists + greetingArtists,
            )
            "DISCOVER" -> copy(discoverLabels = discoverLabels + discoverLbls)
            "FORGOTTEN" -> copy(
                forgottenArtists = forgottenArtists + forgottenArt,
                forgottenSongIds = forgottenSongIds + forgottenSongs,
            )
            "ANNIVERSARY" -> copy(
                anniversaryQueried = true,
                anniversaryArtist = e.anniversaryArtist ?: anniversaryArtist,
            )
            else -> this
        }
    }
}
