package com.hmp.platform

/**
 * 跨平台 @Volatile 桥接。
 * JVM/Android → actual typealias 到 kotlin.jvm.Volatile
 * iOS/Native  → actual 为空注解（Kotlin/Native 单线程隔离模型下无需 volatile 可见性保证）
 */
expect annotation class Volatile()
