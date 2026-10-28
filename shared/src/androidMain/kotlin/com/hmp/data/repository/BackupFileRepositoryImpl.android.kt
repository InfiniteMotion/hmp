package com.hmp.data.repository

import android.content.Context
import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.BackupPathPolicy
import com.hmp.domain.backup.UserBackupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * 只认备份目录**直属**的文件（D7-05）。`canonicalFile` 解析 `..` 与符号链接，
     * 判断交给三端共用的 [BackupPathPolicy]，避免"Desktop 认、iOS 不认"这类漂移。
     */
    private fun ownedFile(filePath: String): File? {
        val dir = backupDir.canonicalPath
        val file = File(filePath).canonicalFile
        return if (BackupPathPolicy.isDirectChildOf(file.path, dir)) file else null
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())

    private fun freeImportedFile(): File {
        val base = "${BackupFileRepository.IMPORTED_PREFIX}-${timestamp()}"
        var candidate = File(backupDir, "$base.json")
        var n = 2
        while (candidate.exists()) {
            candidate = File(backupDir, "$base-$n.json")
            n++
        }
        return candidate
    }

    // D7-11：四个方法都切到 IO —— 原来直接在调用方线程（UI Main）上做整份快照的序列化与读写，
    // 大曲库下会掉帧，StrictMode 打开时直接报磁盘读写违规。Desktop / iOS 早已切走，Android 是三端唯一分叉。
    override suspend fun saveBackup(snapshot: UserBackupSnapshot, filePrefix: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val filename = "$filePrefix-v${snapshot.version}-${timestamp()}.json"
                val file = File(backupDir, filename)

                file.writeText(json.encodeToString(UserBackupSnapshot.serializer(), snapshot))
                HmpLog.i(LogTag.DataBackup) { "💾 Backup saved to ${file.absolutePath}" }
                Result.success(file.absolutePath)
            } catch (e: Exception) {
                HmpLog.e(LogTag.DataBackup, e) { "💾 Failed to save backup" }
                Result.failure(e)
            }
        }

    override suspend fun loadBackup(filePath: String): Result<UserBackupSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                val file = ownedFile(filePath) ?: return@withContext Result.failure(
                    IllegalArgumentException("拒绝读取备份目录之外的文件：$filePath")
                )
                if (!file.exists()) {
                    return@withContext Result.failure(Exception("Backup file not found"))
                }
                val jsonString = file.readText()
                if (jsonString.isBlank()) {
                    return@withContext Result.failure(Exception("Backup file is empty"))
                }
                val snapshot = json.decodeFromString(UserBackupSnapshot.serializer(), jsonString)
                Result.success(snapshot)
            } catch (e: Exception) {
                HmpLog.e(LogTag.DataBackup, e) { "💾 Failed to load backup" }
                Result.failure(e)
            }
        }

    override suspend fun getBackups(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val files = backupDir.listFiles { file ->
                    BackupFileRepository.isBackupFileName(file.name)
                }?.sortedByDescending { it.lastModified() }?.map { it.absolutePath } ?: emptyList()
                Result.success(files)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteBackup(filePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = ownedFile(filePath) ?: return@withContext Result.failure(
                    IllegalArgumentException("拒绝删除备份目录之外的文件：$filePath")
                )
                if (file.exists() && file.delete()) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete file"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * SAF 选中的 Uri 由 `AndroidPlatformServices` 复制进**备份目录**（原来复制到 `cacheDir/restore_temp.json`，
     * 那个位置在目录之外，加了 D7-05 的路径约束后会被自己的守卫拒掉）。
     */
    override suspend fun importExternalFile(filePath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                ownedFile(filePath)?.let { return@withContext Result.success(it.absolutePath) }

                val source = File(filePath)
                if (!source.isFile) {
                    return@withContext Result.failure(Exception("Backup file not found: $filePath"))
                }
                val target = freeImportedFile()
                source.copyTo(target, overwrite = false)
                HmpLog.i(LogTag.DataBackup) { "💾 Backup imported to ${target.absolutePath}" }
                Result.success(target.absolutePath)
            } catch (e: Exception) {
                HmpLog.e(LogTag.DataBackup, e) { "💾 Failed to import backup" }
                Result.failure(e)
            }
        }
}
