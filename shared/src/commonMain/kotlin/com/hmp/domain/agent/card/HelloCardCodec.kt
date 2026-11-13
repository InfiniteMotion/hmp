package com.hmp.domain.agent.card

import kotlinx.serialization.json.Json

/**
 * 卡片内容 JSON 编解码。
 *
 * 2026-11-11：从 `HelloSubAgent` 的私有实现抽出 —— 卡片恢复不再依赖 agent 是否启动，
 * `HelloCardsViewModel` 直接从 `hello_card_cache` 读缓存时用同一套解析。
 * 配置必须与 `HelloSubAgent` 内的 `json` 保持一致（ignoreUnknownKeys / coerceInputValues / isLenient）。
 */
internal object HelloCardCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun decode(type: SlideType, raw: String): SlideContent? {
        if (raw.isBlank()) return null
        return runCatching {
            when (type) {
                SlideType.ANCHOR -> json.decodeFromString(AnchorContent.serializer(), raw)
                SlideType.RADIO_STATUS -> json.decodeFromString(RadioStatusContent.serializer(), raw)
                SlideType.GREETING -> json.decodeFromString(GreetingContent.serializer(), raw)
                SlideType.RECOMMEND -> json.decodeFromString(RecommendContent.serializer(), raw)
                SlideType.DISCOVER -> json.decodeFromString(DiscoverContent.serializer(), raw)
                SlideType.FORGOTTEN -> json.decodeFromString(ForgottenContent.serializer(), raw)
                SlideType.ANNIVERSARY -> json.decodeFromString(AnniversaryContent.serializer(), raw)
                SlideType.ENRICH_TRACKING -> json.decodeFromString(EnrichTrackingContent.serializer(), raw)
                SlideType.NARRATIVE -> json.decodeFromString(NarrativeContent.serializer(), raw)
            }
        }.getOrNull()
    }
}
