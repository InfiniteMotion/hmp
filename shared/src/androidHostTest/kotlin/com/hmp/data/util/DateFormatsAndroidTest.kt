package com.hmp.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * X-06：`shared/src/androidHostTest` 的第一批用例 —— 之前 `shared` 根本没有这个源集，
 * `DateFormats.android.kt`（53 行日期换算）零测试。
 *
 * 这里只断言**与本地时区无关的不变量**：同一套断言在 UTC / UTC+8 / 有夏令时的时区都应成立，
 * 否则测试会随跑测机器的时区变红/变绿，那种绿是假的。
 *
 * 这些用例不需要 Robolectric：`DateFormats.android` 只用了 `java.util`，不碰 Android 框架。
 */
class DateFormatsAndroidTest {

    @Test
    fun dateStringRoundTripsThroughParse() {
        val date = todayDateString()
        val millis = parseDateToMillis(date)
        assertNotNull("今天这种 yyyy-MM-dd 必须能解析回毫秒", millis)

        // 解析结果落在该本地日的 [00:00, 次日 00:00) 内（跨夏令时因此用 <= 25h 的宽区间）
        val range = localDayRange(date)!!
        assertTrue("$millis 应不早于当日 0 点", millis!! >= range.first)
        val span = range.second - range.first
        assertTrue("一天的跨度应在 23h–25h 之间（夏令时），实际 $span ms", span in 23 * 3600_000L..25 * 3600_000L)
    }

    @Test
    fun parseRejectsGarbage() {
        assertNull(parseDateToMillis("not-a-date"))
        assertNull(parseDateToMillis(""))
    }

    @Test
    fun mmddIsTheLocalDateSuffix() {
        // 两个函数用同一时区格式化同一个时间点，月份-日必须是同一组数字
        val now = System.currentTimeMillis()
        val mmdd = formatMmddFromMillis(now)
        val full = localDateString(now)
        assertEquals(full.substring(5), mmdd)
    }

    @Test
    fun hourOfDayIsWithinRangeAndConsistent() {
        val now = System.currentTimeMillis()
        val hour = localHourOfDay(now)
        assertTrue("小时应在 0..23，实际 $hour", hour in 0..23)
        assertTrue("当前小时也应在 0..23", currentHour() in 0..23)
    }

    @Test
    fun isoWeekdayIsOneToSeven() {
        val now = System.currentTimeMillis()
        assertTrue("ISO 周几应在 1..7，实际 ${localDayOfWeekIso(now)}", localDayOfWeekIso(now) in 1..7)
    }

    @Test
    fun millisUntilNextMidnightIsWithinOneDay() {
        val ms = millisUntilNextLocalMidnight()
        assertTrue("距次日 0 点应为正，实际 $ms", ms > 0)
        assertTrue("不应超过 25h（夏令时），实际 $ms", ms <= 25 * 3600_000L)
    }
}
