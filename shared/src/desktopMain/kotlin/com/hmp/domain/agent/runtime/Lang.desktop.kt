package com.hmp.domain.agent.runtime

import java.util.Locale

actual fun detectSystemLang(): Lang {
    return when (Locale.getDefault().language) {
        "zh" -> Lang.ZH
        else -> Lang.EN
    }
}
