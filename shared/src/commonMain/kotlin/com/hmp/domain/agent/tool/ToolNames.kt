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

    // ── DJ（电台专属，8 个 · "dj_" 前缀）──
    // RadioSubAgent 的 ReActLoop 专用：只读感知 + 队列替换。
    // 前缀 "dj_" 只出现在 ToolRegistryView.radio() 白名单里，
    // Master 视图不含 "dj_"——因此这些工具虽注册进同一 ToolRegistry，Master 的 LLM 看不到。
    const val DJ_CURRENT_SONG = "dj_current_song"
    const val DJ_LIBRARY_STATS = "dj_library_stats"
    const val DJ_SEARCH_BY_TAGS = "dj_search_by_tags"
    const val DJ_GET_TOP_ARTISTS = "dj_get_top_artists"
    const val DJ_GET_TOP_LABELS = "dj_get_top_labels"
    const val DJ_RECENTLY_SKIPPED = "dj_recently_skipped"
    const val DJ_QUEUE_PEEK = "dj_queue_peek"
    const val DJ_QUEUE_REPLACE_NEXT = "dj_queue_replace_next"

    /**
     * Registry 默认注册的全部工具名清单（26 + DJ 8 = 34 个）。
     * Playback(4) + Playlist CRUD(5) + Playlist 曲目(3) + Library(11) + Song 标签(3) + DJ(8)。
     * DJ 组只被 ToolRegistryView.radio() 白名单命中，Master 视图看不到（前缀不含 "dj_"）。
     *
     * EnrichSubAgent 富化管道已内化到自循环（runLoop 直接调 repository.fetchMusicExtraInfoWithProvider），
     * song_enrich_llm 工具删除。SubAgent 生命周期管理（enrich 系列 / radio 系列）已重构为内建意图路由，
     * 不再暴露为 LLM 工具。
     */
    val ALL: List<String> = listOf(
        PLAYBACK_STATE, PLAYBACK_CONTROL, PLAYBACK_PLAY_AT,
        PLAYLIST_LIST, PLAYLIST_DETAIL, PLAYLIST_CREATE, PLAYLIST_RENAME, PLAYLIST_DELETE,
        PLAYLIST_ADD_SONG, PLAYLIST_REMOVE_SONG, PLAYLIST_REORDER,
        LIBRARY_SEARCH, LIBRARY_SIMILAR, LIBRARY_STATS, LIBRARY_RECENT_HISTORY,
        // 用户认识模块（画像）
        PROFILE_READ, PROFILE_NOTE, PROFILE_FORGET,
        SONG_TAGS_GET,
        // Batch B
        PLAYBACK_ENQUEUE,
        LIBRARY_TAGS, LIBRARY_SONGS_BY_TAG, LIBRARY_SONGS_BY_ARTIST, LIBRARY_SONGS_BY_ALBUM,
        LIBRARY_ARTISTS, LIBRARY_ALBUMS,
        AGENT_BUDGET,
        SONG_TAG_USER_ADD, SONG_TAG_USER_REMOVE,
        // DJ（电台专属——Master 视图按前缀过滤，看不到）
        DJ_CURRENT_SONG, DJ_LIBRARY_STATS, DJ_SEARCH_BY_TAGS,
        DJ_GET_TOP_ARTISTS, DJ_GET_TOP_LABELS, DJ_RECENTLY_SKIPPED,
        DJ_QUEUE_PEEK, DJ_QUEUE_REPLACE_NEXT,
    )

    /** DJ 域工具名（RadioSubAgent 专属，8 个）。 */
    val DJ_ALL: List<String> = listOf(
        DJ_CURRENT_SONG, DJ_LIBRARY_STATS, DJ_SEARCH_BY_TAGS,
        DJ_GET_TOP_ARTISTS, DJ_GET_TOP_LABELS, DJ_RECENTLY_SKIPPED,
        DJ_QUEUE_PEEK, DJ_QUEUE_REPLACE_NEXT,
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
