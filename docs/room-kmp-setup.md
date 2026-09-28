# Room KMP 配置经验总结

> 本文档总结了在 Kotlin Multiplatform Mobile (KMM) 项目中配置 Room 数据库的经验和最佳实践。

## 概述

Room 2.7+ 支持 Kotlin Multiplatform，但在配置过程中会遇到一些平台差异和编译问题。本文档基于 HMP 项目的实际配置经验，提供一套可行的配置方案。

## 环境要求

- Kotlin: 2.2.21+
- Room: 2.8.3+
- KSP: 2.2.21+
- Gradle: 9.0+

## 核心配置

### 1. build.gradle.kts 配置

```kotlin
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

    iosX64()
    iosArm64()
    iosSimulatorArm64()

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
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            // ... 其他依赖
        }
        androidMain.dependencies {
            // Android 特定依赖
        }
        iosMain.dependencies {
            // iOS 特定依赖
        }
    }
}

// KSP 配置 - 关键：不要在 commonMainMetadata 上运行 KSP
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
```

**关键要点**：
- ❌ 不要添加 `kspCommonMainMetadata` - 会导致 expect/actual 冲突
- ✅ 只为具体平台添加 KSP 依赖

### 2. Database 实体定义 (commonMain)

```kotlin
// commonMain/kotlin/com/hmp/data/database/AppDatabase.kt
package com.hmp.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

// expect object 声明 - 带 override 方法签名
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@ConstructedBy(AppDatabaseConstructor::class)
@Database(
    entities = [
        Music::class,
        MusicExtra::class,
        // ... 其他实体
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(LabelConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    // ... 其他 DAO
}

// 数据库构建工厂函数
expect fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase
```

**关键要点**：
- `expect object` 必须声明 `override fun initialize()` 方法
- 使用 `@ConstructedBy` 注解指向构造函数对象
- 同时提供 `getRoomDatabase()` 工厂函数用于自定义配置

### 3. Android 实现 (androidMain)

```kotlin
// androidMain/kotlin/com/hmp/data/database/DatabaseBuilder.android.kt
package com.hmp.data.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val dbFile = context.getDatabasePath("music_database.db")
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = dbFile.absolutePath
    )
}

actual fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
```

**注意**：Android 不需要手动实现 `AppDatabaseConstructor`，KSP 会自动生成。

### 4. iOS 实现 (iosMain)

```kotlin
// iosMain/kotlin/com/hmp/data/database/DatabaseBuilder.ios.kt
package com.hmp.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = documentDirectory() + "/music_database.db"
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath
    )
}

@OptIn(ExperimentalForeignApi::class)
actual fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
```

## 常见问题与解决方案

### 问题 1: expect/actual 冲突

**错误信息**：
```
AppDatabaseConstructor: expect and corresponding actual are declared in the same module.
```

**原因**：`kspCommonMainMetadata` 在 commonMain 中生成了 actual 实现，与 expect 声明冲突。

**解决方案**：
```kotlin
// 只配置平台特定的 KSP，不要配置 kspCommonMainMetadata
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
}
```

### 问题 2: iOS 平台要求 @ConstructedBy

**错误信息**：
```
The @Database class must be annotated with @ConstructedBy since the source is targeting non-Android platforms.
```

**原因**：Room KMP 在非 Android 平台上强制要求使用 `@ConstructedBy` 注解。

**解决方案**：
- 在 commonMain 中声明 `expect object AppDatabaseConstructor`
- 确保 `expect` 声明包含 `override fun initialize()` 方法签名

### 问题 3: expect object 需要实现接口方法

**错误信息**：
```
Object 'AppDatabaseConstructor' is not abstract and does not implement abstract member: fun initialize(): T
```

**原因**：`expect object` 实现 `RoomDatabaseConstructor` 接口时，需要在 commonMain 中声明抽象方法。

**解决方案**：
```kotlin
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
```

## 编译命令

```bash
# Android
./gradlew :shared:compileAndroidMain

# iOS Simulator (Apple Silicon)
./gradlew :shared:compileKotlinIosSimulatorArm64

# iOS Device
./gradlew :shared:compileKotlinIosArm64

# iOS Simulator (Intel)
./gradlew :shared:compileKotlinIosX64

# 全部平台
./gradlew :shared:compileCommonMainKotlinMetadata \
          :shared:compileAndroidMain \
          :shared:compileKotlinIosSimulatorArm64 \
          :shared:compileKotlinIosArm64 \
          :shared:compileKotlinIosX64
```

## 最佳实践

1. **使用 BundledSQLiteDriver**：跨平台兼容的 SQLite 驱动
2. **设置查询协程上下文**：使用 `Dispatchers.Default` 避免阻塞主线程
3. **破坏性迁移策略**：KMP 中建议使用 `fallbackToDestructiveMigration()`
4. **统一数据库路径**：iOS 使用 `NSDocumentDirectory`，Android 使用 `getDatabasePath()`
5. **版本管理**：启用 `schemaDirectory` 进行数据库版本管理

## 相关文档

- [Room KMP 官方文档](https://developer.android.com/kotlin/multiplatform/room)
- [KSP 官方文档](https://kotlinlang.org/docs/ksp-overview.html)
- [SQLite KMP 驱动](https://developer.android.com/kotlin/multiplatform/sqlite)

---

*文档创建时间：2026-04-24*  
*基于 HMP v5.10 项目经验总结*
