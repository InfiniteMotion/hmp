package com.hearablemusic.player.ui.settings.components

import com.hmp.data.util.localDateString
import com.hmp.data.util.localDayOfWeekIso
import com.hmp.data.util.parseDateToMillis
import com.hmp.data.util.todayDateString

/**
 * 热力图的一格。
 *
 * `date == null` 是**窗口外的补齐格**（首行周一起点、末行周日收尾会带进来几天不属于窗口的日期），
 * 与"这一天在窗口内但没听歌"（`date != null && minutes == 0`）必须能区分开 —— 前者不该上色，
 * 后者是数据本身。
 */
data class HeatmapCell(val date: String?, val minutes: Int)

/** 行 = 周（周一至周日为一行），列 = 星期几（恒 7 列）。 */
data class HeatmapGrid(val rows: List<List<HeatmapCell>>) {
    val weeks: Int get() = rows.size
    val inWindowDays: Int get() = rows.flatten().count { it.date != null }
}

/**
 * 日期序列 → 网格（D5-08 的判据落点，刻意做成不经 Compose 的纯函数）。
 *
 * 旧实现把两件事都做错了：
 * 1. 喂进来的 `listeningData.takeLast(35)` 是「**有听歌的**最后 35 行」，没听歌的日子整段被压缩掉，
 *    所以那 35 格既不是最近 35 天、也不是最近 5 周，更不是站点与 README 承诺的「全年」；
 * 2. 表头硬编码 `M T W T F S S`，填充是行主序 `index = row * cols + col` ——
 *    列与那一天真正的星期毫无关系，只有恰好从周一开始的连续 35 天才对得上。
 *
 * 现在：窗口是 `[today-dayCount+1, today]` 的连续日历日，按**本地星期**对齐列，缺听歌的日子补零值格。
 *
 * @param minutesByDate `yyyy-MM-dd` → 当日分钟数
 * @param today 本地今日（注入而非内部取时钟，判据才可控）
 * @param dayCount 窗口天数（365 = 全年视图）
 */
fun buildListeningHeatmap(
    minutesByDate: Map<String, Int>,
    today: String = todayDateString(),
    dayCount: Int = 365,
): HeatmapGrid {
    require(dayCount > 0) { "dayCount 必须是正数窗口，实际 $dayCount" }
    val start = shiftLocalDate(today, -(dayCount - 1)) ?: return HeatmapGrid(emptyList())

    // 首行锚定到「窗口首日的本周一」，这样每一列的星期几在整个网格里恒定
    val startWeekday = localDayOfWeekIso(parseDateToMillis(start) ?: return HeatmapGrid(emptyList()))
    val anchor = shiftLocalDate(start, -(startWeekday - 1)) ?: start

    val cells = mutableListOf<HeatmapCell>()
    var cursor = anchor
    var guard = 0
    val windowEnd = today
    while (guard < MAX_CELLS) {
        guard++
        // 锚定到周会后，首行左边那几天在窗口之前 —— 两面都要卡住，否则"补齐格"会被当成"那天没听歌"
        val inWindow = cursor >= start && cursor <= windowEnd
        val minutes = if (inWindow) (minutesByDate[cursor] ?: 0) else 0
        cells += HeatmapCell(date = if (inWindow) cursor else null, minutes = if (inWindow) minutes else 0)
        if (cursor == windowEnd) break
        cursor = shiftLocalDate(cursor, 1) ?: break
    }

    // 末行补满 7 格
    while (cells.size % 7 != 0) cells += HeatmapCell(date = null, minutes = 0)

    return HeatmapGrid(cells.chunked(7))
}

/** 中午锚点：跨夏令时的日子里 `本地午夜 + N×24h` 会漂到相邻日，锚在正午就稳（±1h 仍在同一天）。 */
private const val NOON_ANCHOR_MS = 12L * 3_600_000L

/** 迭代上限：一年视图最多 53 个整周 + 首尾补齐 ≈ 371 格，留出余量只为兜住异常输入，不是设计容量。 */
private const val MAX_CELLS = 500

/** `date + deltaDays`（本地日历日）。解析失败返回 null，由调用方停止迭代而不是猜。 */
private fun shiftLocalDate(date: String, deltaDays: Int): String? {
    val midnight = parseDateToMillis(date) ?: return null
    return localDateString(midnight + deltaDays * 86_400_000L + NOON_ANCHOR_MS)
}
