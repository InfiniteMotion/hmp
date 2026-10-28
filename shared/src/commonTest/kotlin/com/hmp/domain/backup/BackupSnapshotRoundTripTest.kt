package com.hmp.domain.backup

import com.hmp.domain.backup.usecase.ExportUserDataBackupUseCase
import com.hmp.domain.backup.usecase.ImportUserDataBackupUseCase
import com.hmp.domain.enum.LabelName
import com.hmp.domain.setting.model.ListeningDuration
import com.hmp.domain.setting.model.PlaybackHistory
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistItem
import com.hmp.test.fakes.FakeBackupFileRepository
import com.hmp.test.fakes.FakeMusicRepository
import com.hmp.test.fakes.FakePlaylistRepository
import com.hmp.test.fakes.FakeSettingsRepository
import com.hmp.test.fakes.FakeTransactionRunner
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * D7-12 的两条判据。
 *
 * 用例 A 同时回答一个立案时未能确证的运行时问题：`PlaylistsSnapshot` / `ListeningStatsSnapshot`
 * 上那 4 个 `@Contextual` 在没有 `serializersModule` 的情况下到底会不会崩。结论见
 * `domain/D7.md` D7-12 的施工结果段 —— 判据先行，不靠推理。
 */
class BackupSnapshotRoundTripTest {

    /** 与 `createJson()` 同配置：线上格式就是按这套开关编码的。 */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private fun nonEmptySnapshot() = UserBackupSnapshot(
        createdAt = 1_700_000_000_000L,
        appSettings = AppSettingsSnapshot(userName = "Xi", themeMode = "dark"),
        musicUserState = MusicUserStateSnapshot(
            userInfos = listOf(UserInfoSnapshot(id = 42, liked = true, playCount = 7)),
            labels = listOf(MusicLabelSnapshot(musicId = 42, label = LabelName.ROCK)),
        ),
        playlists = PlaylistsSnapshot(
            playlists = listOf(
                Playlist(id = 9, name = "通勤", songCount = 2, totalDurationMs = 480_000L, isPinned = true)
            ),
            playlistItems = listOf(
                PlaylistItem(songId = 42, playlistId = 9, itemOrder = 0),
                PlaylistItem(songId = 43, playlistId = 9, itemOrder = 1),
            ),
        ),
        listeningStats = ListeningStatsSnapshot(
            listeningDurations = listOf(ListeningDuration(date = "2026-10-20", duration = 1_800_000L)),
            playbackHistories = listOf(
                PlaybackHistory(id = 1, musicId = 42, playedAt = 1_700_000_000_000L, playDuration = 210_000L, isCompleted = true)
            ),
        ),
    )

    /** 用例 A：有歌单、有听歌记录的往返 —— 这条路径在立案时从未被执行过。 */
    @Test
    fun nonEmptySnapshot_survivesEncodeDecode() {
        val snapshot = nonEmptySnapshot()

        val encoded = json.encodeToString(UserBackupSnapshot.serializer(), snapshot)
        val decoded = json.decodeFromString(UserBackupSnapshot.serializer(), encoded)

        assertEquals(snapshot, decoded, "含歌单/听歌记录的快照必须原样往返，字段顺序与默认值都不该吃掉数据")
        assertEquals(2, decoded.playlists.playlistItems.size)
        assertEquals(1, decoded.playlists.playlistItems[1].itemOrder)
        assertEquals("通勤", decoded.playlists.playlists[0].name)
        assertTrue(decoded.listeningStats.playbackHistories[0].isCompleted)
    }

    /** 用例 B：版本高于当前应用的备份必须被拒绝，且拒绝发生在动库之前。 */
    @Test
    fun import_rejectsNewerSnapshotVersion_beforeTouchingData() = runTest {
        val backupRepo = FakeBackupFileRepository()
        val settings = FakeSettingsRepository()
        val music = FakeMusicRepository()
        val playlists = FakePlaylistRepository()
        val runner = FakeTransactionRunner()
        val export = ExportUserDataBackupUseCase(settings, music, playlists, backupRepo)
        val import = ImportUserDataBackupUseCase(settings, music, playlists, backupRepo, export, runner)

        val path = backupRepo.saveBackup(
            nonEmptySnapshot().copy(version = UserBackupSnapshot.CURRENT_VERSION + 1)
        ).getOrThrow()
        backupRepo.savedFileNames.clear()

        val result = import(path)

        assertIs<BackupVersionTooNewException>(result.exceptionOrNull())
        assertEquals(0, runner.runs, "版本不认识就不该开始恢复：事务一次都不该开")
        assertEquals(0, settings.restoreFromSnapshotCalls)
        assertTrue(backupRepo.savedFileNames.isEmpty(), "连恢复前副本都不必写 —— 这一步发生在验版本之后")
    }

    /** 身份闸门：`{}` 能反序列化成功（每个字段都有默认值），但不能当备份用。 */
    @Test
    fun import_rejectsFileWithoutCreatedAt() = runTest {
        val backupRepo = FakeBackupFileRepository()
        val settings = FakeSettingsRepository()
        val music = FakeMusicRepository()
        val playlists = FakePlaylistRepository()
        val runner = FakeTransactionRunner()
        val export = ExportUserDataBackupUseCase(settings, music, playlists, backupRepo)
        val import = ImportUserDataBackupUseCase(settings, music, playlists, backupRepo, export, runner)

        // `{}` 的反序列化结果就是全默认值：version=1、createdAt=0、四个分区全空。
        val decodedEmpty = json.decodeFromString(UserBackupSnapshot.serializer(), "{}")
        val path = backupRepo.saveBackup(decodedEmpty).getOrThrow()

        val result = import(path)

        assertTrue(result.isFailure)
        assertEquals(0, runner.runs, "认不出的文件绝不能走到 deleteAll + 重建这一步")
    }
}
