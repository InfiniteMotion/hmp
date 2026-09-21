package com.hmp.desktop.player

import com.hmp.domain.music.Music
import com.hmp.domain.music.MusicInfo
import com.hmp.domain.setting.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 播放队列语义测试的最小替身。
 *
 * 为什么不用 `shared` 的 `commonTest/fakes/`：`:desktop:core-player` 的 `desktopTest`
 * 与 `:shared` 的测试源集**没有依赖关系**（后者也未导出 test artifact），跨模块复用
 * 需要引入 KMP 的 test fixtures，成本远高于收益。
 *
 * 为什么用动态代理：`SettingsRepository` 有 117 个方法、`MusicRepository` 86 个 ——
 * 手写完整 fake 要 200+ 个空实现。而队列语义（`addToPlaylist` / `playAt` / `playWith`）
 * **只读 4 个成员**（见 `stubSettingsRepository`），其余方法在测试路径上不会被调用。
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
