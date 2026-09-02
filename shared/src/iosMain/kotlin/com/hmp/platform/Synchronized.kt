package com.hmp.platform

/** Kotlin/Native 单线程隔离模型下，@Synchronized 为空注解（无 JVM monitor 概念）。 */
actual annotation class Synchronized()
