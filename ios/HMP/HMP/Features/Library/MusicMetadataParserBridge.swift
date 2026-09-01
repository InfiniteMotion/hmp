import Foundation
import AVFoundation
import CoreMedia
import sharedIos

class MusicMetadataParser: MetadataParserBridgeParser {
    func parse(filePath: String) -> MusicMetadata? {
        PlatformLogKt.platformLog(severity: 0, tag: "MetadataParser", message: "parsing: \(filePath)")
        let url = URL(fileURLWithPath: filePath)
        let asset = AVURLAsset(url: url)

        var title: String? = nil
        var artist: String? = nil
        var album: String? = nil
        var lyrics: String? = nil

        // First pass: commonMetadata (works for MP3/M4A with ID3/iTunes tags)
        for item in asset.commonMetadata {
            if let key = item.commonKey?.rawValue {
                switch key {
                case "title":
                    title = item.stringValue
                case "artist":
                    artist = item.stringValue
                case "albumName":
                    album = item.stringValue
                case "lyrics":
                    lyrics = item.stringValue
                default:
                    break
                }
            }
        }

        // Second pass: try all metadata formats for FLAC Vorbis comments and other formats
        if title == nil || artist == nil || album == nil || lyrics == nil {
            for format in asset.availableMetadataFormats {
                for item in asset.metadata(forFormat: format) {
                    let keyStr: String? = if let key = item.key as? String {
                        key
                    } else if let key = item.key as? [Any] {
                        (key as? [NSNumber])?.compactMap { String(UnicodeScalar($0.uint8Value) ?? UnicodeScalar("?")) }.joined()
                    } else {
                        nil
                    }
                    guard let key = keyStr else { continue }
                    switch key {
                    case "©nam", "TIT2", "title", "TITLE":
                        if title == nil { title = item.stringValue }
                    case "©ART", "TPE1", "artist", "ARTIST":
                        if artist == nil { artist = item.stringValue }
                    case "©alb", "TALB", "album", "ALBUM":
                        if album == nil { album = item.stringValue }
                    case "©lyr", "USLT", "lyrics", "LYRICS", "UNSYNCEDLYRICS":
                        if lyrics == nil { lyrics = item.stringValue }
                    default:
                        break
                    }
                }
                if title != nil && artist != nil && album != nil && lyrics != nil { break }
            }
        }

        // Third pass: for FLAC files, directly parse Vorbis comments (AVFoundation often skips lyrics)
        if lyrics == nil && url.pathExtension.lowercased() == "flac" {
            lyrics = parseFlacVorbisLyrics(filePath: filePath)
        }

        let durationMs: Int64? = {
            let seconds = CMTimeGetSeconds(asset.duration)
            if seconds.isNaN || seconds.isInfinite || seconds < 0 { return nil }
            return Int64(seconds * 1000)
        }()

        var bitRate: Int32? = nil
        var sampleRate: Int32? = nil

        let audioTracks = asset.tracks(withMediaType: .audio)
        if let track = audioTracks.first {
            let dataRate = track.estimatedDataRate
            if dataRate > 0 {
                bitRate = Int32(dataRate / 1000.0)
            }
            if let desc = track.formatDescriptions.first {
                let asbd = CMAudioFormatDescriptionGetStreamBasicDescription(desc as! CMAudioFormatDescription)
                if let asbd = asbd {
                    let rate = Int32(asbd.pointee.mSampleRate)
                    if rate > 0 {
                        sampleRate = rate
                    }
                }
            }
        }

        let fallbackTitle = url.deletingPathExtension().lastPathComponent
        let format = url.pathExtension.uppercased()

        let result = MusicMetadata(
            title: title ?? fallbackTitle,
            artist: artist ?? "Unknown Artist",
            album: album ?? "Unknown Album",
            year: nil,
            genre: nil,
            track: nil,
            duration: durationMs.map { KotlinLong(longLong: $0) },
            bitRate: bitRate.map { KotlinInt(int: $0) },
            sampleRate: sampleRate.map { KotlinInt(int: $0) },
            format: format,
            lyrics: lyrics,
            albumArt: nil
        )
        PlatformLogKt.platformLog(severity: 0, tag: "MetadataParser", message: "result: title=\(result.title), artist=\(result.artist), album=\(result.album), lyrics=\(lyrics != nil ? "\(lyrics!.prefix(30))..." : "nil")")
        return result
    }

    /// Directly parse FLAC Vorbis comment block for lyrics.
    /// AVFoundation often doesn't expose LYRICS from Vorbis comments.
    private func parseFlacVorbisLyrics(filePath: String) -> String? {
        guard let data = try? Data(contentsOf: URL(fileURLWithPath: filePath), options: .alwaysMapped) else {
            PlatformLogKt.platformLog(severity: 3, tag: "MetadataParser", message: "FLAC: failed to read file")
            return nil
        }
        let bytes = [UInt8](data)

        // FLAC structure: "fLaC" magic (4 bytes) + metadata blocks
        guard bytes.count > 8,
              bytes[0] == 0x66, bytes[1] == 0x4C, bytes[2] == 0x61, bytes[3] == 0x43 else {
            PlatformLogKt.platformLog(severity: 3, tag: "MetadataParser", message: "FLAC: not a valid FLAC file")
            return nil
        }

        var offset = 4
        while offset + 4 <= bytes.count {
            let isLast = (bytes[offset] & 0x80) != 0
            let blockType = Int(bytes[offset] & 0x7F)
            let blockSize = (Int(bytes[offset + 1]) << 16) | (Int(bytes[offset + 2]) << 8) | Int(bytes[offset + 3])
            offset += 4

            // Vorbis comment block type = 4
            if blockType == 4, offset + blockSize <= bytes.count {
                if let lyrics = parseVorbisComments(bytes: bytes, offset: offset, length: blockSize) {
                    return lyrics
                }
            }

            offset += blockSize
            if isLast { break }
        }
        return nil
    }

    /// Parse Vorbis comment block and extract LYRICS field
    private func parseVorbisComments(bytes: [UInt8], offset: Int, length: Int) -> String? {
        var pos = offset
        let end = offset + length

        // Vendor string: 4-byte little-endian length + UTF-8 data
        guard pos + 4 <= end else { return nil }
        let vendorLen = Int(bytes[pos]) | (Int(bytes[pos + 1]) << 8) | (Int(bytes[pos + 2]) << 16) | (Int(bytes[pos + 3]) << 24)
        pos += 4 + vendorLen

        // Comment count: 4-byte little-endian
        guard pos + 4 <= end else { return nil }
        let commentCount = Int(bytes[pos]) | (Int(bytes[pos + 1]) << 8) | (Int(bytes[pos + 2]) << 16) | (Int(bytes[pos + 3]) << 24)
        pos += 4

        // Read each comment: "KEY=VALUE"
        let lyricsKeys: Set<String> = ["LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "LYRIC"]
        for _ in 0..<commentCount {
            guard pos + 4 <= end else { break }
            let commentLen = Int(bytes[pos]) | (Int(bytes[pos + 1]) << 8) | (Int(bytes[pos + 2]) << 16) | (Int(bytes[pos + 3]) << 24)
            pos += 4
            guard pos + commentLen <= end else { break }

            let commentData = Data(bytes[pos..<(pos + commentLen)])
            pos += commentLen

            guard let comment = String(data: commentData, encoding: .utf8),
                  let eqRange = comment.range(of: "=", options: [], range: comment.startIndex..<comment.endIndex) else {
                continue
            }

            let key = String(comment[comment.startIndex..<eqRange.lowerBound]).uppercased()
            let value = String(comment[eqRange.upperBound...])

            if lyricsKeys.contains(key) {
                PlatformLogKt.platformLog(severity: 0, tag: "MetadataParser", message: "FLAC Vorbis: found lyrics via key '\(key)'")
                return value
            }
        }
        return nil
    }
}
