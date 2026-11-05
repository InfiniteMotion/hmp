package com.hmp.domain.backup.usecase

import com.hmp.domain.backup.BackupFileRepository

class GetBackupsUseCase(
    private val backupFileRepository: BackupFileRepository,
) {
    suspend operator fun invoke(): Result<List<String>> = backupFileRepository.getBackups()
}
