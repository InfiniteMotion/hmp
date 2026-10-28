package com.hearablemusic.player.ui.di

import com.hearablemusic.player.player.di.playerModule
import com.hmp.data.di.androidPlatformModule
import com.hmp.data.di.sharedModule
import com.hmp.data.network.BuiltInApiKeyProvider
import io.ktor.client.engine.HttpClientEngine
import kotlin.reflect.KClass
import kotlin.test.Test
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

/**
 * Android 端**真实装配图**的 DI 校验（X-04 的另一半，与 DesktopKoinGraphVerificationTest 对称）。
 *
 * 覆盖桌面那条覆盖不到的 Android 独有注册：`playerModule` 的 `MusicController` 与
 * `AndroidAgentKeepAlivePort`（`:shared` 的校验里 `AgentKeepAlivePort` 只能当外部类型放行），
 * 以及 `uiModule` 的 `CoilAlbumArtPixelsLoader(androidContext())` 与全部 `viewModel { }` 注册。
 * 模块清单与 `MusicApplication.onCreate()` 的 `modules(sharedModule, androidPlatformModule,
 * builtInAiModule, playerModule, uiModule)` 对齐，只少 `builtInAiModule` —— 它在 `android/app`，
 * 本编译单元看不见，所以 `BuiltInApiKeyProvider` 记在 extraTypes 里（这是本条唯一的人为放行）。
 *
 * 用静态 `verify()` 不实例化定义：装配图里有 Room、DataStore、`androidContext()`，
 * 跑起来就不是测试而是碰真机数据了。
 *
 * ⚠️ 与桌面那条同样的**已知假阴性**：带默认值的构造参数被静态校验视为可缺省
 * （`MasterAgent.chatPresenceBus: PresenceBus? = null` 实测删掉注册后两条都仍绿）。
 * 覆盖范围因此是"必传依赖"，不是整张图 —— 细节见 `DesktopKoinGraphVerificationTest` 的类注释。
 */
class AndroidAppKoinGraphVerificationTest {

    @Test
    fun androidAppGraphResolvesWithoutHoles() {
        val appGraph = module {
            includes(sharedModule, androidPlatformModule, playerModule, uiModule)
        }

        appGraph.verify(
            extraTypes = frameworkTypes,
            // ToolRegistry 的 `tools` 由 createBaseToolRegistry 就地组装，静态校验会读它的构造器
            // 当成待注入依赖 —— 这类要走 injections（实测放 extraTypes 不生效）。
            injections = injectedParameters(
                definition<com.hmp.domain.agent.tool.spec.ToolRegistry>(List::class),
            ),
        )
    }

    private companion object {
        /**
         * 人为放行的类型，每条都有理由：
         * - `HttpClientEngine`：Ktor 自己按平台构件选引擎，永远不会有 Koin 定义。
         * - `Function0`：DI 里 `timeProvider = { currentTimeMillis() }` 是就地 SAM lambda；
         *   `GlobalTokenCounter`/`TokenMeter` 是 `:shared` 的 internal 类，跨模块点不到名，
         *   只能整体放行 —— 按定义逐条钉住的那份精度在 `:shared` 的 KoinGraphVerificationTest。
         * - `BuiltInApiKeyProvider`：由 `android/app` 的 `builtInAiModule` 注册，本编译单元不可见。
         * - `Context`：由 `startKoin { androidContext(...) }` 在启动时注入，不是 Koin 定义；
         *   `SettingsRepositoryImpl` / `MusicController` 等都是从它取宿主上下文。
         * - `PlatformServices`：⚠️ **本闸门覆盖不到的一条**。Android 侧它不在任何静态模块里，而是
         *   `MainActivity.onCreate` 现场构造 `AndroidPlatformServices(applicationContext, this)` 后
         *   用 `loadModules` 注册 `single<PlatformServices>`（Desktop 侧则在 `desktopUiModule` 里静态注册，
         *   所以上面那条校验是真验到的）。Android 独有，且 `android/app` 在本编译单元之外 —— 这里只能放行。
         *   含义：若那次动态注册被删掉，本闸门不会红，只会在打开标签编辑页时崩。
         */
        val frameworkTypes: List<KClass<*>> = listOf(
            HttpClientEngine::class,
            Function0::class,
            BuiltInApiKeyProvider::class,
            android.content.Context::class,
            com.hearablemusic.player.ui.platform.PlatformServices::class,
        )
    }
}
