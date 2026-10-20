package com.hmp.data.util

import com.hmp.log.HmpLog
import com.hmp.log.LogTag

/**
 * 密钥读写的可注入门面（D7-01 的动作②）。
 *
 * 为什么要多这一层，而不是各处直接调 `SecureStorageHelper`：
 * 1. `SecureStorageHelper` 是 `expect object`，测试里**换不掉** —— 于是"解不开怎么办"这条分支
 *    从来没被测过，而它恰恰是 Android 换机后的常态路径（见下）；
 * 2. 更要紧的是：在 JVM 测试里调它会**读写开发者机器上真实的 `~/.hmp/keystore.p12`**
 *    （`SecureStorageHelper.desktop.kt` 的路径是写死的 `user.home`）。已有的
 *    `SettingsRepositoryImplTest.saveCustomAiConfig` 就是这么跑的 —— 测试环境不该碰真密钥。
 *
 * ## 为什么"解不开"是必然发生而不是罕见异常
 *
 * Android 的 `allowBackup=true` + `backup_rules.xml` 把 DataStore（内含 `custom_ai_api_key`
 * 与各 Agent 的 `agent_apikey_*` **密文**）一起随云备份 / 设备迁移带走；
 * 而加密密钥在 **AndroidKeyStore 里、不可导出、不随备份迁移**。
 * 所以换机之后：密文回来了、密钥没有 → 解密必抛。抛到 `viewModelScope.launch` 里
 * 就是打开 AI 页即崩溃，而用户唯一的自救办法是清应用数据。
 * 三端里只有 `getAgentEndpointConfig` 用了 `runCatching`，另两处是裸调 —— 同文件两种写法，
 * 说明作者知道会失败，只是漏了两处。
 */
interface SecureSecrets {

    fun encrypt(plainText: String): String

    /**
     * 解不开返回 **null**（绝不抛）。
     *
     * 返回值区分三态，是为了让 UI 能问对问题：
     * `null` = 存过但解不开（"请重新填写 API Key"）；`""` = 存过、确实是空；非空 = 正常。
     * 少了这一层区分，"用户存了空 key"与"迁移后解不开"会长得一模一样。
     */
    fun decryptOrNull(encrypted: String): String?
}

/** 生产实现：包一层各端的 `SecureStorageHelper`，失败记 warn 后回 null。 */
object PlatformSecureSecrets : SecureSecrets {

    override fun encrypt(plainText: String): String = SecureStorageHelper.encrypt(plainText)

    override fun decryptOrNull(encrypted: String): String? =
        runCatching { SecureStorageHelper.decrypt(encrypted) }.getOrElse {
            // 只记 warn 不抛：这不是数据损坏，是"换机后需要重填"这一预期路径
            HmpLog.w(LogTag.DataRepository, it) {
                "密钥解密失败（换机 / 云备份恢复后 Keystore 密钥不迁移，属预期路径），需用户重填：${it.message}"
            }
            null
        }
}
