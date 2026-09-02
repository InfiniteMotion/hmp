package com.hmp.platform

import com.hmp.platform.Synchronized

/**
 * 跨平台 @Synchronized 桥接。
 * JVM/Android → actual typealias 到 kotlin.jvm.Synchronized（JVM monitor）
 * iOS/Native  → actual 为空注解（Kotlin/Native 单线程隔离模型下无并发竞争）
 */
expect annotation class Synchronized()
