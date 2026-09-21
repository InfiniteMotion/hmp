package com.hearablemusic.player

import android.app.Application
import co.touchlab.kermit.Severity
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.log.HmpLog
import com.hmp.log.LogTag
import com.hearablemusic.player.player.di.playerModule
import com.hearablemusic.player.ui.di.uiModule
import com.hmp.initKermit
import com.hmp.data.di.androidPlatformModule
import com.hmp.data.di.sharedModule
import com.hmp.data.network.BuiltInApiKeyProvider
import com.hmp.data.util.MusicTagEditor
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

        // F11-L1 启动即初始化：**主动解析** MasterAgent 单例 → 触发 ChatKoinModule 的
        // `.also { lifecycleScope.launch { initialize() } }`（Scheduler + Enrich + Hello + 画像）。
        // 注意：这里是"取实例"而非"调 initialize()"——initialize 仍只由 .also 执行一次，
        // 手动再调会导致 Scheduler + startEnrich + startHello 执行两次。
        // 这样 agent 运行时在 app 启动即就绪，不再依赖"首次进 UI 才懒初始化"。
        runCatching { GlobalContext.get().get<MasterAgent>() }
            .onFailure { e -> HmpLog.w(LogTag.SystemLifecycle, e) { "🚀 eager MasterAgent resolve failed (non-fatal)" } }

        // 生命周期绑定：JVM shutdown hook 清理 MasterAgent（进程被杀时兜底）
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching {
                GlobalContext.get().get<MasterAgent>().close()
            }
        })
    }

    override fun onTerminate() {
        // 正常退出时（系统/用户杀进程前最后一次回调）
        runCatching {
            GlobalContext.get().get<MasterAgent>().close()
        }
        super.onTerminate()
    }
}
