package com.hearablemusic.player.ui.settings.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 一-3 / D5-08 判据：热力图的格子必须站在**它自己那一天的真实星期**上，缺听歌的日子要占格而不是被压缩掉。
 *
 * 日期都是真实日历（2026-03-01 是周日、2026-03-05 是周四、2026-01-05 是周一），
 * 期望列号按"周一为一行第一列"算 —— 与被测函数的实现路径无关，改错对齐就会红。
 */
class ListeningHeatmapTest {

    private fun cellOf(grid: HeatmapGrid, date: String): Pair<Int, Int>? {
        grid.rows.forEachIndexed { row, cells ->
            val col = cells.indexOfFirst { it.date == date }
            if (col >= 0) return row to col
        }
        return null
    }

    @Test
    fun daysLandOnTheirRealWeekdayColumn() {
        val grid = buildListeningHeatmap(
            minutesByDate = mapOf(
                "2026-03-01" to 30,   // 周日
                "2026-03-05" to 45,   // 周四
            ),
            today = "2026-03-08",     // 周日
            dayCount = 8,             // 窗口 2026-03-01 … 2026-03-08
        )

        // 周一为一行第 0 列 → 周四 = 3、周日 = 6
        val thursday = cellOf(grid, "2026-03-05")
        val sunday = cellOf(grid, "2026-03-01")
        assertEquals(3, thursday?.second, "周四该落在第 4 列（周一为首列）")
        assertEquals(6, sunday?.second, "周日该落在第 7 列")
        assertEquals(45, grid.rows[thursday!!.first][thursday.second].minutes)
    }

    @Test
    fun emptyDaysKeepTheirCells_insteadOfBeingCompactedAway() {
        val grid = buildListeningHeatmap(
            minutesByDate = mapOf("2026-03-01" to 30, "2026-03-05" to 45),
            today = "2026-03-08",
            dayCount = 8,
        )

        // 旧实现是 listeningData.takeLast(35)：2/3/4 日没听歌就整段不占格，
        // 于是"35 格"既不是 35 天也不是 5 周。现在它们必须是零值格。
        listOf("2026-03-02", "2026-03-03", "2026-03-04").forEach { date ->
            val at = cellOf(grid, date)
            assertTrue(at != null, "$date 在窗口内，不该被压缩掉")
            assertEquals(0, grid.rows[at!!.first][at.second].minutes, "$date 没听歌 → 零值格（而不是缺格）")
        }
        assertEquals(8, grid.inWindowDays, "8 天窗口就该有 8 个在窗口的格子")
    }

    @Test
    fun everyRowHasSevenCells() {
        val grid = buildListeningHeatmap(
            minutesByDate = mapOf("2026-03-05" to 45),
            today = "2026-03-08",
            dayCount = 8,
        )
        assertTrue(grid.rows.isNotEmpty())
        grid.rows.forEachIndexed { index, row ->
            assertEquals(7, row.size, "第 $index 行不满 7 格 —— 星期标签就会与列错位")
        }
    }

    /** 「全年」这一维是真话：365 天窗口 = 53 行（首末日按周一锚定，不多出一整行）。 */
    @Test
    fun fullYearViewIs53WeekRows() {
        val grid = buildListeningHeatmap(
            minutesByDate = mapOf("2026-01-05" to 10),
            today = "2027-01-04",
            dayCount = 365,
        )

        assertEquals(53, grid.weeks, "全年视图的行数")
        assertEquals(365, grid.inWindowDays, "窗口内应有 365 个日历日")
        // 2026-01-05 是周一 → 该在第一行第一列
        assertEquals(0 to 0, cellOf(grid, "2026-01-05"))
    }

    /** 窗口外的补齐格（首行左侧、末行右侧）不能带上日期，否则会被当成"那天没听歌"。 */
    @Test
    fun paddingCellsAreMarkedAsOutsideTheWindow() {
        val grid = buildListeningHeatmap(
            minutesByDate = emptyMap(),
            today = "2026-03-05",   // 周四
            dayCount = 1,           // 只看这一天
        )

        assertEquals(1, grid.inWindowDays)
        val padding = grid.rows.flatten().count { it.date == null }
        // 窗口首日 2026-03-05 是周四 → 锚到本周一 2026-03-02：前面补 3 格、后面补 3 格（该行只有一天在窗口内）
        assertEquals(6, padding, "单天窗口锚到周后应有 6 个窗口外格")
    }
}
