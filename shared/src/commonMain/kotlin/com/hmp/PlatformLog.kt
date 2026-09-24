package com.hmp

import co.touchlab.kermit.Severity

/**
 * 跨平台统一日志入口 —— Swift/iOS 桥接层调用此 expect，各端 actual 直接委托到 Kermit。
 *
 * ⚠️ 这是**桥接实现**，不是业务门面。业务代码用 Kotlin `HmpLog`；Swift 侧用 `HmpLog.swift` 的 `HmpLog`。
 *
 * Swift 侧用法（经 `HmpLog` 门面，不要直调本函数）：
 * ```swift
 * HmpLog.i(HmpTag.playerIos, "▶️ playback started")
 * ```
 *
 * severity 映射：
 *   0 = Debug, 1 = Info, 2 = Warn, 3 = Error
 */
expect fun platformLog(severity: Int, tag: String, message: String)

fun severityFromInt(severity: Int): Severity = when (severity) {
    0 -> Severity.Debug
    2 -> Severity.Warn
    3 -> Severity.Error
    else -> Severity.Info
}
