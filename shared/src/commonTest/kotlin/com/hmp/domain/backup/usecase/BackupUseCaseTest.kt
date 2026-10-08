package com.hmp.domain.backup.usecase

import com.hmp.domain.backup.BackupFileRepository
import com.hmp.domain.backup.BackupRestoreFailedException
import com.hmp.domain.backup.UserBackupSnapshot
import com.hmp.test.fakes.FakeBackupFileRepository
import com.hmp.test.fakes.FakeMusicRepository
import com.hmp.test.fakes.FakePlaylistRepository
import com.hmp.test.fakes.FakeSettingsRepository
import com.hmp.test.fakes.FakeTransactionRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupUseCaseTest {

    private val settingsRepository = FakeSettingsRepository()
    private val musicRepository = FakeMusicRepository()
    private val playlistRepository = FakePlaylistRepository()
    private val backupFileRepository = FakeBackupFileRepository()

    private val exportUseCase = ExportUserDataBackupUseCase(settingsRepository, musicRepository, playlistRepository, backupFileRepository)
    private val transactionRunner = FakeTransactionRunner()
    private val importUseCase = ImportUserDataBackupUseCase(
        settingsRepository, musicRepository, playlistRepository, backupFileRepository,
        exportUseCase, transactionRunner
    )
    private val deleteUseCase = DeleteBackupUseCase(backupFileRepository)
    private val getBackupsUseCase = GetBackupsUseCase(backupFileRepository)

    @Test
    fun export_returnsPath() = runTest {
        val result = exportUseCase()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isNotEmpty())
    }

    @Test
    fun exportAndImport_roundTrip() = runTest {
        val path = exportUseCase().getOrNull()!!
        val importResult = importUseCase(path)
        assertTrue(importResult.isSuccess)
    }

    @Test
    fun getBackups_returnsSavedPaths() = runTest {
        exportUseCase()
        exportUseCase()
        val result = getBackupsUseCase()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()!!.size)
    }

    @Test
    fun deleteBackup_removesFile() = runTest {
        val path = exportUseCase().getOrNull()!!
        assertEquals(1, getBackupsUseCase().getOrNull()!!.size)
        assertTrue(deleteUseCase(path).isSuccess)
        assertEquals(0, getBackupsUseCase().getOrNull()!!.size)
    }

    @Test
    fun deleteBackup_nonExisting_returnsFailure() = runTest {
        val result = deleteUseCase("/nonexistent.json")
        assertTrue(result.isFailure)
    }

    @Test
    fun importBackup_nonExisting_returnsFailure() = runTest {
        val result = importUseCase("/nonexistent.json")
        assertTrue(result.isFailure)
    }

    @Test
    fun getBackups_emptyRepository_returnsEmptyList() = runTest {
        val result = getBackupsUseCase()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }

    // region D7-02：恢复前先留副本

    @Test
    fun import_writesExactlyOnePreRestoreCopy() = runTest {
        val path = exportUseCase().getOrNull()!!
        backupFileRepository.savedFileNames.clear()

        assertTrue(importUseCase(path).isSuccess)
        assertEquals(1, backupFileRepository.savedFileNames.size)
        assertTrue(backupFileRepository.savedFileNames[0].startsWith(BackupFileRepository.PRE_RESTORE_PREFIX))
        assertEquals(1, transactionRunner.runs, "四条 restore 要整段交给事务，不能各自开")
    }

    @Test
    fun import_cannotWriteSafetyCopy_abortsBeforeAnyRestore() = runTest {
        val path = exportUseCase().getOrNull()!!
        backupFileRepository.failNextSave = true

        assertTrue(importUseCase(path).isFailure)
        assertEquals(0, settingsRepository.restoreFromSnapshotCalls, "副本没写成就不该开始改库")
        assertEquals(0, transactionRunner.runs)
    }

    @Test
    fun import_step3Fails_reportsSafetyCopyPath() = runTest {
        val path = exportUseCase().getOrNull()!!
        playlistRepository.failRestoreWith = IllegalStateException("第 3 步坏了")

        val result = importUseCase(path)
        val error = assertIs<BackupRestoreFailedException>(result.exceptionOrNull())
        // 副本必须在备份列表里看得见，否则"手工找回"是句空话
        assertTrue(error.safetyCopyPath!!.startsWith("/${BackupFileRepository.PRE_RESTORE_PREFIX}-"))
        assertTrue(getBackupsUseCase().getOrNull()!!.contains(error.safetyCopyPath))
    }

    @Test
    fun import_cancellation_isRethrownNotWrapped() = runTest {
        val path = exportUseCase().getOrNull()!!
        playlistRepository.failRestoreWith = CancellationException("协程已取消")

        assertFailsWith<CancellationException> { importUseCase(path) }
    }

    // endregion
}
