package com.hearablemusic.player.ui.common.util

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeIntervalSince1970

/** iOS actual：NSDate / NSCalendar / NSDateFormatter（本地时区与语言环境，epoch 毫秒语义）。 */
actual fun epochMillisToYearMonth(epochMillis: Long): Pair<Int, Int> {
    val date = NSDate(timeIntervalSinceReferenceDate = epochMillis / 1000.0 - NSTimeIntervalSince1970)
    val comps = NSCalendar.currentCalendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth,
        fromDate = date,
    )
    return comps.year.toInt() to comps.month.toInt()
}

actual fun nowEpochMillis(): Long = ((NSDate().timeIntervalSinceReferenceDate + NSTimeIntervalSince1970) * 1000.0).toLong()

actual fun formatEpochMillis(epochMillis: Long, pattern: String): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = pattern
    }
    return formatter.stringFromDate(
        NSDate(timeIntervalSinceReferenceDate = epochMillis / 1000.0 - NSTimeIntervalSince1970),
    )
}
