package com.hmp.domain.backup

/**
 * 备份目录的路径策略（D7-05）。
 *
 * 为什么要有这一层：`BackupFileRepository` 的 `loadBackup` / `deleteBackup` 只该碰本端备份目录里的
 * 文件 —— 否则任意调用方（Desktop 的文件选择、iOS `KoinHelper` 的 Swift 入口、将来新加的恢复入口）
 * 都能让 app 去解析或删除任意可写文件。三端各写一遍判断必然漂移，所以**判据收在这里**，
 * 平台只负责把路径规范化（Desktop / Android 用 `canonicalPath` 连符号链接一起解，iOS 没有
 * 对应 API，只做字符串折叠）。
 *
 * 两个刻意写死的口径：
 * - **只认直属子文件**，不认子目录里的文件。备份目录是平的，允许嵌套只会扩大可触达面。
 * - **前缀比较不算数**：`~/.hmp/backups-evil/a.json` 与 `~/.hmp/backups` 共享前缀，必须是
 *   "父目录逐字符相等"，不是 `startsWith`。
 */
object BackupPathPolicy {

    /**
     * 折叠 `.`、`..`、重复分隔符，并把 Windows 的反斜杠统一成正斜杠。
     *
     * 只保证**字符串层面**的规范化：不解析符号链接（iOS 侧没有等价 API）。因此 iOS 上
     * 一个指向目录外的符号链接可以绕过这一层 —— 记在 D7.md 的残余里，不假装做到了。
     */
    fun normalize(path: String): String {
        val unified = path.replace('\\', '/')
        val prefix = when {
            unified.startsWith("/") -> "/"

            // "C:/Users/..." -> 盘符保留，便于与同一来源的目录路径比较
            unified.length >= 3 && unified[1] == ':' && unified[2] == '/' -> unified.substring(0, 3)

            else -> ""
        }
        val body = if (prefix.isEmpty()) unified else unified.removePrefix(prefix)
        val parts = ArrayDeque<String>()
        for (segment in body.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(segment)
            }
        }
        return prefix + parts.joinToString("/")
    }

    /** `filePath` 是否是 [dirPath] 的**直属**子文件（两者都会先规范化）。 */
    fun isDirectChildOf(filePath: String, dirPath: String): Boolean {
        val file = normalize(filePath)
        val dir = normalize(dirPath).trimEnd('/')
        val parent = file.substringBeforeLast('/', missingDelimiterValue = "")
        return parent.isNotEmpty() && parent == dir
    }
}
