package com.hmp.domain.agent.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 侧写派生（证据 → 侧写）单测 —— 契约 §4.1.2 / §3.1 / §7。
 *
 * [PortraitComposer] 是侧写的**唯一派生点**，所以这里覆盖的是"侧写怎么长出来"的全部规则。
 */
class PortraitComposerTest {

    private val now = 1_700_000_000_000L
    private val oneWeek = 7L * 24 * 3600 * 1000

    private fun libraryEvidence(
        slot: String,
        value: String,
        source: String = ProfileSources.LIBRARY_SHAPE,
        confidence: Double = 0.4,
        id: Long = 1,
    ) = ProfileEvidence(
        predicate = PortraitType.predicateOf(PortraitType.LIBRARY, slot),
        value = value,
        source = source,
        confidence = confidence,
        id = id,
        createdAt = now,
        updatedAt = now,
    )

    // ── 空输入 ─────────────────────────────────────────────────────────

    @Test
    fun emptyEvidence_producesNoPortrait() {
        assertTrue(PortraitComposer.compose(emptyList(), now).isEmpty())
    }

    @Test
    fun unknownPredicate_isIgnored() {
        val bogus = ProfileEvidence(
            predicate = "library_portrait.madeUp",
            value = "X",
            source = ProfileSources.LIBRARY_SHAPE,
            confidence = 0.9,
            id = 7,
        )
        assertTrue(
            PortraitComposer.compose(listOf(bogus), now).isEmpty(),
            "闭集外的谓词不得变成侧写",
        )
    }

    // ── 曲库侧写：直接 L3，不衰减 ───────────────────────────────────────

    @Test
    fun libraryPortrait_landsOnL3_andCarriesEvidenceRefs() {
        val evidence = listOf(
            libraryEvidence(PortraitType.SCALE, LibraryModeler.SCALE_MEDIUM, id = 11),
            libraryEvidence(PortraitType.PATH_GROUPS, "深夜,爵士", id = 12),
        )
        val portraits = PortraitComposer.compose(evidence, now)

        assertEquals(1, portraits.size)
        val p = portraits.single()
        assertEquals(PortraitType.LIBRARY, p.type)
        assertEquals(PortraitTier.L3, p.tier, "曲库是长期资产，不靠跨会话晋升")
        assertEquals(LibraryModeler.SCALE_MEDIUM, p.slots[PortraitType.SCALE])
        assertEquals("深夜,爵士", p.slots[PortraitType.PATH_GROUPS])
        assertEquals(listOf(11L, 12L), p.evidenceRefs, "反链要指得到具体证据行")
        assertEquals(0.4, p.confidence, 1e-9)
        assertTrue(p.factualOnly)
    }

    @Test
    fun libraryPortrait_isNotDecayedByTime() {
        val old = now - 30 * oneWeek
        val evidence = listOf(
            ProfileEvidence(
                predicate = PortraitType.predicateOf(PortraitType.LIBRARY, PortraitType.SCALE),
                value = LibraryModeler.SCALE_SMALL,
                source = ProfileSources.LIBRARY_SHAPE,
                confidence = 0.4,
                id = 1,
                createdAt = old,
                updatedAt = old,
            )
        )
        assertEquals(
            0.4,
            PortraitComposer.compose(evidence, now).single().confidence,
            1e-9,
            "曲库没变不等于这份认识过期 —— 曲库侧写不随时间衰减",
        )
    }

    // ── 阶段二"纠正"阶段一：同槽位选优的自然结果 ─────────────────────────

    @Test
    fun contentModeling_overridesShapeForSameSlot_byConfidence() {
        val evidence = listOf(
            // 形态阶段先判「口味集中」
            libraryEvidence(PortraitType.ARTIST_CONCENTRATION, LibraryModeler.CONCENTRATION_FOCUSED, id = 1),
            // 内容阶段改判「人集中但风格分散」（槽位同名，置信度更高）
            libraryEvidence(
                slot = PortraitType.ARTIST_CONCENTRATION,
                value = LibraryModeler.CONCENTRATION_BROAD,
                source = ProfileSources.LIBRARY_CONTENT,
                confidence = 0.55,
                id = 2,
            ),
        )
        val p = PortraitComposer.compose(evidence, now).single()

        assertEquals(
            LibraryModeler.CONCENTRATION_BROAD,
            p.slots[PortraitType.ARTIST_CONCENTRATION],
            "内容建模应当覆盖形态建模的同名槽位 —— 这是选优的自然结果，不需要额外机制",
        )
        assertEquals(0.55, p.confidence, 1e-9)
        assertEquals(listOf(2L), p.evidenceRefs, "只有胜出的那条进反链")
    }

    @Test
    fun tieOnConfidence_prefersMostRecentlyUpdated() {
        val evidence = listOf(
            libraryEvidence(PortraitType.DURATION_TENDENCY, "SHORT", id = 1).copy(updatedAt = now - oneWeek),
            libraryEvidence(PortraitType.DURATION_TENDENCY, "LONG", id = 2).copy(updatedAt = now),
        )
        assertEquals(
            "LONG",
            PortraitComposer.compose(evidence, now).single().slots[PortraitType.DURATION_TENDENCY],
            "同分取更晚更新者（否则同一份数据两次派生结果可能不同）",
        )
    }

    // ── 否决 ───────────────────────────────────────────────────────────

    @Test
    fun deniedType_producesNoPortrait() {
        val evidence = listOf(
            libraryEvidence(PortraitType.SCALE, LibraryModeler.SCALE_LARGE, id = 1),
            ProfileEvidence(
                predicate = PortraitType.DENIED,
                value = PortraitType.LIBRARY.id,
                source = ProfileSources.T1_USER,
                confidence = 0.9,
                id = 2,
            ),
        )
        assertTrue(
            PortraitComposer.compose(evidence, now).isEmpty(),
            "用户否决后不得重建（剧本 P14）",
        )
    }

    // ── 行为侧写的分层与衰减 ───────────────────────────────────────────

    @Test
    fun behaviorPortrait_staysL2_withoutCrossSessionEvidence() {
        val evidence = listOf(
            ProfileEvidence(
                predicate = PortraitType.predicateOf(PortraitType.HABITS, PortraitType.COMPLETION_RATE),
                value = "HIGH",
                source = ProfileSources.T0_BEHAVIOR,
                confidence = 0.6,
                id = 1,
                createdAt = now - 10 * oneWeek,
                updatedAt = now,
                distinctSessions = 1,
            )
        )
        assertEquals(PortraitTier.L2, PortraitComposer.compose(evidence, now).single().tier)
    }

    @Test
    fun behaviorPortrait_promotesToL3_afterCrossSessionAndDuration() {
        val evidence = listOf(
            ProfileEvidence(
                predicate = PortraitType.predicateOf(PortraitType.HABITS, PortraitType.COMPLETION_RATE),
                value = "HIGH",
                source = ProfileSources.T0_BEHAVIOR,
                confidence = 0.6,
                id = 1,
                createdAt = now - 10 * oneWeek,
                updatedAt = now,
                distinctSessions = ProfileConfig.PROMOTE_MIN_SESSIONS,
            )
        )
        assertEquals(PortraitTier.L3, PortraitComposer.compose(evidence, now).single().tier)
    }

    @Test
    fun behaviorPortrait_decays_whenStaleForWeeks_butIsNotDropped() {
        val stale = now - (ProfileConfig.DECAY_AFTER_WEEKS + 1) * oneWeek
        val evidence = listOf(
            ProfileEvidence(
                predicate = PortraitType.predicateOf(PortraitType.HABITS, PortraitType.COMPLETION_RATE),
                value = "LOW",
                source = ProfileSources.T0_BEHAVIOR,
                confidence = 0.6,
                id = 1,
                createdAt = now - 20 * oneWeek,
                updatedAt = stale,
                distinctSessions = 5,
            )
        )
        val composed = PortraitComposer.compose(evidence, now)
        assertEquals(1, composed.size, "降权不等于删除 —— 设置页仍要看得见")
        assertEquals(0.3, composed.single().confidence, 1e-9)
    }

    // ── 上下文门槛：只过滤推断类 ───────────────────────────────────────

    @Test
    fun contextGate_letsFactualPortraitsThroughBelowFloor() {
        val evidence = listOf(libraryEvidence(PortraitType.SCALE, LibraryModeler.SCALE_SMALL, id = 1))
        val portraits = PortraitComposer.compose(evidence, now)

        assertEquals(0.4, portraits.single().confidence, 1e-9)
        assertEquals(
            1,
            PortraitComposer.selectForContext(portraits).size,
            "曲库形态（0.4）必须能进上下文，否则新用户第一次对话永远是空画像",
        )
    }

    @Test
    fun contextGate_blocksLowConfidenceInference() {
        val evidence = listOf(
            ProfileEvidence(
                predicate = PortraitType.predicateOf(PortraitType.TASTE, PortraitType.LEADING_GENRE),
                value = "JAZZ",
                source = ProfileSources.T2_DIALOGUE,
                confidence = 0.35,
                id = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        val portraits = PortraitComposer.compose(evidence, now)
        assertEquals(1, portraits.size)
        assertTrue(
            PortraitComposer.selectForContext(portraits).isEmpty(),
            "对话推断（0.35）低于门槛，不该进上下文",
        )
    }

    @Test
    fun contextSelection_ordersL3First_thenConfidence() {
        val evidence = listOf(
            libraryEvidence(PortraitType.SCALE, LibraryModeler.SCALE_SMALL, id = 1),
            ProfileEvidence(
                predicate = PortraitType.predicateOf(PortraitType.HABITS, PortraitType.COMPLETION_RATE),
                value = "HIGH",
                source = ProfileSources.T0_BEHAVIOR,
                confidence = 0.9,
                id = 2,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val ordered = PortraitComposer.selectForContext(PortraitComposer.compose(evidence, now))
        assertEquals(PortraitTier.L3, ordered.first().tier, "L3 在前（更稳）")
    }
}
