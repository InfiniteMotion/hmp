package com.hearablemusic.player.ui.settings.pages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 一-3 / D5-10 判据：时段柱图的峰值解读要带上小时数。
 *
 * 旧函数体是 `if (next == 0) "-24" else "-"` —— `hour` 压根没进返回值，
 * 于是资源串「%1$s 是你最常听的时段」渲染成「深夜 - 是你最常听的时段」这种缺主语句。
 * `internal` 是为这条判据留的测试缝（同模块的 commonTest 可见，无需把内部工具公开给 UI 之外）。
 */
class HourRangeLabelTest {

    @Test
    fun includesBothEndpoints() {
        assertEquals("22-23", formatHourRange(22))
        assertEquals("0-1", formatHourRange(0))
        assertEquals("12-13", formatHourRange(12))
    }

    /** 跨零点仍写成 `23-0`：与 KDoc 的例子一致，也比 `-24` 这种"看着像 bug"的写法诚实。 */
    @Test
    fun midnightWrapsToZero() {
        assertEquals("23-0", formatHourRange(23))
    }

    @Test
    fun rejectsOutOfRangeHour() {
        assertFailsWith<IllegalArgumentException> { formatHourRange(24) }
        assertFailsWith<IllegalArgumentException> { formatHourRange(-1) }
    }
}
