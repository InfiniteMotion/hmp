package com.hmp.domain.agent.runtime.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun detectSystemLang(): Lang {
    val first = NSLocale.preferredLanguages.firstOrNull() as? String ?: return Lang.EN
    return if (first.startsWith("zh")) Lang.ZH else Lang.EN
}
