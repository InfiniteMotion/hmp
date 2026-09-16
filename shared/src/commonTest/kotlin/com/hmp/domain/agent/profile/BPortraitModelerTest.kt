package com.hmp.domain.agent.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B 类三侧写建模单测 —— 契约 §5.2（可弱确证，只能降级表达）。
 *
 * 重点覆盖：会话数不足不说（数据太少就不说）、确定性是"交叉"不是单项、
 * moodConsistency 继承覆盖率折扣、无数据维度的槽位（structureTolerance / energyTendency）不产证据。
 */
class BPortraitModelerTest {

    private fun snapshot(
        totalPlays: Int = 40,
        activeDays: Int = 6,
        distinctTracks: Int = 10,
        novelTracks: Int = 2,
        sessionCount: Int = 5,
        avgSessionMinutes: Float = 50f,
        avgTracksPerSession: Float = 2f,
    ) = BehaviorSnapshot(
        windowDays = 90,
        totalPlays = totalPlays,
        activeDays = activeDays,
        distinctTracks = distinctTracks,
        novelTracks = novelTracks,
        sessionCount = sessionCount,
        avgSessionMinutes = avgSessionMinutes,
        avgTracksPerSession = avgTracksPerSession,
    )

    private fun slotValue(drafts: List<EvidenceDraft>, type: PortraitType, slot: String): String? =
        drafts.firstOrNull { it.predicate == PortraitType.predicateOf(type, slot) }?.value

    // ── 注意力模式 ──────────────────────────────────────────────────

    @Test
    fun longSessions_lowTrackCount_mapToLongSession_andHighFragmentation() {
        val drafts = BPortraitModeler.toBehaviorEvidenceDrafts(snapshot())
        assertEquals("LONG", slotValue(drafts, PortraitType.ATTENTION, PortraitType.SESSION_LENGTH))
        assertEquals("HIGH", slotValue(drafts, PortraitType.ATTENTION, PortraitType.FRAGMENTATION))
    }

    @Test
    fun shortSessions_manyTracks_mapToShortSession_andLowFragmentation() {
        val drafts = BPortraitModeler.toBehaviorEvidenceDrafts(
            snapshot(avgSessionMinutes = 8f, avgTracksPerSession = 10f),
        )
        assertEquals("SHORT", slotValue(drafts, PortraitType.ATTENTION, PortraitType.SESSION_LENGTH))
        assertEquals("LOW", slotValue(drafts, PortraitType.ATTENTION, PortraitType.FRAGMENTATION))
    }

    @Test
    fun tooFewSessions_attentionSlotsSilent() {
        val drafts = BPortraitModeler.toBehaviorEvidenceDrafts(snapshot(sessionCount = 2))
        assertNull(slotValue(drafts, PortraitType.ATTENTION, PortraitType.SESSION_LENGTH))
        assertNull(slotValue(drafts, PortraitType.ATTENTION, PortraitType.FRAGMENTATION))
        // 确定性不受会话数影响（它来自播放分布，不是会话）
        assertNotNull(slotValue(drafts, PortraitType.ORDER, PortraitType.DETERMINISM))
    }

    // ── 秩序确定性：重复度 × 新歌比例的交叉 ────────────────────────

    @Test
    fun determinismHigh_onlyWhenRepeatAndNotChasing() {
        // playsPerTrack = 40/10 = 4.0 ≥ 3.0；newRatio = 1/10 = 0.1 ≤ 0.1 → HIGH
        val drafts = BPortraitModeler.toBehaviorEvidenceDrafts(
            snapshot(totalPlays = 40, distinctTracks = 10, novelTracks = 1),
        )
        assertEquals("HIGH", slotValue(drafts, PortraitType.ORDER, PortraitType.DETERMINISM))
    }

    @Test
    fun determinismLow_whenAlwaysChasingNew() {
        // playsPerTrack = 20/20 = 1.0 ≤ 1.2；newRatio = 12/20 = 0.6 ≥ 0.4 → LOW
        val drafts = BPortraitModeler.toBehaviorEvidenceDrafts(
            snapshot(totalPlays = 20, distinctTracks = 20, novelTracks = 12),
        )
        assertEquals("LOW", slotValue(drafts, PortraitType.ORDER, PortraitType.DETERMINISM))
    }

    @Test
    fun determinismMedium_whenSignalsMixed() {
        // playsPerTrack 高但也在追新：两个条件都不成立 → MEDIUM
        val drafts = BPortraitModeler.toBehaviorEvidenceDrafts(
            snapshot(totalPlays = 40, distinctTracks = 10, novelTracks = 5),
        )
        assertEquals("MEDIUM", slotValue(drafts, PortraitType.ORDER, PortraitType.DETERMINISM))
    }

    // ── 主样本量闸门 ────────────────────────────────────────────────

    @Test
    fun insufficientPlays_produceNothing() {
        assertTrue(BPortraitModeler.toBehaviorEvidenceDrafts(snapshot(totalPlays = 10)).isEmpty())
        assertTrue(BPortraitModeler.toBehaviorEvidenceDrafts(snapshot(activeDays = 1)).isEmpty())
    }

    // ── 无数据维度的槽位绝不产证据（不假装知道）────────────────────

    @Test
    fun structureTolerance_andEnergyTendency_haveNoWriter() {
        val drafts = BPortraitModeler.toBehaviorEvidenceDrafts(snapshot())
        assertNull(slotValue(drafts, PortraitType.ORDER, PortraitType.STRUCTURE_TOLERANCE))
        assertNull(slotValue(drafts, PortraitType.INTENSITY, PortraitType.ENERGY_TENDENCY))
    }

    // ── 情绪一致性（曲库 MOOD 近似）────────────────────────────────

    @Test
    fun moodConsistency_mapsMoodMix_withCoverageDiscount() {
        val draft = BPortraitModeler.moodConsistencyDraft(LibraryModeler.MIX_SINGLE, coverageRate = 0.8f)
        assertNotNull(draft)
        assertEquals("HIGH", draft.value)
        assertEquals(ProfileSources.LIBRARY_CONTENT, draft.source)
        assertEquals(0.55 * 0.8f.toDouble(), draft.confidence, 1e-6)
    }

    @Test
    fun moodConsistency_variedMapsLow_andLowCoverageIsSilent() {
        assertEquals("LOW", BPortraitModeler.moodConsistencyDraft(LibraryModeler.MIX_VARIED, 0.9f)?.value)
        assertEquals("MEDIUM", BPortraitModeler.moodConsistencyDraft(LibraryModeler.MIX_MEDIUM, 0.9f)?.value)
        assertNull(BPortraitModeler.moodConsistencyDraft(LibraryModeler.MIX_SINGLE, 0.2f), "覆盖率过低不写")
        assertNull(BPortraitModeler.moodConsistencyDraft(null, 0.9f))
    }
}
