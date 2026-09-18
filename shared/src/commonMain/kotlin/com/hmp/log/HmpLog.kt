package com.hmp.log

import co.touchlab.kermit.Logger

/**
 * 唯一日志门面（极薄委托，零新依赖，底层 Kermit，见 docs/LOGGING.md）。
 * 所有代理日志一律经此入口；禁止散落 print* 或裸调 Logger。
 */
object HmpLog {
    fun d(tag: LogTag, msg: () -> String) = Logger.d(tag.v) { msg() }
    fun i(tag: LogTag, msg: () -> String) = Logger.i(tag.v) { msg() }
    fun w(tag: LogTag, e: Throwable? = null, msg: () -> String) = Logger.w(e, tag.v) { msg() }
    fun e(tag: LogTag, e: Throwable? = null, msg: () -> String) = Logger.e(e, tag.v) { msg() }
}