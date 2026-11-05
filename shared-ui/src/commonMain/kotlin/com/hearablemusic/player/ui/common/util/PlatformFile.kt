package com.hearablemusic.player.ui.common.util

/**
 * 文件路径可写性检查的多平台抽象。
 *
 * Android actual 用 File，Desktop/iOS actual 用各自文件系统 API。
 */

/** 指定路径的文件当前是否可直接写入（分区存储下用户媒体通常为 false，需走 SAF 授权）。 */
expect fun isFilePathWritable(path: String): Boolean
