package com.hearablemusic.player

import android.app.Application
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.hearablemusic.player.player.di.playerModule
import com.hearablemusic.player.ui.di.uiModule
import com.hmp.initKermit
import com.hmp.data.di.androidPlatformModule
import com.hmp.data.di.sharedModule
import com.hmp.data.network.BuiltInApiKeyProvider
import com.hmp.data.util.MusicTagEditor
import com.hmp.domain.agent.runtime.MasterAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

class MusicApplication : Application() {

    companion object {
        lateinit var instance: MusicApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initKermit(if (BuildConfig.DEBUG) Severity.Debug else Severity.Warn)
        MusicTagEditor.init(this)
        val builtInAiModule = module {
            single {
                BuiltInApiKeyProvider(
                    endpoint = BuildConfig.BUILT_IN_AI_ENDPOINT,
                    apiKey = BuildConfig.BUILT_IN_AI_API_KEY,
                    model = BuildConfig.BUILT_IN_AI_MODEL
                )
            }
        }
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@MusicApplication)
            modules(sharedModule, androidPlatformModule, builtInAiModule, playerModule, uiModule)
        }

        // App 启动后静默启动 MasterAgent 后台（Scheduler + Enrich 健康检测）
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val masterAgent: MasterAgent = GlobalContext.get().get()
                masterAgent.initialize()
            } catch (e: Exception) {
                Logger.e(e, tag = "Agent.Master") { "silent initialize failed" }
            }
        }
    }
}
