package com.hmp.domain.setting.model

import com.hmp.data.database.currentTimeMillis
import kotlinx.serialization.Serializable

@Serializable
data class ListeningDuration(
    val date: String,
    val duration: Long,
    val updatedAt: Long = currentTimeMillis(),
)
