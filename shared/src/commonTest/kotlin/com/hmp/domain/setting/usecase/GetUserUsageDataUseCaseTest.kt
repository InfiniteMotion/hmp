package com.hmp.domain.setting.usecase

import com.hmp.test.fakes.FakeMusicRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetUserUsageDataUseCaseTest {

    private val musicRepository = FakeMusicRepository()
    private val useCase = GetUserUsageDataUseCase(musicRepository)

    @Test
    fun getAnalytics_returnsDefaultValues() = runTest {
        val analytics = useCase.getAnalytics()
        assertEquals(0, analytics.totalPlayCount)
        assertEquals(0, analytics.totalSkipCount)
        assertEquals(0, analytics.likedCount)
        assertEquals(0L, analytics.totalListeningMinutes)
        assertTrue(analytics.topPlayedSongs.isEmpty())
    }
}
