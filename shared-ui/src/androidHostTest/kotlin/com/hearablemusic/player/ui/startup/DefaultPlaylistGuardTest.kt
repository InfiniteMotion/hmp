package com.hearablemusic.player.ui.startup

import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.backup.PlaylistsSnapshot
import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [DefaultPlaylistGuard] 的启动不变量测试。
 *
 * 要钉死的事故：destructive migration 清掉 `playlist` 表后，DataStore 残留的
 * 旧系统歌单 id 变悬空指针——旧判据（id==null 才建）永远不触发，队列持久化
 * FK 必败被静默吞掉，播放胶囊「闪一下消失」。判据必须穿透到 DB 行。
 */
class DefaultPlaylistGuardTest {

    private companion object {
        const val CURRENT = "默认歌单"
        const val LIKED = "红心"
        const val RECENT = "最近播放"
    }

    /** 只对 Guard 语义有状态的 PlaylistRepository 替身；其余成员空实现。 */
    private class GuardPlaylistRepository : PlaylistRepository {
        val metas = mutableMapOf<Long, Playlist>()
        val createdNames = mutableListOf<String>()
        val removedNames = mutableListOf<String>()
        var nextId = 1L

        /** 置 true 时 [getPlaylistMeta] 抛异常，模拟迁移期锁库等 DB 故障 */
        var metaThrows = false

        /**
         * 非空时 [getPlaylistMeta] 挂起至其完成 —— 用于在「查行」这一步制造并发交错点。
         * 不加这个挡板，两个 ensureAll 会在测试调度器上先后跑完（fake 无挂起点），
         * 根本测不到 [DefaultPlaylistGuard] 的串行保护。
         */
        var metaGate: CompletableDeferred<Unit>? = null

        override suspend fun getPlaylistMeta(id: Long): Playlist? {
            metaGate?.await()
            if (metaThrows) throw RuntimeException("db locked")
            return metas[id]
        }

        override suspend fun createPlaylist(name: String): Long {
            createdNames += name
            val id = nextId++
            metas[id] = Playlist(id = id, name = name)
            return id
        }

        override suspend fun removePlaylist(name: String) {
            removedNames += name
        }

        override fun getMusicInfoInPlaylist(playlistId: Long): Flow<List<MusicInfo>> = emptyFlow()
        override suspend fun resetPlaylistItems(playlistId: Long, musicList: List<MusicInfo>) = Unit
        override suspend fun getPlaylistById(playlistId: Long): List<MusicInfo> = emptyList()
        override suspend fun removePlaylistById(id: Long) = Unit
        override suspend fun getAllPlaylists(): List<Playlist> = emptyList()
        override suspend fun renamePlaylist(id: Long, newName: String) = Unit
        override suspend fun updatePlaylistCover(id: Long, coverUri: String?) = Unit
        override suspend fun updatePlaylistDescription(id: Long, description: String?) = Unit
        override suspend fun setPlaylistPinned(id: Long, isPinned: Boolean) = Unit
        override suspend fun incrementPlaylistPlayCount(id: Long) = Unit
        override suspend fun setPlaylistLastPlayedAt(id: Long, timestamp: Long) = Unit
        override suspend fun addToPlaylist(playlistId: Long, musicId: Long, musicPath: String) = Unit
        override suspend fun removeItemFromPlaylist(musicId: Long, playlistId: Long) = Unit
        override suspend fun reorderPlaylistItems(playlistId: Long, orderedMusicIds: List<Long>) = Unit
        override suspend fun getPlaylistByIdList(playlistIdList: List<Long>): List<MusicInfo> = emptyList()
        override fun getAllPlaylistsFlow(): Flow<List<Playlist>> = emptyFlow()
        override suspend fun exportPlaylistsSnapshot(): PlaylistsSnapshot = PlaylistsSnapshot()
        override suspend fun restoreFromSnapshot(snapshot: PlaylistsSnapshot) = Unit
    }

    /** 存方法 → 读方法，用于让 fake 具备真实「存了就能读回」的状态语义。 */
    private val getterOfSave = mapOf(
        "saveCurrentPlaylistId" to "getCurrentPlaylistId",
        "saveLikedPlaylistId" to "getLikedPlaylistId",
        "saveRecentPlaylistId" to "getRecentPlaylistId",
    )

    /**
     * SettingsRepository 有百余个成员，手写完整 fake 不现实；Guard 只触碰
     * 六个方法（三个读 + 三个存），动态代理按方法名拦截即可。其余成员返回
     * 类型安全的零值（Flow → emptyFlow，其余 null/Unit/0）。
     *
     * 必须**有状态**：存进去的 id 要能被后续读回，否则第二次 ensure 永远看到
     * 「id 缺失」而重复重建，测不出自愈的幂等性。
     */
    @Suppress("UNCHECKED_CAST")
    private fun settingsFake(
        ids: Map<String, Long?>,
        saves: MutableMap<String, MutableList<Long>>,
    ): SettingsRepository {
        val stored = ids.toMutableMap()
        return java.lang.reflect.Proxy.newProxyInstance(
            SettingsRepository::class.java.classLoader,
            arrayOf(SettingsRepository::class.java),
        ) { _, method, args ->
            getterOfSave[method.name]?.let { getter ->
                val value = args?.first() as Long
                saves[method.name]?.let { it += value }
                stored[getter] = value
                return@newProxyInstance Unit
            }
            stored[method.name]?.let { return@newProxyInstance it }
            when (method.returnType) {
                Void.TYPE -> Unit
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                else -> when {
                    Flow::class.java.isAssignableFrom(method.returnType) -> emptyFlow<Any?>()
                    List::class.java.isAssignableFrom(method.returnType) -> emptyList<Any>()
                    else -> null
                }
            }
        } as SettingsRepository
    }

    private fun defaultSaves(): MutableMap<String, MutableList<Long>> = mutableMapOf(
        "saveCurrentPlaylistId" to mutableListOf(),
        "saveLikedPlaylistId" to mutableListOf(),
        "saveRecentPlaylistId" to mutableListOf(),
    )

    private fun newGuard(
        repo: GuardPlaylistRepository,
        ids: Map<String, Long?> = emptyMap(),
        saves: MutableMap<String, MutableList<Long>> = defaultSaves(),
    ): DefaultPlaylistGuard {
        val settings = settingsFake(ids, saves)
        return DefaultPlaylistGuard(ManagePlaylistUseCase(repo, settings), settings)
    }

    @Test
    fun missingIds_createsAllThreeAndSavesNewIds() = runTest {
        val repo = GuardPlaylistRepository()
        val saves = defaultSaves()
        newGuard(repo, saves = saves).ensureAll(CURRENT, LIKED, RECENT)

        assertEquals(listOf(CURRENT, LIKED, RECENT), repo.createdNames)
        assertEquals(listOf(CURRENT, LIKED, RECENT), repo.removedNames)
        assertEquals(listOf(1L), saves["saveCurrentPlaylistId"])
        assertEquals(listOf(2L), saves["saveLikedPlaylistId"])
        assertEquals(listOf(3L), saves["saveRecentPlaylistId"])
    }

    @Test
    fun danglingCurrentId_rebuildsOnlyThatOne() = runTest {
        val repo = GuardPlaylistRepository().apply {
            // 红心/最近行健在；默认歌单行被 destructive migration 清掉
            metas[2L] = Playlist(id = 2, name = LIKED)
            metas[3L] = Playlist(id = 3, name = RECENT)
            nextId = 10L
        }
        val ids = mapOf(
            "getCurrentPlaylistId" to 1L,
            "getLikedPlaylistId" to 2L,
            "getRecentPlaylistId" to 3L,
        )
        val saves = defaultSaves()
        newGuard(repo, ids, saves).ensureAll(CURRENT, LIKED, RECENT)

        assertEquals(listOf(CURRENT), repo.createdNames)
        assertEquals(listOf(CURRENT), repo.removedNames)
        assertEquals(listOf(10L), saves["saveCurrentPlaylistId"])
        assertEquals(emptyList<Long>(), saves["saveLikedPlaylistId"])
        assertEquals(emptyList<Long>(), saves["saveRecentPlaylistId"])
    }

    @Test
    fun allIdsValid_noRebuildNoSaves() = runTest {
        val repo = GuardPlaylistRepository().apply {
            metas[1L] = Playlist(id = 1, name = CURRENT)
            metas[2L] = Playlist(id = 2, name = LIKED)
            metas[3L] = Playlist(id = 3, name = RECENT)
        }
        val ids = mapOf(
            "getCurrentPlaylistId" to 1L,
            "getLikedPlaylistId" to 2L,
            "getRecentPlaylistId" to 3L,
        )
        val saves = defaultSaves()
        newGuard(repo, ids, saves).ensureAll(CURRENT, LIKED, RECENT)

        assertEquals(emptyList<String>(), repo.createdNames)
        assertEquals(emptyList<String>(), repo.removedNames)
        assertEquals(emptyList<Long>(), saves["saveCurrentPlaylistId"])
        assertEquals(emptyList<Long>(), saves["saveLikedPlaylistId"])
        assertEquals(emptyList<Long>(), saves["saveRecentPlaylistId"])
    }

    /**
     * 并发钉子：两个调用方（AppRoot 启动自愈 + PlaylistViewModel 初始化）同时 ensureAll，
     * 每条系统歌单只能建一次。没串行化时会各建一条同名行，DataStore 只留住最后写入的 id。
     */
    @Test
    fun concurrentEnsureAll_createsExactlyOnePerSystemPlaylist() = runTest {
        val repo = GuardPlaylistRepository()
        val saves = defaultSaves()
        val guard = newGuard(repo, saves = saves)
        // 挡板卡在「查行」这一步：两个调用方都读到「行不存在」后才会继续，
        // 只有串行化能保证第二个是在第一个**建完并落库**之后才判定的。
        repo.metaGate = CompletableDeferred()

        val jobs = List(2) { launch { guard.ensureAll(CURRENT, LIKED, RECENT) } }
        runCurrent()
        repo.metaGate?.complete(Unit)
        advanceUntilIdle()
        jobs.forEach { it.join() }

        assertEquals(listOf(CURRENT, LIKED, RECENT), repo.createdNames)
        assertEquals(1, saves["saveCurrentPlaylistId"]?.size)
        assertEquals(1, saves["saveLikedPlaylistId"]?.size)
        assertEquals(1, saves["saveRecentPlaylistId"]?.size)
    }

    @Test
    fun metaLookupThrows_treatedAsMissing_rebuildContinues() = runTest {
        val repo = GuardPlaylistRepository().apply {
            metaThrows = true
            nextId = 7L
        }
        val ids = mapOf("getCurrentPlaylistId" to 1L)
        val saves = defaultSaves()
        newGuard(repo, ids, saves).ensureAll(CURRENT, LIKED, RECENT)

        // 查行失败（DB 故障）视同失效：重建且不中断其余两条
        assertEquals(listOf(CURRENT, LIKED, RECENT), repo.createdNames)
        assertEquals(listOf(7L), saves["saveCurrentPlaylistId"])
    }
}
