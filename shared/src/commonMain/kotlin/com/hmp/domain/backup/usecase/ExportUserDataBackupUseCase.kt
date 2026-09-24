package com.hmp.domain.backup.usecase

import com.hmp.data.database.currentTimeMillis
import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.UserBackupSnapshot
import com.hmp.domain.music.MusicRepository
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository

class ExportUserDataBackupUseCase(
    private val settingsRepository: SettingsRepository,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val backupFileRepository: BackupFileRepository
) {
    suspend operator fun invoke(): Result<String> {
        return try {
            val appSettings = settingsRepository.exportAppSettingsSnapshot()
            val musicUserState = musicRepository.exportMusicUserStateSnapshot()
            val listeningStats = musicRepository.exportListeningStatsSnapshot()
            val playlists = playlistRepository.exportPlaylistsSnapshot()

            val snapshot = UserBackupSnapshot(
                createdAt = currentTimeMillis(),
                appSettings = appSettings,
                musicUserState = musicUserState,
                listeningStats = listeningStats,
                playlists = playlists
            )

            backupFileRepository.saveBackup(snapshot)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
