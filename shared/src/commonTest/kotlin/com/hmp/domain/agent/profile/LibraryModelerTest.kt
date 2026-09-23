package com.hmp.domain.agent.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 曲库形态建模（阶段一）单测 —— 契约 §4.1。
 *
 * 全是纯函数，不需要 Room / 不需要真实曲库。
 */
class LibraryModelerTest {

    private fun track(
        artist: String = "A",
        album: String = "AL",
        durationMs: Long = 240_000,
        path: String = "/Users/someone/Music/Rock/01.mp3",
    ) = LibraryTrack(artist, album, durationMs, path)

    // ── 空库：不产出任何证据，冷启动不留空壳（剧本 P1）────────────────────

    @Test
    fun emptyLibrary_producesNothing() {
        assertNull(LibraryModeler.computeShape(emptyList()), "空曲库不该给出形态")
        assertTrue(LibraryModeler.toEvidenceDrafts(null).isEmpty(), "空曲库不该产出证据")
    }

    // ── 规模分档 ───────────────────────────────────────────────────────

    @Test
    fun scale_followsBandBoundaries() {
        fun scaleOf(n: Int) = LibraryModeler.computeShape(List(n) { track() })!!.scale

        assertEquals(LibraryModeler.SCALE_TINY, scaleOf(49))
        assertEquals(LibraryModeler.SCALE_SMALL, scaleOf(50))
        assertEquals(LibraryModeler.SCALE_SMALL, scaleOf(299))
        assertEquals(LibraryModeler.SCALE_MEDIUM, scaleOf(300))
        assertEquals(LibraryModeler.SCALE_LARGE, scaleOf(1000))
        assertEquals(LibraryModeler.SCALE_HUGE, scaleOf(5000))
    }

    // ── 艺术家集中度 ───────────────────────────────────────────────────

    @Test
    fun concentration_focused_whenTop1Dominates() {
        // 10 首里 3 首同一歌手 → top1 = 30% ≥ 20%
        val tracks = List(3) { track(artist = "Big") } + List(7) { i -> track(artist = "S$i") }
        assertEquals(
            LibraryModeler.CONCENTRATION_FOCUSED,
            LibraryModeler.computeShape(tracks)!!.artistConcentration,
        )
    }

    @Test
    fun concentration_broad_whenNobodyStandsOut() {
        // 20 首、20 个歌手：top1 = 5%、top5 = 25% —— 但这里 top5 是 25% ≥ 20%，
        // 故用 40 个歌手让 top5 落到 12.5%（< 20%）才判 BROAD
        val tracks = List(40) { i -> track(artist = "Artist$i") }
        assertEquals(
            LibraryModeler.CONCENTRATION_BROAD,
            LibraryModeler.computeShape(tracks)!!.artistConcentration,
        )
    }

    @Test
    fun concentration_balanced_inTheMiddle() {
        // 10 首、5 个歌手各 2 首：top1 = 20%→FOCUSED 边界，改 6 人：top1≈17%、top5≈83%→BALANCED
        val tracks = List(6) { i -> List(2) { track(artist = "A$i") } }.flatten()
        assertEquals(
            LibraryModeler.CONCENTRATION_BALANCED,
            LibraryModeler.computeShape(tracks)!!.artistConcentration,
        )
    }

    // ── 专辑完整度 ─────────────────────────────────────────────────────

    @Test
    fun albumCompleteness_albumOriented_whenMostTracksBelongToFullAlbums() {
        // 8 首同专辑（≥6 曲算整张）→ 100% 属专辑
        val tracks = List(8) { track(album = "FullAlbum") }
        assertEquals(
            LibraryModeler.ALBUM_ORIENTED,
            LibraryModeler.computeShape(tracks)!!.albumCompleteness,
        )
    }

    @Test
    fun albumCompleteness_singleOriented_whenLooseTracksDominate() {
        // 10 首各属不同专辑（每组 1 曲 < 6）→ 0% 属专辑
        val tracks = List(10) { i -> track(album = "Album$i") }
        assertEquals(
            LibraryModeler.ALBUM_SINGLE_ORIENTED,
            LibraryModeler.computeShape(tracks)!!.albumCompleteness,
        )
    }

    // ── 路径脱敏：这是要进 system prompt 的东西，必须挡住三类 ────────────

    @Test
    fun pathGroup_keepsMeaningfulDirectoryName() {
        assertEquals("爵士", LibraryModeler.pathGroupOf("/Users/xi/Music/爵士/01.mp3"))
        assertEquals("深夜", LibraryModeler.pathGroupOf("/Users/xi/Music/深夜/02.flac"))
    }

    @Test
    fun pathGroup_dropsGenericContainers() {
        assertNull(LibraryModeler.pathGroupOf("/Users/xi/Music/01.mp3"), "Music 不是用户的分类")
        assertNull(LibraryModeler.pathGroupOf("/Users/xi/Downloads/a.mp3"))
        assertNull(LibraryModeler.pathGroupOf("/storage/emulated/0/Download/x.mp3"))
    }

    @Test
    fun pathGroup_dropsHomeDirectory_neverLeaksUsername() {
        // 文件直接放在家目录下 —— 末级目录名就是用户名，绝不能进画像
        assertNull(LibraryModeler.pathGroupOf("/Users/xi/song.mp3"), "家目录名即用户名，不得采集")
        assertNull(LibraryModeler.pathGroupOf("/home/xi/song.mp3"))
    }

    @Test
    fun pathGroup_handlesWindowsSeparators() {
        assertEquals("爵士", LibraryModeler.pathGroupOf("C:\\Users\\xi\\Music\\爵士\\01.mp3"))
        assertNull(LibraryModeler.pathGroupOf("C:\\Users\\xi\\Music\\01.mp3"))
    }

    @Test
    fun pathStructure_organized_whenSeveralBalancedGroups() {
        val tracks = listOf("爵士", "摇滚", "民谣", "电子").flatMap { g ->
            List(5) { track(path = "/Users/xi/Music/$g/x.mp3") }
        }
        val shape = LibraryModeler.computeShape(tracks)!!
        assertEquals(LibraryModeler.PATH_ORGANIZED, shape.pathStructure)
        assertEquals(setOf("爵士", "摇滚", "民谣", "电子"), shape.pathGroups.toSet())
    }

    @Test
    fun pathStructure_flat_whenNoUsableGroups() {
        val tracks = List(5) { track(path = "/Users/xi/Music/x$it.mp3") }
        val shape = LibraryModeler.computeShape(tracks)!!
        assertEquals(LibraryModeler.PATH_FLAT, shape.pathStructure)
        assertTrue(shape.pathGroups.isEmpty())
    }

    @Test
    fun pathGroups_omittedFromEvidence_whenEmpty() {
        val shape = LibraryModeler.computeShape(List(3) { track(path = "/Users/xi/Music/x.mp3") })!!
        val predicates = LibraryModeler.toEvidenceDrafts(shape).map { it.predicate }
        assertTrue(
            PortraitType.predicateOf(PortraitType.LIBRARY, PortraitType.PATH_GROUPS) !in predicates,
            "没有可识别目录就不该写这条证据",
        )
    }

    // ── 时长倾向用中位数（均值会被长曲拉偏）──────────────────────────────

    @Test
    fun durationTendency_usesMedian_notMean() {
        // 9 首 3 分钟的短歌 + 1 首 60 分钟的长曲：均值 8.7 分钟（会被判 LONG），中位数 3 分钟（SHORT）
        val tracks = List(9) { track(durationMs = 180_000) } + track(durationMs = 3_600_000)
        assertEquals(
            LibraryModeler.DURATION_SHORT,
            LibraryModeler.computeShape(tracks)!!.durationTendency,
        )
    }

    // ── 证据草稿 ───────────────────────────────────────────────────────

    @Test
    fun evidenceDrafts_areClosedSetWithCorrectSource() {
        val shape = LibraryModeler.computeShape(List(4) { track(artist = "A$it") })!!
        val drafts = LibraryModeler.toEvidenceDrafts(shape, sessionId = "scan-1")

        assertTrue(drafts.isNotEmpty())
        drafts.forEach { draft ->
            assertTrue(
                PortraitType.isKnown(draft.predicate),
                "证据谓词必须在闭集内，越界的是 ${draft.predicate}",
            )
            assertEquals(ProfileSources.LIBRARY_SHAPE, draft.source)
            assertEquals(0.4, draft.confidence, 1e-9)
            assertEquals("scan-1", draft.sessionId)
        }
        val slots = drafts.mapNotNull { PortraitType.parse(it.predicate)?.second }
        assertTrue(PortraitType.SCALE in slots)
        assertTrue(PortraitType.ARTIST_CONCENTRATION in slots)
    }

    // ── 谓词闭集本身 ───────────────────────────────────────────────────

    @Test
    fun predicateClosure_rejectsUnknownAndAcceptsKnown() {
        assertTrue(PortraitType.isKnown(PortraitType.predicateOf(PortraitType.LIBRARY, PortraitType.SCALE)))
        assertTrue(PortraitType.isKnown(PortraitType.DENIED))
        assertTrue(!PortraitType.isKnown("library_portrait.madeUpSlot"), "自由发明的谓词必须被拒")
        assertTrue(!PortraitType.isKnown("nonexistent.type.slot"))
        assertNull(PortraitType.parse("library_portrait.madeUpSlot"))
    }

    @Test
    fun predicateParse_roundTrips() {
        val p = PortraitType.predicateOf(PortraitType.LIBRARY, PortraitType.PATH_GROUPS)
        assertEquals("library_portrait.pathGroups", p)
        val parsed = PortraitType.parse(p)
        assertNotNull(parsed)
        assertEquals(PortraitType.LIBRARY, parsed.first)
        assertEquals(PortraitType.PATH_GROUPS, parsed.second)
    }
}
