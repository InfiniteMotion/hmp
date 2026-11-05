package com.hearablemusic.player.ui.common.util

import java.io.File

/** Android actual：java.io.File.canWrite()（与旧 EditMusicTagsViewModel 内联实现一致）。 */
actual fun isFilePathWritable(path: String): Boolean = File(path).canWrite()
