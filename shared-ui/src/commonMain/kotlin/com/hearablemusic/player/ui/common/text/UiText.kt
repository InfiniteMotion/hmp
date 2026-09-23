package com.hearablemusic.player.ui.common.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 非 composable 作用域的文本载体：只携带「引用 + 参数」，解析推迟到组合期。
 *
 * **为什么需要它**
 *
 * Compose 的 `stringResource` 依赖组合环境（`Locale` / 主题 / 密度），只能在 composable 内调用。
 * 而非 composable 侧唯一的口是挂起 `getString`，它走的是**系统**环境、且结果一经调用即被冻结：
 * 一旦将来引入应用内语言覆盖（覆盖 Compose `Locale`），`stringResource` 会跟随、`getString` 不会，
 * 同一屏就会出现两种语言。因此凡需跨非 composable 边界携带的文本，一律用它承载。
 *
 * **用法**
 * ```
 * data class Item(val title: UiText)                 // 数据类 / 枚举 / 顶层常量携带
 * Item(Res.string.foo.asUiText(n))                   // 资源 + 位置参数
 * Item(UiText.Raw(llmOutput))                        // 不可译原文
 *
 * // 渲染端（唯一解析场所）：
 * Text(item.title.asString())
 * ```
 *
 * **约定**
 * - 参数随引用一起走，**不要在非 composable 侧预拼接**文本（语序、复数、量词随语言变化）。
 * - 参数可嵌套 [UiText]，解析时先递归展开（如「为你推荐 · %1$s」的 `%1$s` 是另一段本地化文本）。
 * - 一次性副作用（toast / 交给平台 API）仍用挂起 `getString` 即可，不必包一层。
 *
 * @see asString
 */
sealed interface UiText {

    /** 资源引用 + 位置参数（对应 `stringResource(res, *args)`）。 */
    data class Res(val res: StringResource, val args: List<Any> = emptyList()) : UiText

    /** 不可译原文（LLM 输出、歌名/歌手名、endpoint、用户输入等）。 */
    data class Raw(val text: String) : UiText
}

/** 把资源引用包装成 [UiText]，可带位置参数：`Res.string.foo.asUiText(count)`。 */
fun StringResource.asUiText(vararg args: Any): UiText = UiText.Res(this, args.toList())

/**
 * 解析为最终字符串。
 *
 * **这是唯一允许的解析场所**（组合期），因此只在 composable 内可用。参数中若嵌套 [UiText]，
 * 会先递归展开再作为格式化参数传入。
 */
@Composable
fun UiText.asString(): String {
    if (this is UiText.Raw) return text
    val resText = this as UiText.Res
    if (resText.args.isEmpty()) return stringResource(resText.res)
    val resolved = ArrayList<Any>(resText.args.size)
    for (arg in resText.args) {
        resolved += if (arg is UiText) arg.asString() else arg
    }
    return stringResource(resText.res, *resolved.toTypedArray())
}
