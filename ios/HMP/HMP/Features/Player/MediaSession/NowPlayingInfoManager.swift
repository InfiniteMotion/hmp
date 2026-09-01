import AVFoundation
import MediaPlayer
import sharedIos

class NowPlayingInfoManager {
    private var nowPlayingSession: MPNowPlayingSession?
    private var currentArtwork: UIImage?
    private var lastPositionUpdate: TimeInterval = 0

    var hasSession: Bool { nowPlayingSession != nil }
    var hasNowPlayingInfo: Bool { MPNowPlayingInfoCenter.default().nowPlayingInfo != nil }

    func setupNowPlayingSession(with player: AVPlayer?) {
        if let player = player {
            nowPlayingSession = MPNowPlayingSession(players: [player])
            PlatformLogKt.platformLog(severity: 1, tag: "NowPlayingInfo", message: "Session created with AVPlayer")
        } else {
            PlatformLogKt.platformLog(severity: 2, tag: "NowPlayingInfo", message: "WARNING: AVPlayer is nil, will create session when available")
        }
    }

    func ensureSession(with player: AVPlayer?) {
        guard let player = player else { return }
        if nowPlayingSession == nil {
            nowPlayingSession = MPNowPlayingSession(players: [player])
            PlatformLogKt.platformLog(severity: 1, tag: "NowPlayingInfo", message: "Session created lazily with AVPlayer")
        }
    }

    func updateTrack(title: String, artist: String, album: String,
                     artwork: UIImage?, duration: TimeInterval) {
        currentArtwork = artwork

        var info = [String: Any]()
        info[MPMediaItemPropertyTitle] = title
        info[MPMediaItemPropertyArtist] = artist
        info[MPMediaItemPropertyAlbumTitle] = album
        info[MPMediaItemPropertyPlaybackDuration] = duration
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = 0
        info[MPNowPlayingInfoPropertyPlaybackRate] = 1.0
        info[MPNowPlayingInfoPropertyIsLiveStream] = false
        info[MPNowPlayingInfoPropertyMediaType] = MPNowPlayingInfoMediaType.audio.rawValue

        if let artwork = artwork {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: artwork.size) { _ in
                return artwork
            }
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        MPNowPlayingInfoCenter.default().playbackState = .playing
        PlatformLogKt.platformLog(severity: 0, tag: "NowPlayingInfo", message: "updateTrack: title=\(title), artist=\(artist), duration=\(duration)s, playbackState=playing")
        lastPositionUpdate = Date().timeIntervalSinceReferenceDate
    }

    func updatePlaybackState(isPlaying: Bool, position: TimeInterval) {
        guard var info = MPNowPlayingInfoCenter.default().nowPlayingInfo else {
            PlatformLogKt.platformLog(severity: 0, tag: "NowPlayingInfo", message: "updatePlaybackState: no nowPlayingInfo set yet")
            return
        }
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = position
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        MPNowPlayingInfoCenter.default().playbackState = isPlaying ? .playing : .paused
        PlatformLogKt.platformLog(severity: 0, tag: "NowPlayingInfo", message: "updatePlaybackState: isPlaying=\(isPlaying), position=\(position)s")
        lastPositionUpdate = Date().timeIntervalSinceReferenceDate
    }

    func updatePosition(position: TimeInterval, duration: TimeInterval) {
        let now = Date().timeIntervalSinceReferenceDate
        guard now - lastPositionUpdate >= 5.0 else { return }

        guard var info = MPNowPlayingInfoCenter.default().nowPlayingInfo else { return }
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = position
        info[MPMediaItemPropertyPlaybackDuration] = duration
        if let rate = info[MPNowPlayingInfoPropertyPlaybackRate] as? Double, rate == 0.0 {
            info[MPNowPlayingInfoPropertyPlaybackRate] = 0.0
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        lastPositionUpdate = now
    }

    func updateArtwork(_ artwork: UIImage?) {
        currentArtwork = artwork
        guard var info = MPNowPlayingInfoCenter.default().nowPlayingInfo else { return }
        if let artwork = artwork {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: artwork.size) { _ in
                return artwork
            }
        } else {
            info.removeValue(forKey: MPMediaItemPropertyArtwork)
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    func clear() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        MPNowPlayingInfoCenter.default().playbackState = .stopped
        currentArtwork = nil
        nowPlayingSession = nil
    }
}
