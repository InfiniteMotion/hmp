package com.hmp.test.fakes

import com.hmp.data.util.SecureSecrets

/**
 * 可注入的密钥门面替身（D7-01 的动作②带来的能力）。
 *
 * 两件事它要办：
 * 1. **让单测不再碰真密钥** —— Desktop 的 `SecureStorageHelper` 读写
 *    `~/.hmp/keystore.p12`（路径写死在 `user.home`），不打桩就等于拿开发者机器上的真 keystore 跑测试；
 * 2. **让"解不开"这条分支第一次可测** —— 换机 / 云备份恢复后 Android 必然走到这里，
 *    而 `SecureStorageHelper` 是 `expect object`，测试里换不掉，所以那条崩溃路径一直没人看过。
 *
 * @param prefix "密钥还在这台设备上"时用的可逆变换前缀
 * @param keyLost true = 模拟密钥已经不在了（换机后）：任何密文都解不开
 */
class FakeSecureSecrets(
    private val prefix: String = "enc:",
    private val keyLost: Boolean = false,
) : SecureSecrets {

    var decryptCalls = 0
        private set

    override fun encrypt(plainText: String): String = prefix + plainText

    override fun decryptOrNull(encrypted: String): String? {
        decryptCalls++
        if (keyLost) return null
        return if (encrypted.startsWith(prefix)) encrypted.removePrefix(prefix) else null
    }
}
