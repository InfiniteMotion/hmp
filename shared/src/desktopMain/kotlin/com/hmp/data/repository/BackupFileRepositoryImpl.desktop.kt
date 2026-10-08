package com.hmp.data.repository

import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.UserBackupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BackupFileRepositoryImpl(
    private val json: Json
) : BackupFileRepository {

    private val backupDir: File by lazy {
        val dir = File(System.getProperty("user.home"), ".hmp/backups")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    override suspend fun saveBackup(snapshot: UserBackupSnapshot, filePrefix: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                val filename = "$filePrefix-v${snapshot.version}-$timestamp.json"
                val file = File(backupDir, filename)

                val jsonString = json.encodeToString(UserBackupSnapshot.serializer(), snapshot)
                file.writeText(jsonString)
                Result.success(file.absolutePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun loadBackup(filePath: String): Result<UserBackupSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(Exception("Backup file not found"))
                }
                val jsonString = file.readText()
                val snapshot = json.decodeFromString(UserBackupSnapshot.serializer(), jsonString)
                Result.success(snapshot)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getBackups(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val files = backupDir.listFiles()
                    ?.filter { BackupFileRepository.isBackupFileName(it.name) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { it.absolutePath }
                    ?: emptyList()
                Result.success(files)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteBackup(filePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(Exception("File not found"))
                }
                if (file.delete()) Result.success(Unit)
                else Result.failure(Exception("Failed to delete file"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
