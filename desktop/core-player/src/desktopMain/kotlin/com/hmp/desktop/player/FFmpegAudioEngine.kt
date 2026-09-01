package com.hmp.desktop.player

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

class FFmpegAudioEngine : AudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var ffmpegProcess: Process? = null
    private var sourceLine: SourceDataLine? = null
    private var playbackJob: Job? = null

    private var currentPath: String? = null
    private var isPaused = false
    private var isStopped = true
    private var durationMs: Long = 0L
    private var bytesWritten: Long = 0L
    private var sampleRate: Float = 44100f
    private var channels: Int = 2
    private var sampleSizeInBytes: Int = 2
    private var seekPositionMs: Long = 0L
    private var volume: Float = 1.0f

    override var onPlaybackComplete: (() -> Unit)? = null
    override var onError: ((Exception) -> Unit)? = null

    private val ffmpegPath: String
        get() {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val javaHome = System.getProperty("java.home") ?: ""
            val ffmpegName = if (isWindows) "ffmpeg.exe" else "ffmpeg"

            // Explicit system property (set by Gradle during development)
            System.getProperty("hmp.ffmpeg.path")?.let { path ->
                val file = File(path)
                if (file.exists() && file.canExecute()) return file.absolutePath
            }

            // Bundled ffmpeg (inside packaged distribution's runtime/bin)
            val bundledCandidates = if (javaHome.isNotEmpty()) {
                listOf(File(javaHome, "bin/$ffmpegName"))
            } else emptyList()

            // Common install locations
            val commonCandidates = if (isWindows) {
                val localAppData = System.getenv("LOCALAPPDATA") ?: ""
                val programFiles = System.getenv("ProgramFiles") ?: ""
                val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: ""
                listOfNotNull(
                    File("C:/ffmpeg/bin/$ffmpegName"),
                    if (localAppData.isNotEmpty()) File("$localAppData/ffmpeg/bin/$ffmpegName") else null,
                    if (programFiles.isNotEmpty()) File("$programFiles/ffmpeg/bin/$ffmpegName") else null,
                    if (programFilesX86.isNotEmpty()) File("$programFilesX86/ffmpeg/bin/$ffmpegName") else null
                )
            } else {
                listOf(
                    File("/usr/bin/$ffmpegName"),
                    File("/usr/local/bin/$ffmpegName"),
                    File("${System.getProperty("user.home")}/ffmpeg/bin/$ffmpegName")
                )
            }

            // Check PATH environment variable
            val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
            val pathCandidates = pathDirs.map { File(it, ffmpegName) }

            for (candidate in bundledCandidates + commonCandidates + pathCandidates) {
                if (candidate.exists() && candidate.canExecute()) {
                    return candidate.absolutePath
                }
            }

            // Fallback: bare name (relies on OS PATH resolution)
            return ffmpegName
        }

    override fun play(path: String) {
        stop()
        currentPath = path
        isStopped = false
        isPaused = false
        seekPositionMs = 0L
        bytesWritten = 0L

        Logger.i(null, "AudioEngine") { "play: $path, ffmpeg=$ffmpegPath" }
        startPlayback(path, 0L)
    }

    override fun pause() {
        if (!isPlaying()) return
        isPaused = true
        sourceLine?.stop()
    }

    override fun resume() {
        if (!isPaused) return
        isPaused = false
        sourceLine?.start()
    }

    override fun stop() {
        isStopped = true
        isPaused = false
        playbackJob?.cancel()
        playbackJob = null
        sourceLine?.let { line ->
            try {
                line.stop()
                line.close()
            } catch (_: Exception) {}
        }
        sourceLine = null
        ffmpegProcess?.let { process ->
            try {
                process.destroyForcibly()
            } catch (_: Exception) {}
        }
        ffmpegProcess = null
        currentPath = null
    }

    override fun seekTo(positionMs: Long) {
        val path = currentPath ?: return
        val wasPlaying = isPlaying() || isPaused
        if (!wasPlaying) return

        seekPositionMs = positionMs
        bytesWritten = 0L
        isPaused = false

        // Restart playback from the new position
        sourceLine?.let { line ->
            try {
                line.stop()
                line.flush()
                line.close()
            } catch (_: Exception) {}
        }
        sourceLine = null
        ffmpegProcess?.destroyForcibly()
        ffmpegProcess = null
        playbackJob?.cancel()

        startPlayback(path, positionMs)
    }

    override fun getCurrentPosition(): Long {
        if (isPaused) return seekPositionMs
        val bytesPerMs = sampleRate * channels * sampleSizeInBytes / 1000.0
        return if (bytesPerMs > 0) seekPositionMs + (bytesWritten / bytesPerMs).toLong() else 0L
    }

    override fun getDuration(): Long = durationMs

    override fun isPlaying(): Boolean = !isStopped && !isPaused && (ffmpegProcess?.isAlive == true)

    override fun isLoaded(): Boolean = currentPath != null && !isStopped

    override fun isPaused(): Boolean = isPaused

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    override fun release() {
        stop()
    }

    /**
     * Probe the audio file's duration, sample rate, and channel count using FFmpeg.
     * Runs on IO dispatcher to avoid blocking the main thread.
     */
    private suspend fun probeDuration(path: String) = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            Logger.d(null, "AudioEngine") { "probing: $ffmpegPath -i $path" }
            process = ProcessBuilder(
                ffmpegPath, "-i", path,
                "-f", "null", "-"
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            Logger.d(null, "AudioEngine") { "probe exit=$exitCode, output: ${output.take(300)}" }

            // Parse duration from ffmpeg output: Duration: HH:MM:SS.ss
            val durationRegex = Regex("""Duration:\s*(\d+):(\d+):(\d+)\.(\d+)""")
            durationRegex.find(output)?.let { match ->
                val hours = match.groupValues[1].toLong()
                val minutes = match.groupValues[2].toLong()
                val seconds = match.groupValues[3].toLong()
                val centiseconds = match.groupValues[4].toLong()
                durationMs = (hours * 3600 + minutes * 60 + seconds) * 1000 + centiseconds * 10
            }

            // Parse sample rate from ffmpeg output: "44100 Hz" or "48000 Hz"
            val sampleRateRegex = Regex("""(\d+) Hz""")
            sampleRateRegex.find(output)?.let { match ->
                val parsed = match.groupValues[1].toFloatOrNull()
                if (parsed != null && parsed > 0) {
                    sampleRate = parsed
                }
            }

            // Parse channel count from layout string
            val channelLayoutRegex = Regex("""(mono|stereo|[\d.]+\s*channels?)""")
            channelLayoutRegex.find(output)?.let { match ->
                val layout = match.groupValues[1].trim()
                channels = when {
                    layout == "mono" -> 1
                    layout == "stereo" -> 2
                    layout.contains("channel") -> {
                        // "5.1 channels" -> 6, "7.1 channels" -> 8
                        val numStr = layout.substringBefore("channel").trim()
                        val num = numStr.toFloatOrNull()
                        if (num != null) {
                            // Integer part + 1 for the .1 subwoofer
                            val base = num.toInt()
                            val hasSubwoofer = num > base
                            (base + if (hasSubwoofer) 1 else 0).coerceIn(1, 8)
                        } else 2
                    }
                    else -> 2
                }
            }
        } catch (_: Exception) {
            durationMs = 0L
            // Keep default sampleRate (44100) and channels (2) on probe failure
        } finally {
            process?.destroyForcibly()
        }
    }

    /**
     * Start FFmpeg-based audio playback via Java Sound API.
     * Probes duration first, then starts the decode process.
     * Checks process exit code and reports errors.
     */
    private fun startPlayback(path: String, seekMs: Long) {
        playbackJob = scope.launch {
            try {
                // Probe duration/format on IO thread (no longer blocks Main)
                probeDuration(path)

                val seekArgs = if (seekMs > 0) {
                    listOf("-ss", String.format("%.3f", seekMs / 1000.0))
                } else {
                    emptyList()
                }

                val command = listOf(
                    ffmpegPath,
                    *seekArgs.toTypedArray(),
                    "-i", path,
                    "-f", "s16le",
                    "-acodec", "pcm_s16le",
                    "-ar", sampleRate.toInt().toString(),
                    "-ac", channels.toString(),
                    "-"
                )

                Logger.d(null, "AudioEngine") { "command: ${command.joinToString(" ")}" }

                val pb = ProcessBuilder(command)
                pb.redirectErrorStream(false)
                val process = pb.start()
                ffmpegProcess = process
                Logger.d(null, "AudioEngine") { "process started, alive=${process.isAlive}" }

                // Capture stderr for error diagnosis
                var stderrOutput = ""
                val stderrThread = Thread({
                    try {
                        stderrOutput = process.errorStream.bufferedReader().readText()
                    } catch (_: Exception) {}
                }, "ffmpeg-stderr-${path.hashCode()}")
                stderrThread.isDaemon = true
                stderrThread.start()

                val frameSize = channels * sampleSizeInBytes
                val format = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    frameSize,
                    sampleRate,
                    false
                )
                val info = DataLine.Info(SourceDataLine::class.java, format)
                Logger.d(null, "AudioEngine") { "format: ${sampleRate.toInt()}Hz ${channels}ch 16bit frameSize=$frameSize" }

                if (!AudioSystem.isLineSupported(info)) {
                    val msg = "Audio line not supported for format: ${sampleRate.toInt()}Hz ${channels}ch"
                    Logger.e(null, "AudioEngine") { "ERROR: $msg" }
                    withContext(Dispatchers.Main) {
                        onError?.invoke(Exception(msg))
                    }
                    return@launch
                }

                val line = AudioSystem.getLine(info) as SourceDataLine
                sourceLine = line
                line.open(format)
                line.start()
                Logger.i(null, "AudioEngine") { "SourceDataLine opened and started" }

                sampleSizeInBytes = 2

                val buffer = ByteArray(8192)
                val audioStream = BufferedInputStream(process.inputStream)
                Logger.d(null, "AudioEngine") { "reading PCM data, format=${sampleRate.toInt()}Hz ${channels}ch 16bit" }

                while (isActive && !isStopped) {
                    if (isPaused) {
                        delay(50)
                        continue
                    }

                    val bytesRead = audioStream.read(buffer)
                    if (bytesRead == -1) break

                    // Apply volume if needed
                    if (volume < 1.0f) {
                        applyVolume(buffer, bytesRead, volume)
                    }

                    line.write(buffer, 0, bytesRead)
                    bytesWritten += bytesRead
                }

                Logger.d(null, "AudioEngine") { "read loop ended, bytesWritten=$bytesWritten, isStopped=$isStopped" }

                line.drain()
                line.stop()
                line.close()

                audioStream.close()
                stderrThread.join(1000)

                val exitCode = process.waitFor()
                process.destroyForcibly()

                Logger.i(null, "AudioEngine") { "process exited with code $exitCode, stderr: ${stderrOutput.take(300)}" }

                if (!isStopped && isActive) {
                    if (exitCode != 0 && bytesWritten == 0L) {
                        withContext(Dispatchers.Main) {
                            onError?.invoke(Exception("Playback failed (exit $exitCode): ${stderrOutput.take(200)}"))
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onPlaybackComplete?.invoke()
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(e, "AudioEngine") { "exception: ${e.message}" }
                if (!isStopped) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke(e)
                    }
                }
            }
        }
    }

    private fun applyVolume(buffer: ByteArray, length: Int, vol: Float) {
        // Apply volume gain to 16-bit PCM samples
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val adjusted = (sample.toShort() * vol).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = (adjusted and 0xFF).toByte()
            buffer[i + 1] = (adjusted shr 8).toByte()
            i += 2
        }
    }

    private fun calculateBytesForMs(ms: Long): Long {
        val bytesPerMs = sampleRate * channels * sampleSizeInBytes / 1000.0
        return (ms * bytesPerMs).toLong()
    }
}
