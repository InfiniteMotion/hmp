package com.hmp.domain.music

/**
 * 三端共用的"这条音频算不算一首歌"口径（D2-09）。
 *
 * 修前 Desktop 与 iOS 各写一份扩展名集合（Desktop 9 项、iOS 7 项，iOS 少 `opus` / `wma`），
 * Android 则根本没有白名单 —— 同一个音乐文件夹在三台设备上扫出**不同数量**的歌，用户无从得知原因。
 * 现在三端都读这一份：Desktop / iOS 在文件遍历时判，Android 在 MediaStore 查询之后后置过滤
 * （用户 2026-10-12 定的口径：**Android 也按白名单收窄**）。
 *
 * 三条口径写清楚，别当"已经全端一致"收工：
 * - **Android 仍以 `MediaStore.is_music` 为第一道**，白名单是第二道。收窄意味着已发布平台上
 *   现在看得见的、不在名单里的 `is_music=1` 条目（如 `.aiff` / `.m4b`）会从曲库消失 ——
 *   这是用户明确选择的方向（换三端一致），不是实现疏漏。
 * - `ape` / `dsf`（DSD）/ `wv` **没放进来**：Desktop 解码走 FFmpeg 子进程，放宽必须以"随包那份
 *   FFmpeg 真能解"为前件（见 `AGENTS.md` §五），Android / iOS 的系统解码器也不认。
 * - 名单里的 `wma` 对 iOS 是**新增**项，而 AVPlayer 解不了 WMA —— iOS 会"收进库但放不出来"。
 *   用户 2026-10-12 明确接受这一代价（保持名单只有一份，不给某端单减），所以
 *   **"能扫进来"与"能播"在本项目里不是同一个集合**；将来要分开得另开一层申报，别偷偷改这份名单。
 *
 * 扩展名按小写比较。
 */
object AudioFormats {

    val supportedExtensions: Set<String> = setOf(
        "mp3", "flac", "m4a", "wav", "aac", "ogg", "opus", "wma", "alac",
    )

    /**
     * 时长下限（D2-09 的尾巴，用户 2026-10-12 定：三端统一 5 秒，只挡碎片）。
     *
     * 修前三端各按各的：Android 的 MediaStore 查询带 `DURATION > 60000`（**一分钟**！于是 40 秒的
     * 前奏、音效、采样片段在 Android 上根本看不见），Desktop / iOS 毫无下限（0 秒碎片也收）。
     * 在"同一份音乐在三台机器上不一样"这件事里，**它比格式白名单影响更大**。
     */
    const val MIN_TRACK_DURATION_MS = 5_000L

    /**
     * 时长下限谓词。**读不到时长就不因它丢条目**（`durationMs == null` 一律算合格）——
     * Desktop / iOS 对解析不出元数据的文件（标签器不认、文件损坏）本来就拿不到时长，
     * 那种情况交给白名单与后续播放去判，不在这里静默少歌：下限的目的是挡"几秒的碎片"，
     * 不是给"读不出来"判死刑。Android 的 `DURATION` 列总有值，所以那端实际就是 `>= 5000`。
     */
    fun isPlausibleTrack(durationMs: Long?): Boolean =
        durationMs == null || durationMs >= MIN_TRACK_DURATION_MS

    /** 取路径 / 文件名的扩展名（不含点）；没有扩展名返回空串。 */
    fun extensionOf(path: String): String = path.substringAfterLast('.', "")

    fun isSupported(path: String): Boolean = extensionOf(path).lowercase() in supportedExtensions

    /** 白名单 + 时长下限的合取：三端在各自正确的时机问同一个问题。 */
    fun isIncludedInLibrary(path: String, durationMs: Long?): Boolean =
        isSupported(path) && isPlausibleTrack(durationMs)
}
