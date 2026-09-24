package com.hmp.domain.backup.usecase

import com.hmp.domain.backup.AppSettingsSnapshot
import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository

class ImportUserDataBackupUseCase(
    private val settingsRepository: SettingsRepository,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val backupFileRepository: BackupFileRepository
) {
    suspend operator fun invoke(filePath: String): Result<Unit> {
        return try {
            val result = backupFileRepository.loadBackup(filePath)
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: Exception("Failed to load backup"))
            }
            val snapshot = result.getOrThrow()

            settingsRepository.restoreFromSnapshot(snapshot.appSettings ?: AppSettingsSnapshot(themeMode = "default", backgroundStyle = "FLUID"))
            musicRepository.restoreMusicUserState(snapshot.musicUserState)
            musicRepository.restoreListeningStats(snapshot.listeningStats)
            playlistRepository.restoreFromSnapshot(snapshot.playlists)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
