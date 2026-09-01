import Foundation
import sharedIos
import UIKit

/// 播放编排器单例 — 等价于 Android MusicController
/// 管理：播放状态、队列、播放模式、进度、历史记录、定时器
@Observable
class MusicPlayerController {
    static let shared = MusicPlayerController()

    // MARK: - Published State

    var isPlaying: Bool = false
    var currentPlaylist: [MusicInfo] = []
    var currentIndex: Int = -1
    var currentPlayingMusic: MusicInfo? = nil
    var currentPosition: Int64 = 0
    var duration: Int64 = 0
    var playbackMode: PlaybackMode = .sequential
    var likeStatus: Bool = false
    var currentMusicLyrics: String? = nil
    var isMiniPlayerVisible: Bool = false
    var timerRemaining: Int64? = nil
    
    /// 初始化是否完成（用于 UI 等待初始化完成）
    var isInitializationComplete: Bool = false

    // MARK: - Private

    let engine: PlayerEngine
    private let currentPlaybackUseCase: CurrentPlaybackUseCase
    private let playbackHistoryUseCase: PlaybackHistoryUseCase
    private let timerUseCase: TimerUseCase
    private let managePlaylistUseCase: ManagePlaylistUseCase
    private let settingsRepository: SettingsRepository

    private var currentPlaybackHistoryId: Int64? = nil
    private var playbackStartTime: Date? = nil
    private var listeningDurationAccumulator: Int64 = 0
    private var listeningDurationTimer: Timer? = nil
    private var sleepTimer: Timer? = nil
    
    /// 恢复位置的缓存（在引擎准备好后使用）
    private var pendingSeekPosition: Int64 = 0
    /// 是否正在初始化播放器（用于防止重复初始化）
    private var isInitializing: Bool = false

    private init() {
        self.engine = PlayerEngine()
        self.currentPlaybackUseCase = KoinHelperKt.getCurrentPlaybackUseCase()
        self.playbackHistoryUseCase = KoinHelperKt.getPlaybackHistoryUseCase()
        self.timerUseCase = KoinHelperKt.getTimerUseCase()
        self.managePlaylistUseCase = KoinHelperKt.getManagePlaylistUseCase()
        self.settingsRepository = KoinHelperKt.getSettingsRepository()

        setupEngineCallbacks()
        setupAppLifecycleObservers()
        restoreSavedState()
    }

    // MARK: - App Lifecycle

    private func setupAppLifecycleObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAppDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAppWillTerminate),
            name: UIApplication.willTerminateNotification,
            object: nil
        )
    }

    @objc private func handleAppDidEnterBackground() {
        PlatformLogKt.platformLog(severity: 0, tag: "MusicPlayerController", message: "App did enter background, saving playback state")
        persistPlaybackState()
        saveCurrentPosition()
    }

    @objc private func handleAppWillTerminate() {
        PlatformLogKt.platformLog(severity: 0, tag: "MusicPlayerController", message: "App will terminate, saving playback state")
        persistPlaybackState()
        saveCurrentPosition()
        HMPMediaSession.shared.onPlaybackStopped()
    }

    // MARK: - Engine Callbacks

    private func setupEngineCallbacks() {
        engine.onPlaybackEnded = { [weak self] in
            self?.handlePlaybackEnded()
        }
        engine.onPositionUpdated = { [weak self] pos, dur in
            self?.currentPosition = pos
            self?.duration = dur
            HMPMediaSession.shared.onPositionUpdated(position: pos, duration: dur)
            if pos % 10000 < 500 {
                self?.saveCurrentPosition()
            }
        }
        engine.onPlayStateChanged = { [weak self] playing in
            self?.isPlaying = playing
            HMPMediaSession.shared.onPlaybackStateChanged(isPlaying: playing)
            self?.saveCurrentPosition()
        }
        engine.onError = { [weak self] msg in
            PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "error: \(msg)")
        }
        engine.onReady = { [weak self] in
            self?.handleEngineReady()
        }
    }

    /// 引擎准备好后的回调 - 用于恢复播放位置
    private func handleEngineReady() {
        if pendingSeekPosition > 0 {
            engine.seekToMs(pendingSeekPosition)
            PlatformLogKt.platformLog(severity: 1, tag: "MusicPlayerController", message: "Engine ready, seeking to: \(pendingSeekPosition)ms")
            pendingSeekPosition = 0
        }
    }

    // MARK: - Playback Controls

    func playWith(_ musicInfo: MusicInfo) {
        currentPlaylist = [musicInfo]
        currentIndex = 0
        startPlaying(musicInfo)
    }

    func addAllToPlaylistInOrder(_ list: [MusicInfo]) {
        currentPlaylist = list
        if !list.isEmpty {
            currentIndex = 0
            startPlaying(list[0])
        }
        Task {
            await persistCurrentPlaylistToDatabaseWithCurrentId()
        }
    }

    func addAllToPlaylistByShuffle(_ list: [MusicInfo]) {
        currentPlaylist = list.shuffled()
        if !currentPlaylist.isEmpty {
            currentIndex = 0
            startPlaying(currentPlaylist[0])
        }
        Task {
            await persistCurrentPlaylistToDatabaseWithCurrentId()
        }
    }

    func playOrResume() {
        if engine.isPlaying {
            return
        }
        if currentPlayingMusic != nil {
            engine.resume()
        }
    }

    func pauseMusic() {
        engine.pause()
        pauseListeningDurationTracking()
    }

    func seekTo(position: Int64) {
        if engine.isReady {
            engine.seekToMs(position)
            currentPosition = position
        } else {
            pendingSeekPosition = position
        }
    }

    func playNext() {
        guard !currentPlaylist.isEmpty else { return }

        let nextIndex: Int
        switch playbackMode {
        case .repeatOne:
            nextIndex = currentIndex
        case .shuffle:
            nextIndex = Int.random(in: 0..<currentPlaylist.count)
        default:
            nextIndex = currentIndex + 1
        }

        guard nextIndex < currentPlaylist.count else { return }
        currentIndex = nextIndex
        startPlaying(currentPlaylist[nextIndex])
    }

    func playPrevious() {
        guard !currentPlaylist.isEmpty else { return }

        if currentPosition > 3000 {
            seekTo(position: 0)
            return
        }

        let prevIndex = max(0, currentIndex - 1)
        currentIndex = prevIndex
        startPlaying(currentPlaylist[prevIndex])
    }

    func togglePlaybackModeByOrder() {
        switch playbackMode {
        case .sequential:
            playbackMode = .repeatOne
        case .repeatOne:
            playbackMode = .shuffle
        case .shuffle:
            playbackMode = .sequential
        default:
            playbackMode = .sequential
        }
    }

    func addToNextPlay(_ musicInfo: MusicInfo) {
        let insertIndex = currentIndex + 1
        if insertIndex >= currentPlaylist.count {
            currentPlaylist.append(musicInfo)
        } else {
            currentPlaylist.insert(musicInfo, at: insertIndex)
        }
    }

    /// 按曲目播放（PlaybackController.playAt(music) 桥接）
    func playAt(_ musicInfo: MusicInfo) {
        guard let idx = currentPlaylist.firstIndex(where: { $0.music.id == musicInfo.music.id }) else { return }
        playAt(idx)
    }

    /// 追加到队列尾部（不打断当前播放；PlaybackController.addToPlaylist 桥接）
    func addToPlaylist(_ musicInfo: MusicInfo) {
        currentPlaylist.append(musicInfo)
        Task {
            await persistCurrentPlaylistToDatabaseWithCurrentId()
        }
    }

    /// 从队列移除（PlaybackController.removeFromPlaylist 桥接）
    func removeFromPlaylist(_ musicInfo: MusicInfo) {
        guard let idx = currentPlaylist.firstIndex(where: { $0.music.id == musicInfo.music.id }) else { return }
        currentPlaylist.remove(at: idx)
        if currentPlayingMusic?.music.id == musicInfo.music.id {
            // 移除的是当前曲目：切换到同位置（或前一曲）继续，队列空则清空
            if currentPlaylist.isEmpty {
                clearPlaylist()
            } else {
                currentIndex = min(idx, currentPlaylist.count - 1)
                startPlaying(currentPlaylist[currentIndex])
            }
        } else if currentIndex > idx {
            currentIndex -= 1
        }
        Task {
            await persistCurrentPlaylistToDatabaseWithCurrentId()
        }
    }

    /// 置顶队列（PlaybackController.moveToTop 桥接）
    func moveToTop(_ musicInfo: MusicInfo) {
        guard let idx = currentPlaylist.firstIndex(where: { $0.music.id == musicInfo.music.id }) else { return }
        let item = currentPlaylist.remove(at: idx)
        currentPlaylist.insert(item, at: 0)
        if currentIndex == idx {
            currentIndex = 0
        } else if currentIndex < idx {
            currentIndex += 1
        }
        Task {
            await persistCurrentPlaylistToDatabaseWithCurrentId()
        }
    }

    /// 心动模式：以当前曲目为种子，其余随机洗牌生成新队列（标签权重算法在桌面端 shared 层，此处近似）
    func playHeartMode() {
        guard !currentPlaylist.isEmpty else { return }
        var list = currentPlaylist
        let seed = list.removeFirst()
        list.shuffle()
        currentPlaylist = [seed] + list
        currentIndex = 0
        startPlaying(seed)
        Task {
            await persistCurrentPlaylistToDatabaseWithCurrentId()
        }
    }

    func playAt(_ index: Int) {
        guard index >= 0 && index < currentPlaylist.count else { return }
        currentIndex = index
        startPlaying(currentPlaylist[index])
    }

    func clearPlaylist() {
        currentPlaylist = []
        currentIndex = -1
        currentPlayingMusic = nil
        isMiniPlayerVisible = false
        engine.stop()
        HMPMediaSession.shared.onPlaybackStopped()
        Task {
            await persistCurrentPlaylistToDatabaseWithCurrentId()
        }
    }

    // MARK: - Private Helpers

    private func startPlaying(_ musicInfo: MusicInfo) {
        if currentPlaybackHistoryId != nil {
            endCurrentPlaybackSession(isCompleted: false)
        }

        currentPlayingMusic = musicInfo
        isMiniPlayerVisible = true

        let path = musicInfo.music.path
        let fileExists = FileManager.default.fileExists(atPath: path)
        if !fileExists {
            let filename = (path as NSString).lastPathComponent
            let docsDir = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first ?? ""
            let newPath = (docsDir as NSString).appendingPathComponent(filename)
            if FileManager.default.fileExists(atPath: newPath) {
                let url = URL(fileURLWithPath: newPath)
                engine.play(url: url)
                HMPMediaSession.shared.onTrackChanged(musicInfo: musicInfo)
                return
            }
        }
        let url = URL(fileURLWithPath: path)
        engine.play(url: url)

        loadMetadata(for: musicInfo)
        startNewPlaybackSession(musicInfo: musicInfo)
        persistPlaybackState()

        HMPMediaSession.shared.onTrackChanged(musicInfo: musicInfo)
    }

    private func loadMetadata(for musicInfo: MusicInfo) {
        let musicId = musicInfo.music.id

        Task {
            do {
                let liked = try await currentPlaybackUseCase.getLikedStatus(musicId: musicId)
                await MainActor.run { self.likeStatus = liked.boolValue }
            } catch {
                PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "getLikedStatus failed: \(error)")
            }
        }

        Task {
            do {
                let lyrics = try await currentPlaybackUseCase.getMusicLyrics(musicId: musicId)
                await MainActor.run { self.currentMusicLyrics = lyrics }
            } catch {
                PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "getMusicLyrics failed: \(error)")
            }
        }
    }

    private func handlePlaybackEnded() {
        endCurrentPlaybackSession(isCompleted: true)

        switch playbackMode {
        case .repeatOne:
            seekTo(position: 0)
            engine.resume()
            startNewPlaybackSession(musicInfo: currentPlayingMusic!)
        case .shuffle:
            playNext()
        default:
            if currentIndex + 1 < currentPlaylist.count {
                playNext()
            }
        }
    }

    // MARK: - Playback Session Tracking

    private func startNewPlaybackSession(musicInfo: MusicInfo) {
        let musicId = musicInfo.music.id
        playbackStartTime = Date()
        listeningDurationAccumulator = 0

        Task {
            do {
                let historyId = try await playbackHistoryUseCase.startPlaybackSession(musicId: musicId, source: nil)
                await MainActor.run { self.currentPlaybackHistoryId = historyId.int64Value }
            } catch {
                PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "startPlaybackSession failed: \(error)")
            }
        }

        startListeningDurationTracking()
    }

    private func endCurrentPlaybackSession(isCompleted: Bool) {
        guard let historyId = currentPlaybackHistoryId,
              let musicInfo = currentPlayingMusic else { return }

        let duration = listeningDurationAccumulator
        stopListeningDurationTracking()

        Task {
            do {
                if isCompleted {
                    try await playbackHistoryUseCase.completePlaybackSession(
                        historyId: historyId,
                        musicId: musicInfo.music.id,
                        duration: duration
                    )
                } else {
                    try await playbackHistoryUseCase.skipPlaybackSession(
                        historyId: historyId,
                        musicId: musicInfo.music.id,
                        duration: duration,
                        isSkip: true
                    )
                }
            } catch {
                PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "endPlaybackSession failed: \(error)")
            }
        }

        currentPlaybackHistoryId = nil
    }

    private func startListeningDurationTracking() {
        stopListeningDurationTracking()
        listeningDurationTimer = Timer.scheduledTimer(withTimeInterval: 30.0, repeats: true) { [weak self] _ in
            self?.recordListeningDurationTick()
        }
    }

    private func stopListeningDurationTracking() {
        listeningDurationTimer?.invalidate()
        listeningDurationTimer = nil
    }

    private func pauseListeningDurationTracking() {
        if let start = playbackStartTime {
            listeningDurationAccumulator += Int64(Date().timeIntervalSince(start) * 1000)
            playbackStartTime = nil
        }
    }

    private func recordListeningDurationTick() {
        if let start = playbackStartTime {
            listeningDurationAccumulator += Int64(Date().timeIntervalSince(start) * 1000)
            playbackStartTime = Date()
        }

        let duration = listeningDurationAccumulator
        listeningDurationAccumulator = 0

        Task {
            do {
                try await playbackHistoryUseCase.recordListeningDuration(duration: duration)
            } catch {
                PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "recordListeningDuration failed: \(error)")
            }
        }
    }

    // MARK: - Sleep Timer

    func startTimer(minutes: Int) {
        cancelTimer()
        let ms = Int64(minutes) * 60 * 1000
        timerUseCase.setTimerRemaining(milliseconds: KotlinLong(longLong: ms))

        sleepTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.tickSleepTimer()
        }
    }

    func cancelTimer() {
        sleepTimer?.invalidate()
        sleepTimer = nil
        timerUseCase.cancelTimer()
        timerRemaining = nil
    }

    private func tickSleepTimer() {
        timerUseCase.decrementTimer(decrement: 1000)
        if !timerUseCase.isTimerActive() {
            pauseMusic()
            cancelTimer()
        }
        timerRemaining = (timerUseCase.timerRemaining.value as? KotlinLong)?.int64Value
    }

    // MARK: - Like

    func updateLikedStatus(_ liked: Bool) {
        guard let musicInfo = currentPlayingMusic else { return }
        likeStatus = liked

        Task {
            do {
                try await currentPlaybackUseCase.updateLikedStatus(musicId: musicInfo.music.id, liked: liked)
            } catch {
                PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "updateLikedStatus failed: \(error)")
            }
        }
    }

    // MARK: - Persist / Restore State

    private func persistPlaybackState() {
        guard let musicInfo = currentPlayingMusic else { return }
        Task {
            do {
                try await settingsRepository.saveCurrentMusicId(id: musicInfo.music.id)
                try await settingsRepository.saveCurrentPosition(position: currentPosition)
                if let playlistId = try await settingsRepository.getCurrentPlaylistId() {
                    try await persistCurrentPlaylistToDatabase(playlistId: playlistId.int64Value)
                }
            } catch {
                PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "persistPlaybackState failed: \(error)")
            }
        }
    }

    private func persistCurrentPlaylistToDatabase(playlistId: Int64) async {
        do {
            try await managePlaylistUseCase.resetPlaylistItems(playlistId: playlistId, playlist: currentPlaylist)
        } catch {
            PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "persistCurrentPlaylistToDatabase failed: \(error)")
        }
    }

    private func persistCurrentPlaylistToDatabaseWithCurrentId() async {
        do {
            let playlistId = try await settingsRepository.getCurrentPlaylistId()
            guard let pid = playlistId else { return }
            try await persistCurrentPlaylistToDatabase(playlistId: pid.int64Value)
        } catch {
            PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "persistCurrentPlaylistToDatabaseWithCurrentId failed: \(error)")
        }
    }

    func initializeDefaultPlaylists() async {
        if isInitializing {
            PlatformLogKt.platformLog(severity: 0, tag: "MusicPlayerController", message: "Already initializing, skipping")
            return
        }
        isInitializing = true
        
        do {
            // 检查并创建默认播放列表
            if try await settingsRepository.getCurrentPlaylistId() == nil {
                try? await managePlaylistUseCase.removePlaylist(name: "默认播放列表")
                let defaultId = try await managePlaylistUseCase.createPlaylist(name: "默认播放列表")
                try await settingsRepository.saveCurrentPlaylistId(playlistId: defaultId.int64Value)
                PlatformLogKt.platformLog(severity: 1, tag: "MusicPlayerController", message: "Created default playlist with id: \(defaultId)")
            }

            // 检查并创建红心播放列表
            if try await settingsRepository.getLikedPlaylistId() == nil {
                try? await managePlaylistUseCase.removePlaylist(name: "红心")
                let likedId = try await managePlaylistUseCase.createPlaylist(name: "红心")
                try await settingsRepository.saveLikedPlaylistId(playlistId: likedId.int64Value)
                PlatformLogKt.platformLog(severity: 1, tag: "MusicPlayerController", message: "Created liked playlist with id: \(likedId)")
            }

            // 检查并创建最近播放列表
            if try await settingsRepository.getRecentPlaylistId() == nil {
                try? await managePlaylistUseCase.removePlaylist(name: "最近播放")
                let recentId = try await managePlaylistUseCase.createPlaylist(name: "最近播放")
                try await settingsRepository.saveRecentPlaylistId(playlistId: recentId.int64Value)
                PlatformLogKt.platformLog(severity: 1, tag: "MusicPlayerController", message: "Created recent playlist with id: \(recentId)")
            }

            // 初始化完成后，恢复播放状态
            await loadPlaylistFromSettings()
            await restoreLastPosition()
            
            await MainActor.run {
                self.isInitializationComplete = true
                PlatformLogKt.platformLog(severity: 1, tag: "MusicPlayerController", message: "Initialization complete")
            }
        } catch {
            PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "Failed to initialize playlists: \(error)")
            await MainActor.run {
                self.isInitializationComplete = true
            }
        }
        
        isInitializing = false
    }

    private func restoreSavedState() {
        // 等待 initializeDefaultPlaylists 完成后再恢复
    }

    private func loadPlaylistFromSettings() async {
        do {
            guard let playlistId = try await settingsRepository.getCurrentPlaylistId() else {
                PlatformLogKt.platformLog(severity: 0, tag: "MusicPlayerController", message: "loadPlaylistFromSettings: no currentPlaylistId")
                return
            }
            let currentMusicId = try await KoinHelperKt.getCurrentMusicId()
            let list = try await managePlaylistUseCase.getPlaylistById(playlistId: playlistId.int64Value)

            await MainActor.run {
                self.currentPlaylist = list
                self.playbackMode = .sequential
                
                if let musicId = currentMusicId {
                    // 查找当前歌曲在列表中的位置
                    if let idx = list.firstIndex(where: { $0.music.id == musicId.int64Value }) {
                        self.currentIndex = idx
                        self.currentPlayingMusic = list[idx]
                        self.isMiniPlayerVisible = true
                        PlatformLogKt.platformLog(severity: 1, tag: "MusicPlayerController", message: "Restored playback state: musicId=\(musicId), index=\(idx)")
                    } else {
                        // 如果保存的歌曲不在当前列表中，重置状态
                        self.currentIndex = 0
                        self.currentPlayingMusic = list.first
                        self.isMiniPlayerVisible = !list.isEmpty
                        PlatformLogKt.platformLog(severity: 0, tag: "MusicPlayerController", message: "Saved music not found in playlist, resetting to first item")
                    }
                } else {
                    self.currentIndex = 0
                    self.currentPlayingMusic = list.first
                    self.isMiniPlayerVisible = !list.isEmpty
                }
            }
        } catch {
            PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "loadPlaylistFromSettings failed: \(error)")
        }
    }

    private func restoreLastPosition() async {
        do {
            let lastPos = try await KoinHelperKt.getSettingsCurrentPosition()
            let lastPosValue = lastPos.int64Value
            await MainActor.run {
                self.currentPosition = lastPosValue
                PlatformLogKt.platformLog(severity: 1, tag: "MusicPlayerController", message: "Restored position: \(lastPosValue)ms")
                
                // 如果有当前歌曲且位置大于0，设置待恢复位置
                if self.currentPlayingMusic != nil && lastPosValue > 0 {
                    self.pendingSeekPosition = lastPosValue
                    PlatformLogKt.platformLog(severity: 0, tag: "MusicPlayerController", message: "Pending seek position: \(lastPosValue)ms")
                }
            }
        } catch {
            PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "restoreLastPosition failed: \(error)")
        }
    }

    func saveCurrentPosition() {
        Task {
            do {
                try await settingsRepository.saveCurrentPosition(position: currentPosition)
            } catch {
                PlatformLogKt.platformLog(severity: 3, tag: "MusicPlayerController", message: "saveCurrentPosition failed: \(error)")
            }
        }
    }
}