package com.hearablemusic.player.ui.common.util

import com.hmp.data.util.parentPathOf

/**
 * Desktop actual：走 `shared` 那一份 [parentPathOf]（D2-13）。
 *
 * 反斜杠与正斜杠都算分隔符 —— 与 `java.io.File.parent` 在 Windows 上的行为一致。
 * 之所以不直接写 `File(path).parent`：那条只能在"这台机器的 File 恰好认识它"的平台上验，
 * 而这条规则要在任何 runner 上都能对 `C:\Music\a.mp3` 断言（判据要求）。
 */
actual fun fileParentOf(path: String): String? = parentPathOf(path, "\\/")
