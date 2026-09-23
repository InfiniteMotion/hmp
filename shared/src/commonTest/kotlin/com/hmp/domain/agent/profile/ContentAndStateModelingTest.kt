package com.hmp.domain.agent.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 曲库**内容建模（阶段二）** 与 **状态快照** 的单测 —— 契约 §4.1.2 / §4.1.4 / §4.3。
 */
class ContentAndStateModelingTest {

    private fun content(
        total: Int = 100,
        enriched: Int = 90,
        genres: Map<String, Int> = emptyMap(),
        moods: Map<String, Int> = emptyMap(),
        languages: Map<String, Int> = emptyMap(),
        eras: Map<String, Int> = emptyMap(),
    ) = LibraryContentSnapshot(total, enriched, genres, moods, languages, eras)

    // ── 阶段二：不假装知道 ─────────────────────────────────────────────

    @Test
    fun computeContent_returnsNull_whenNoLabelsAtAll() {
        assertNull(
            LibraryModeler.computeContent(content()),
            "一条标签都没有时不该编出内容结论（契约 §4.1.6）",
        )
        assertTrue(LibraryModeler.toContentEvidenceDrafts(null, 0.9f).isEmpty())
    }

    // ── 阶段二是「纠正」：同样"人集中"，到内容阶段才看得出风格散 ─────────

    @Test
    fun genreBreadth_distinguishesNarrowFromBroad() {
        // 90% 集中在一个流派 → NARROW
        val narrow = LibraryModeler.computeContent(content(genres = mapOf("ROCK" to 90, "JAZZ" to 10)))
        assertEquals(LibraryModeler.BREADTH_NARROW, assertNotNull(narrow).genreBreadth)

        // 10 个流派各 10%，最大的只占 10% → BROAD
        val broad = LibraryModeler.computeContent(
            content(genres = (1..10).associate { "G$it" to 10 })
        )
        assertEquals(LibraryModeler.BREADTH_BROAD, assertNotNull(broad).genreBreadth)
    }

    @Test
    fun languageMix_singleBilingualMultilingual() {
        fun mixOf(vararg pairs: Pair<String, Int>) =
            assertNotNull(LibraryModeler.computeContent(content(languages = pairs.toMap()))).languageMix

        assertEquals(LibraryModeler.LANG_SINGLE, mixOf("ZH" to 95, "EN" to 5))
        assertEquals(LibraryModeler.LANG_BILINGUAL, mixOf("ZH" to 55, "EN" to 45))
        assertEquals(
            LibraryModeler.LANG_MULTILINGUAL,
            mixOf("ZH" to 40, "EN" to 30, "JP" to 20, "KR" to 10),
        )
    }

    // ── 覆盖率折扣：这是"稳定地错"的解药 ────────────────────────────────

    @Test
    fun coverageDiscount_scalesConfidenceByCoverage() {
        val shape = LibraryModeler.computeContent(content(genres = mapOf("ROCK" to 50, "JAZZ" to 30)))

        val high = LibraryModeler.toContentEvidenceDrafts(shape, coverageRate = 1.0f).first()
        val low = LibraryModeler.toContentEvidenceDrafts(shape, coverageRate = 0.3f).first()

        assertEquals(0.55, high.confidence, 1e-9, "覆盖率 100% 时取满 0.55")
        // 容差放宽到 1e-6：覆盖率是 Float（0.3f → 0.3000000119…），换算成 Double 会有这个量级的偏差
        assertEquals(0.55 * 0.3f.toDouble(), low.confidence, 1e-6, "覆盖率 30% 要打折 —— 剩下 70% 是未知")
        assertTrue(low.confidence < high.confidence)
        assertEquals(ProfileSources.LIBRARY_CONTENT, high.source)
    }

    @Test
    fun contentEvidence_staysInsidePredicateClosure() {
        val shape = LibraryModeler.computeContent(content(genres = mapOf("ROCK" to 80, "JAZZ" to 20)))
        LibraryModeler.toContentEvidenceDrafts(shape, 0.9f).forEach {
            assertTrue(PortraitType.isKnown(it.predicate), "越界谓词：${it.predicate}")
        }
    }

    // ── 状态快照 ───────────────────────────────────────────────────────

    @Test
    fun stateEvidence_isT1_andCoversTheExplicitSignals() {
        val state = LibraryStateSnapshot(
            totalSongs = 100,
            likedCount = 25,
            dislikedCount = 3,
            playlistNames = listOf("深夜开车", "通勤"),
            hiddenFolderCount = 2,
            userCorrectedLabelCount = 7,
        )
        val drafts = LibraryModeler.toStateEvidenceDrafts(state, sessionId = "scan-1")

        drafts.forEach {
            assertEquals(ProfileSources.T1_USER, it.source, "用户显式给的是最高 ground truth")
            assertEquals(0.9, it.confidence, 1e-9)
            assertTrue(PortraitType.isKnown(it.predicate))
        }
        val bySlot = drafts.associate { PortraitType.parse(it.predicate)!!.second to it.value }
        assertEquals(LibraryModeler.SHARE_MANY, bySlot[PortraitType.LIKED_SHARE])
        assertEquals(LibraryModeler.SHARE_SOME, bySlot[PortraitType.DISLIKED_SHARE])
        assertEquals("深夜开车,通勤", bySlot[PortraitType.PLAYLIST_NAMES])
    }

    @Test
    fun stateEvidence_omitsEverythingWhenUserGaveNothing() {
        val silent = LibraryStateSnapshot(totalSongs = 500, likedCount = 0, dislikedCount = 0, hiddenFolderCount = 0)
        assertTrue(LibraryModeler.toStateEvidenceDrafts(silent).isEmpty(), "用户什么都没给就不写证据")
    }

    @Test
    fun stateEvidence_skipsPlaylistNames_whenNothingSurvivesSanitizing() {
        val state = LibraryStateSnapshot(
            totalSongs = 10,
            likedCount = 0,
            dislikedCount = 0,
            playlistNames = listOf("a/b", "这个歌单名字实在是太长了超过上限", "  "),
            hiddenFolderCount = 0,
        )
        val slots = LibraryModeler.toStateEvidenceDrafts(state)
            .mapNotNull { PortraitType.parse(it.predicate)?.second }
        assertTrue(
            PortraitType.PLAYLIST_NAMES !in slots,
            "歌单名全被脱敏掉时不该留下一条空证据",
        )
    }

    @Test
    fun sanitizeNames_capsCountLengthAndDropsSeparators() {
        val names = LibraryModeler.sanitizeNames(
            listOf("深夜", "通勤", "深夜", "a/b", "很长的歌单名字超过十二个字符了", "1", "2", "3", "4", "5")
        )
        assertEquals(listOf("深夜", "通勤", "1", "2", "3"), names, "去重 + 截断到 5 条")
    }
}
