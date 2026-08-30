package com.hmp.domain.agent.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextAssemblerTest {

    @Test
    fun buildLibraryOverview_formatsAllSections() {
        val ov = LibraryOverview(
            totalCount = 3000,
            languageDistribution = mapOf("国语" to 2000, "英语" to 800, "日语" to 200),
            topGenres = listOf("流行" to 900, "摇滚" to 450, "电子" to 300),
            eraDistribution = mapOf("2010s" to 1200, "2000s" to 800),
            topPlayedSongs = listOf("晴天" to "周杰伦", "夜曲" to "周杰伦"),
        )
        val s = ContextAssembler.buildLibraryOverview(ov)
        assertTrue(s.contains("曲库共 3000 首"))
        assertTrue(s.contains("语言分布：国语 2000 首、英语 800 首、日语 200 首"))
        assertTrue(s.contains("流派 top3：流行(900)、摇滚(450)、电子(300)"))
        assertTrue(s.contains("年代分布：2010s 1200 首、2000s 800 首"))
        assertTrue(s.contains("常听（top2）：晴天 · 周杰伦、夜曲 · 周杰伦"))
    }

    @Test
    fun buildLibraryOverview_truncatesToBudget() {
        val ov = LibraryOverview(totalCount = 100, topPlayedSongs = (1..60).map { "歌$it" to "艺人$it" })
        val s = ContextAssembler.buildLibraryOverview(ov, maxChars = 120)
        assertTrue(s.length <= 120 + 1) // 允许末尾省略号
    }

    @Test
    fun buildRecognitionProgress_coversEmptyLibrary() {
        assertEquals("曲库为空，我还在等你导入音乐。", ContextAssembler.buildRecognitionProgress(0, 0))
        assertEquals("我已经认识了你的 2312 / 2500 首歌。", ContextAssembler.buildRecognitionProgress(2312, 2500))
    }

    @Test
    fun buildTimeOfDay_mapsHours() {
        assertEquals("清晨", ContextAssembler.buildTimeOfDay(7))
        assertEquals("中午", ContextAssembler.buildTimeOfDay(13))
        assertEquals("深夜", ContextAssembler.buildTimeOfDay(23))
        assertEquals("深夜", ContextAssembler.buildTimeOfDay(2))
    }

    @Test
    fun buildNowPlaying_handlesIdleAndPlaying() {
        assertTrue(ContextAssembler.buildNowPlaying(null, null, false, 0, 0).contains("没有在播放"))
        val s = ContextAssembler.buildNowPlaying("晴天", "周杰伦", true, 90_000, 240_000)
        assertTrue(s.contains("正在播放：晴天 · 周杰伦"))
        assertTrue(s.contains("已播放约 37%"))
    }

    @Test
    fun assembleFirstTurnBlock_ordersSections_andDefaultsPersona() {
        val s = ContextAssembler.assembleFirstTurnBlock(
            personaText = "你是伙伴",
            libraryOverview = "曲库共 100 首",
            recognition = "已认识 10 / 100",
            timeOfDay = "晚上",
            nowPlaying = "正在播放：A · B",
            userTitle = "小周",
        )
        assertTrue(s.startsWith("你是伙伴"))
        assertTrue(s.contains("称呼为「小周」"))
        assertTrue(s.contains("【当前曲目】"))
        assertTrue(s.contains("【时段】"))
        assertTrue(s.contains("【曲库概况】"))
        assertTrue(s.contains("【认识进度】"))
        // 相对顺序：当前曲目 → 时段 → 曲库概况 → 认识进度
        assertTrue(s.indexOf("【当前曲目】") < s.indexOf("【时段】"))
        assertTrue(s.indexOf("【时段】") < s.indexOf("【曲库概况】"))
        assertTrue(s.indexOf("【曲库概况】") < s.indexOf("【认识进度】"))
    }
}
