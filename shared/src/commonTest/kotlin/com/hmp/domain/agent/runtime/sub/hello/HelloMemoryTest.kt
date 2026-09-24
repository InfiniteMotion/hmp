package com.hmp.domain.agent.runtime.sub.hello

import com.hmp.data.database.HelloCardCache
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HelloMemory 记忆协调层单测（G13）。
 *
 * 用 `HelloMemory(null)` 纯跑内存 todayCache，不依赖 Room；
 * 覆盖 mergeWith 按 cardType 分流、跨卡歌手协调（G7 修复后行为）、空值不误吐、GREETING 类型去重。
 */
class HelloMemoryTest {

    private fun cache(
        cardType: String,
        recommendSongIds: List<String>? = null,
        recommendArtists: List<String>? = null,
        recommendLabels: List<String>? = null,
        greetingType: String? = null,
        discoverLabels: List<String>? = null,
        forgottenArtists: List<String>? = null,
        forgottenSongIds: List<String>? = null,
        anniversaryArtist: String? = null,
    ) = HelloCardCache(
        cardType = cardType,
        cardContentJson = "",
        generatedAt = 1L,
        generatedForDate = "2026-09-14",
        recommendSongIds = recommendSongIds,
        recommendArtists = recommendArtists,
        recommendLabels = recommendLabels,
        greetingType = greetingType,
        discoverLabels = discoverLabels,
        forgottenArtists = forgottenArtists,
        forgottenSongIds = forgottenSongIds,
        anniversaryArtist = anniversaryArtist,
    )

    /** G7 修复后：记录 RECOMMEND + FORGOTTEN 后，歌手维度协调应真实进 prompt */
    @Test
    fun recordRecommendAndForgotten_populatesCrossCardArtistCoordination() = runTest {
        val memory = HelloMemory(null)
        memory.record(
            cache(
                cardType = "RECOMMEND",
                recommendSongIds = listOf("100"),
                recommendArtists = listOf("周杰伦"),
                recommendLabels = listOf("夜晚"),
            ),
        )
        memory.record(
            cache(
                cardType = "FORGOTTEN",
                forgottenSongIds = listOf("200"),
                forgottenArtists = listOf("陈奕迅"),
            ),
        )

        val forgottenCtx = memory.buildContextForCard("FORGOTTEN")
        assertTrue(forgottenCtx.contains("RECOMMEND 已推歌手"), "RECOMMEND 歌手应进入 FORGOTTEN 跨卡协调")
        assertTrue(forgottenCtx.contains("周杰伦"))
        assertTrue(forgottenCtx.contains("最近 7 天 FORGOTTEN 已随笔歌手"), "FORGOTTEN 自身歌手应进 weekly 协调")
        assertTrue(forgottenCtx.contains("陈奕迅"))

        val recommendCtx = memory.buildContextForCard("RECOMMEND")
        assertTrue(recommendCtx.contains("FORGOTTEN 已随笔歌手"))
        assertTrue(recommendCtx.contains("陈奕迅"))
        assertFalse(recommendCtx.contains("RECOMMEND 已推歌手"), "同类型 RECOMMEND 不应重复列出自己")
    }

    /** 空值防护：artist 未填时不应误吐协调行（G7 修复前恒为这种状态，需保证不崩、不误提示） */
    @Test
    fun crossCardCoordinationAbsentWhenArtistsNull() = runTest {
        val memory = HelloMemory(null)
        memory.record(
            cache(
                cardType = "RECOMMEND",
                recommendSongIds = listOf("100"),
                // recommendArtists / recommendLabels 留 null
            ),
        )
        val ctx = memory.buildContextForCard("FORGOTTEN")
        assertFalse(ctx.contains("RECOMMEND 已推歌手"), "artist 为空时不应输出协调行")
        assertFalse(ctx.contains("最近 7 天 DISCOVER 已提 label"), "未记 DISCOVER 时不应输出 label 协调")
    }

    /** GREETING 类型去重：记录后应在协调文本中可见，供后续 GREETING 避开重复 */
    @Test
    fun greetingTypeDedup_recordedAndVisible() = runTest {
        val memory = HelloMemory(null)
        memory.record(cache(cardType = "GREETING", greetingType = "QUOTE"))
        val ctx = memory.buildContextForCard("GREETING")
        assertTrue(ctx.contains("今日已使用的 GREETING 类型"))
        assertTrue(ctx.contains("QUOTE"))
    }
}
