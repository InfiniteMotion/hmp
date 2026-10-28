package com.hmp.di

import com.hmp.data.di.sharedModule
import com.hmp.domain.agent.infra.PresenceBus
import com.hmp.domain.agent.policy.PolicyGuard
import com.hmp.domain.agent.port.AgentKeepAlivePort
import com.hmp.domain.agent.port.NowPlayingContextProvider
import com.hmp.domain.agent.port.PlaybackCommandPort
import com.hmp.domain.agent.runtime.GlobalTokenCounter
import com.hmp.domain.agent.runtime.MasterAgent
import com.hmp.domain.agent.runtime.TokenMeter
import com.hmp.domain.agent.tool.spec.ToolRegistry
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * DI 模块图校验（X-04 / X-闸）。
 *
 * 此前全仓没有任何图校验（`checkModules`/`verify()` 0 命中）：新加一条 `single` 写错类型或漏注入，
 * 编译与既有单测都不报，只有跑到那个注入点才炸 —— 而 iOS 是纯懒解析，表现为"进页面才崩"。
 *
 * 用 `verifyAll`（静态结构校验）而不是 `checkModules`：后者会**实例化每条定义**，而
 * `desktopPlatformModule` 里有 `single<AppDatabase> { getRoomDatabase(getDatabaseBuilder()) }`
 * 和 DataStore —— 在测试里跑就等于打开开发者真实的 `~/.hmp` 库（还会触发迁移）。
 * `verifyAll` 只比对"每条定义的依赖有没有人提供"，一条定义都不执行，因此没有副作用。
 *
 * 两个模块一起验：`sharedModule` 要 DAO/仓库，平台模块要 UseCase/Transport，单验一个会把对方报成缺失。
 */
class KoinGraphVerificationTest {

    @Test
    fun sharedAndDesktopGraphResolveWithoutHoles() {
        // 用 includes 展平成一个图再验，而不是 listOf(a, b).verifyAll()：
        // verifyAll 按各模块的**主类型**建索引，看不见 `singleOf(::MusicRepositoryImpl) bind MusicRepository::class`
        // 这种次类型注册（实测把 MusicRepository 报成缺失，而 includes 展平后即可通过）。
        // 平台模块里仓库、端口一半以上是 bind 出来的，走 verifyAll 就等于把那半边关掉。
        val desktopGraph = module { includes(sharedModule, desktopPlatformModule) }

        desktopGraph.verify(
            extraTypes = externalBeans + frameworkTypes,
            // timeProvider 在 DI 里是就地写的 SAM lambda（`{ currentTimeMillis() }`），
            // 静态校验会把它读成一条需要注入的 kotlin.Function0。按定义逐条声明，
            // 不用 extraTypes 全局放行 Function0 —— 那样以后任何真的漏注入的函数字面量都会被跳过。
            injections = injectedParameters(
                definition<GlobalTokenCounter>(Function0::class),
                definition<TokenMeter>(Function0::class),
                definition<MasterAgent>(Function0::class),
            ),
        )
    }

    private companion object {
        /**
         * 由**别的编译单元**注册的类型 —— `:shared` 看不到它们的注册点，所以只能显式登记。
         *
         * 这不等于没验：完整装配图在 shared-ui 里各验一条 —— `DesktopKoinGraphVerificationTest`
         * （sharedModule + desktopPlatformModule + desktopPlayerModule + desktopUiModule）与
         * `AndroidAppKoinGraphVerificationTest`（sharedModule + androidPlatformModule + playerModule + uiModule）。
         * 前五个由 commonMain 的 `chatGatewayModule` 提供、被两端各自 `includes`，在那两条上真检查；
         * `AgentKeepAlivePort` 由 `playerModule` 静态注册，也在 Android 那条上真验到。
         * 本条的职责是把**领域/数据层自身**的图钉住（它不该依赖 UI 是否存在）。
         */
        val externalBeans: List<KClass<*>> = listOf(
            ToolRegistry::class,
            PolicyGuard::class,
            PresenceBus::class,
            PlaybackCommandPort::class,
            NowPlayingContextProvider::class,
            AgentKeepAlivePort::class,
        )

        /**
         * 不经 Koin 的框架类型：`createHttpClient(json)` 里的引擎由 Ktor 自己按平台构件挑
         * （desktop 走 CIO/OkHttp），不是谁注入进来的，所以它永远不会有 Koin 定义。
         */
        val frameworkTypes: List<KClass<*>> = listOf(
            HttpClientEngine::class,
        )
    }
}
