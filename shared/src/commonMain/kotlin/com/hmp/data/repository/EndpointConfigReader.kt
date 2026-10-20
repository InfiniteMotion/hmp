package com.hmp.data.repository

import com.hmp.data.util.SecureSecrets
import com.hmp.domain.setting.model.AiEndpointConfig

/**
 * 从 DataStore 里的三段值拼出一个 `AiEndpointConfig`（D7-01）。
 *
 * 为什么会有这一个函数：三端各自的 `SettingsRepositoryImpl` 里有 **9 处**同样的"读 endpoint /
 * 解密密钥 / 拼 config"代码，而它们的错误处理**不一致** —— 只有 `getAgentEndpointConfig` 包了
 * `runCatching`，`customAiConfig` 与 `getCustomAiConfig` 是裸调 `decrypt`。
 * 于是同一次换机恢复，有的入口崩、有的静默回空。收成一份之后，"解不开怎么办"只有一处答案，
 * 三端不可能再各写一套（`SecureSecrets` 负责"不抛"，这里负责"三态怎么折成字段"）。
 *
 * @param encryptedKey DataStore 里存的密文；`null` 表示这个槽位从来没存过。
 */
internal fun readEndpointConfig(
    endpoint: String,
    encryptedKey: String?,
    model: String,
    secrets: SecureSecrets,
): AiEndpointConfig {
    val decrypted = encryptedKey?.let { secrets.decryptOrNull(it) }
    val apiKey = decrypted.orEmpty()
    return AiEndpointConfig(
        endpoint = endpoint,
        apiKey = apiKey,
        selectedModel = model,
        isConfigured = endpoint.isNotBlank() && apiKey.isNotBlank(),
        // 「存过密文」且「解不开」才是要重填；存过但解出空串是真·空 key，不该提示
        keyNeedsRefill = encryptedKey != null && decrypted == null,
    )
}
