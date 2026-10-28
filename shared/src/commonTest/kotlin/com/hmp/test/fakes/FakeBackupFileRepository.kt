package com.hmp.test.fakes

import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.UserBackupSnapshot

class FakeBackupFileRepository : BackupFileRepository {

    private val backups = mutableMapOf<String, UserBackupSnapshot>()
    private var nextId = 1

    /** 供 D7-02 的判据断言：每次导入只多一份 pre-restore 副本。 */
    val savedFileNames = mutableListOf<String>()

    /** 供 D7-02 的判据断言：写不出安全副本时本次恢复必须整段放弃。 */
    var failNextSave = false

    override suspend fun saveBackup(snapshot: UserBackupSnapshot, filePrefix: String): Result<String> {
        if (failNextSave) {
            failNextSave = false
            return Result.failure(IllegalStateException("磁盘写满"))
        }
        val name = "$filePrefix-${nextId++}.json"
        savedFileNames += name
        val path = "/$name"
        backups[path] = snapshot
        return Result.success(path)
    }

    override suspend fun loadBackup(filePath: String): Result<UserBackupSnapshot> {
        val snapshot = backups[filePath]
        return if (snapshot != null) {
            Result.success(snapshot)
        } else {
            Result.failure(IllegalArgumentException("Backup not found: $filePath"))
        }
    }

    override suspend fun getBackups(): Result<List<String>> {
        return Result.success(backups.keys.toList().sorted())
    }

    override suspend fun deleteBackup(filePath: String): Result<Unit> {
        return if (backups.remove(filePath) != null) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("Backup not found: $filePath"))
        }
    }

    /**
     * 替身里"备份目录"就是 [backups] 的键集合：已知路径原样返回（幂等），
     * 未知路径按"目录外的文件"处理 —— 与真实实现里 `importExternalFile` 复制成功、
     * `loadBackup` 拒绝越界的组合等价。
     */
    override suspend fun importExternalFile(filePath: String): Result<String> {
        return if (backups.containsKey(filePath)) {
            Result.success(filePath)
        } else {
            Result.failure(IllegalArgumentException("Backup not found: $filePath"))
        }
    }
}
