package com.hmp.domain.agent.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** R3 后半：摘要只用于「防重复执行」，所以要短、要含工具名与结果。 */
class ToolRunSummaryTest {

    private fun rec(name: String, outcome: String = "success") = ToolExecutionRecord(name, outcome, "")

    @Test
    fun `空列表不产出摘要`() {
        assertNull(buildToolRunSummary(emptyList()))
    }

    @Test
    fun `摘要包含工具名与结果，并明确提示勿重复`() {
        val s = buildToolRunSummary(listOf(rec("playlist.add"), rec("library.search")))!!
        assertTrue(s.contains("playlist.add(success)"))
        assertTrue(s.contains("library.search(success)"))
        assertTrue(s.contains("不要重复执行"))
    }

    @Test
    fun `只列最近 N 个，避免提示词膨胀`() {
        val s = buildToolRunSummary((1..10).map { rec("t$it") })!!
        assertFalse(s.contains("t1("), "最早的应被截掉")
        assertTrue(s.contains("t10("))
    }
}
