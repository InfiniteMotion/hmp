package com.hearablemusic.player.ui.di

import com.hmp.data.di.sharedModule
import com.hmp.desktop.player.di.desktopPlayerModule
import com.hmp.di.desktopPlatformModule
import com.hmp.domain.agent.tool.spec.ToolRegistry
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * Desktop 端**真实装配图**的 DI 校验（X-04 的另一半）。
 *
 * `:shared` 里那条 `KoinGraphVerificationTest` 只能看见 `sharedModule + desktopPlatformModule`，
 * 于是把 `ToolRegistry` / `PolicyGuard` / `PresenceBus` / `PlaybackCommandPort` /
 * `NowPlayingContextProvider` 当外部类型登记放行 —— 也就是说"UI 侧删一条注册"它不会报。
 * 本条按 `HmpDesktopApplication.init()` 的实际装配
 * （`initKoinDesktop(desktopPlayerModule, desktopUiModule)`，两者再叠 `sharedModule` + 平台模块）
 * 把四个模块一起验，上面那 5 个 bean 由 `desktopUiModule → includes(chatGatewayModule)` 真实提供，
 * 不再是人为登记项。
 *
 * **实测信号**：注释掉 `single<PlaybackController> { DesktopMusicControllerPlaybackAdapter(get()) }`
 * → 本条红并报 `Missing definition for '[field:'playbackController' - type:PlaybackController]'
 * in definition '[Factory: …ViewModel]'`；同时 Android 那条不受影响（两条各自独立）。
 *
 * ⚠️ **已知假阴性（别把绿当保证）**：带**默认值**的构造参数被静态校验视为可缺省。
 * 例：`MasterAgent.chatPresenceBus: PresenceBus? = null` —— 注释掉 `single { PresenceBus() }` 后
 * 两条校验**都仍绿**，而装配图里写的是 `chatPresenceBus = get()`，运行期照样抛。
 * 同理还有 `keepAlivePort = getOrNull()`（那是真有意的可选）。所以：
 * 必传依赖（ViewModel 构造、singleOf 的仓库依赖）由本条守；可选参数那条路径只能靠运行期/真机。
 *
 * 依旧用静态 `verify()` 而不是 `checkModules()`：后者会实例化每条定义，等于在测试里打开
 * 开发者真实的 `~/.hmp` 数据库与 DataStore。
 */
class DesktopKoinGraphVerificationTest {

    @Test
    fun desktopAppGraphResolvesWithoutHoles() {
        val appGraph = module {
            includes(sharedModule, desktopPlatformModule, desktopPlayerModule, desktopUiModule)
        }

        appGraph.verify(
            extraTypes = frameworkTypes,
            // `single { createBaseToolRegistry(get()) }` 的主类型是 ToolRegistry，静态校验会去读它的构造器，
            // 把工厂函数就地组装好的 `tools: List<Tool>` 当成待注入依赖。这类要走近 injections 声明
            // （实测放进 extraTypes 不生效）；extraTypes 只对工厂 lambda 里的 get<T>() 生效。
            injections = injectedParameters(
                definition<ToolRegistry>(List::class),
            ),
        )
    }

    private companion object {
        /**
         * 框架/非 Koin 类型，登记后跳过：
         * - `HttpClientEngine`：`createHttpClient(json)` 的引擎由 Ktor 按平台构件自选，永远不会有 Koin 定义。
         * - `Function0`：DI 里 `timeProvider = { currentTimeMillis() }` 是就地写的 SAM lambda，静态校验会把它
         *   读成待注入类型。**这里只能整体放行**：`GlobalTokenCounter` / `TokenMeter` 是 `:shared` 的 `internal`
         *   类，跨模块点不到名，没法像 `:shared` 那条校验一样按定义逐条声明（那边已逐条钉住）。
         *   代价：本条对"函数字面量型注入漏注册"不敏感，那条精度由 `:shared` 的校验负责。
         */
        val frameworkTypes: List<KClass<*>> = listOf(
            HttpClientEngine::class,
            Function0::class,
        )
    }
}
