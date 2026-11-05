package com.hmp.domain.backup.usecase

import com.hmp.domain.backup.BackupFileRepository

class DeleteBackupUseCase(
    private val backupFileRepository: BackupFileRepository,
) {
    suspend operator fun invoke(filePath: String): Result<Unit> = backupFileRepository.deleteBackup(filePath)
}
