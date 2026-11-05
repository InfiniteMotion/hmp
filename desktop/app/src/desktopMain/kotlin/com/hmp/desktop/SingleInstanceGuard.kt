package com.hmp.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException

/**
 * Prevents multiple instances of HMP from running simultaneously.
 *
 * Uses an exclusive file lock (~/.hmp/hmp.lock) to detect whether another
 * process already holds the lock. If so, [tryAcquire] returns false and
 * the caller should exit.
 *
 * The lock is automatically released when the JVM exits (FileLock is
 * tied to the FileChannel which closes on shutdown), but calling
 * [release] is still recommended for a clean exit.
 */
object SingleInstanceGuard {
    private var lockFile: RandomAccessFile? = null
    private var fileLock: java.nio.channels.FileLock? = null

    /**
     * Attempt to acquire the singleton lock.
     *
     * @return true if this instance is the sole owner; false if another
     *         instance is already running.
     */
    fun tryAcquire(): Boolean = try {
        val lockDir = File(System.getProperty("user.home"), ".hmp")
        if (!lockDir.exists()) lockDir.mkdirs()

        val file = File(lockDir, "hmp.lock")
        lockFile = RandomAccessFile(file, "rw")
        fileLock = lockFile?.channel?.tryLock()
        fileLock != null
    } catch (_: OverlappingFileLockException) {
        // Another instance in the same JVM — still counts as locked
        false
    } catch (_: Exception) {
        false
    }

    /**
     * Release the singleton lock explicitly.
     * Also called automatically on normal JVM shutdown.
     */
    fun release() {
        try {
            fileLock?.release()
        } catch (_: Exception) {}
        try {
            lockFile?.close()
        } catch (_: Exception) {}
        fileLock = null
        lockFile = null
    }
}
