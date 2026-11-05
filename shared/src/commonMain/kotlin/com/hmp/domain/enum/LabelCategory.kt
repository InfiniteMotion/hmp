package com.hmp.domain.enum

import kotlinx.serialization.Serializable

@Serializable
enum class LabelCategory {
    GENRE,
    MOOD,
    SCENARIO,
    LANGUAGE,
    ERA,
}
