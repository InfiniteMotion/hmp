package com.hmp.desktop.player

import com.hmp.domain.backup.PlaylistsSnapshot
import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.playlist.Playlist
import com.hmp.domain.playlist.PlaylistRepository
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * 播放队列语义测试的最小替身。
 *
 * 为什么不用 `shared` 的 `commonTest/fakes/`：`:desktop:core-player` 的 `desktopTest`
 * 与 `:shared` 的测试源集**没有依赖关系**（后者也未导出 test artifact），跨模块复用
 * 需要引入 KMP 的 test fixtures，成本远高于收益。
 *
 * 为什么用动态代理：`SettingsRepository` 有 117 个方法、`MusicRepository` 86 个 ——
 * 手写完整 fake 要 200+ 个空实现。而队列语义与启动恢复只读少数几个成员
 * （见 [settingsRepositoryWithPlaylistId] 与 [stubInterface] 的默认值），其余方法
 * 在测试路径上不会被调用。
 *
 * ⚠️ 该替身只服务「队列语义」这一组测试。若将来要测其它路径，请先确认所依赖的成员
 * 是否已在此处给出合理返回值，否则会在 `scope.launch` 的 try/catch 里被静默吞掉。
 */

/** 造一条测试曲目。id 即身份，标题便于断言失败时定位。 */
internal fun testMusicInfo(id: Long, title: String = "曲目 $id"): MusicInfo = MusicInfo(
    music = Music(
        id = id,
        title = title,
        artist = "测试艺术家",
        album = "测试专辑",
        duration = 180_000L,
        path = "/test/$id.mp3",
        albumArtUri = "",
    ),
    extra = null,
    userInfo = null,
)

/**
 * 生成接口的默认实现：属性返回 [MutableStateFlow]/[MutableStateFlow] 包裹的合理值，
 * 方法返回类型安全的零值（Unit / null / 0 / false / 空集合）。
 *
 * 关键：`DesktopMusicController` **构造期**会读 `settingsRepository.currentPlaylistId`
 * / `likedPlaylistId` / `recentPlaylistId` 与 `timerUseCase.timerRemaining` —— 若这些
 * 返回 null，构造即崩溃，所以不能简单地让所有属性都返回 null。
 */
@Suppress("UNCHECKED_CAST")
internal inline fun <reified T : Any> stubInterface(
    noinline handler: (name: String) -> Any? = { null },
): T {
    val clazz = T::class.java
    return java.lang.reflect.Proxy.newProxyInstance(
        clazz.classLoader,
        arrayOf(clazz),
    ) { _, method, _ ->
        val custom = handler(method.name)
        if (custom != null) return@newProxyInstance custom

        when (val rt = method.returnType) {
            Void.TYPE -> Unit
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            else -> when {
                // 关键：Flow 默认返回「什么也不发射」的流，而非 MutableStateFlow(null)。
                // 否则 init 里的 collectLatest 会把 null 赋给非空的 _currentPlaylist，
                // 且 advanceUntilIdle() 会被 init 的响应式恢复把手动添加的列表覆写掉。
                kotlinx.coroutines.flow.Flow::class.java.isAssignableFrom(rt) ->
                    kotlinx.coroutines.flow.emptyFlow<Any?>()
                List::class.java.isAssignableFrom(rt) -> emptyList<Any>()
                Set::class.java.isAssignableFrom(rt) -> emptySet<Any>()
                Map::class.java.isAssignableFrom(rt) -> emptyMap<Any, Any>()
                else -> null
            }
        }
    } as T
}

/**
 * 可控的 [PlaylistRepository] 替身，用于编排「DB 回流」与「本地写盘落地」的时序。
 *
 * 为什么不能用 [stubInterface]：这里的两个挂起点（[getMusicInfoInPlaylist] 的发射、
 * [resetPlaylistItems] 的挂起）都需要测试**主动驱动**，而动态代理只能给出静态返回值。
 *
 * 为什么必须能卡住写盘：[writeGate] 复现的是真实竞争 —— 磁盘写入比 DB 查询回流慢，
 * 所以「写入还没落地、回流先到」才是默认情形。若不卡住，写入会在测试调度器上
 * 瞬间完成，竞争窗口消失，回归测试就测不到 [DesktopMusicController] 里的保护逻辑。
 */
internal class FakePlaylistRepository(
    initialDbPlaylist: List<MusicInfo> = emptyList(),
) : PlaylistRepository {

    /** 模拟 DB 侧的快照流。测试改值即触发「回流」。 */
    val dbPlaylist = MutableStateFlow(initialDbPlaylist)

    /** 每次 [resetPlaylistItems] 落盘的载荷，用于断言「写回的到底是不是空列表」。 */
    val writes = mutableListOf<List<MusicInfo>>()

    /** 非空时 [resetPlaylistItems] 会挂起至其完成 —— 模拟写盘耗时。 */
    var writeGate: CompletableDeferred<Unit>? = null

    override fun getMusicInfoInPlaylist(playlistId: Long): Flow<List<MusicInfo>> = dbPlaylist

    override suspend fun resetPlaylistItems(playlistId: Long, musicList: List<MusicInfo>) {
        writeGate?.await()
        writes += musicList
    }

    override suspend fun getPlaylistById(playlistId: Long): List<MusicInfo> = dbPlaylist.value

    override suspend fun createPlaylist(name: String): Long = 0L
    override suspend fun removePlaylist(name: String) = Unit
    override suspend fun removePlaylistById(id: Long) = Unit
    override suspend fun getAllPlaylists(): List<Playlist> = emptyList()
    override suspend fun getPlaylistMeta(id: Long): Playlist? = null
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

/**
 * 让 `settingsRepository.currentPlaylistId` 由测试控制的替身。
 *
 * [playlistId] 传 [Flow] 而非具体值，是为了区分两种「重发」语义：
 * `MutableStateFlow` 只在值变化时发射（模拟正常的 id 变更），
 * `MutableSharedFlow` 可以重发同一个值（模拟 DataStore 因其它设置项写入而重放）。
 */
internal fun settingsRepositoryWithPlaylistId(
    playlistId: Flow<Long?>,
): SettingsRepository = stubInterface { name ->
    when (name) {
        "getCurrentPlaylistId" -> playlistId
        // loadPlaylistFromSettings 对 currentMusicId 取 .first()：默认 emptyFlow 会让它抛
        // NoSuchElementException、整段恢复被 catch 吞掉，启动后内存队列恒为空。
        "getCurrentMusicId" -> MutableStateFlow<Long?>(null)
        "getLikedPlaylistId" -> MutableStateFlow<Long?>(2L)
        "getRecentPlaylistId" -> MutableStateFlow<Long?>(3L)
        "getCurrentPosition" -> MutableStateFlow(0L)
        else -> null
    }
}

/**
 * `DesktopMusicController` 测试的统一入口：把 `Dispatchers.Main` 接管为与本用例
 * **同一 scheduler** 的 [StandardTestDispatcher]。
 *
 * 为什么必须显式接管：controller 的 `scope` 绑定 `Dispatchers.Main`，而
 * `desktopTestRuntimeClasspath` 里有 `kotlinx-coroutines-swing`，于是 Main 是一个
 * **真实的 Swing EDT**，不是测试调度器。`runTest` 不会替你改这一点，后果分两种：
 * - 断言「同步赋值」的用例照常通过（如 `addToPlaylist` 的队列长度）；
 * - 断言「`scope.launch` 里的赋值」的用例则在赌竞态 —— EDT 恰好先跑完就绿，
 *   否则红。本模块就出现过「单独跑绿、全量跑红」的假失败。
 *
 * 用 Standard 而非 Unconfined：需要 [kotlinx.coroutines.test.runCurrent] 的
 * 「跑到挂起点就停」语义来制造「写入悬在半空」的竞争窗口，Unconfined 会一路跑完。
 *
 * ⚠️ 一旦用例触发了播放（`playMusic` / `playAt` / `playWith` / `setPlaylist` …），
 * **不要用 `advanceUntilIdle()`** —— `startProgressTracking()` 是
 * `while (isActive) { …; delay(100) }` 的无限循环，在测试调度器上永远有新任务，
 * `advanceUntilIdle()` 会一直推进虚拟时间直到 CPU 打满、用例永不返回。
 * 这类用例请用 `runCurrent()`：只跑「当前虚拟时刻」已排队的任务，足以让
 * `playMusic` 里那次 `scope.launch` 完成赋值，而 `delay(100)` 会挂起并让它返回。
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun runTestWithMain(body: suspend TestScope.() -> Unit): TestResult = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        body()
    } finally {
        Dispatchers.resetMain()
    }
}
