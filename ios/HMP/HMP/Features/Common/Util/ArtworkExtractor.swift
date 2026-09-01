import Foundation
import AVFoundation
import UIKit
import sharedIos

/// 从音频文件提取嵌入封面并保存到磁盘
/// 实现 KMP ArtworkBridge.ArtworkExtractor 接口
class ArtworkExtractor: NSObject, ArtworkBridgeArtworkExtractor {

    /// 封面保存目录名
    private static let coversDirName = "covers"

    /// 获取封面保存目录路径，不存在则创建
    static func coversDirectory() -> String? {
        let docs = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first!
        let dir = (docs as NSString).appendingPathComponent(coversDirName)
        let fm = FileManager.default
        if !fm.fileExists(atPath: dir) {
            try? fm.createDirectory(atPath: dir, withIntermediateDirectories: true)
        }
        return dir
    }

    /// 根据音乐文件路径计算封面文件路径（不提取，仅计算路径）
    static func savedCoverPath(for musicFilePath: String) -> String {
        let hash = abs(musicFilePath.stableHash)
        let filename = "cover_\(hash).jpg"
        guard let dir = coversDirectory() else { return "" }
        return (dir as NSString).appendingPathComponent(filename)
    }

    /// ArtworkBridgeArtworkExtractor 协议方法（同步，供 KMP 扫描时调用）
    func extractAndSave(filePath: String) -> String? {
        // 先检查磁盘是否已有缓存
        let savedPath = ArtworkExtractor.savedCoverPath(for: filePath)
        if FileManager.default.fileExists(atPath: savedPath) {
            return savedPath
        }

        let url = URL(fileURLWithPath: filePath)
        let asset = AVURLAsset(url: url, options: [AVURLAssetPreferPreciseDurationAndTimingKey: true])

        guard let data = extractArtworkData(from: asset) else {
            return nil
        }
        return saveImageData(data, filePath: filePath)
    }

    /// 异步提取封面（供 AlbumCover 后台调用）
    static func extractAsync(filePath: String) -> String? {
        let fm = FileManager.default
        guard fm.fileExists(atPath: filePath) else { return nil }
        
        let savedPath = savedCoverPath(for: filePath)
        if fm.fileExists(atPath: savedPath) {
            return savedPath
        }

        let url = URL(fileURLWithPath: filePath)
        let asset = AVURLAsset(url: url, options: [AVURLAssetPreferPreciseDurationAndTimingKey: true])

        let data = extractArtworkDataStatic(from: asset)
        guard let data else { return nil }

        return saveImageDataStatic(data, filePath: filePath)
    }

    // MARK: - 实例方法（同步 API）

    private func extractArtworkData(from asset: AVURLAsset) -> Data? {
        // 方法1: 通过 commonMetadata 查找 artwork
        for item in asset.commonMetadata {
            if item.commonKey == AVMetadataKey.commonKeyArtwork {
                if let data = item.dataValue {
                    return data
                }
            }
        }

        // 方法2: 遍历所有元数据格式查找封面
        for format in asset.availableMetadataFormats {
            for item in asset.metadata(forFormat: format) {
                // 检查常见封面键名
                let keyStr = item.key as? String
                let commonKeyStr = item.commonKey?.rawValue
                
                if keyStr == "APIC" || keyStr == "covr" || keyStr == "artwork" || keyStr == "cover" ||
                   commonKeyStr == "artwork" {
                    if let data = item.dataValue {
                        return data
                    }
                }
                
                // 某些格式可能将封面存储在 value 中
                if let value = item.value as? Data {
                    // 验证是否为图片数据
                    if Self.isLikelyImageData(data: value) {
                        return value
                    }
                }
            }
        }
        
        // 方法3: 尝试通过 iTunes 元数据格式获取
        if asset.availableMetadataFormats.contains(AVMetadataFormat.iTunesMetadata) {
            for item in asset.metadata(forFormat: AVMetadataFormat.iTunesMetadata) {
                if let key = item.key as? String, key == "covr" {
                    if let data = item.value as? Data {
                        return data
                    }
                    // 有时封面数据在 dataValue 中
                    if let data = item.dataValue {
                        return data
                    }
                }
            }
        }

        return nil
    }

    // MARK: - 静态方法（后台线程用）

    private static func extractArtworkDataStatic(from asset: AVURLAsset) -> Data? {
        // 方法1: 通过 commonMetadata 查找 artwork
        for item in asset.commonMetadata {
            if item.commonKey == AVMetadataKey.commonKeyArtwork {
                if let data = item.dataValue {
                    return data
                }
            }
        }

        // 方法2: 遍历所有元数据格式查找封面
        for format in asset.availableMetadataFormats {
            for item in asset.metadata(forFormat: format) {
                // 检查常见封面键名
                let keyStr = item.key as? String
                let commonKeyStr = item.commonKey?.rawValue
                
                if keyStr == "APIC" || keyStr == "covr" || keyStr == "artwork" || keyStr == "cover" ||
                   commonKeyStr == "artwork" {
                    if let data = item.dataValue {
                        return data
                    }
                }
                
                // 某些格式可能将封面存储在 value 中
                if let value = item.value as? Data {
                    // 验证是否为图片数据
                    if isLikelyImageData(data: value) {
                        return value
                    }
                }
            }
        }
        
        // 方法3: 尝试通过 iTunes 元数据格式获取
        if asset.availableMetadataFormats.contains(AVMetadataFormat.iTunesMetadata) {
            for item in asset.metadata(forFormat: AVMetadataFormat.iTunesMetadata) {
                if let key = item.key as? String, key == "covr" {
                    if let data = item.value as? Data {
                        return data
                    }
                    // 有时封面数据在 dataValue 中
                    if let data = item.dataValue {
                        return data
                    }
                }
            }
        }

        return nil
    }
    
    /// 检查数据是否为图片格式
    private static func isLikelyImageData(data: Data) -> Bool {
        // 检查 JPEG 魔数 (FF D8)
        if data.count >= 2, data[0] == 0xFF, data[1] == 0xD8 {
            return true
        }
        // 检查 PNG 魔数 (89 50 4E 47)
        if data.count >= 4, data[0] == 0x89, data[1] == 0x50, data[2] == 0x4E, data[3] == 0x47 {
            return true
        }
        return false
    }

    private static func saveImageDataStatic(_ data: Data, filePath: String) -> String? {
        guard let image = UIImage(data: data) else { return nil }

        let hash = abs(filePath.stableHash)
        let filename = "cover_\(hash).jpg"
        guard let dir = coversDirectory() else { return nil }
        let savePath = (dir as NSString).appendingPathComponent(filename)

        if FileManager.default.fileExists(atPath: savePath) {
            return savePath
        }

        let scaled = image.scaleToFit(maxSize: 600)
        guard let jpeg = scaled.jpegData(compressionQuality: 0.85) else { return nil }

        do {
            try jpeg.write(to: URL(fileURLWithPath: savePath))
            return savePath
        } catch {
            PlatformLogKt.platformLog(severity: 3, tag: "ArtworkExtractor", message: "save failed: \(error)")
            return nil
        }
    }

    private func saveImageData(_ data: Data, filePath: String) -> String? {
        guard let image = UIImage(data: data) else { return nil }

        let hash = abs(filePath.stableHash)
        let filename = "cover_\(hash).jpg"
        guard let dir = ArtworkExtractor.coversDirectory() else { return nil }
        let savePath = (dir as NSString).appendingPathComponent(filename)

        if FileManager.default.fileExists(atPath: savePath) {
            return savePath
        }

        let scaled = image.scaleToFit(maxSize: 600)
        guard let jpeg = scaled.jpegData(compressionQuality: 0.85) else { return nil }

        do {
            try jpeg.write(to: URL(fileURLWithPath: savePath))
            return savePath
        } catch {
            PlatformLogKt.platformLog(severity: 3, tag: "ArtworkExtractor", message: "save failed: \(error)")
            return nil
        }
    }
}

// MARK: - Helper Extensions

extension String {
    var stableHash: Int64 {
        var hash: UInt64 = 0xcbf29ce484222325
        for byte in self.utf8 {
            hash ^= UInt64(byte)
            hash &*= 0x100000001b3
        }
        return Int64(bitPattern: hash)
    }
}

extension UIImage {
    func scaleToFit(maxSize: CGFloat) -> UIImage {
        let size = self.size
        if size.width <= maxSize && size.height <= maxSize {
            return self
        }
        let scale = maxSize / max(size.width, size.height)
        let newSize = CGSize(width: size.width * scale, height: size.height * scale)
        // UIGraphicsImageRenderer must run on main thread
        if Thread.isMainThread {
            let renderer = UIGraphicsImageRenderer(size: newSize)
            return renderer.image { _ in
                self.draw(in: CGRect(origin: .zero, size: newSize))
            }
        } else {
            // Fallback: use CGImage + CIContext for background thread
            guard let cgImage = self.cgImage else { return self }
            let context = CIContext()
            let ciImage = CIImage(cgImage: cgImage)
            let scaledCI = ciImage.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
            guard let scaledCG = context.createCGImage(scaledCI, from: scaledCI.extent) else { return self }
            return UIImage(cgImage: scaledCG)
        }
    }
}
