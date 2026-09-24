package com.hmp.domain.agent.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 行为建模单测 —— 契约 §4.2。
 *
 * 重点验三件事：**数据太少就不说**、**跳过点分桶**（本模块最被低估的信号）、
 * 以及各槽位落进闭集。
 */
class BehaviorModelerTest {

    private fun snapshot(
        totalPlays: Int = 100,
        activeDays: Int = 20,
        hours: Map<Int, Int> = mapOf(22 to 10, 23 to 8, 21 to 6),
        completed: Int = 70,
        skips: Map<String, Int> = mapOf(BehaviorModeler.SKIP_INTRO to 12),
        distinctTracks: Int = 40,
        novelTracks: Int = 5,
    ) = BehaviorSnapshot(
        windowDays = 90,
        totalPlays = totalPlays,
        activeDays = activeDays,
        hourHistogram = hours,
        completedPlays = completed,
        skipPointBuckets = skips,
        distinctTracks = distinctTracks,
        novelTracks = novelTracks,
    )

    private fun slotsOf(s: BehaviorSnapshot): Map<String, String> =
        BehaviorModeler.toEvidenceDrafts(s, sessionId = "behavior-1")
            .associate { PortraitType.parse(it.predicate)!!.second to it.value }

    // ── 数据太少就不说 ─────────────────────────────────────────────────

    @Test
    fun noSignal_producesNothing() {
        assertTrue(BehaviorModeler.toEvidenceDrafts(snapshot(totalPlays = 0, activeDays = 0)).isEmpty())
        assertTrue(
            BehaviorModeler.toEvidenceDrafts(snapshot(totalPlays = 5, activeDays = 1)).isEmpty(),
            "三次播放推不出习惯，只能推出一晚的心情",
        )
    }

    // ── 时段 ───────────────────────────────────────────────────────────

    @Test
    fun timePortrait_picksPrimaryAndSecondary() {
        val slots = slotsOf(snapshot(hours = mapOf(22 to 10, 23 to 8, 8 to 3)))
        assertEquals(BehaviorModeler.PART_NIGHT, slots[PortraitType.PRIMARY_PART])
        assertEquals(BehaviorModeler.PART_MORNING, slots[PortraitType.SECONDARY_PART])
    }

    @Test
    fun timePortrait_stabilityFollowsConcentration() {
        val focused = slotsOf(snapshot(hours = mapOf(22 to 20, 10 to 1)))
        assertEquals(BehaviorModeler.STABILITY_HIGH, focused[PortraitType.PART_STABILITY])

        val scattered = slotsOf(snapshot(hours = mapOf(2 to 5, 8 to 4, 12 to 4, 16 to 4, 20 to 3)))
        assertEquals(BehaviorModeler.STABILITY_LOW, scattered[PortraitType.PART_STABILITY])
    }

    // ── 听法 ───────────────────────────────────────────────────────────

    @Test
    fun habits_completionAndRepeatBands() {
        val eager = slotsOf(snapshot(completed = 90, totalPlays = 100, distinctTracks = 40))
        assertEquals(BehaviorModeler.RATE_HIGH, eager[PortraitType.COMPLETION_RATE])
        // 100 次播放 / 40 首 = 2.5 次每首 → 不上不下
        assertEquals(BehaviorModeler.RATE_MEDIUM, eager[PortraitType.REPEAT_RATE])

        val repeating = slotsOf(snapshot(completed = 20, totalPlays = 200, distinctTracks = 20))
        assertEquals(BehaviorModeler.RATE_LOW, repeating[PortraitType.COMPLETION_RATE])
        assertEquals(BehaviorModeler.RATE_HIGH, repeating[PortraitType.REPEAT_RATE])
    }

    @Test
    fun skipPoint_reportedOnlyWhenThereAreEnoughSkips() {
        val few = slotsOf(snapshot(skips = mapOf(BehaviorModeler.SKIP_INTRO to 2)))
        assertTrue(
            PortraitType.SKIP_POINT !in few,
            "两次跳过说明不了任何事，不该下结论",
        )

        val many = slotsOf(
            snapshot(skips = mapOf(BehaviorModeler.SKIP_INTRO to 30, BehaviorModeler.SKIP_MIDDLE to 5))
        )
        assertEquals(BehaviorModeler.SKIP_INTRO, many[PortraitType.SKIP_POINT])
    }

    // ── 探索 ───────────────────────────────────────────────────────────

    @Test
    fun exploration_newVsReturning() {
        val explorer = slotsOf(snapshot(distinctTracks = 40, novelTracks = 30))
        assertEquals(BehaviorModeler.EXPLORE_HIGH, explorer[PortraitType.NEW_RATIO])
        assertEquals(BehaviorModeler.RATE_LOW, explorer[PortraitType.RETURN_RATE])

        val repeater = slotsOf(snapshot(distinctTracks = 40, novelTracks = 0))
        assertEquals(BehaviorModeler.EXPLORE_REPEAT, repeater[PortraitType.NEW_RATIO])
        assertEquals(BehaviorModeler.RATE_HIGH, repeater[PortraitType.RETURN_RATE])
    }

    // ── 纪律 ───────────────────────────────────────────────────────────

    @Test
    fun allEvidence_isT0Behavior_andInsideClosure() {
        val drafts = BehaviorModeler.toEvidenceDrafts(snapshot())
        assertTrue(drafts.isNotEmpty())
        drafts.forEach {
            assertEquals(ProfileSources.T0_BEHAVIOR, it.source)
            assertEquals(0.6, it.confidence, 1e-9)
            assertTrue(PortraitType.isKnown(it.predicate), "越界谓词：${it.predicate}")
        }
        // 三个侧写都该有产出
        val types = drafts.mapNotNull { PortraitType.parse(it.predicate)?.first }.toSet()
        assertEquals(
            setOf(PortraitType.TIME, PortraitType.HABITS, PortraitType.EXPLORATION),
            types,
        )
    }

    @Test
    fun partOf_coversAllHoursWithoutGaps() {
        val parts = (0..23).map { BehaviorModeler.partOf(it) }
        assertEquals(24, parts.size)
        assertTrue(parts.none { it.isBlank() })
        assertEquals(BehaviorModeler.PART_DAWN, BehaviorModeler.partOf(3))
        assertEquals(BehaviorModeler.PART_NIGHT, BehaviorModeler.partOf(23))
    }
}
