package com.hmp

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.hmp.log.MemLogWriter

/**
 * 三端统一的 Kermit 日志初始化。
 *
 * 在默认 platformLogWriter()（系统目标）之外追加一个 [MemLogWriter]（内存环形缓冲）：
 * - Android → android.util.Log（Logcat 原生 TAG 过滤）
 * - iOS    → OSLog（进入 Xcode Console 系统日志）
 * - Desktop → System.out + ANSI 颜色
 *
 * 日志同时进内存缓冲 + 系统目标，两路互不干扰；内存缓冲有界（见 [MemLogWriter]）。
 *
 * Release 构建时传入 Severity.Warn 屏蔽 DEBUG/INFO。
 */
fun initKermit(minSeverity: Severity = Severity.Debug) {
    Logger.setMinSeverity(minSeverity)
    Logger.addLogWriter(memLogWriter)
}

/**
 * Swift/iOS 便捷入口 —— 避免 Swift 侧直接引用 Severity 枚举。
 * Release 构建时 Swift 用 Bool.isReleaseBuild 传 true，由 Kotlin 侧映射 Severity。
 */
fun initKermitForIos(isReleaseBuild: Boolean = false) {
    Logger.setMinSeverity(if (isReleaseBuild) Severity.Warn else Severity.Debug)
    Logger.addLogWriter(memLogWriter)
}

/** 全局共享的内存日志缓冲（单例，供 UI 订阅）。 */
val memLogWriter: MemLogWriter by lazy { MemLogWriter() }
