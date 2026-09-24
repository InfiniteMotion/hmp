package com.hmp.data.repository

import android.content.Context
import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.UserBackupSnapshot
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

class BackupFileRepositoryImpl(
    private val context: Context,
    private val json: Json
) : BackupFileRepository {

    private val backupDir by lazy {
        File(context.filesDir, "backups").apply {
            if (!exists()) mkdirs()
        }
    }

    override suspend fun saveBackup(snapshot: UserBackupSnapshot): Result<String> {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val filename = "hearable-backup-v${snapshot.version}-$timestamp.json"
            val file = File(backupDir, filename)

            file.writeText(json.encodeToString(UserBackupSnapshot.serializer(), snapshot))
            HmpLog.i(LogTag.DataBackup) { "💾 Backup saved to ${file.absolutePath}" }
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            HmpLog.e(LogTag.DataBackup, e) { "💾 Failed to save backup" }
            Result.failure(e)
        }
    }

    override suspend fun loadBackup(filePath: String): Result<UserBackupSnapshot> {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(Exception("Backup file not found"))
            }
            val jsonString = file.readText()
            if (jsonString.isBlank()) {
                return Result.failure(Exception("Backup file is empty"))
            }
            val snapshot = json.decodeFromString(UserBackupSnapshot.serializer(), jsonString)
            Result.success(snapshot)
        } catch (e: Exception) {
            HmpLog.e(LogTag.DataBackup, e) { "💾 Failed to load backup" }
            Result.failure(e)
        }
    }

    override suspend fun getBackups(): Result<List<String>> {
        return try {
            val files = backupDir.listFiles { file ->
                file.name.startsWith("hearable-backup-") && file.name.endsWith(".json")
            }?.sortedByDescending { it.lastModified() }?.map { it.absolutePath } ?: emptyList()
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteBackup(filePath: String): Result<Unit> {
         return try {
            val file = File(filePath)
            if (file.exists() && file.delete()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
