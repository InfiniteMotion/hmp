package com.hmp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.hmp.domain.setting.model.AiAccessMode
import com.hmp.domain.setting.model.AiEndpointConfig
import com.hmp.test.fakes.FakeSecureSecrets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * D7-01 判据：换机 / 云备份恢复后"密文回来了、密钥没了"，AI 配置要**降级而不是崩**。
 *
 * 场景是怎么发生的：`allowBackup=true` + `backup_rules.xml` 把 DataStore 整个带走，
 * 里面存着 `custom_ai_api_key` 与各 Agent 的 `agent_apikey_*`（都是密文）；
 * 而 AndroidKeyStore 的密钥**不可导出、不随备份迁移**。于是恢复后解密必抛 ——
 * 修前三处读取点里有两处是裸 `decrypt`（只有 `getAgentEndpointConfig` 包了 `runCatching`），
 * 抛到 `AiSettingsViewModel` 的 `viewModelScope.launch` 上就是打开 AI 页即崩溃，
 * 用户唯一的自救办法是清应用数据。
 *
 * 下面每一例都用**同一个 DataStore 文件、两个不同"密钥状态"的仓库**来复现那一刻：
 * 前一个实例正常保存（写入密文），后一个实例的密钥已经不认这些密文了。
 */
class AiKeyUnreadableTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        // 真 DataStore（临时目录），只把密钥那一层换成替身 —— 读取/降级走的是生产代码
        file = File(Files.createTempDirectory("hmp-aikey-").toFile(), "prefs.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create { file }
    }

    @After
    fun teardown() {
        file.delete()
        file.parentFile?.delete()
    }

    private fun repoWithKeyLost() = SettingsRepositoryImpl(
        dataStore = dataStore,
        json = json,
        secrets = FakeSecureSecrets(keyLost = true),
    )

    private fun repoWithKey() = SettingsRepositoryImpl(
        dataStore = dataStore,
        json = json,
        secrets = FakeSecureSecrets(),
    )

    @Test
    fun customKey_thatCannotBeDecrypted_degradesToEmpty_andSaysRefill() = runTest {
        repoWithKey().saveCustomAiConfig(
            AiEndpointConfig(endpoint = "https://api.test.com", apiKey = "sk-live", selectedModel = "gpt-4")
        )

        val config = repoWithKeyLost().getCustomAiConfig()

        assertEquals("解不开就回空串，不抛", "", config.apiKey)
        assertEquals("非密钥字段不受影响，不该被一起清掉", "https://api.test.com", config.endpoint)
        assertFalse(config.isConfigured)
        assertTrue("存过密文却解不开 = 需要重填（这是给 UI 的那句提示用的）", config.keyNeedsRefill)
    }

    @Test
    fun theFlowPath_degradesTheSameWay() = runTest {
        // 崩溃实际发生在 Flow 这条路上（AiSettingsViewModel collect 的就是它），所以单独走一遍
        repoWithKey().saveCustomAiConfig(AiEndpointConfig(endpoint = "e", apiKey = "sk-live", selectedModel = "m"))

        val config = repoWithKeyLost().customAiConfig.first()

        assertEquals("", config.apiKey)
        assertTrue("Flow 那条路径同样要降级 + 带标记", config.keyNeedsRefill)
    }

    @Test
    fun agentKey_thatCannotBeDecrypted_degradesInsteadOfThrowing() = runTest {
        repoWithKey().saveAgentEndpointConfig(
            "HELLO", AiEndpointConfig(endpoint = "https://api.test.com", apiKey = "sk-agent", selectedModel = "m")
        )

        val config = repoWithKeyLost().getAgentEndpointConfig("HELLO")

        // 这一处原本就包了 runCatching，所以它不崩 —— 但三端从此走同一条路，不再有两种写法
        assertTrue("Agent 那条也要带重填标记", config!!.keyNeedsRefill)
        assertEquals("", config.apiKey)
    }

    @Test
    fun neverStored_isNotReportedAsNeedingRefill() = runTest {
        // 三态的最后一态：没存过 ≠ 存了但解不开。少了这条区分，
        // "用户从来没配过 AI"也会被提示"请重新填写 API Key"
        val fresh = repoWithKeyLost().getCustomAiConfig()

        assertEquals("", fresh.apiKey)
        assertFalse(fresh.keyNeedsRefill)
    }

    @Test
    fun readableKey_isNotReportedAsNeedingRefill() = runTest {
        repoWithKey().saveCustomAiConfig(AiEndpointConfig(endpoint = "e", apiKey = "sk-live", selectedModel = "m"))

        val config = repoWithKey().getCustomAiConfig()

        assertEquals("sk-live", config.apiKey)
        assertFalse("能解密还报需重填，就是把提示变成了噪音", config.keyNeedsRefill)
    }

    @Test
    fun accessModeCustom_stillResolvesWithoutThrowing() = runTest {
        repoWithKey().saveAiAccessMode(AiAccessMode.CUSTOM)
        repoWithKey().saveCustomAiConfig(AiEndpointConfig(endpoint = "e", apiKey = "sk-live", selectedModel = "m"))

        val active = repoWithKeyLost().getActiveAiConfig()

        assertEquals("", active.apiKey)
        assertTrue("取生效配置的入口也不能抛", active.keyNeedsRefill)
    }
}
