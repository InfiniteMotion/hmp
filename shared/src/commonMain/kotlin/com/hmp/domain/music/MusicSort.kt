package com.hmp.domain.music

/**
 * 曲库列表的排序键（D2-08）。
 *
 * 取代原先的裸 `orderBy: String`。那条签名在三端各自的 `when (orderBy) { … else -> 按 id }` 里
 * 兜底，于是**拼错键不报错、只是换个排法**，而且已经发生了三处：
 * - `RadioSubAgent` 三处传 `"play_count"`（下划线），三端全都落进 `else` —— 拿到的是按 id 排；
 * - 列表页排序菜单里的 `"fileSize"` 与 `"date"` 三端都不认 —— 用户点"按文件大小排序"实际按 id 排；
 * - 反过来仓库认的 `"album"` 菜单里又没有。
 *
 * 枚举把这一整类问题挪到编译期：`when` 漏一个取值就编不过，也不再有"兜底"这一档。
 *
 * 排序偏好仍有一处存在 DataStore 里是字符串（`SettingsRepository.galleryOrderBy` /
 * `galleryOrderType`，iOS 经 `KoinHelper` 写）。**那一处目前没有任何读取方** ——
 * 设置项只写不读，所以本包不碰它（要不要接上、存量值怎么映射，是 D7 的设置链问题）。
 */
enum class MusicSortKey {
    TITLE,
    ARTIST,
    ALBUM,
    DURATION,
    FILE_SIZE,
    PLAY_COUNT,

    /** 加入曲库的时间，真源是 `MusicExtra.date`（索引条的年月档读的是同一列）。 */
    DATE,
    ID,
}

/** 排序方向。原先是 `"ASC"` / `"DESC"` 字符串配 `uppercase() == "DESC"` —— 其余任意值都静默当升序。 */
enum class SortDirection {
    ASC,
    DESC;

    val isDescending: Boolean get() = this == DESC
}
