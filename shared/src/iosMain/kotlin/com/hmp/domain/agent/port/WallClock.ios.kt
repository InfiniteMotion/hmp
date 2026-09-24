package com.hmp.domain.agent.port

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSDate

actual fun currentLocalMoment(): LocalMoment {
    val calendar = NSCalendar.currentCalendar
    val now = NSDate()
    val hour = calendar.component(NSCalendarUnitHour, fromDate = now).toInt()
    // Foundation 的 weekday：1 = 周日 … 7 = 周六，需转成 ISO（1 = 周一 … 7 = 周日）
    val foundationWeekday = calendar.component(NSCalendarUnitWeekday, fromDate = now).toInt()
    val isoDayOfWeek = if (foundationWeekday == 1) 7 else foundationWeekday - 1
    return LocalMoment(
        hourOfDay = hour,
        dayOfWeek = isoDayOfWeek,
    )
}
