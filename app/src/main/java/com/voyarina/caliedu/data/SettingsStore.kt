package com.voyarina.caliedu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.voyarina.caliedu.ui.UiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

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

/**
 * 字号档位：90%~130%，每档 5%。
 *
 * 刻度全用整数百分比算：`(1.3f - 0.9f) / 0.05f` 在浮点里是 7.999…，
 * 取整会掉成 7，档位就变成每格 5.7%——110% 这一档直接被跳过。
 */
object TextScale {
    const val MIN_PERCENT = 90
    const val MAX_PERCENT = 130
    const val STEP_PERCENT = 5

    const val MIN = MIN_PERCENT / 100f
    const val MAX = MAX_PERCENT / 100f

    /** Slider 的 steps 是「两端之间还能停几个点」：(130-90)/5 档去掉两头。 */
    val STEPS: Int = (MAX_PERCENT - MIN_PERCENT) / STEP_PERCENT - 1

    fun clamp(scale: Float): Float = scale.coerceIn(MIN, MAX)

    /** 对齐到最近的 5%：滑块和读数是同一个数，不会出现 103%。 */
    fun snap(scale: Float): Float {
        val percent = (percent(clamp(scale)) + STEP_PERCENT / 2f) / STEP_PERCENT
        return clamp(percent.toInt() * STEP_PERCENT / 100f)
    }

    fun percent(scale: Float): Int = (scale * 100).roundToInt()

    fun label(scale: Float): String = "${percent(scale)}%"
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
    private val keyScale = floatPreferencesKey("text_scale")
    private val keyLibrary = stringPreferencesKey("pdf_library")
    private val keyReadingBar = booleanPreferencesKey("reading_toggle_bar")
    private val keyStats = booleanPreferencesKey("records_stats")

    /** 解一次密要进 Keystore，而 DataStore 每次改风格/字号都会重新发一遍，缓存住省这趟。 */
    private var sealed: String? = null
    private var plain: String = ""

    private fun reveal(stored: String): String {
        if (stored == sealed) return plain
        val opened = SecretBox.open(stored)
        sealed = stored
        plain = opened
        return opened
    }

    val flow: Flow<LlmSettings> = context.dataStore.data.map { p ->
        val base = LlmSettings()
        LlmSettings(
            baseUrl = p[keyBase] ?: base.baseUrl,
            apiKey = reveal(p[keyApi] ?: ""),
            model = p[keyModel] ?: base.model,
            mode = ChannelMode.of(p[keyMode]),
            autoGrade = p[keyGrade] ?: true,
            runCode = p[keyRun] ?: false,
        )
    }

    val styleFlow: Flow<UiStyle> = context.dataStore.data.map { p -> UiStyle.of(p[keyStyle]) }

    val themeFlow: Flow<ThemeMode> = context.dataStore.data.map { p -> ThemeMode.of(p[keyTheme]) }

    val scaleFlow: Flow<Float> = context.dataStore.data.map { p -> TextScale.snap(p[keyScale] ?: 1f) }

    val readingBarFlow: Flow<Boolean> = context.dataStore.data.map { p -> p[keyReadingBar] ?: false }

    val statsFlow: Flow<Boolean> = context.dataStore.data.map { p -> p[keyStats] ?: true }

    val libraryFlow: Flow<PdfLibrary> = context.dataStore.data.map { p ->
        val raw = p[keyLibrary]
        if (raw.isNullOrBlank()) PdfLibrary() else runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<PdfLibrary>(raw)
        }.getOrDefault(PdfLibrary())
    }

    suspend fun current(): LlmSettings = flow.first()

    suspend fun currentStyle(): UiStyle = styleFlow.first()

    suspend fun save(settings: LlmSettings) {
        // 加密走 IO：首次生成 Keystore 密钥可能要几十毫秒，别卡在界面上。
        val sealedKey = withContext(Dispatchers.IO) { SecretBox.seal(settings.apiKey.trim()) }
        context.dataStore.edit { p ->
            p[keyBase] = settings.baseUrl.trim()
            p[keyModel] = settings.model.trim()
            p[keyApi] = sealedKey
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

    suspend fun saveScale(scale: Float) {
        context.dataStore.edit { p -> p[keyScale] = scale }
    }

    suspend fun saveReadingBar(on: Boolean) {
        context.dataStore.edit { p -> p[keyReadingBar] = on }
    }

    suspend fun saveStats(on: Boolean) {
        context.dataStore.edit { p -> p[keyStats] = on }
    }

    suspend fun saveLibrary(library: PdfLibrary) {
        context.dataStore.edit { p -> p[keyLibrary] = Json.encodeToString(library) }
    }
}
