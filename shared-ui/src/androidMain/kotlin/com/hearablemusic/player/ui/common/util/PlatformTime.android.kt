package com.hearablemusic.player.ui.common.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Android actual：与原 MusicListConfig 行为一致的 Calendar 实现。 */
actual fun epochMillisToYearMonth(epochMillis: Long): Pair<Int, Int> {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMillis
    return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
}

actual fun nowEpochMillis(): Long = Calendar.getInstance().timeInMillis

/** Android actual：与原 SongDetailScreen 的 SimpleDateFormat 行为一致。 */
actual fun formatEpochMillis(epochMillis: Long, pattern: String): String {
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(Date(epochMillis))
}
