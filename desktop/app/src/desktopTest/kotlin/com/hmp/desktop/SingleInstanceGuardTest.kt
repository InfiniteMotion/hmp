package com.hmp.desktop

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 桌面壳的单实例锁（`SingleInstanceGuard`）。
 *
 * 这是 desktop/app 里少数能在本机闭环的行为 —— 其余（无边框窗口、托盘、主题监听线程、
 * 退出时序）都要实机走查，见 `docs/7_3/domain/D9.md` §五。
 *
 * ⚠️ 全程把 `user.home` 指到临时目录：真路径是 `~/.hmp/hmp.lock`，
 * 直接在开发者 home 里加锁会和在跑的应用抢锁，还会留下脏文件。
 * `SingleInstanceGuard.tryAcquire()` 是**调用时**读 `user.home`，所以这个隔离成立。
 */
class SingleInstanceGuardTest {

    private lateinit var fakeHome: File
    private var originalHome: String? = null

    @BeforeTest
    fun redirectHomeToTemp() {
        originalHome = System.getProperty("user.home")
        fakeHome = File(System.getProperty("java.io.tmpdir"), "hmp-single-instance-${System.nanoTime()}")
        check(fakeHome.mkdirs()) { "临时 home 目录创建失败：${fakeHome.path}" }
        System.setProperty("user.home", fakeHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        // release() 是全局 object 的状态，必须每个用例后清干净，否则用例之间互相占锁
        SingleInstanceGuard.release()
        originalHome?.let { System.setProperty("user.home", it) }
        fakeHome.deleteRecursively()
    }

    @Test
    fun firstAcquireSucceeds_andLockFileLandsUnderDotHmp() {
        assertTrue(SingleInstanceGuard.tryAcquire(), "无人持锁时首个实例应拿到单实例锁")
        assertTrue(
            File(fakeHome, ".hmp/hmp.lock").isFile,
            "锁文件路径必须是 ~/.hmp/hmp.lock —— 与 Main.kt 与文档口径一致",
        )
    }

    @Test
    fun secondAcquireWhileHeldIsRefused() {
        assertTrue(SingleInstanceGuard.tryAcquire())
        assertFalse(
            SingleInstanceGuard.tryAcquire(),
            "锁未释放前再次获取必须失败，否则重复启动的第二个进程会照常起来",
        )
    }

    @Test
    fun releaseAllowsNextStart() {
        assertTrue(SingleInstanceGuard.tryAcquire())
        SingleInstanceGuard.release()
        assertTrue(SingleInstanceGuard.tryAcquire(), "干净退出（onExit 走 release()）之后应能重新启动")
    }

    /**
     * 崩溃残留不阻塞启动：上一次进程被 kill 时没调 `release()`，只留下一个锁文件。
     * 文件锁随进程/通道结束由 OS 收回，所以**存在 `hmp.lock` 文件本身不代表有人在跑**。
     * 这条若变红，说明用户"崩过一次就再也打不开"了。
     */
    @Test
    fun staleLockFileFromCrashedProcessDoesNotBlockStartup() {
        val lockDir = File(fakeHome, ".hmp").apply { mkdirs() }
        val staleLock = File(lockDir, "hmp.lock")
        staleLock.writeText("stale holder already gone")
        assertTrue(staleLock.isFile, "前置条件：先造出崩溃残留的锁文件")

        assertTrue(SingleInstanceGuard.tryAcquire(), "残留的锁文件不该被当成「另一个实例在跑」")
    }
}
