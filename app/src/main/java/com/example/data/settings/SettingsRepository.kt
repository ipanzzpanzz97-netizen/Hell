package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "carbon_pro_settings"
)

enum class EngineMode {
    CLOUD_BUILTIN,
    CUSTOM_SERVER
}

enum class SafetyThresholdOption(val label: String, val description: String) {
    ONLY_HIGH("Minimum (Only High)", "Tapisan paling rendah yang dibenarkan untuk penyelidikan & kod."),
    MEDIUM_AND_ABOVE("Seimbang (Medium)", "Keseimbangan antara keterbukaan dan tapisan standard."),
    LOW_AND_ABOVE("Ketat (Low)", "Sekat kandungan sensitif pada tahap rendah.")
}

enum class ThemePreference(val label: String) {
    DARK("Carbon Dark"),
    LIGHT("Platinum Light"),
    SYSTEM("Sistem")
}

data class CarbonSettings(
    val engineMode: EngineMode = EngineMode.CLOUD_BUILTIN,
    val cloudModel: String = DEFAULT_CLOUD_MODEL,
    val safetyThreshold: SafetyThresholdOption = SafetyThresholdOption.ONLY_HIGH,
    val customApiUrl: String = "",
    val customApiKey: String = "",
    val customModelName: String = "carbon-pro",
    val customUseSse: Boolean = true,
    val systemPrompt: String = DEFAULT_CARBON_SYSTEM_PROMPT,
    val structuredOutputEnabled: Boolean = false,
    val deepAgentMode: Boolean = true,
    val temperature: Float = 0.7f,
    val themePreference: ThemePreference = ThemePreference.DARK
) {
    companion object {
        const val DEFAULT_CLOUD_MODEL = "gemini-3.8-flash"
        val AVAILABLE_CLOUD_MODELS = listOf(
            "gemini-3.8-flash" to "Gemini 3.8 Flash • Pantas & Serba Boleh (Disyorkan)",
            "gemini-4-argon" to "Gemini 4 Argon • Penaakulan Mendalam & Kod Kompleks",
            "gemini-3.5-flash-lite" to "Gemini 3.5 Flash-Lite • Latensi Ultra Rendah"
        )

        val DEFAULT_CARBON_SYSTEM_PROMPT = """
            Anda adalah CARBON-PRO, ejen penaakulan AI berprestasi tinggi untuk kejuruteraan perisian skala besar, analisis keselamatan siber defensif, matematik, sains, dan aliran kerja berbilang langkah.

            Protokol Pemikiran & Jawapan:
            1. Analisis keperluan, kebergantungan (dependencies), kekangan sistem, dan kes pinggiran sebelum menjawab.
            2. Rangka dan sahkan logik atau kod secara kendiri untuk mengelakkan ralat sintaks atau kelemahan logik.
            3. Berikan jawapan yang jelas, langsung, terperinci, dan tanpa pembukaan berbunga-bunga. Gunakan blok kod Markdown berlabel bahasa apabila menulis kod.
            4. Apabila pengguna bertanya dalam Bahasa Melayu atau Bahasa Inggeris, balas secara semula jadi dalam bahasa yang sepadan dengan ketepatan teknikal tinggi.
        """.trimIndent()
    }
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val ENGINE_MODE = stringPreferencesKey("engine_mode")
        val CLOUD_MODEL = stringPreferencesKey("cloud_model")
        val SAFETY_THRESHOLD = stringPreferencesKey("safety_threshold")
        val CUSTOM_API_URL = stringPreferencesKey("custom_api_url")
        val CUSTOM_API_KEY = stringPreferencesKey("custom_api_key")
        val CUSTOM_MODEL_NAME = stringPreferencesKey("custom_model_name")
        val CUSTOM_USE_SSE = booleanPreferencesKey("custom_use_sse")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val STRUCTURED_OUTPUT = booleanPreferencesKey("structured_output")
        val DEEP_AGENT_MODE = booleanPreferencesKey("deep_agent_mode")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
    }

    val settingsFlow: Flow<CarbonSettings> = context.settingsDataStore.data.map { prefs ->
        CarbonSettings(
            engineMode = prefs[Keys.ENGINE_MODE]?.let {
                runCatching { EngineMode.valueOf(it) }.getOrNull()
            } ?: EngineMode.CLOUD_BUILTIN,
            cloudModel = prefs[Keys.CLOUD_MODEL] ?: CarbonSettings.DEFAULT_CLOUD_MODEL,
            safetyThreshold = prefs[Keys.SAFETY_THRESHOLD]?.let {
                runCatching { SafetyThresholdOption.valueOf(it) }.getOrNull()
            } ?: SafetyThresholdOption.ONLY_HIGH,
            customApiUrl = prefs[Keys.CUSTOM_API_URL] ?: "",
            customApiKey = prefs[Keys.CUSTOM_API_KEY] ?: "",
            customModelName = prefs[Keys.CUSTOM_MODEL_NAME] ?: "carbon-pro",
            customUseSse = prefs[Keys.CUSTOM_USE_SSE] ?: true,
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: CarbonSettings.DEFAULT_CARBON_SYSTEM_PROMPT,
            structuredOutputEnabled = prefs[Keys.STRUCTURED_OUTPUT] ?: false,
            deepAgentMode = prefs[Keys.DEEP_AGENT_MODE] ?: true,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
            themePreference = prefs[Keys.THEME_PREFERENCE]?.let {
                runCatching { ThemePreference.valueOf(it) }.getOrNull()
            } ?: ThemePreference.DARK
        )
    }

    suspend fun updateSettings(transform: (CarbonSettings) -> CarbonSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = CarbonSettings(
                engineMode = prefs[Keys.ENGINE_MODE]?.let {
                    runCatching { EngineMode.valueOf(it) }.getOrNull()
                } ?: EngineMode.CLOUD_BUILTIN,
                cloudModel = prefs[Keys.CLOUD_MODEL] ?: CarbonSettings.DEFAULT_CLOUD_MODEL,
                safetyThreshold = prefs[Keys.SAFETY_THRESHOLD]?.let {
                    runCatching { SafetyThresholdOption.valueOf(it) }.getOrNull()
                } ?: SafetyThresholdOption.ONLY_HIGH,
                customApiUrl = prefs[Keys.CUSTOM_API_URL] ?: "",
                customApiKey = prefs[Keys.CUSTOM_API_KEY] ?: "",
                customModelName = prefs[Keys.CUSTOM_MODEL_NAME] ?: "carbon-pro",
                customUseSse = prefs[Keys.CUSTOM_USE_SSE] ?: true,
                systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: CarbonSettings.DEFAULT_CARBON_SYSTEM_PROMPT,
                structuredOutputEnabled = prefs[Keys.STRUCTURED_OUTPUT] ?: false,
                deepAgentMode = prefs[Keys.DEEP_AGENT_MODE] ?: true,
                temperature = prefs[Keys.TEMPERATURE] ?: 0.7f,
                themePreference = prefs[Keys.THEME_PREFERENCE]?.let {
                    runCatching { ThemePreference.valueOf(it) }.getOrNull()
                } ?: ThemePreference.DARK
            )
            val updated = transform(current)
            prefs[Keys.ENGINE_MODE] = updated.engineMode.name
            prefs[Keys.CLOUD_MODEL] = updated.cloudModel
            prefs[Keys.SAFETY_THRESHOLD] = updated.safetyThreshold.name
            prefs[Keys.CUSTOM_API_URL] = updated.customApiUrl
            prefs[Keys.CUSTOM_API_KEY] = updated.customApiKey
            prefs[Keys.CUSTOM_MODEL_NAME] = updated.customModelName
            prefs[Keys.CUSTOM_USE_SSE] = updated.customUseSse
            prefs[Keys.SYSTEM_PROMPT] = updated.systemPrompt
            prefs[Keys.STRUCTURED_OUTPUT] = updated.structuredOutputEnabled
            prefs[Keys.DEEP_AGENT_MODE] = updated.deepAgentMode
            prefs[Keys.TEMPERATURE] = updated.temperature
            prefs[Keys.THEME_PREFERENCE] = updated.themePreference.name
        }
    }
}
