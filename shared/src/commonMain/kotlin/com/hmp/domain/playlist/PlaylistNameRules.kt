package com.hmp.domain.playlist

/**
 * 歌单名校验口径（D3-05④ / D3-12）：空 / 超长 / 重名。
 *
 * 为什么单独抽出来：歌单有两个写入方 —— UI（`DialogViewModel`）与 Agent 的 `playlist_create` /
 * `playlist_rename`。过去只有 UI 校验，Agent 能造出 UI 造不出来的脏数据（重名、超长名）。
 * 现在两边共用这一份规则；差一个"这条规则必须两边同时改"的约定，就靠这个文件收口。
 *
 * 重名比较用**小写**：DB 的 `UNIQUE(playlist.name)` 是大小写敏感的，但用户视角「Rock」与
 * 「rock」是同一个名字，所以这里比索引更严一档（与旧 UI 行为保持一致）。
 */
enum class PlaylistNameError { EMPTY, TOO_LONG, DUPLICATE }

const val PLAYLIST_NAME_MAX_LENGTH = 30

/**
 * 只判格式（空 / 超长），不查库。
 *
 * 重名要查库，由调用方用 `PlaylistRepository.getPlaylistByName` 判 —— 纯函数留在这里，
 * 是为了让 UI 与 Agent 至少在"多长算太长"这件事上不可能分叉。
 */
fun validatePlaylistNameFormat(raw: String): PlaylistNameError? {
    val trimmed = raw.trim()
    return when {
        trimmed.isEmpty() -> PlaylistNameError.EMPTY
        trimmed.length > PLAYLIST_NAME_MAX_LENGTH -> PlaylistNameError.TOO_LONG
        else -> null
    }
}

/** 重名比较口径：小写 + 去首尾空白。 */
fun normalizePlaylistName(raw: String): String = raw.trim().lowercase()

/**
 * 完整校验（空 / 超长 / 重名）—— **全仓唯一实现**。
 *
 * UI 的三个入口（弹窗创建/编辑、管理页行内重命名、列表页回退入口）与 Agent 的两个工具都调这一份；
 * 之前 `ManagePlaylistUseCase` 与 `PlaylistTools` 各写了一遍同样的 `firstOrNull { ... }`，
 * 那种"两边都记得改"的约定迟早会散架。
 *
 * @param excludeId 重命名时传自己的 id；否则「改成原名」会被判成重名。
 */
suspend fun validatePlaylistName(
    raw: String,
    repository: PlaylistRepository,
    excludeId: Long? = null,
): PlaylistNameError? {
    validatePlaylistNameFormat(raw)?.let { return it }
    val normalized = normalizePlaylistName(raw)
    val existing = repository.getAllPlaylists()
        .firstOrNull { normalizePlaylistName(it.name) == normalized }
    return if (existing == null || existing.id == excludeId) null else PlaylistNameError.DUPLICATE
}

/**
 * 写入被名称规则拒绝时抛出。
 *
 * 为什么是异常而不是返回值：`createPlaylist` / `renamePlaylist` 的返回类型（`Long` / `Unit`）被
 * 十几处测试与调用点使用，改成密封结果会把改动面摊到与规则无关的地方。
 * 调用点要展示文案时按 [error] 翻成本地化字符串即可。
 */
class PlaylistNameException(val error: PlaylistNameError) :
    IllegalArgumentException(
        when (error) {
            PlaylistNameError.EMPTY -> "歌单名不能为空"
            PlaylistNameError.TOO_LONG -> "歌单名不能超过 $PLAYLIST_NAME_MAX_LENGTH 个字"
            PlaylistNameError.DUPLICATE -> "已存在同名歌单"
        },
    )
