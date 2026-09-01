import AVFoundation
import sharedIos

/// 音频会话管理器 - 处理音频焦点、中断和路由变化
class AudioSessionManager {
    static let shared = AudioSessionManager()

    private init() {
        setupAudioSession()
        setupNotificationObservers()
    }

    func setupAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(
                .playback,
                mode: .default,
                options: .allowAirPlay
            )
            try AVAudioSession.sharedInstance().setActive(true)
            PlatformLogKt.platformLog(severity: 1, tag: "AudioSession", message: "Setup successful: category=playback, options=allowAirPlay, active=true")
        } catch {
            PlatformLogKt.platformLog(severity: 3, tag: "AudioSession", message: "setup failed: \(error)")
        }
    }

    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            self?.handleInterruption(notification)
        }

        NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            self?.handleRouteChange(notification)
        }
    }

    private func handleInterruption(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else { return }

        switch type {
        case .began:
            MusicPlayerController.shared.pauseMusic()
        case .ended:
            if let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt,
               AVAudioSession.InterruptionOptions(rawValue: optionsValue).contains(.shouldResume) {
                MusicPlayerController.shared.playOrResume()
            }
        @unknown default:
            break
        }
    }

    private func handleRouteChange(_ notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else { return }

        switch reason {
        case .oldDeviceUnavailable:
            // Headphone disconnected — pause playback
            MusicPlayerController.shared.pauseMusic()
        default:
            break
        }
    }

    func setActive(_ active: Bool) {
        do {
            try AVAudioSession.sharedInstance().setActive(active)
        } catch {
            PlatformLogKt.platformLog(severity: 3, tag: "AudioSession", message: "setActive failed: \(error)")
        }
    }
}
