package com.hmp.domain.backup.usecase

import com.hmp.domain.backup.AppSettingsSnapshot
import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.BackupRestoreFailedException
import com.hmp.domain.backup.TransactionRunner
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.CancellationException

/**
 * 从备份文件恢复用户数据。
 *
 * 两条纪律（一-2 / D7-02）：
 * 1. **先留恢复前副本，再动库**。旧写法是四条 restore 串行跑完再 `catch → Result.failure`，
 *    中途失败时用户的歌单已被 `deleteAll()` 清空且没有任何回退路径。副本都写不出来就直接放弃本次恢复。
 * 2. **Room 侧的三条 restore 收进一个事务**。跨表写（曲目用户态 / 听歌统计 / 歌单）必须全成或全不成。
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
        val snapshot = backupFileRepository.loadBackup(filePath).getOrElse {
            return Result.failure(it ?: Exception("Failed to load backup"))
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
