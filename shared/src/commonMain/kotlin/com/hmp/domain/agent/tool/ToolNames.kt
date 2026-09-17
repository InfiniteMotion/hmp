package com.hmp.domain.agent.tool

/**
 * 原子工具名常量（S 阶段——基于 5 能力域重新整理）。
 * 域前缀统一：playback_*（播放）/ playlist_*（歌单）/ library_*（曲库检索）/ song_*（富化标签）/ agent_*（会话配额）。
 * Registry 全部注册 [ALL] 集合，LLM function-calling schema 按此全量下发。
 */
object ToolNames {
    // ── Playback（播放控制，5 个原子）──
    const val PLAYBACK_STATE = "playback_state"
    const val PLAYBACK_CONTROL = "playback_control"
    const val PLAYBACK_PLAY_AT = "playback_play_at"
    const val PLAYBACK_ENQUEUE = "playback_enqueue"

    // ── Playlist 实体 CRUD（5 个）──
    const val PLAYLIST_LIST = "playlist_list"
    const val PLAYLIST_DETAIL = "playlist_detail"
    const val PLAYLIST_CREATE = "playlist_create"
    const val PLAYLIST_RENAME = "playlist_rename"
    const val PLAYLIST_DELETE = "playlist_delete"

    // ── Playlist 曲目管理（3 个）──
    const val PLAYLIST_ADD_SONG = "playlist_add_song"
    const val PLAYLIST_REMOVE_SONG = "playlist_remove_song"
    const val PLAYLIST_REORDER = "playlist_reorder"

    // ── Library 搜索与聚合（11 个）──
    const val LIBRARY_SEARCH = "library_search"
    const val LIBRARY_SIMILAR = "library_similar"
    const val LIBRARY_STATS = "library_stats"
    const val LIBRARY_RECENT_HISTORY = "library_recent_history"

    // ── 用户认识模块（画像）──
    /** 只读：伙伴对用户的印象（子 Agent 用，如电台选种子） */
    const val PROFILE_READ = "profile_read"
    /** 写入（CONFIRM）：记下用户显式陈述的偏好 */
    const val PROFILE_NOTE = "profile_note"
    /** 写入（CONFIRM）：否决一类印象 */
    const val PROFILE_FORGET = "profile_forget"
    const val LIBRARY_ARTISTS = "library_artists"
    const val LIBRARY_ALBUMS = "library_albums"
    const val LIBRARY_TAGS = "library_tags"
    const val LIBRARY_SONGS_BY_ARTIST = "library_songs_by_artist"
    const val LIBRARY_SONGS_BY_ALBUM = "library_songs_by_album"
    const val LIBRARY_SONGS_BY_TAG = "library_songs_by_tag"
    const val AGENT_BUDGET = "agent_budget"

    // ── Song 标签（3 个）──
    const val SONG_TAGS_GET = "song_tags_get"
    const val SONG_TAG_USER_ADD = "song_tag_user_add"
    const val SONG_TAG_USER_REMOVE = "song_tag_user_remove"

    // ── F9-A0：Capability 统一状态查询 ──
    const val CAPABILITY_STATUS = "capability_status"

    /**
     * Registry 默认注册的全部工具名清单（共 29 个）。
     * Playback(4) + Playlist CRUD(5) + Playlist 曲目(3) + Library(11) + Song 标签(3) + Profile(3) + Capability 状态查询(1)。
     *
     * DJ 工具（电台专属）已在 F9-A0 删除——电台控制权完全收归 RadioSubAgent runLoop，
     * 外部只通过 Capability 接口控制生命周期，LLM 不直接操作电台内部队列。
     */
    val ALL: List<String> = listOf(
        PLAYBACK_STATE, PLAYBACK_CONTROL, PLAYBACK_PLAY_AT,
        PLAYLIST_LIST, PLAYLIST_DETAIL, PLAYLIST_CREATE, PLAYLIST_RENAME, PLAYLIST_DELETE,
        PLAYLIST_ADD_SONG, PLAYLIST_REMOVE_SONG, PLAYLIST_REORDER,
        LIBRARY_SEARCH, LIBRARY_SIMILAR, LIBRARY_STATS, LIBRARY_RECENT_HISTORY,
        PROFILE_READ, PROFILE_NOTE, PROFILE_FORGET,
        SONG_TAGS_GET,
        PLAYBACK_ENQUEUE,
        LIBRARY_TAGS, LIBRARY_SONGS_BY_TAG, LIBRARY_SONGS_BY_ARTIST, LIBRARY_SONGS_BY_ALBUM,
        LIBRARY_ARTISTS, LIBRARY_ALBUMS,
        AGENT_BUDGET,
        SONG_TAG_USER_ADD, SONG_TAG_USER_REMOVE,
        CAPABILITY_STATUS,
    )

    /** 批次 B 新增工具名（需底层补完后才能注册）。 */
    val ALL_BATCH_B: List<String> = listOf(
        PLAYBACK_ENQUEUE,
        LIBRARY_ARTISTS, LIBRARY_ALBUMS, LIBRARY_TAGS,
        LIBRARY_SONGS_BY_ARTIST, LIBRARY_SONGS_BY_ALBUM, LIBRARY_SONGS_BY_TAG,
        AGENT_BUDGET,
        SONG_TAG_USER_ADD, SONG_TAG_USER_REMOVE,
    )
}

/** 将毫秒游标格式化为 mm:ss（纯算术，供 LLM 回填上下文的人类可读时长）。 */
internal fun formatPositionMs(positionMs: Long): String {
    val totalSec = (positionMs.coerceAtLeast(0L)) / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/** limit 默认 10，越界拒绝（schema 已约束 min/max）。 */
internal const val DEFAULT_RESULT_LIMIT = 10
