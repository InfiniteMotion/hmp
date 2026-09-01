plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
    kotlin("native.cocoapods")
}

kotlin {
    android {
        namespace = "com.hmp.shared"
        compileSdk { version = release(36) }
    }

    jvm("desktop")

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.addAll(listOf("-Xexpect-actual-classes"))
    }

    cocoapods {
        summary = "HMP shared module"
        homepage = "https://github.com/hmp/shared"
        version = "1.0.0"
        ios.deploymentTarget = "16.0"
        podfile = project.file("../ios/Podfile")
        framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.ktor.core)
            implementation(libs.ktor.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.logging)
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.ktor.okhttp)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.jaudiotagger)
            implementation("com.belerweb:pinyin4j:2.5.1")
        }
        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.java)
                implementation(libs.jaudiotagger)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        val desktopTest by getting {
            dependencies {
                // room-testing 仅桌面 JVM 使用（迁移测试在 desktopTest 源集）；
                // 不能进 commonTest——room-migration 未发布 iosArm64 目标，会破坏 iOS 测试编译元数据解析
                // （androidx.room:room-migration-iosarm64:2.8.3 不存在，IDE 同步 transform*ForIde 会失败）
                implementation(libs.androidx.room.testing)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            // ktor-client-mock 为完整 KMP（iosArm64/iossimulatorarm64 构件齐全），可留在 commonTest
            implementation(libs.ktor.client.mock)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
}

room {
    // exportSchema=true（AppDatabase @Database）：KSP 导出 schemas/<fqn>/{1,2}.json，
    // 供 desktopTest 的 MigrationTestHelper（room-testing）跑 v1→v2 迁移测试。
    // 模块相对路径避免与 copyIconsToIos 隐式输出重叠校验冲突。
    schemaDirectory("schemas")
}

// Copy shared icons to iOS project bundle resources
// 源目录 shared/src/commonMain/resources/icons 当前不存在（历史遗留），任务保持空转；
// 仅确保 iOS 编译任务链的输入/输出位置合法（绝对路径会触发隐式依赖校验失败）
val copyIconsToIos by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("src/commonMain/resources/icons"))
    into(layout.projectDirectory.dir("../ios/HMP/HMP/icons"))
}

tasks.matching { it.name == "compileKotlinIosSimulatorArm64" }.configureEach {
    finalizedBy(copyIconsToIos)
}
tasks.matching { it.name == "compileKotlinIosArm64" }.configureEach {
    finalizedBy(copyIconsToIos)
}
tasks.matching { it.name == "compileKotlinIosX64" }.configureEach {
    finalizedBy(copyIconsToIos)
}