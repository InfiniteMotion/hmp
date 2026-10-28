package com.hmp.data.util

/**
 * 一次扫描产出的曲目条目（落库由 `MusicRepositoryBase.persistScannedLibrary` 一份完成，D2-01 / D2-02）。
 *
 * **这里原先还有一句 `expect object DeviceMusicScanner`，现在没有 expect/actual 了（D2-10）**：
 * 三端真正共享的抽象是 `MusicRepositoryBase.performMusicScan()`（"这台设备上有哪些歌"），
 * 而 Desktop / iOS 的 `object DeviceMusicScanner` 只是**各自平台内部**按文件名遍历目录的助手，
 * Android 压根不需要它 —— 它的扫描是 `MusicRepositoryImpl.android.kt` 里那条 MediaStore 查询。
 *
 * 那道 expect/actual 造成的错觉有代价：androidMain 曾经有一份**零调用**的
 * `DeviceMusicScanner.android.kt`，里面还写着 `val context = android.app.Application()`
 * （新建的 Application 没有 base context，`contentResolver.query` 必失败）。
 * 它唯一的用处是让"三端对称"这句话看起来成立 —— 谁真去接线，谁就拿到一个崩溃。
 * 已删（2026-10-12，一-5 B 批）。
 */
data class ScannedMusicFile(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val albumArtUri: String,
    val bitRate: Int? = null,
    val sampleRate: Int? = null,
    val fileSize: Long? = null,
    val format: String? = null,
    val lyrics: String? = null
)
