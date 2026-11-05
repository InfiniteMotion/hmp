package com.hmp.data.repository

import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.BackupPathPolicy
import com.hmp.domain.backup.UserBackupSnapshot
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
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
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataUsingEncoding

@OptIn(ExperimentalForeignApi::class)
class BackupFileRepositoryImpl(
    private val json: Json,
) : BackupFileRepository {

    private val backupDir: String by lazy {
        val docs = NSFileManager.defaultManager.URLForDirectory(
            NSDocumentDirectory,
            NSUserDomainMask,
            null,
            false,
            null,
        )!!
        val dir = "${docs.path}/backups"
        if (!NSFileManager.defaultManager.fileExistsAtPath(dir)) {
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        }
        dir
    }

    /**
     * 只认备份目录**直属**的文件（D7-05）。判断交给三端共用的 [BackupPathPolicy]；
     * 这里只有字符串折叠（iOS 没有 `canonicalPath` 等价物）—— 符号链接能绕过这一层，
     * 残余记在 D7.md，不假装做到了。
     */
    private fun ownedPath(filePath: String): String? {
        val normalized = BackupPathPolicy.normalize(filePath)
        return if (BackupPathPolicy.isDirectChildOf(normalized, backupDir)) normalized else null
    }

    private fun timestamp(): String {
        val formatter = NSDateFormatter()
        formatter.dateFormat = "yyyyMMdd-HHmmss"
        return formatter.stringFromDate(NSDate())
    }

    // D7-11 的 iOS 半边**刻意不改**：`Dispatchers.IO` 在 Kotlin/Native 上是 `internal`
    // （kotlinx-coroutines 1.10.1，编译报 "Cannot access 'val IO': it is internal"），
    // 所以三端没法统一到同一个调度器。这里保持 Default —— 它是 K/N 上唯一现成的后台池。
    // Android 那半仍然从 Main 切到了 IO（那边 IO 是公开 API），那才是本条真正要修的分叉。
    override suspend fun saveBackup(snapshot: UserBackupSnapshot, filePrefix: String): Result<String> = withContext(Dispatchers.Default) {
        try {
            val filename = "$filePrefix-v${snapshot.version}-${timestamp()}.json"
            val filePath = "$backupDir/$filename"

            val jsonString = json.encodeToString(UserBackupSnapshot.serializer(), snapshot)
            val data = (jsonString as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!
            NSFileManager.defaultManager.createFileAtPath(filePath, data, null)
            Result.success(filePath)
        } catch (e: Exception) {
            HmpLog.e(LogTag.DataBackup, e) { "💾 iOS: failed to save backup" }
            Result.failure(e)
        }
    }

    override suspend fun loadBackup(filePath: String): Result<UserBackupSnapshot> = withContext(Dispatchers.Default) {
        try {
            val owned = ownedPath(filePath) ?: return@withContext Result.failure(
                IllegalArgumentException("拒绝读取备份目录之外的文件：$filePath"),
            )
            if (!NSFileManager.defaultManager.fileExistsAtPath(owned)) {
                return@withContext Result.failure(Exception("Backup file not found"))
            }
            val data = NSFileManager.defaultManager.contentsAtPath(owned)
                ?: return@withContext Result.failure(Exception("Failed to read backup file"))
            val bytes = data.bytes
                ?: return@withContext Result.failure(Exception("Failed to read backup data"))
            val jsonString = bytes.readBytes(data.length.toInt()).decodeToString()
            val snapshot = json.decodeFromString(UserBackupSnapshot.serializer(), jsonString)
            Result.success(snapshot)
        } catch (e: Exception) {
            HmpLog.e(LogTag.DataBackup, e) { "💾 iOS: failed to load backup" }
            Result.failure(e)
        }
    }

    override suspend fun getBackups(): Result<List<String>> = withContext(Dispatchers.Default) {
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

    override suspend fun deleteBackup(filePath: String): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            val owned = ownedPath(filePath) ?: return@withContext Result.failure(
                IllegalArgumentException("拒绝删除备份目录之外的文件：$filePath"),
            )
            if (!NSFileManager.defaultManager.fileExistsAtPath(owned)) {
                return@withContext Result.failure(Exception("File not found"))
            }
            val deleted = NSFileManager.defaultManager.removeItemAtPath(owned, null)
            if (deleted) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** UIDocumentPicker（`asCopy: true`）给的是沙箱 tmp 里的路径，必须复制进备份目录后才能被读取。 */
    override suspend fun importExternalFile(filePath: String): Result<String> = withContext(Dispatchers.Default) {
        try {
            ownedPath(filePath)?.let { return@withContext Result.success(it) }

            val source = BackupPathPolicy.normalize(filePath)
            if (!NSFileManager.defaultManager.fileExistsAtPath(source)) {
                return@withContext Result.failure(Exception("Backup file not found: $filePath"))
            }
            val data = NSFileManager.defaultManager.contentsAtPath(source)
                ?: return@withContext Result.failure(Exception("Failed to read backup file"))
            val millis = (NSDate().timeIntervalSinceReferenceDate * 1000).toLong()
            val target = "$backupDir/${BackupFileRepository.IMPORTED_PREFIX}-$millis.json"
            val created = NSFileManager.defaultManager.createFileAtPath(target, data, null)
            if (created) {
                Result.success(target)
            } else {
                Result.failure(Exception("Failed to import backup file"))
            }
        } catch (e: Exception) {
            HmpLog.e(LogTag.DataBackup, e) { "💾 iOS: failed to import backup" }
            Result.failure(e)
        }
    }
}
