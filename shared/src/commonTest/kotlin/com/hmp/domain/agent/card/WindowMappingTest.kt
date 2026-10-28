package com.hmp.domain.agent.card

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 一-3 / D5-02 + D5-03 判据的纯函数那半：`NarrativeTimeRange → days → 窗口起点`这条链。
 *
 * D5-02 的病根是"时段维度没走 `daysToCutoffMs`，自己算了还 `coerceAtLeast(1)`"，
 * 于是 `-1`（全部）被夹成 1 天。这里钉住 `-1` 是**合法的"全部"哨兵**，
 * 且在 `daysToCutoffMs` 里确实映射到比一年更早的起点 —— 谁的映射改了都会红。
 *
 * D5-03 的另一半（`HelloSubAgent` 里曾私抄一份 `ALL -> 3650`）已由"只留这一份映射"消掉：
 * 下面这组期望值就是全站唯一口径，第二个映射不存在了才谈得上一致。
 */
class WindowMappingTest {

    @Test
    fun fiveRanges_mapToTheDocumentedDayWindows() {
        assertEquals(1, NarrativeTimeRange.DAY.toDays())
        assertEquals(7, NarrativeTimeRange.WEEK.toDays())
        assertEquals(30, NarrativeTimeRange.MONTH.toDays())
        assertEquals(365, NarrativeTimeRange.YEAR.toDays())
        assertEquals(-1, NarrativeTimeRange.ALL.toDays(), "「全部」是 -1 哨兵，不是 3650，也不是 1")
    }

    @Test
    fun cutoff_isMonotonic_andAllIsEarliest() = runTest {
        val all = daysToCutoffMs(NarrativeTimeRange.ALL.toDays())
        val year = daysToCutoffMs(NarrativeTimeRange.YEAR.toDays())
        val week = daysToCutoffMs(NarrativeTimeRange.WEEK.toDays())
        val day = daysToCutoffMs(NarrativeTimeRange.DAY.toDays())

        assertTrue(all < year, "『全部』的起点必须比一年更早")
        assertTrue(year < week && week < day, "窗口必须随天数单调变窄")
    }

    @Test
    fun zeroAndNegativeOtherThanAll_areNotSilentlyWidened() = runTest {
        // days=0 不是合法窗口：coerceAtLeast(1) 那种"静默夹取"正是 D5-02 要拿掉的东西。
        // 现在它按字面算 = 起点即现在 → 窗口为空。调用方要"全部"必须显式传 -1。
        val now = com.hmp.data.database.currentTimeMillis()
        assertTrue(daysToCutoffMs(0) >= now, "days=0 应当给出空窗口，而不是被夹成 1 天")
    }
}
