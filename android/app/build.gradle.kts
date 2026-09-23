import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hearablemusic.player"
    compileSdk {
        version = release(37)
    }

    // 统一签名配置 — 优先 keystore.properties，回退到环境变量/Gradle属性
    val ksPropsFile = rootProject.file("keystore.properties")
    val ksProps = if (ksPropsFile.exists()) Properties().apply { load(FileInputStream(ksPropsFile)) } else null

    signingConfigs {
        create("unified") {
            storeFile = file(ksProps?.getProperty("storeFile")
                ?: System.getenv("KEYSTORE_FILE")
                ?: "hmp-unified-key.jks")
            storePassword = ksProps?.getProperty("storePassword")
                ?: System.getenv("KEYSTORE_PASSWORD")
                ?: providers.gradleProperty("KEYSTORE_PASSWORD").orNull
            keyAlias = ksProps?.getProperty("keyAlias")
                ?: System.getenv("KEY_ALIAS")
                ?: providers.gradleProperty("KEY_ALIAS").getOrElse("hmpkey")
            keyPassword = ksProps?.getProperty("keyPassword")
                ?: System.getenv("KEY_PASSWORD")
                ?: providers.gradleProperty("KEY_PASSWORD").orNull
        }
    }

    defaultConfig {
        applicationId = "com.hearablemusic.player"
        minSdk = 33
        targetSdk = 37
        versionCode = project.findProperty("hmp.versionCode")?.toString()?.toIntOrNull() ?: 51000
        versionName = project.findProperty("hmp.versionName")?.toString() ?: "5.10.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 内置 AI API Key（免费体验 / 付费模式共用）
        // 本地开发: 在 gradle.properties 中填写（不提交 Git）
        // CI 构建: GitHub Actions secrets 自动注入
        val builtInEndpoint = providers.gradleProperty("BUILT_IN_AI_ENDPOINT").getOrElse("https://api.deepseek.com/v1")
        val builtInKey = providers.gradleProperty("BUILT_IN_AI_API_KEY").getOrElse("")
        val builtInModel = providers.gradleProperty("BUILT_IN_AI_MODEL").getOrElse("deepseek-chat")
        buildConfigField("String", "BUILT_IN_AI_ENDPOINT", "\"$builtInEndpoint\"")
        buildConfigField("String", "BUILT_IN_AI_API_KEY", "\"$builtInKey\"")
        buildConfigField("String", "BUILT_IN_AI_MODEL", "\"$builtInModel\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("unified")
        }
        debug {
            signingConfig = signingConfigs.getByName("unified")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    //noinspection WrongGradleMethod
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // AGP 9 KMP + CMP 双打包修复：composeResources 同时进 APK 根（Java resources）
    // 与 assets/composeResources（CMP 在 Android 的运行时读取路径），净增 ~12.6MB。
    // 排除根级重复份；shared 模块的 icons/ pinyindb/ 走 classLoader 读取，不可排除。
    packaging {
        resources {
            excludes += "composeResources/**"
        }
    }
}

dependencies {

    implementation(project(":shared"))
    implementation(project(":shared-ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // 依赖替换（方案 §2.2）：app 壳同步对齐 CMP 1.8.2，避免 BOM 拉高 compose 版本与共享层冲突
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.androidx.media3.common)

    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
