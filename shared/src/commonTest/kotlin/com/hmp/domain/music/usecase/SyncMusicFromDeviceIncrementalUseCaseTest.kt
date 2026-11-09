package com.hmp.domain.music.usecase

import com.hmp.test.fakes.FakeMusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncMusicFromDeviceIncrementalUseCaseTest {

    private val repository = FakeMusicRepository()
    private val useCase = SyncMusicFromDeviceIncrementalUseCase(repository)

    @Test
    fun invoke_returnsSuccess() = runTest {
        val result = useCase()
        assertTrue(result.isSuccess)
    }

    @Test
    fun isScanning_initiallyFalse() = runTest {
        assertFalse(useCase.isScanning().first())
    }
}
