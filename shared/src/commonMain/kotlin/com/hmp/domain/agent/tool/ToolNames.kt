package com.hmp.domain.agent.tool

/** M3-T2 工具名常量（集中一处，M4 引擎按名路由、Registry 拼装均引用此处）。 */
object ToolNames {
    const val SEARCH_LIBRARY = "searchLibrary"
    const val GET_LISTEN_STATS = "getListenStats"
    const val GET_RECENT_HISTORY = "getRecentHistory"
    const val GET_NOW_PLAYING_CONTEXT = "getNowPlayingContext"
    const val GET_SIMILAR_SONGS = "getSimilarSongs"
    const val GET_MUSIC_EXTRA = "getMusicExtra"
    const val ENRICH_SONG = "enrichSong"
    const val CREATE_PLAYLIST = "createPlaylist"
    const val ADD_TO_PLAYLIST = "addToPlaylist"
    const val REORDER_PLAYLIST = "reorderPlaylist"
    const val CONTROL_PLAYBACK = "controlPlayback"

    /** Registry 注册的全部工具名（M3-T2 十项）。 */
    val ALL: List<String> = listOf(
        SEARCH_LIBRARY, GET_LISTEN_STATS, GET_RECENT_HISTORY,
        GET_NOW_PLAYING_CONTEXT, GET_SIMILAR_SONGS, GET_MUSIC_EXTRA,
        ENRICH_SONG, CREATE_PLAYLIST, ADD_TO_PLAYLIST, REORDER_PLAYLIST,
        CONTROL_PLAYBACK,
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