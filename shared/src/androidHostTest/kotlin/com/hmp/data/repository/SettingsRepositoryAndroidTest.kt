package com.hmp.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * X-06：`SettingsRepositoryImpl.android`（500 行）此前零测试 —— `desktopTest` 里那 57 例跑的是
 * `SettingsRepositoryImpl.desktop`，两个文件差异 **298 行**，是真正的第二份实现，不是同一份代码换个壳。
 *
 * 这组用例钉「DataStore 键名 ↔ 读写方法」这一层：键名拼错是这类实现最常见、也最难靠人眼发现的错
 * （编译期完全合法，运行期就是读不到 / 写不进去）。
 *
 * ⚠️ **不假设初始为空**：Robolectric 在同一测试类里复用 Application，`preferencesDataStore` 的实例
 * 与落盘文件会跨用例保留（首版断言 `assertNull` 就是因此红的）。所以每条用例都用**不同的值**并断言
 * 写进去的就是读出来的 —— 这样即使有残留也骗不过去，而"首次启动为空"是另一个问题（默认值），
 * 需要能注入独立 DataStore 才测得了，不在本组范围。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryAndroidTest {

    private fun repo() = SettingsRepositoryImpl(RuntimeEnvironment.getApplication())

    @Test
    fun likedPlaylistId_roundTrips() = runTest {
        val repo = repo()
        repo.saveLikedPlaylistId(42L)
        assertEquals(42L, repo.getLikedPlaylistId())
    }

    @Test
    fun recentPlaylistId_roundTrips() = runTest {
        val repo = repo()
        repo.saveRecentPlaylistId(7L)
        assertEquals(7L, repo.getRecentPlaylistId())
    }

    @Test
    fun currentPlaylistId_overwrites() = runTest {
        val repo = repo()
        repo.saveCurrentPlaylistId(1L)
        assertEquals(1L, repo.getCurrentPlaylistId())

        repo.saveCurrentPlaylistId(9L)
        assertEquals(9L, repo.getCurrentPlaylistId(), "重复写入应覆盖而不是追加")
    }

    @Test
    fun likedAndRecentKeysAreIndependent() = runTest {
        val repo = repo()
        repo.saveLikedPlaylistId(3L)
        repo.saveRecentPlaylistId(5L)

        // 键名若被抄成同一个，这两条会互相覆盖
        assertEquals(3L, repo.getLikedPlaylistId())
        assertEquals(5L, repo.getRecentPlaylistId())
    }
}
