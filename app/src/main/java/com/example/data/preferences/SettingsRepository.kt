package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pocketmind_settings")

enum class PreferredBackend(val displayName: String) {
    AUTO("Auto (Best Available)"),
    GEMINI_NANO("Gemini Nano (AICore)"),
    LITERT_LM("LiteRT-LM (.litertlm)"),
    EXTRACTIVE_NLP("Built-in Extractive NLP"),
}

enum class AcceleratorMode(val label: String) {
    GPU("GPU (OpenCL)"),
    CPU("CPU (Portable)"),
}

data class UserPreferences(
    val strictOfflineMode: Boolean = false,
    val autoSyncWhenOnline: Boolean = true,
    val wifiOnlySync: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
    val preferredBackend: PreferredBackend = PreferredBackend.AUTO,
    val acceleratorMode: AcceleratorMode = AcceleratorMode.GPU,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val systemPrompt: String = "You are PocketMind, a concise, privacy-first AI assistant running completely offline on the user's Android device.",
    val activeLiteRtModelPath: String = "",
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val STRICT_OFFLINE = booleanPreferencesKey("strict_offline_mode")
        val AUTO_SYNC_ONLINE = booleanPreferencesKey("auto_sync_when_online")
        val WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")
        val LAST_SYNC_TS = longPreferencesKey("last_sync_timestamp")
        val PREFERRED_BACKEND = stringPreferencesKey("preferred_backend")
        val ACCELERATOR_MODE = stringPreferencesKey("accelerator_mode")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = floatPreferencesKey("top_p")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val ACTIVE_LITERT_MODEL = stringPreferencesKey("active_litert_model_path")
    }

    val preferencesFlow: Flow<UserPreferences> = context.settingsDataStore.data.map { prefs ->
        UserPreferences(
            strictOfflineMode = prefs[Keys.STRICT_OFFLINE] ?: false,
            autoSyncWhenOnline = prefs[Keys.AUTO_SYNC_ONLINE] ?: true,
            wifiOnlySync = prefs[Keys.WIFI_ONLY_SYNC] ?: true,
            lastSyncTimestamp = prefs[Keys.LAST_SYNC_TS] ?: 0L,
            preferredBackend = prefs[Keys.PREFERRED_BACKEND]
                ?.let { runCatching { PreferredBackend.valueOf(it) }.getOrNull() }
                ?: PreferredBackend.AUTO,
            acceleratorMode = prefs[Keys.ACCELERATOR_MODE]
                ?.let { runCatching { AcceleratorMode.valueOf(it) }.getOrNull() }
                ?: AcceleratorMode.GPU,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            topK = prefs[Keys.TOP_K] ?: 40,
            topP = prefs[Keys.TOP_P] ?: 0.95f,
            systemPrompt = prefs[Keys.SYSTEM_PROMPT]
                ?: "You are PocketMind, a concise, privacy-first AI assistant running completely offline on the user's Android device.",
            activeLiteRtModelPath = prefs[Keys.ACTIVE_LITERT_MODEL] ?: "",
        )
    }

    suspend fun setStrictOfflineMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.STRICT_OFFLINE] = enabled }
    }

    suspend fun setAutoSyncWhenOnline(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_SYNC_ONLINE] = enabled }
    }

    suspend fun setWifiOnlySync(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.WIFI_ONLY_SYNC] = enabled }
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.settingsDataStore.edit { it[Keys.LAST_SYNC_TS] = timestamp }
    }

    suspend fun setPreferredBackend(backend: PreferredBackend) {
        context.settingsDataStore.edit { it[Keys.PREFERRED_BACKEND] = backend.name }
    }

    suspend fun setAcceleratorMode(mode: AcceleratorMode) {
        context.settingsDataStore.edit { it[Keys.ACCELERATOR_MODE] = mode.name }
    }

    suspend fun updateSampling(temperature: Float, topK: Int, topP: Float) {
        context.settingsDataStore.edit {
            it[Keys.TEMPERATURE] = temperature.coerceIn(0.0f, 1.5f)
            it[Keys.TOP_K] = topK.coerceIn(1, 100)
            it[Keys.TOP_P] = topP.coerceIn(0.1f, 1.0f)
        }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.settingsDataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt }
    }

    suspend fun setActiveLiteRtModelPath(path: String) {
        context.settingsDataStore.edit { it[Keys.ACTIVE_LITERT_MODEL] = path }
    }
}
