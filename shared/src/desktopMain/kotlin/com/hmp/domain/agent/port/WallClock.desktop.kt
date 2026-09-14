package com.hmp.domain.agent.port

import java.time.LocalDateTime

actual fun currentLocalMoment(): LocalMoment {
    val now = LocalDateTime.now()
    return LocalMoment(
        hourOfDay = now.hour,
        dayOfWeek = now.dayOfWeek.value,   // java.time: 1 = 周一 … 7 = 周日，与 ISO 一致
    )
}
