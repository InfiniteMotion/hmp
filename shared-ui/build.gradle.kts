plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library")
    // Res.* 访问器生成配套：带入的组件依赖与下方手动依赖同坐标同版本，自动去重
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // AGP 9 内置 Kotlin 支持的新式 KMP 库 DSL（与 :shared 模块同模式）
    android {
        namespace = "com.hearablemusic.player.ui"
        compileSdk { version = release(36) }
        // res 支持（R 类生成）：AGP 9 KMP 库插件默认关闭，需显式 opt-in（kotlinlang.org AGP 9 迁移指南）
        androidResources {
            enable = true
        }
        // 单元测试（host test）：AGP 9 KMP 库插件默认关闭，需 withHostTest 显式 opt-in；
        // 源集目录随之从 androidUnitTest 改名为 androidHostTest
        withHostTest {
        }
        // AGP 9 KMP DSL：属性赋值形式（lambda 形式的 version 字段要 MinSdkVersion 类型，不可用 Int）
        minSdk = 33
    }

    jvm("desktop")

    // 方向 A Phase 1（A1）：iOS targets 落地，与 :shared 模块同模式。
    // 依赖栈（CMP 1.9.3 / navigation3 / koin-compose / coil3 / haze）均具备 iOS 构件。
    // 注：不启用 iosX64 —— org.jetbrains.androidx.navigation3:navigation3-ui 未发布
    // ios_x64 构件（仅 iosArm64/iosSimulatorArm64/macosArm64），声明该 target 会导致
    // compileKotlinIosX64 无法解析依赖；x64 模拟器由 Rosetta 跑 arm64 切片覆盖。
    iosArm64()
    iosSimulatorArm64()

    // CocoaPods 导出已收敛到 :shared-ios（聚合框架 sharedIos，见 shared-ios/README）——
    // 本模块不再单独导出 pod，双框架方案的 duplicate symbol / Koin 全局分裂问题见该模块注释

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.kotlinx.coroutines)
            // Res 访问器运行时（composeResources 配套）
            implementation(compose.components.resources)

            // androidx.compose → Compose Multiplatform（同包名，代码 import 不变）
            // 版本 1.9.3：AGP 9.0 要求 CMP ≥1.9.3（官方兼容矩阵）；material3 独立版本见 libs.versions.toml
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.animation)

            // navigation3 runtime（KMP）
            implementation(libs.androidx.navigation3.runtime)
            // api：AppRoot 的公开类型面（NavDisplay）；desktop 壳接 nav3 返回
            // 需要 navigationevent-compose（本依赖的传递项），implementation 不传递
            api(libs.jetbrains.navigation3.ui)

            // nav3 NavKey @Serializable 序列化支持
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.coil.compose.kmp)        // Coil3 AsyncImage（KMP）
            implementation(libs.haze)                    // 底部融合栏毛玻璃（CMP 兼容库）
            implementation(libs.haze.materials)

            // api：LocalAppViewModelStoreOwner 公开类型 ViewModelStoreOwner 的来源（desktop 壳需触达）
            api(libs.jetbrains.lifecycle.viewmodel.compose)  // ViewModel/viewModelScope（KMP 分发）
            // 注意：libs.koin.compose 的实际坐标是 koin-androidx-compose（纯 Android AAR），
            // commonMain 误用它曾致 desktop JavaCompile 变体解析失败；
            // KMP 版（org.koin.compose.*）在 koin-compose-multiplatform alias 下
            implementation(libs.koin.compose.multiplatform)
            implementation(libs.koin.compose.viewmodel)   // koinViewModel()（KMP）

            // NavDisplay 的 ViewModel decorator
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)

            // Kermit — ChatViewModel/ChatAgentGateway 日志
            api(libs.kermit)
        }

        androidMain.dependencies {
            implementation(project(":shared"))
            api(project(":android:core-player"))

            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)

            implementation(libs.jetbrains.compose.ui.tooling.preview)

            // media3：@UnstableApi 注解（MusicControllerPlaybackAdapter）
            implementation(libs.androidx.media3.common)

            // Android 端网络图片加载（coil3 ktor 网络引擎注册；coil 核心 API 在 commonMain）
            implementation(libs.coil.network.ktor3)

            implementation(libs.koin.android)

            // Material3 Adaptive → CMP 分发版
            implementation(libs.jetbrains.material3.adaptive)
            implementation(libs.jetbrains.material3.adaptive.layout)
            implementation(libs.jetbrains.material3.adaptive.navigation)

            // 原为 debugImplementation，KMP 源集无 debug 变体，并入 androidMain
            implementation(libs.jetbrains.compose.ui.tooling)
        }

        // androidHostTest 源集无预生成访问器，用 getByName（AGP 9 KMP）
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.mockk)
            implementation(libs.kotlinx.coroutines.test)
        }

        // desktop actual 所需（skiko 解码 PlatformImage.desktop 用；
        // app 壳重复引入时 Gradle 按版本去重）
        // 命名 target（jvm("desktop")）的源集访问器需 by getting（与 :shared 同模式）
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                // PlaybackController 桌面适配器委托 FFmpeg 引擎
                // （与 androidMain 的 api(project(":android:core-player")) 同模式）
                api(project(":desktop:core-player"))
            }
        }
    }
}

// composeResources 资源访问器 public 化（<包名>.generated.resources），供 app 壳引用
compose.resources {
    publicResClass = true
    packageOfResClass = "com.hearablemusic.player.ui.generated.resources"
}
