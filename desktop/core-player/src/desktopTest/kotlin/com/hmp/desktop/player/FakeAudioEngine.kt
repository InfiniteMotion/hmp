package com.hmp.desktop.player

/**
 * FakeAudioEngine for testing DesktopMusicController.
 * Simulates audio playback state without actual audio output.
 */
class FakeAudioEngine : AudioEngine {

    var playCalled = false
    var pauseCalled = false
    var resumeCalled = false
    var stopCalled = false
    var releaseCalled = false
    var seekToPosition: Long = -1L
    var lastVolume: Float = 1.0f

    private var _isPlaying = false
    private var _isPaused = false
    private var _isLoaded = false
    private var _currentPosition = 0L
    private var _duration = 180000L

    override var onPlaybackComplete: (() -> Unit)? = null
    override var onError: ((Exception) -> Unit)? = null

    fun simulatePlaybackComplete() {
        onPlaybackComplete?.invoke()
    }
    fun simulateError(error: Exception) {
        onError?.invoke(error)
    }
    fun setLoaded(loaded: Boolean) {
        _isLoaded = loaded
    }
    fun setDuration(duration: Long) {
        _duration = duration
    }
    fun setCurrentPosition(position: Long) {
        _currentPosition = position
    }

    override fun play(path: String) {
        playCalled = true
        _isPlaying = true
        _isPaused = false
        _isLoaded = true
    }
    override fun pause() {
        pauseCalled = true
        _isPlaying = false
        _isPaused = true
    }
    override fun resume() {
        resumeCalled = true
        _isPlaying = true
        _isPaused = false
    }
    override fun stop() {
        stopCalled = true
        _isPlaying = false
        _isPaused = false
    }
    override fun seekTo(positionMs: Long) {
        seekToPosition = positionMs
        _currentPosition = positionMs
    }
    override fun getCurrentPosition(): Long = _currentPosition
    override fun getDuration(): Long = _duration
    override fun isPlaying(): Boolean = _isPlaying
    override fun isLoaded(): Boolean = _isLoaded
    override fun isPaused(): Boolean = _isPaused
    override fun setVolume(volume: Float) {
        lastVolume = volume
    }
    override fun release() {
        releaseCalled = true
        _isPlaying = false
        _isLoaded = false
    }
}
