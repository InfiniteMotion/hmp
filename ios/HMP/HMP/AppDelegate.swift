import UIKit
import sharedIos
import MediaPlayer

/// iOS AppDelegate — 在应用启动时初始化 Koin (KMP DI)
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        #if DEBUG
        KermitInitKt.initKermitForIos(isReleaseBuild: false)
        #else
        KermitInitKt.initKermitForIos(isReleaseBuild: true)
        #endif
        requestMusicLibraryPermission()
        ensureDocumentsFolderVisible()
        // shared（业务+平台）+ shared-ui（Compose UI）一次性装配
        IosUiKoinModuleKt.installKoinIosWithSharedUi()
        PlatformLogKt.platformLog(severity: 0, tag: "AppDelegate", message: "Koin initialized (shared + shared-ui)")
        MetadataParserBridge().register(parser: MusicMetadataParser())
        ArtworkBridge().register(extractor: ArtworkExtractor())

        // 初始化默认播放列表（这会触发播放状态的恢复）
        Task {
            await MusicPlayerController.shared.initializeDefaultPlaylists()
        }

        // 方向 A Phase 1：Swift 播放引擎 → Kotlin PlaybackController 双桥
        Task { @MainActor in
            PlaybackBridge.install()
        }

        // 方向 A Phase 1：平台服务桥（分享 / 图库选图 / 备份文件选择 → Compose PlatformServices）
        Task { @MainActor in
            PlatformServicesBridge.install()
        }

        return true
    }

    private func requestMusicLibraryPermission() {
        let status = MPMediaLibrary.authorizationStatus()
        if status == .notDetermined {
            MPMediaLibrary.requestAuthorization { _ in }
        }
    }

    /// 在 Documents 目录创建一个占位文件，使 HMP 文件夹在文件 App 中可见
    private func ensureDocumentsFolderVisible() {
        guard let documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else {
            PlatformLogKt.platformLog(severity: 3, tag: "AppDelegate", message: "cannot get Documents directory")
            return
        }

        let placeholderFile = documentsURL.appendingPathComponent(".hmp-folder-visible")
        if !FileManager.default.fileExists(atPath: placeholderFile.path) {
            do {
                try "HMP Music Player Documents".write(to: placeholderFile, atomically: true, encoding: .utf8)
                PlatformLogKt.platformLog(severity: 1, tag: "AppDelegate", message: "created placeholder file to make Documents folder visible: \(placeholderFile.path)")
            } catch {
                PlatformLogKt.platformLog(severity: 3, tag: "AppDelegate", message: "failed to create placeholder file: \(error)")
            }
        }
    }
}
