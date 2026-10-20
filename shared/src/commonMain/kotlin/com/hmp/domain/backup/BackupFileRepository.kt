package com.hmp.domain.backup

interface BackupFileRepository {
    /**
     * 落一份备份文件，返回可再喂给 [loadBackup] 的路径。
     *
     * `filePrefix` 决定文件名前缀：用户主动导出用 [USER_BACKUP_PREFIX]，
     * 恢复前的安全副本用 [PRE_RESTORE_PREFIX]（一-2 / D7-02）。
     * 两种都必须出现在 [getBackups] 里 —— 否则那份"手工找回用的副本"在界面上是隐形的。
     */
    suspend fun saveBackup(
        snapshot: UserBackupSnapshot,
        filePrefix: String = USER_BACKUP_PREFIX,
    ): Result<String>

    suspend fun loadBackup(filePath: String): Result<UserBackupSnapshot>
    suspend fun getBackups(): Result<List<String>>
    suspend fun deleteBackup(filePath: String): Result<Unit>

    companion object {
        const val USER_BACKUP_PREFIX = "hearable-backup"
        const val PRE_RESTORE_PREFIX = "pre-restore"

        /** 三端共用的列表可见性判据，避免各写一份 filter 而出现"能写不能看"。 */
        fun isBackupFileName(name: String): Boolean =
            (name.startsWith("$USER_BACKUP_PREFIX-") || name.startsWith("$PRE_RESTORE_PREFIX-")) &&
                name.endsWith(".json")
    }
}
