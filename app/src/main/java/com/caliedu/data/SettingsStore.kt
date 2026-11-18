package com.caliedu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.caliedu.ui.UiStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 模型怎么接：直连（填 Key）或手动接力（复制提示词、粘回 JSON）。 */
enum class ChannelMode(val key: String, val label: String, val note: String) {
    DIRECT("direct", "直连", "填 Key，App 自己发请求"),
    RELAY("relay", "手动接力", "复制提示词到任意模型，把 JSON 粘回来"),
    ;

    companion object {
        fun of(key: String?): ChannelMode = entries.firstOrNull { it.key == key } ?: DIRECT
    }
}

/** 深浅色：跟随系统 / 固定浅 / 固定深。 */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    ;

    companion object {
        fun of(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
        fun next(mode: ThemeMode): ThemeMode = entries[(mode.ordinal + 1) % entries.size]
    }
}

data class LlmSettings(
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val mode: ChannelMode = ChannelMode.DIRECT,
    val autoGrade: Boolean = true,
    val runCode: Boolean = false,
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    /** 这一课能不能真的调模型（直连要 Key，接力只要有手）。 */
    val canTalk: Boolean get() = mode == ChannelMode.RELAY || configured

    /** 能不能用它批改开放题——接力模式下每判一题都要人工转一趟，不值得。 */
    val canGrade: Boolean get() = canTalk && autoGrade && mode == ChannelMode.DIRECT
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val keyBase = stringPreferencesKey("base_url")
    private val keyModel = stringPreferencesKey("model")
    private val keyApi = stringPreferencesKey("api_key")
    private val keyStyle = stringPreferencesKey("ui_style")
    private val keyMode = stringPreferencesKey("channel_mode")
    private val keyGrade = booleanPreferencesKey("auto_grade")
    private val keyRun = booleanPreferencesKey("run_code")
    private val keyTheme = stringPreferencesKey("theme_mode")

    val flow: Flow<LlmSettings> = context.dataStore.data.map { p ->
        val base = LlmSettings()
        LlmSettings(
            baseUrl = p[keyBase] ?: base.baseUrl,
            apiKey = p[keyApi] ?: "",
            model = p[keyModel] ?: base.model,
            mode = ChannelMode.of(p[keyMode]),
            autoGrade = p[keyGrade] ?: true,
            runCode = p[keyRun] ?: false,
        )
    }

    val styleFlow: Flow<UiStyle> = context.dataStore.data.map { p -> UiStyle.of(p[keyStyle]) }

    val themeFlow: Flow<ThemeMode> = context.dataStore.data.map { p -> ThemeMode.of(p[keyTheme]) }

    suspend fun current(): LlmSettings = flow.first()

    suspend fun currentStyle(): UiStyle = styleFlow.first()

    suspend fun save(settings: LlmSettings) {
        context.dataStore.edit { p ->
            p[keyBase] = settings.baseUrl.trim()
            p[keyModel] = settings.model.trim()
            p[keyApi] = settings.apiKey.trim()
            p[keyMode] = settings.mode.key
            p[keyGrade] = settings.autoGrade
            p[keyRun] = settings.runCode
        }
    }

    suspend fun saveStyle(style: UiStyle) {
        context.dataStore.edit { p -> p[keyStyle] = style.key }
    }

    suspend fun saveTheme(mode: ThemeMode) {
        context.dataStore.edit { p -> p[keyTheme] = mode.key }
    }
}
