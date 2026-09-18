package com.hmp.desktop

import com.hmp.desktop.player.di.desktopPlayerModule
import com.hmp.di.initKoinDesktop
import com.hearablemusic.player.ui.di.desktopUiModule
import com.hmp.log.HmpLog
import com.hmp.log.LogTag

object HmpDesktopApplication {
    fun init() {
        val start = System.currentTimeMillis()
        initKoinDesktop(desktopPlayerModule, desktopUiModule)
        HmpLog.i(LogTag.SystemLifecycle) { "🚀 +${System.currentTimeMillis() - start}ms — initKoinDesktop (startKoin + module registration)" }
    }
}
