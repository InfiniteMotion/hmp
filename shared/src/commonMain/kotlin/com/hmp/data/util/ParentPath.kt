package com.hmp.data.util

/**
 * 取父目录 —— **分隔符是参数，不是写死的 `/`**（D2-13）。
 *
 * 为什么要有这一份：设置页的「已隐藏文件夹」原先在 Android / Desktop / iOS 各写一条实现
 * （Android 用 `File(path).parent`，Desktop 与 iOS 手写 `path.substringBeforeLast("/", "Unknown")`），
 * 而 Desktop 存的 path 是 `file.absolutePath` —— Windows 上它是 `C:\Music\a.mp3`，里面根本没有 `/`，
 * 于是**所有被隐藏的曲目会聚成一个错误的分组**，那组的"取消隐藏"可能一次恢复全部。
 * 同一个功能还有第四份实现在 UI 侧（`shared-ui` 的 `fileParentOf` expect/actual），
 * 两边口径必须一致，否则"隐藏"与"取消隐藏"对不上号。
 *
 * 分隔符做成参数是为了**可验**：判据要能在 macOS / Linux 的 runner 上对反斜杠路径断言分组结果，
 * 那只能靠显式传入分隔符，不能靠"这台机器的 `File` 恰好认识它"。
 *
 * 与 `java.io.File.parent` 对齐的几个边界（刻意一致，否则 UI 侧与仓库侧会给出不同标签）：
 * - 裸文件名（没有任何分隔符）→ null；
 * - `/a.mp3` → `/`（不是空串）；
 * - Windows 盘根 `C:\a.mp3` → `C:\`（`File.parent` 就是这么给的，不是 `C:`）。
 */

/** 没有父目录时，「已隐藏文件夹」列表用来兜底的分组名。 */
const val UNKNOWN_FOLDER = "Unknown"

/**
 * @param separators 本端认可的分隔符集合。POSIX 传 `"/"`；Desktop 传 `"\\/"`
 *   （Kotlin 里这是"反斜杠 + 正斜杠"两个字符）—— Windows 的绝对路径用 `\`，
 *   但用户手填或从别处搬来的目录里可能是 `/`，`java.io.File` 在 Windows 上两种都认，这里保持同样的宽容。
 * @return 父目录；[path] 里没有分隔符时返回 null（调用方决定怎么显示，见 [UNKNOWN_FOLDER]）。
 */
fun parentPathOf(path: String, separators: String = "/"): String? {
    val index = path.lastIndexOfAny(separators.toCharArray())
    if (index < 0) return null
    val parent = path.substring(0, index)
    return when {
        parent.isEmpty() -> separators.substring(0, 1)                       // "/a.mp3" → "/"
        parent.length == 2 && parent[1] == ':' -> parent + separators[0]      // "C:\a.mp3" → "C:\"
        else -> parent
    }
}
