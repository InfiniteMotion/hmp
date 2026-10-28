package com.hmp.domain.backup.usecase

import com.hmp.domain.backup.AppSettingsSnapshot
import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.BackupRestoreFailedException
import com.hmp.domain.backup.BackupVersionTooNewException
import com.hmp.domain.backup.TransactionRunner
import com.hmp.domain.backup.UserBackupSnapshot
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.CancellationException

/**
 * 从备份文件恢复用户数据。
 *
 * 四条纪律：
 * 1. **先落地再读**（D7-05）。仓库层的 `loadBackup` / `deleteBackup` 只认备份目录内的文件，
 *    所以用户从任意位置挑来的备份先由 [BackupFileRepository.importExternalFile] 复制进目录。
 *    这样一来"仓库只碰自己的目录"是结构性的，而不是每个调用方各自自觉。
 * 2. **认得出是本应用的备份才继续**（D7-12）。`UserBackupSnapshot` 每个字段都有默认值，
 *    一个 `{}` 也能反序列化成功 —— 而恢复会先清空再重建，认错文件的代价是整个曲库用户态。
 *    两道闸门：身份（`createdAt` 必须由导出侧写入）、版本（高于 [UserBackupSnapshot.CURRENT_VERSION] 拒绝）。
 * 3. **先留恢复前副本，再动库**（一-2 / D7-02）。旧写法是四条 restore 串行跑完再 `catch → Result.failure`，
 *    中途失败时用户的歌单已被 `deleteAll()` 清空且没有任何回退路径。副本都写不出来就直接放弃本次恢复。
 * 4. **Room 侧的三条 restore 收进一个事务**。跨表写（曲目用户态 / 听歌统计 / 歌单）必须全成或全不成。
 *    回滚由 [TransactionRunner] 承担；失败时把副本路径回传给 UI，用户至少知道去哪手工找回。
 *    设置那条走 DataStore（文件），进不了 Room 事务，所以排在最后：库回滚时设置也还没动。
 *    取消不算失败：`CancellationException` 复抛，只有真实故障才包成 [BackupRestoreFailedException]。
 */
class ImportUserDataBackupUseCase(
    private val settingsRepository: SettingsRepository,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val backupFileRepository: BackupFileRepository,
    private val exportUseCase: ExportUserDataBackupUseCase,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(filePath: String): Result<Unit> {
        val stagedPath = backupFileRepository.importExternalFile(filePath).getOrElse {
            return Result.failure(it ?: Exception("Failed to import backup"))
        }

        val snapshot = backupFileRepository.loadBackup(stagedPath).getOrElse {
            return Result.failure(it ?: Exception("Failed to load backup"))
        }

        if (snapshot.version > UserBackupSnapshot.CURRENT_VERSION) {
            return Result.failure(
                BackupVersionTooNewException(snapshot.version, UserBackupSnapshot.CURRENT_VERSION)
            )
        }
        if (snapshot.createdAt <= 0L) {
            // 导出侧一定会写 createdAt（`ExportUserDataBackupUseCase`）。为 0 说明这不是本应用
            // 导出的备份 —— 最典型的是 `{}`，它能反序列化成功但四个分区全空，继续下去就是把库清空。
            return Result.failure(
                IllegalArgumentException("这不是 Hearable Music Player 的备份文件（缺少 createdAt），本次未改动任何数据。")
            )
        }

        val safetyCopyPath = exportUseCase(BackupFileRepository.PRE_RESTORE_PREFIX).getOrNull()
            ?: return Result.failure(
                IllegalStateException("未能生成恢复前副本，本次恢复未开始，曲库与设置保持原样。")
            )

        return try {
            transactionRunner.run {
                // Room 侧三条先做：它们受事务保护，失败即全部回滚。
                musicRepository.restoreMusicUserState(snapshot.musicUserState)
                musicRepository.restoreListeningStats(snapshot.listeningStats)
                playlistRepository.restoreFromSnapshot(snapshot.playlists)
                // 设置写在 DataStore（文件），不在这条事务里 —— 放最后，
                // 让"库回滚了而设置已改"这种半截状态只可能由 DataStore 自己写失败造成（罕见，且有副本可找回）。
                settingsRepository.restoreFromSnapshot(
                    snapshot.appSettings
                        ?: AppSettingsSnapshot(themeMode = "default", backgroundStyle = "FLUID")
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            // 取消不是失败：复抛出去，别让"用户关了页面"变成一条 BackupRestoreFailedException，
            // 也别让调用方的协作式取消在这里断掉（R39 明确要求复抛）。
            throw e
        } catch (e: Exception) {
            Result.failure(BackupRestoreFailedException(safetyCopyPath, e))
        }
    }
}
