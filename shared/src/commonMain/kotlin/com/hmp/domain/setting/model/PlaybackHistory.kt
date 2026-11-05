package com.hmp.domain.setting.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackHistory(
    val id: Long = 0,
    val musicId: Long,
    val playedAt: Long,
    val playDuration: Long = 0,
    val isCompleted: Boolean = false,
    val source: String? = null,
)
