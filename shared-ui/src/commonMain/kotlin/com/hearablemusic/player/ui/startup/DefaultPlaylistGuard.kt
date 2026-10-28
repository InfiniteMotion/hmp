package com.hearablemusic.player.ui.startup

import com.hmp.domain.playlist.usecase.ManagePlaylistUseCase
import com.hmp.domain.setting.SettingsRepository
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 启动不变量：三个系统歌单（默认播放 / 红心 / 最近播放）在 DB 中必然存在，
 * 且 DataStore 里的 id 必然指向真实存在的歌单行。
 *
 * 为什么需要：id 存 DataStore、行存 Room，是两个独立存储。destructive migration
 * 或任何清库操作只重建 DB、不动 DataStore——残留的旧 id 变悬空指针，队列持久化
 * 从此外键必败且被静默吞掉（播放胶囊「闪一下消失」的根因）。校验必须穿透到
 * DB 行：id 缺失、或 id 指向的行不存在，一律重建（getOrCreate 语义）。
 *
 * 歌单名由调用方传入（common 层拿不到 compose 资源）：AppRoot 与
 * PlaylistViewModel 用 `getString(Res.string.*)` 解析后调用。
 */
class DefaultPlaylistGuard(
    private val managePlaylistUseCase: ManagePlaylistUseCase,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * 串行为什么必要：[ensure] 是 check-then-act（查行 → 不存在则接管同名行 / 新建）。
     * 调用方不止一个（AppRoot 启动自愈 + PlaylistViewModel 初始化），两者在启动期会并发跑：
     * 若交错，会各建一条同名歌单行，DataStore 只留住最后写入的那个 id，另一条成孤儿。
     * 锁是**进程级**的（companion），因为各调用方各自 new 一个 Guard，实例锁不起作用。
     */
    suspend fun ensureAll(currentName: String, likedName: String, recentName: String) =
        GUARD_LOCK.withLock {
            ensure(
                displayName = currentName,
                getId = { settingsRepository.getCurrentPlaylistId() },
                saveId = { settingsRepository.saveCurrentPlaylistId(it) },
            )
            ensure(
                displayName = likedName,
                getId = { settingsRepository.getLikedPlaylistId() },
                saveId = { settingsRepository.saveLikedPlaylistId(it) },
            )
            ensure(
                displayName = recentName,
                getId = { settingsRepository.getRecentPlaylistId() },
                saveId = { settingsRepository.saveRecentPlaylistId(it) },
            )
        }

    private companion object {
        val GUARD_LOCK = Mutex()
    }

    /**
     * 单条校验永不上抛：自愈失败只记日志，不能炸掉启动链路，也不能阻断其余
     * 两条系统歌单的校验。
     *
     * 三种形状，按"最小破坏"排：
     * 1. id 指向的行在 → 什么都不做（幂等）；
     * 2. id 悬空/缺失，但库里有同名行 → **接管那一行**（把 DataStore 的 id 指过去）；
     * 3. 两者都没有 → 新建。
     *
     * 第 2 步以前是"按名删除 + 新建一条空的"：那等于把用户的心动 / 最近播放在一次悬空 id
     * 之后整个清空（条目随 `playlist_item` 的外键级联一起没），还顺带绕过了
     * `removePlaylistById` 的系统歌单保护（D3-05②③）。
     */
    private suspend fun ensure(
        displayName: String,
        getId: suspend () -> Long?,
        saveId: suspend (Long) -> Unit,
    ) {
        try {
            val id = getId()
            val meta = id?.let { runCatching { managePlaylistUseCase.getPlaylistMeta(it) }.getOrNull() }
            if (meta != null) return

            val existing = runCatching { managePlaylistUseCase.findPlaylistByName(displayName) }.getOrNull()
            if (existing != null) {
                saveId(existing.id)
                HmpLog.w(LogTag.SystemInit) {
                    "系统歌单 id 失效(id=$id, name=$displayName)，已接管同名歌单 id=${existing.id}"
                }
                return
            }

            val newId = managePlaylistUseCase.createPlaylist(displayName)
            saveId(newId)
            HmpLog.w(LogTag.SystemInit) { "系统歌单缺失(id=$id, name=$displayName)，已新建 id=$newId" }
        } catch (e: Exception) {
            HmpLog.e(LogTag.SystemInit, e) { "系统歌单自愈失败: name=$displayName" }
        }
    }
}
