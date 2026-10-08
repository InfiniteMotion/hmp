package com.hmp.data.repository

import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.UserBackupSnapshot
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding

@OptIn(ExperimentalForeignApi::class)
class BackupFileRepositoryImpl(
    private val json: Json
) : BackupFileRepository {

    private val backupDir: String by lazy {
        val docs = NSFileManager.defaultManager.URLForDirectory(
            NSDocumentDirectory, NSUserDomainMask, null, false, null
        )!!
        val dir = "${docs.path}/backups"
        if (!NSFileManager.defaultManager.fileExistsAtPath(dir)) {
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        }
        dir
    }

    override suspend fun saveBackup(snapshot: UserBackupSnapshot, filePrefix: String): Result<String> =
        withContext(Dispatchers.Default) {
            try {
                val formatter = NSDateFormatter()
                formatter.dateFormat = "yyyyMMdd-HHmmss"
                val timestamp = formatter.stringFromDate(NSDate())
                val filename = "$filePrefix-v${snapshot.version}-$timestamp.json"
                val filePath = "$backupDir/$filename"

                val jsonString = json.encodeToString(UserBackupSnapshot.serializer(), snapshot)
                val data = (jsonString as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!
                NSFileManager.defaultManager.createFileAtPath(filePath, data, null)
                Result.success(filePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun loadBackup(filePath: String): Result<UserBackupSnapshot> =
        withContext(Dispatchers.Default) {
            try {
                if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
                    return@withContext Result.failure(Exception("Backup file not found"))
                }
                val data = NSFileManager.defaultManager.contentsAtPath(filePath)
                    ?: return@withContext Result.failure(Exception("Failed to read backup file"))
                val bytes = data.bytes
                    ?: return@withContext Result.failure(Exception("Failed to read backup data"))
                val jsonString = bytes.readBytes(data.length.toInt()).decodeToString()
                val snapshot = json.decodeFromString(UserBackupSnapshot.serializer(), jsonString)
                Result.success(snapshot)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getBackups(): Result<List<String>> =
        withContext(Dispatchers.Default) {
            try {
                val files = NSFileManager.defaultManager.contentsOfDirectoryAtPath(backupDir, null)
                    ?.filterIsInstance<String>()
                    ?.filter { BackupFileRepository.isBackupFileName(it) }
                    ?.map { "$backupDir/$it" }
                    ?.sortedByDescending {
                        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath("$backupDir/$it", null)
                        (attrs?.get("NSFileModificationDate") as? NSDate)?.timeIntervalSinceReferenceDate ?: 0.0
                    }
                    ?: emptyList()
                Result.success(files)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteBackup(filePath: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            try {
                if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
                    return@withContext Result.failure(Exception("File not found"))
                }
                val deleted = NSFileManager.defaultManager.removeItemAtPath(filePath, null)
                if (deleted) Result.success(Unit)
                else Result.failure(Exception("Failed to delete file"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
