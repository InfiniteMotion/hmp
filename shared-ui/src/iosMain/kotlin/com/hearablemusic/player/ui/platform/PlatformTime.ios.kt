package com.hearablemusic.player.ui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long {
    return (NSDate().timeIntervalSinceReferenceDate * 1000).toLong()
}