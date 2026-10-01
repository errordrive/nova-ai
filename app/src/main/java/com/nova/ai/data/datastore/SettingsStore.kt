package com.nova.ai.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Preferences DataStore backing [SettingsStore]; name "nova_settings". */
private val Context.dataStore by preferencesDataStore(name = "nova_settings")

private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
private val KEY_DEFAULT_PROVIDER_ID = stringPreferencesKey("default_provider_id")
private val KEY_DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
private val KEY_MAX_AGENT_STEPS = intPreferencesKey("max_agent_steps")
private val KEY_STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")

/**
 * App-wide user settings snapshot.
 *
 * @property themeMode One of "system", "light", "dark".
 */
data class AppSettings(
    val themeMode: String = "system",
    val memoryEnabled: Boolean = true,
    val defaultProviderId: String? = null,
    val defaultModelId: String? = null,
    val maxAgentSteps: Int = 25,
    val streamingEnabled: Boolean = true
)

/**
 * Reads and writes [AppSettings] via DataStore preferences.
 *
 * Holds no secrets; API keys live in
 * [com.nova.ai.data.security.SecureCredentialStore].
 */
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Hot-ish stream of the current settings; emits on every change. */
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME_MODE] ?: "system",
            memoryEnabled = prefs[KEY_MEMORY_ENABLED] ?: true,
            defaultProviderId = prefs[KEY_DEFAULT_PROVIDER_ID],
            defaultModelId = prefs[KEY_DEFAULT_MODEL_ID],
            maxAgentSteps = prefs[KEY_MAX_AGENT_STEPS] ?: 25,
            streamingEnabled = prefs[KEY_STREAMING_ENABLED] ?: true
        )
    }

    /** Sets the UI theme mode ("system", "light", or "dark"). */
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode }
    }

    /** Enables or disables long-term memory features. */
    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_MEMORY_ENABLED] = enabled }
    }

    /** Sets the preferred provider id, or clears it when null. */
    suspend fun setDefaultProvider(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_DEFAULT_PROVIDER_ID)
            else prefs[KEY_DEFAULT_PROVIDER_ID] = id
        }
    }

    /** Sets the preferred model id, or clears it when null. */
    suspend fun setDefaultModel(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_DEFAULT_MODEL_ID)
            else prefs[KEY_DEFAULT_MODEL_ID] = id
        }
    }

    /** Sets the maximum number of steps an agent run may take. */
    suspend fun setMaxAgentSteps(steps: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_MAX_AGENT_STEPS] = steps }
    }

    /** Enables or disables token streaming in chat. */
    suspend fun setStreamingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_STREAMING_ENABLED] = enabled }
    }
}
