package com.iceflow.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.iceflow.data.model.AppLanguage

/** 当前语言，由 [IceFlowTheme] 提供；修改设置后立即生效。 */
val LocalLanguage = staticCompositionLocalOf { AppLanguage.SIMPLIFIED_CHINESE }

/** 文案查表：按当前语言取字符串，缺失时回退到简体中文。 */
@Composable
@ReadOnlyComposable
fun strings(): AppStrings = AppStrings.forLanguage(LocalLanguage.current)

/** 按指定语言取文案，用于在预览或导出时绕过 CompositionLocal。 */
fun stringsOf(language: AppLanguage): AppStrings = AppStrings.forLanguage(language)

/** 便捷访问当前语言文案：`T("tab.home")`。 */
@Composable
@ReadOnlyComposable
fun T(key: String): String = strings().get(key)

/** 带格式参数的文案，例如 `Tf("groups.members", count)`。 */
@Composable
@ReadOnlyComposable
fun Tf(key: String, vararg args: Any?): String = strings().format(key, *args)

/** 界面语言相关的格式化文案。 */
class AppStrings private constructor(val language: AppLanguage) {

    private val table = stringTable[language] ?: fallbackStrings

    fun get(key: String): String = table[key] ?: fallbackStrings[key] ?: key

    fun format(key: String, vararg args: Any?): String = runCatching { String.format(get(key), *args) }.getOrDefault(get(key))

    companion object {
        private val cache = mutableMapOf<AppLanguage, AppStrings>()

        fun forLanguage(language: AppLanguage): AppStrings = cache.getOrPut(language) { AppStrings(language) }
    }
}
