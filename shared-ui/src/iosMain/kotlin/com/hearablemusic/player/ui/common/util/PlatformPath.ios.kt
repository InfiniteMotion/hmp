package com.hearablemusic.player.ui.common.util

import com.hmp.data.util.parentPathOf

/**
 * iOS actual：沙盒是 POSIX 路径，走 [parentPathOf] 的默认分隔符（D2-13）。
 *
 * 原先这里是 `NSString.stringByDeletingLastPathComponent` —— 它对裸文件名返回 `"."`
 * 而不是"没有父目录"，于是这种行会在「已隐藏文件夹」里变成一个看不懂的 `.` 分组；
 * 换成与另两端同一份规则后，这种情况统一回 null（由调用方显示成"未知"）。
 */
actual fun fileParentOf(path: String): String? = parentPathOf(path, "/")
