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

    /**
     * 把用户从任意位置挑来的备份文件复制进本端备份目录，返回**目录内**的路径（D7-05）。
     *
     * 为什么需要这一步：[loadBackup] / [deleteBackup] 只接受备份目录内的路径，越界一律
     * `Result.failure` —— 否则任意一个调用方（Desktop 的文件选择、iOS `KoinHelper` 的
     * Swift 入口）都能让 app 去解析/删除任意可写文件。于是"从文件恢复"必须先落地一份
     * 自己的副本。
     *
     * **复制的是字节，不是信任**：路径在目录内只证明这份文件是本次导入产生的；内容是否
     * 真是本应用的备份，由 [com.hmp.domain.backup.usecase.ImportUserDataBackupUseCase]
     * 的版本闸门与身份校验把关（`UserBackupSnapshot` 每个字段都有默认值，`{}` 也能反序列化成功）。
     *
     * 已在目录内的路径原样返回（幂等），调用方不必区分"列表里的备份"与"外面挑来的文件"。
     */
    suspend fun importExternalFile(filePath: String): Result<String>

    companion object {
        const val USER_BACKUP_PREFIX = "hearable-backup"
        const val PRE_RESTORE_PREFIX = "pre-restore"

        /**
         * 外部导入的副本前缀。刻意保留 `hearable-backup-` 开头：它必须出现在 [getBackups] 里，
         * 否则用户刚导入的那份文件在列表中是隐形的，也无法在恢复失败后手工找回。
         */
        const val IMPORTED_PREFIX = "$USER_BACKUP_PREFIX-imported"

        /** 三端共用的列表可见性判据，避免各写一份 filter 而出现"能写不能看"。 */
        fun isBackupFileName(name: String): Boolean = (name.startsWith("$USER_BACKUP_PREFIX-") || name.startsWith("$PRE_RESTORE_PREFIX-")) &&
            name.endsWith(".json")
    }
}
