package com.hmp.domain.setting.model

import kotlinx.serialization.Serializable

@Serializable
data class ScanDirectoryConfig(
    val scanDirectories: List<String> = emptyList(),
    val blockedDirectories: List<String> = emptyList(),
)
