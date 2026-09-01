package com.hmp

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * 三端统一的 Kermit 日志初始化。
 *
 * Kermit 默认已使用 platformLogWriter()：
 * - Android → android.util.Log（Logcat 原生 TAG 过滤）
 * - iOS    → OSLog（进入 Xcode Console 系统日志）
 * - Desktop → System.out + ANSI 颜色
 *
 * Release 构建时传入 Severity.Warn 屏蔽 DEBUG/INFO。
 */
fun initKermit(minSeverity: Severity = Severity.Debug) {
    Logger.setMinSeverity(minSeverity)
}

/**
 * Swift/iOS 便捷入口 —— 避免 Swift 侧直接引用 Severity 枚举。
 * Release 构建时 Swift 用 Bool.isReleaseBuild 传 true，由 Kotlin 侧映射 Severity。
 */
fun initKermitForIos(isReleaseBuild: Boolean = false) {
    Logger.setMinSeverity(if (isReleaseBuild) Severity.Warn else Severity.Debug)
}
