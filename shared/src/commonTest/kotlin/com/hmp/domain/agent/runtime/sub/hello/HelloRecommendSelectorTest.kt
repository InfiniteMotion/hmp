package com.hmp.domain.agent.runtime.sub.hello

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D3 模型层解析判据：只认候选池内的 id、去重、截断、失败回落。
 */
class HelloRecommendSelectorTest {

    private val allowed = setOf(11L, 22L, 33L)

    @Test
    fun parse_validJson_keepsModelOrder() {
        val raw = """{"overview":"今晚适合慢下来","items":[{"id":22,"reason":"低饱和的夜色"},{"id":11,"reason":"开场抓耳"}]}"""

        val result = HelloRecommendSelector.parse(raw, allowed, max = 10)

        assertEquals("今晚适合慢下来", result?.overview)
        assertEquals(listOf(22L, 11L), result?.items?.map { it.trackId }, "顺序应沿用模型给的播放顺序")
        assertEquals("低饱和的夜色", result?.items?.first()?.reason)
    }

    @Test
    fun parse_markdownFence_isTolerated() {
        val raw = """
            ```json
            {"overview":"测试","items":[{"id":11,"reason":"可以"}]}
            ```
        """.trimIndent()

        val result = HelloRecommendSelector.parse(raw, allowed, max = 10)

        assertEquals(listOf(11L), result?.items?.map { it.trackId })
    }

    @Test
    fun parse_dropsUnknownAndDuplicateIds() {
        val raw = """{"overview":"x","items":[
            {"id":11,"reason":"a"},
            {"id":999,"reason":"编造的"},
            {"id":11,"reason":"重复"},
            {"id":33,"reason":"c"}
        ]}"""

        val result = HelloRecommendSelector.parse(raw, allowed, max = 10)

        assertEquals(listOf(11L, 33L), result?.items?.map { it.trackId })
    }

    @Test
    fun parse_capsAtMax() {
        val raw = """{"overview":"x","items":[
            {"id":11,"reason":"a"},{"id":22,"reason":"b"},{"id":33,"reason":"c"}
        ]}"""

        val result = HelloRecommendSelector.parse(raw, allowed, max = 2)

        assertEquals(2, result?.items?.size)
    }

    @Test
    fun parse_blankReason_getsDefault() {
        val raw = """{"overview":"x","items":[{"id":11,"reason":"  "}]}"""

        val result = HelloRecommendSelector.parse(raw, allowed, max = 10)

        assertTrue(result?.items?.first()?.reason?.isNotBlank() == true)
    }

    @Test
    fun parse_truncatesLongText() {
        val longOverview = "开".repeat(100)
        val longReason = "推".repeat(100)
        val raw = """{"overview":"$longOverview","items":[{"id":11,"reason":"$longReason"}]}"""

        val result = HelloRecommendSelector.parse(raw, allowed, max = 10)

        assertEquals(HelloRecommendSelector.MAX_OVERVIEW_CHARS, result?.overview?.length)
        assertEquals(HelloRecommendSelector.MAX_REASON_CHARS, result?.items?.first()?.reason?.length)
    }

    @Test
    fun parse_nonJson_returnsNull() {
        assertNull(HelloRecommendSelector.parse("抱歉，我无法完成", allowed, max = 10))
        assertNull(HelloRecommendSelector.parse("", allowed, max = 10))
    }

    @Test
    fun parse_noValidItems_returnsNull() {
        val raw = """{"overview":"x","items":[{"id":999,"reason":"out"}]}"""
        assertNull(HelloRecommendSelector.parse(raw, allowed, max = 10))
    }

    @Test
    fun buildPrompt_containsAllCandidatesAndCount() {
        val local = RecommendCandidate(1, "A", "a", listOf("POP"), 0, false)
        val other = RecommendCandidate(2, "B", null, emptyList(), 3, true)
        val prompt = HelloRecommendSelector.buildPrompt(
            source = com.hmp.domain.agent.card.RecommendSource.DAILY,
            phase = com.hmp.domain.agent.card.TimePhase.MORNING_COMMUTE,
            localPicks = listOf(local),
            library = listOf(local, other),
            totalCount = 900,
            omittedCount = 0,
            count = 2,
        )

        assertTrue("id=1" in prompt && "id=2" in prompt)
        assertTrue("本地算法推荐" in prompt, "本地列表必须作为参考段出现")
        assertTrue("共 900 首" in prompt, "要告诉模型曲库总量")
        assertTrue("选出 2 首" in prompt)
        assertTrue("JSON" in prompt)
    }

    @Test
    fun buildPrompt_notesOmittedRowsWhenLibraryIsTruncated() {
        val prompt = HelloRecommendSelector.buildPrompt(
            source = com.hmp.domain.agent.card.RecommendSource.PRIVATE,
            phase = com.hmp.domain.agent.card.TimePhase.NIGHT,
            localPicks = emptyList(),
            library = listOf(RecommendCandidate(1, "A", "a", emptyList(), 0, false)),
            totalCount = 5000,
            omittedCount = 4999,
            count = 8,
        )

        assertTrue("已省略 4999 首" in prompt, "截断时必须明确告诉模型省略了多少")
        assertTrue("共 5000 首" in prompt)
    }

    @Test
    fun libraryRowBudget_isReasonable() {
        assertTrue(HelloRecommendSelector.libraryRowBudget() >= HelloRecommendSelector.MIN_LIBRARY_ROWS)
        assertTrue(HelloRecommendSelector.libraryRowBudget() <= 1000, "预算不应无限大")
    }
}
