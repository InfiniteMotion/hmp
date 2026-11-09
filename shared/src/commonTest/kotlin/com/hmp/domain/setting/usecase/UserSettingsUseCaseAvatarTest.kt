package com.hmp.domain.setting.usecase

import com.hmp.test.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 7.5 P1 闭环判据：换头像后 `UserScreen` 必须立刻看到新值。
 * `UserSettingsUseCase` 是 Koin single，`avatarUri` 是全局唯一可订阅镜像，
 * 个人资料页写入与 UserScreen 订阅走的是同一份状态。
 */
class UserSettingsUseCaseAvatarTest {

    @Test
    fun saveAvatarUri_updatesSharedMirror() = runTest {
        val useCase = UserSettingsUseCase(FakeSettingsRepository())
        assertEquals("", useCase.avatarUri.value)

        useCase.saveAvatarUri("content://avatar/new")

        assertEquals("content://avatar/new", useCase.avatarUri.value)
        assertEquals("content://avatar/new", useCase.getAvatarUri())
    }

    @Test
    fun getAvatarUri_refreshesMirrorFromRepository() = runTest {
        val repository = FakeSettingsRepository()
        val useCase = UserSettingsUseCase(repository)

        // 备份恢复这类绕过 UseCase 的写入：镜像起初不知道
        repository.saveAvatarUri("content://avatar/from-backup")
        assertEquals("", useCase.avatarUri.value)

        // 重新读取即回填，UserScreen 进入页面时的 getAvatarUri() 走这条路
        assertEquals("content://avatar/from-backup", useCase.getAvatarUri())
        assertEquals("content://avatar/from-backup", useCase.avatarUri.value)
    }
}
