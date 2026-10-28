package com.hearablemusic.player.ui.common.util

import com.hmp.data.util.parentPathOf

/** Android actual：POSIX 路径，走 `shared` 那一份 [parentPathOf] 的默认分隔符（D2-13）。 */
actual fun fileParentOf(path: String): String? = parentPathOf(path, "/")
