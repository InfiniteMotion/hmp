package com.hmp.ios

/**
 * 聚合框架锚点（A1）：shared-ios 无自有业务代码，仅此文件让模块拥有编译产物，
 * 触发 linkPod*Framework 把 :shared 与 :shared-ui 两个 klib 链接进统一框架。
 * Swift 侧统一 import sharedIos。
 */
const val SHARED_IOS_FRAMEWORK_VERSION: String = "7.2.1-a1"
