import ActivityKit
import UIKit
import sharedIos

@available(iOS 16.1, *)
class LiveActivityManager {
    static let shared = LiveActivityManager()

    private var currentActivity: Activity<HMPNowPlayingAttributes>?
    private var lastUpdateTime: Date = .distantPast
    private let updateInterval: TimeInterval = 1.0

    private init() {}

    func startActivity(musicInfo: MusicInfo, isPlaying: Bool,
                       showAnimation: Bool = true, showLyrics: Bool = false) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

        endActivity()

        let attributes = HMPNowPlayingAttributes()
        let artworkData = loadCompressedArtwork(musicInfo: musicInfo)

        let state = HMPNowPlayingAttributes.ContentState(
            title: musicInfo.music.title,
            artist: musicInfo.music.artist,
            album: musicInfo.music.album,
            isPlaying: isPlaying,
            position: 0,
            duration: Double(musicInfo.music.duration) / 1000.0,
            artworkData: artworkData,
            currentLyricLine: nil,
            showAnimation: showAnimation,
            showLyrics: showLyrics
        )

        let content = ActivityContent(state: state, staleDate: nil)

        do {
            currentActivity = try Activity.request(
                attributes: attributes,
                content: content,
                pushType: nil
            )
            lastUpdateTime = Date()
        } catch {
            PlatformLogKt.platformLog(severity: 3, tag: "LiveActivityManager", message: "Failed to start activity: \(error)")
        }
    }

    func updateActivity(isPlaying: Bool, position: Double, duration: Double,
                        lyricLine: String?) {
        guard let activity = currentActivity else { return }

        let now = Date()
        guard now.timeIntervalSince(lastUpdateTime) >= updateInterval else { return }
        lastUpdateTime = now

        let state = HMPNowPlayingAttributes.ContentState(
            title: activity.content.state.title,
            artist: activity.content.state.artist,
            album: activity.content.state.album,
            isPlaying: isPlaying,
            position: position,
            duration: duration,
            artworkData: activity.content.state.artworkData,
            currentLyricLine: lyricLine,
            showAnimation: activity.content.state.showAnimation,
            showLyrics: activity.content.state.showLyrics
        )

        let content = ActivityContent(state: state, staleDate: nil)

        Task {
            await activity.update(content)
        }
    }

    func updateArtwork(_ imageData: Data?) {
        guard let activity = currentActivity, let data = imageData else { return }

        var state = activity.content.state
        let newState = HMPNowPlayingAttributes.ContentState(
            title: state.title,
            artist: state.artist,
            album: state.album,
            isPlaying: state.isPlaying,
            position: state.position,
            duration: state.duration,
            artworkData: data,
            currentLyricLine: state.currentLyricLine,
            showAnimation: state.showAnimation,
            showLyrics: state.showLyrics
        )

        let content = ActivityContent(state: newState, staleDate: nil)

        Task {
            await activity.update(content)
        }
    }

    func endActivity() {
        guard let activity = currentActivity else { return }

        Task {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        currentActivity = nil
    }

    private func loadCompressedArtwork(musicInfo: MusicInfo) -> Data? {
        var image: UIImage?

        if !musicInfo.music.albumArtUri.isEmpty {
            image = CoverCache.shared.get(path: musicInfo.music.albumArtUri)
        }

        if image == nil && !musicInfo.music.path.isEmpty {
            image = CoverCache.shared.getOrExtractSync(musicPath: musicInfo.music.path)
        }

        guard let img = image else { return nil }

        let maxSize: CGFloat = 200
        let scale = min(maxSize / img.size.width, maxSize / img.size.height)
        let scaledSize = CGSize(
            width: img.size.width * scale,
            height: img.size.height * scale
        )
        let renderer = UIGraphicsImageRenderer(size: scaledSize)
        let scaled = renderer.image { _ in
            img.draw(in: CGRect(origin: .zero, size: scaledSize))
        }
        return scaled.jpegData(compressionQuality: 0.5)
    }
}
