package com.hmp.domain.agent.runtime

import com.hmp.domain.agent.runtime.sub.shared.SubAgent
import com.hmp.platform.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 子 Agent 注册表（D6-04）。
 *
 * 原实现是一张裸 `MutableMap`：三个 `start*` 各持**自己的** lifecycle mutex，写的却是同一张表；
 * 读侧（UI / 工具层 / `capabilities`）在任意线程直接读。两条并发路径都成立：
 * 两个 `start*` 同时 `put` → 丢写；`start` 与 `close()` 的 `clear()` 并发 → `ConcurrentModificationException`。
 *
 * 现在的形状：
 * - 写路径由 [mutex] 串行化，且**整份替换**（copy-on-write），不做就地修改；
 * - 读侧读的是 `@Volatile` 的不可变快照 —— 任何时刻拿到的都是一份完整映射，不需要加锁、也不会 CME；
 * - [clearNow] 给非 suspend 的 `close()` 用：单次引用替换，语义是"从现在起谁都看不到旧注册表"。
 */
internal class SubAgentRegistry {

    @Volatile
    private var snapshot: Map<String, SubAgent> = emptyMap()

    private val mutex = Mutex()

    /** 当前快照。调用方只读，不要缓存到比一次操作更久的生命周期里。 */
    val entries: Map<String, SubAgent> get() = snapshot

    operator fun get(key: String): SubAgent? = snapshot[key]

    suspend fun put(key: String, agent: SubAgent) = mutex.withLock {
        snapshot = snapshot + (key to agent)
    }

    suspend fun remove(key: String): SubAgent? = mutex.withLock {
        val removed = snapshot[key]
        if (removed != null) snapshot = snapshot - key
        removed
    }

    suspend fun clear() = mutex.withLock { snapshot = emptyMap() }

    /**
     * 非 suspend 的终态清理（`MasterAgent.close()` 用）。
     *
     * 不走 [mutex]：`close()` 是平台终止路径，不能挂起。这里只做一次引用替换 ——
     * 读侧立刻看不到任何子 Agent；极少数"正在 put 的协程随后又写回一条"是已知且可接受的，
     * 因为 close 之后 Master 本就不再复用。
     */
    fun clearNow() {
        snapshot = emptyMap()
    }
}
