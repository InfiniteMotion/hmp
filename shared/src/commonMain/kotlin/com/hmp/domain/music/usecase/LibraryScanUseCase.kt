package com.hmp.domain.music.usecase

import com.hmp.domain.agent.port.LibraryMutatedNotifier
import com.hmp.domain.setting.usecase.UserSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 设备扫描的唯一入口（7.5 VM 拆分）。
 *
 * 「扫一遍 → 标记已加载 → 通知画像重建」这三步原先散在 `LibraryViewModel` 里；
 * 拆分后引导页与曲库设置页各需要一个触发点，收成 UseCase 是为了让
 * **增量 / 全量**的选择只有一处（D2-03：改扫描目录只能走增量，绝不会掉进破坏性全量）。
 *
 * 曲库列表本身不在这里：写入之后由 Room 的可观察查询把新数据推回各屏，
 * 调用方不需要、也不应该再手动重查一次。
 */
class LibraryScanUseCase(
    private val loadMusicFromDeviceUseCase: LoadMusicFromDeviceUseCase,
    private val syncMusicFromDeviceIncrementalUseCase: SyncMusicFromDeviceIncrementalUseCase,
    private val userSettingsUseCase: UserSettingsUseCase,
    private val libraryMutated: LibraryMutatedNotifier,
) {

    /** 扫描进行中（三端仓库共用一个状态位，引导页与设置页都订阅它）。 */
    val isScanning: Flow<Boolean> = loadMusicFromDeviceUseCase.isScanning()

    /** 增量扫描：引导页首次扫描、设置页改目录、手动「增量」都走这条。 */
    suspend fun incremental(): Result<Unit> = run(syncMusicFromDeviceIncrementalUseCase::invoke)

    /** 全量重建：只有用户在设置页显式确认的「全量重扫」走这条。 */
    suspend fun full(): Result<Unit> = run(loadMusicFromDeviceUseCase::invoke)

    // 扫描 + 落库不能跑在 Main 上（调用方是 viewModelScope）：与拆分前的 `launch(Dispatchers.Default)` 同口径。
    private suspend fun run(scan: suspend () -> Result<Unit>): Result<Unit> = withContext(Dispatchers.Default) {
        val result = scan()
        if (result.isSuccess) {
            // 首次扫描完成后持久化标记，用于触发 AI 自动补全等一次性逻辑
            userSettingsUseCase.saveIsLoadMusic(true)
            // 库变了就重建画像（事件驱动，见 agent-profile.md §4.1.4）
            libraryMutated.onLibraryMutated()
        }
        result
    }
}
