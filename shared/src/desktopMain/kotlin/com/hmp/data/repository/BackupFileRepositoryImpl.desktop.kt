package com.hmp.data.repository

import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.BackupPathPolicy
import com.hmp.domain.backup.UserBackupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BackupFileRepositoryImpl(
    private val json: Json,
    /**
     * 测试注入点：判据要在一个临时目录里造"越界文件"，不能拿开发者真实的 `~/.hmp/backups` 做实验。
     * 生产装配一律走默认值（Koin 只传 `json`）。
     */
    backupDirectory: File? = null,
) : BackupFileRepository {

    private val backupDir: File by lazy {
        val dir = backupDirectory ?: File(System.getProperty("user.home"), ".hmp/backups")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * 只认备份目录**直属**的文件（D7-05）。
     *
     * `canonicalFile` 负责解析 `..` 与符号链接，判断本身交给 [BackupPathPolicy] —— 三端共用一份，
     * 免得 Desktop 认了某个路径而 iOS 不认。
     */
    private fun ownedFile(filePath: String): File? {
        val dir = backupDir.canonicalPath
        // 判断用 canonical（解析 `..` 与符号链接），但返回原始 File：
        // macOS 上临时目录的 /var 是 /private/var 的符号链接，若把 canonicalFile
        // 的 absolutePath 返回给调用方，会与 saveBackup/getBackups 的路径口径不一致，
        // 幂等导入会被误判成新文件再复制一份。
        val file = File(filePath)
        return if (BackupPathPolicy.isDirectChildOf(file.canonicalPath, dir)) file else null
    }

    private fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

    /** 同秒内多次导入不覆盖前一份：`-2`、`-3`… 取第一个空位。 */
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

    override suspend fun saveBackup(snapshot: UserBackupSnapshot, filePrefix: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val filename = "$filePrefix-v${snapshot.version}-${timestamp()}.json"
            val file = File(backupDir, filename)

            val jsonString = json.encodeToString(UserBackupSnapshot.serializer(), snapshot)
            file.writeText(jsonString)
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadBackup(filePath: String): Result<UserBackupSnapshot> = withContext(Dispatchers.IO) {
        try {
            val file = ownedFile(filePath) ?: return@withContext Result.failure(
                IllegalArgumentException("拒绝读取备份目录之外的文件：$filePath"),
            )
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

    override suspend fun getBackups(): Result<List<String>> = withContext(Dispatchers.IO) {
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

    override suspend fun deleteBackup(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = ownedFile(filePath) ?: return@withContext Result.failure(
                IllegalArgumentException("拒绝删除备份目录之外的文件：$filePath"),
            )
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File not found"))
            }
            if (file.delete()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importExternalFile(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 幂等：列表里点"恢复"传进来的就是目录内路径，不该再复制一份。
            ownedFile(filePath)?.let { return@withContext Result.success(it.absolutePath) }

            val source = File(filePath)
            if (!source.isFile) {
                return@withContext Result.failure(Exception("Backup file not found: $filePath"))
            }
            val target = freeImportedFile()
            source.copyTo(target, overwrite = false)
            Result.success(target.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
