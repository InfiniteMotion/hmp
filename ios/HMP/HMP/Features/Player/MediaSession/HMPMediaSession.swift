import MediaPlayer
import ActivityKit
import sharedIos

class HMPMediaSession {
    static let shared = HMPMediaSession()

    private let nowPlayingInfo: NowPlayingInfoManager
    private let remoteCommand: RemoteCommandManager
    let artworkLoader: ArtworkLoader
    private var liveActivityManager: Any?

    private var currentMusicInfo: MusicInfo?
    private var isCurrentlyPlaying: Bool = false
    private var currentPositionMs: Int64 = 0
    private var currentDurationMs: Int64 = 0

    var isLiveActivityEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: "hmp_live_activity_enabled") }
        set { UserDefaults.standard.set(newValue, forKey: "hmp_live_activity_enabled") }
    }

    var showCompactAnimation: Bool {
        get { UserDefaults.standard.object(forKey: "hmp_compact_animation") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "hmp_compact_animation") }
    }

    var showExpandedLyrics: Bool {
        get { UserDefaults.standard.bool(forKey: "hmp_expanded_lyrics") }
        set { UserDefaults.standard.set(newValue, forKey: "hmp_expanded_lyrics") }
    }

    private init() {
        artworkLoader = ArtworkLoader()
        nowPlayingInfo = NowPlayingInfoManager()
        remoteCommand = RemoteCommandManager()

        setupRemoteCommandCallbacks()
        setupLiveActivityIfNeeded()
        setupLiveActivityNotificationObservers()
    }

    // MARK: - Public Interface

    func onTrackChanged(musicInfo: MusicInfo) {
        AudioSessionManager.shared.setupAudioSession()

        currentMusicInfo = musicInfo
        currentPositionMs = 0
        currentDurationMs = musicInfo.music.duration

        let avPlayer = MusicPlayerController.shared.engine.avPlayer
        PlatformLogKt.platformLog(severity: 0, tag: "HMPMediaSession", message: "onTrackChanged: title=\(musicInfo.music.title), avPlayer=\(avPlayer != nil ? "exists" : "nil"), isCurrentlyPlaying=\(isCurrentlyPlaying)")

        nowPlayingInfo.setupNowPlayingSession(with: avPlayer)

        let title = musicInfo.music.title
        let artist = musicInfo.music.artist
        let album = musicInfo.music.album
        let duration = Double(musicInfo.music.duration) / 1000.0

        nowPlayingInfo.updateTrack(
            title: title,
            artist: artist,
            album: album,
            artwork: nil,
            duration: duration
        )

        if isCurrentlyPlaying {
            let position = Double(currentPositionMs) / 1000.0
            nowPlayingInfo.updatePlaybackState(isPlaying: true, position: position)
        }

        artworkLoader.loadArtwork(musicInfo: musicInfo) { [weak self] artwork in
            guard let self, self.currentMusicInfo?.music.id == musicInfo.music.id else { return }
            self.nowPlayingInfo.updateArtwork(artwork)
            if #available(iOS 16.1, *) {
                if let data = artwork?.jpegData(compressionQuality: 0.5) {
                    self.liveActivityManagerAny?.updateArtwork(data)
                }
            }
        }

        remoteCommand.updateAvailableCommands(
            hasNext: true,
            hasPrevious: true
        )

        if #available(iOS 16.1, *) {
            if isLiveActivityEnabled {
                liveActivityManagerAny?.startActivity(
                    musicInfo: musicInfo,
                    isPlaying: isCurrentlyPlaying,
                    showAnimation: showCompactAnimation,
                    showLyrics: showExpandedLyrics
                )
            }
        }
    }

    func onPlaybackStateChanged(isPlaying: Bool) {
        PlatformLogKt.platformLog(severity: 0, tag: "HMPMediaSession", message: "onPlaybackStateChanged: isPlaying=\(isPlaying), hasInfo=\(nowPlayingInfo.hasNowPlayingInfo)")
        isCurrentlyPlaying = isPlaying

        if isPlaying {
            nowPlayingInfo.ensureSession(with: MusicPlayerController.shared.engine.avPlayer)
        }

        guard nowPlayingInfo.hasNowPlayingInfo else {
            PlatformLogKt.platformLog(severity: 0, tag: "HMPMediaSession", message: "Skipping playback state update (no track info yet, will apply in onTrackChanged)")
            return
        }

        let position = Double(currentPositionMs) / 1000.0
        nowPlayingInfo.updatePlaybackState(isPlaying: isPlaying, position: position)

        if #available(iOS 16.1, *) {
            let duration = Double(currentDurationMs) / 1000.0
            let lyricLine = MusicPlayerController.shared.currentMusicLyrics?
                .components(separatedBy: "\n")
                .first(where: { !$0.trimmingCharacters(in: .whitespaces).isEmpty })
            liveActivityManagerAny?.updateActivity(
                isPlaying: isPlaying,
                position: position,
                duration: duration,
                lyricLine: lyricLine
            )
        }
    }

    func onPositionUpdated(position: Int64, duration: Int64) {
        currentPositionMs = position
        currentDurationMs = duration

        guard isCurrentlyPlaying else { return }

        let positionSec = Double(position) / 1000.0
        let durationSec = Double(duration) / 1000.0
        nowPlayingInfo.updatePosition(position: positionSec, duration: durationSec)

        if #available(iOS 16.1, *) {
            let lyricLine = MusicPlayerController.shared.currentMusicLyrics?
                .components(separatedBy: "\n")
                .first(where: { !$0.trimmingCharacters(in: .whitespaces).isEmpty })
            liveActivityManagerAny?.updateActivity(
                isPlaying: isCurrentlyPlaying,
                position: positionSec,
                duration: durationSec,
                lyricLine: lyricLine
            )
        }
    }

    func onPlaybackStopped() {
        currentMusicInfo = nil
        isCurrentlyPlaying = false
        currentPositionMs = 0
        currentDurationMs = 0
        nowPlayingInfo.clear()
        artworkLoader.cancel()
        AudioSessionManager.shared.setActive(false)

        if #available(iOS 16.1, *) {
            liveActivityManagerAny?.endActivity()
        }
    }

    // MARK: - Live Activity

    @available(iOS 16.1, *)
    private var liveActivityManagerAny: LiveActivityManager? {
        return liveActivityManager as? LiveActivityManager
    }

    private func setupLiveActivityIfNeeded() {
        if #available(iOS 16.1, *) {
            liveActivityManager = LiveActivityManager.shared
        }
    }

    private func setupLiveActivityNotificationObservers() {
        NotificationCenter.default.addObserver(
            forName: .hmpPlay,
            object: nil,
            queue: .main
        ) { _ in
            MusicPlayerController.shared.playOrResume()
        }

        NotificationCenter.default.addObserver(
            forName: .hmpPause,
            object: nil,
            queue: .main
        ) { _ in
            MusicPlayerController.shared.pauseMusic()
        }

        NotificationCenter.default.addObserver(
            forName: .hmpNextTrack,
            object: nil,
            queue: .main
        ) { _ in
            MusicPlayerController.shared.playNext()
        }

        NotificationCenter.default.addObserver(
            forName: .hmpPreviousTrack,
            object: nil,
            queue: .main
        ) { _ in
            MusicPlayerController.shared.playPrevious()
        }
    }

    // MARK: - Remote Command

    private func setupRemoteCommandCallbacks() {
        remoteCommand.onPlay = { [weak self] in
            guard self?.currentMusicInfo != nil else { return }
            MusicPlayerController.shared.playOrResume()
        }

        remoteCommand.onPause = { [weak self] in
            guard self?.currentMusicInfo != nil else { return }
            MusicPlayerController.shared.pauseMusic()
        }

        remoteCommand.onNext = {
            MusicPlayerController.shared.playNext()
        }

        remoteCommand.onPrevious = {
            MusicPlayerController.shared.playPrevious()
        }

        remoteCommand.onSeek = { [weak self] positionTime in
            let positionMs = Int64(positionTime * 1000)
            MusicPlayerController.shared.seekTo(position: positionMs)
            self?.currentPositionMs = positionMs
        }

        remoteCommand.onSeekForward = { [weak self] in
            let current = self?.currentPositionMs ?? 0
            MusicPlayerController.shared.seekTo(position: current + 10_000)
        }

        remoteCommand.onSeekBackward = { [weak self] in
            let current = self?.currentPositionMs ?? 0
            MusicPlayerController.shared.seekTo(position: max(0, current - 10_000))
        }

        remoteCommand.setupCommands()
    }
}
