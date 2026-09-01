package com.hmp.desktop

import com.hmp.desktop.player.di.desktopPlayerModule
import com.hmp.di.initKoinDesktop
import com.hearablemusic.player.ui.di.desktopUiModule
import co.touchlab.kermit.Logger

object HmpDesktopApplication {
    fun init() {
        val start = System.currentTimeMillis()
        initKoinDesktop(desktopPlayerModule, desktopUiModule)
        Logger.i(null, "Startup") { "+${System.currentTimeMillis() - start}ms — initKoinDesktop (startKoin + module registration)" }
    }
}
