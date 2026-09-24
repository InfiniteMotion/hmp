package com.hmp

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

actual fun platformLog(severity: Int, tag: String, message: String) {
    val s = severityFromInt(severity)
    when {
        s >= Severity.Error -> Logger.e(null, tag) { message }
        s >= Severity.Warn -> Logger.w(null, tag) { message }
        s >= Severity.Info -> Logger.i(null, tag) { message }
        else -> Logger.d(null, tag) { message }
    }
}
