package com.hmp.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D7-05 的判定层判据：备份目录只认直属子文件。
 *
 * 这些断言与平台 IO 分开，是为了让"越界"这件事能直接喂字符串验证，而不是靠造真实文件去猜
 * （`File.parent` 这类平台调用在 Windows 上对 `/` 与 `\` 的行为并不一致，见 D2-13 的教训）。
 */
class BackupPathPolicyTest {

    private val dir = "/home/x/.hmp/backups"

    @Test
    fun directChild_isAccepted() {
        assertTrue(BackupPathPolicy.isDirectChildOf("$dir/hearable-backup-v1-20261028-102000.json", dir))
    }

    @Test
    fun dotDotEscape_isRejected() {
        assertFalse(BackupPathPolicy.isDirectChildOf("$dir/../../escape.json", dir))
        assertFalse(BackupPathPolicy.isDirectChildOf("$dir/../escape.json", dir))
    }

    @Test
    fun siblingDirectoryWithSharedPrefix_isRejected() {
        // 前缀比较会把这个放进来 —— 判据就是钉住"逐段相等"而不是 startsWith
        assertFalse(BackupPathPolicy.isDirectChildOf("/home/x/.hmp/backups-evil/a.json", dir))
    }

    @Test
    fun nestedSubdirectory_isRejected() {
        assertFalse(BackupPathPolicy.isDirectChildOf("$dir/sub/a.json", dir))
    }

    @Test
    fun windowsSeparators_areNormalized() {
        // 盘符保留，分隔符统一 —— 于是 Windows 的 canonicalPath 与配置里的 `/` 写法能比较
        assertEquals("C:/Users/TS/.hmp/backups", BackupPathPolicy.normalize("C:\\Users\\TS\\.hmp\\backups"))
        assertTrue(
            BackupPathPolicy.isDirectChildOf(
                "C:\\Users\\TS\\.hmp\\backups\\a.json",
                "C:/Users/TS/.hmp/backups",
            ),
        )
    }

    @Test
    fun trailingSlashOnDirectory_isTolerated() {
        assertTrue(BackupPathPolicy.isDirectChildOf("$dir/a.json", "$dir/"))
    }
}
