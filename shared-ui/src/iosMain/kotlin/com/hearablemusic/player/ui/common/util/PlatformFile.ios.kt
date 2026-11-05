package com.hearablemusic.player.ui.common.util

import platform.Foundation.NSFileManager

/** iOS actual：NSFileManager 可写性检查。 */
actual fun isFilePathWritable(path: String): Boolean = NSFileManager.defaultManager.isWritableFileAtPath(path)
