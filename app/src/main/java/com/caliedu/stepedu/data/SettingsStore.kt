package com.caliedu.stepedu.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class LlmSettings(
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val keyBase = stringPreferencesKey("base_url")
    private val keyModel = stringPreferencesKey("model")
    private val keyApi = stringPreferencesKey("api_key")

    val flow: Flow<LlmSettings> = context.dataStore.data.map { p ->
        LlmSettings(
            baseUrl = p[keyBase] ?: LlmSettings().baseUrl,
            apiKey = p[keyApi] ?: "",
            model = p[keyModel] ?: LlmSettings().model,
        )
    }

    suspend fun current(): LlmSettings = flow.first()

    suspend fun save(settings: LlmSettings) {
        context.dataStore.edit { p ->
            p[keyBase] = settings.baseUrl.trim()
            p[keyModel] = settings.model.trim()
            p[keyApi] = settings.apiKey.trim()
        }
    }
}
