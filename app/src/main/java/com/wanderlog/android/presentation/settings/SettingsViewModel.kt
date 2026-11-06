package com.wanderlog.android.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wanderlog.android.core.util.BudgetDisplayCurrencies
import com.wanderlog.android.data.sync.SyncTombstoneResetter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import java.io.File

private const val PREFS_NAME = "wanderlog_secure"
const val KEY_AI_PROVIDER = "ai_provider"
const val KEY_OPENAI_API_KEY = "openai_api_key"
const val KEY_GEMINI_API_KEY = "gemini_api_key"
const val KEY_MAPS_API_KEY = "maps_api_key"
const val KEY_OPENAI_MODEL = "openai_model"
const val KEY_OPENAI_PARSING_MODEL = "openai_parsing_model"
const val KEY_GEMINI_MODEL = "gemini_model"
const val KEY_GEMINI_PARSING_MODEL = "gemini_parsing_model"
const val KEY_BUDGET_DISPLAY_CURRENCY = "budget_display_currency"

enum class AiProvider(val id: String, val label: String) {
    OPENAI("openai", "OpenAI"),
    GEMINI("gemini", "Gemini");

    companion object {
        fun sanitize(raw: String?): AiProvider {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.id == normalized } ?: OPENAI
        }
    }
}

data class OpenAiModelOption(
    val id: String,
    val label: String,
    val usageTier: String
)

object OpenAiModels {
    const val DEFAULT_MODEL = "gpt-5.4-mini"
    const val DEFAULT_PARSING_MODEL = "gpt-4o-mini"

    val options = listOf(
        OpenAiModelOption("gpt-5.6-sol", "GPT-5.6 Sol", "Complex planning and reasoning; API access depends on your account"),
        OpenAiModelOption("gpt-5.6-terra", "GPT-5.6 Terra", "Balances capability and cost; API access depends on your account"),
        OpenAiModelOption("gpt-5.6-luna", "GPT-5.6 Luna", "Lower-cost workloads; API access depends on your account"),
        OpenAiModelOption("gpt-5.4-mini", "GPT-5.4 Mini", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-5.4", "GPT-5.4", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-5.5", "GPT-5.5", "Model availability depends on your account"),
        OpenAiModelOption("gpt-5.4-nano", "GPT-5.4 Nano", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-5.2", "GPT-5.2", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-5.1", "GPT-5.1", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-5.1-codex", "GPT-5.1 Codex", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-5.1-codex-mini", "GPT-5.1 Codex Mini", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-5", "GPT-5", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-5-codex", "GPT-5 Codex", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-5-mini", "GPT-5 Mini", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-5-nano", "GPT-5 Nano", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-4.1", "GPT-4.1", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-4.1-mini", "GPT-4.1 Mini", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-4.1-nano", "GPT-4.1 Nano", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-4o", "GPT-4o", "API access and usage limits depend on your account"),
        OpenAiModelOption("gpt-4o-mini", "GPT-4o Mini", "API access and usage limits depend on your account"),
        OpenAiModelOption("o1", "o1", "API access and usage limits depend on your account"),
        OpenAiModelOption("o1-mini", "o1 Mini", "API access and usage limits depend on your account"),
        OpenAiModelOption("o3", "o3", "API access and usage limits depend on your account"),
        OpenAiModelOption("o3-mini", "o3 Mini", "API access and usage limits depend on your account"),
        OpenAiModelOption("o4-mini", "o4 Mini", "API access and usage limits depend on your account")
    )

    val parsingOptions = listOf(
        "gpt-5.6-sol",
        "gpt-5.6-terra",
        "gpt-5.6-luna",
        "gpt-4o-mini",
        "gpt-4o",
        "gpt-4.1-mini",
        "gpt-4.1",
        "gpt-4.1-nano",
        "o1-mini",
        "o1",
        "o3-mini",
        "o3",
        "o4-mini"
    ).mapNotNull { modelId -> options.firstOrNull { it.id == modelId } }

    private val validIds = options.mapTo(linkedSetOf()) { it.id }
    private val validParsingIds = parsingOptions.mapTo(linkedSetOf()) { it.id }

    fun sanitize(model: String?): String {
        val candidate = model?.trim().orEmpty()
        return if (candidate in validIds) candidate else DEFAULT_MODEL
    }

    fun labelFor(model: String): String =
        options.firstOrNull { it.id == model }?.label ?: DEFAULT_MODEL

    fun sanitizeParsingModel(model: String?): String {
        val candidate = model?.trim().orEmpty()
        return if (candidate in validParsingIds) candidate else DEFAULT_PARSING_MODEL
    }
}

object GeminiModels {
    const val DEFAULT_MODEL = "gemini-3.8-flash"
    const val DEFAULT_PARSING_MODEL = "gemini-3.5-flash-lite"

    val options = listOf(
        OpenAiModelOption("gemini-4-argon", "Gemini 4 Argon", "Flagship reasoning and complex planning; API access and usage limits depend on your account"),
        OpenAiModelOption("gemini-3.8-flash", "Gemini 3.8 Flash", "Fast multimodal reasoning and general tasks; API access and usage limits depend on your account"),
        OpenAiModelOption("gemini-3.6-flash", "Gemini 3.6 Flash", "Balanced speed and capability; API access and usage limits depend on your account"),
        OpenAiModelOption("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Cost-optimized, high-throughput tasks and parsing; API access and usage limits depend on your account")
    )

    val parsingOptions = options

    private val validIds = options.mapTo(linkedSetOf()) { it.id }
    private val validParsingIds = parsingOptions.mapTo(linkedSetOf()) { it.id }

    fun sanitize(model: String?): String {
        val candidate = model?.trim().orEmpty()
        return if (candidate in validIds) candidate else DEFAULT_MODEL
    }

    fun labelFor(model: String): String =
        options.firstOrNull { it.id == model }?.label ?: DEFAULT_MODEL

    fun sanitizeParsingModel(model: String?): String {
        val candidate = model?.trim().orEmpty()
        return if (candidate in validParsingIds) candidate else DEFAULT_PARSING_MODEL
    }
}

data class SettingsState(
    val aiProvider: AiProvider = AiProvider.OPENAI,
    val openAiKey: String = "",
    val geminiKey: String = "",
    val mapsKey: String = "",
    val openAiModel: String = OpenAiModels.DEFAULT_MODEL,
    val openAiParsingModel: String = OpenAiModels.DEFAULT_PARSING_MODEL,
    val geminiModel: String = GeminiModels.DEFAULT_MODEL,
    val geminiParsingModel: String = GeminiModels.DEFAULT_PARSING_MODEL,
    val budgetDisplayCurrency: String = BudgetDisplayCurrencies.DEFAULT,
    val resetInProgress: Boolean = false,
    val resetMessage: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncTombstoneResetter: SyncTombstoneResetter
) : ViewModel() {

    private val prefs by lazy { openSecurePrefs(context) }

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                aiProvider = AiProvider.sanitize(prefs.getString(KEY_AI_PROVIDER, AiProvider.OPENAI.id)),
                openAiKey = prefs.getString(KEY_OPENAI_API_KEY, "") ?: "",
                geminiKey = prefs.getString(KEY_GEMINI_API_KEY, "") ?: "",
                mapsKey = prefs.getString(KEY_MAPS_API_KEY, "") ?: "",
                openAiModel = OpenAiModels.sanitize(prefs.getString(KEY_OPENAI_MODEL, OpenAiModels.DEFAULT_MODEL)),
                openAiParsingModel = OpenAiModels.sanitizeParsingModel(
                    prefs.getString(KEY_OPENAI_PARSING_MODEL, OpenAiModels.DEFAULT_PARSING_MODEL)
                ),
                geminiModel = GeminiModels.sanitize(prefs.getString(KEY_GEMINI_MODEL, GeminiModels.DEFAULT_MODEL)),
                geminiParsingModel = GeminiModels.sanitizeParsingModel(
                    prefs.getString(KEY_GEMINI_PARSING_MODEL, GeminiModels.DEFAULT_PARSING_MODEL)
                ),
                budgetDisplayCurrency = BudgetDisplayCurrencies.sanitize(
                    prefs.getString(KEY_BUDGET_DISPLAY_CURRENCY, BudgetDisplayCurrencies.DEFAULT)
                )
            )
        }
    }

    fun onAiProviderChange(v: AiProvider) {
        _state.update { it.copy(aiProvider = v) }
        prefs.edit().putString(KEY_AI_PROVIDER, v.id).apply()
    }

    fun onOpenAiKeyChange(v: String) = _state.update { it.copy(openAiKey = v) }
    fun onGeminiKeyChange(v: String) = _state.update { it.copy(geminiKey = v) }
    fun onMapsKeyChange(v: String) = _state.update { it.copy(mapsKey = v) }

    fun onOpenAiModelChange(v: String) {
        val sanitized = OpenAiModels.sanitize(v)
        _state.update { it.copy(openAiModel = sanitized) }
        prefs.edit().putString(KEY_OPENAI_MODEL, sanitized).apply()
    }

    fun onOpenAiParsingModelChange(v: String) {
        val sanitized = OpenAiModels.sanitizeParsingModel(v)
        _state.update { it.copy(openAiParsingModel = sanitized) }
        prefs.edit().putString(KEY_OPENAI_PARSING_MODEL, sanitized).apply()
    }

    fun onGeminiModelChange(v: String) {
        val sanitized = GeminiModels.sanitize(v)
        _state.update { it.copy(geminiModel = sanitized) }
        prefs.edit().putString(KEY_GEMINI_MODEL, sanitized).apply()
    }

    fun onGeminiParsingModelChange(v: String) {
        val sanitized = GeminiModels.sanitizeParsingModel(v)
        _state.update { it.copy(geminiParsingModel = sanitized) }
        prefs.edit().putString(KEY_GEMINI_PARSING_MODEL, sanitized).apply()
    }

    fun onBudgetDisplayCurrencyChange(v: String) {
        val sanitized = BudgetDisplayCurrencies.sanitize(v)
        _state.update { it.copy(budgetDisplayCurrency = sanitized) }
        prefs.edit().putString(KEY_BUDGET_DISPLAY_CURRENCY, sanitized).apply()
    }

    fun save() {
        prefs.edit()
            .putString(KEY_AI_PROVIDER, _state.value.aiProvider.id)
            .putString(KEY_OPENAI_API_KEY, _state.value.openAiKey.trim())
            .putString(KEY_GEMINI_API_KEY, _state.value.geminiKey.trim())
            .putString(KEY_MAPS_API_KEY, _state.value.mapsKey.trim())
            .putString(KEY_OPENAI_MODEL, OpenAiModels.sanitize(_state.value.openAiModel))
            .putString(
                KEY_OPENAI_PARSING_MODEL,
                OpenAiModels.sanitizeParsingModel(_state.value.openAiParsingModel)
            )
            .putString(KEY_GEMINI_MODEL, GeminiModels.sanitize(_state.value.geminiModel))
            .putString(
                KEY_GEMINI_PARSING_MODEL,
                GeminiModels.sanitizeParsingModel(_state.value.geminiParsingModel)
            )
            .putString(
                KEY_BUDGET_DISPLAY_CURRENCY,
                BudgetDisplayCurrencies.sanitize(_state.value.budgetDisplayCurrency)
            )
            .apply()
        _state.update { it.copy(saved = true) }
    }

    fun resetDeletedTombstones() {
        viewModelScope.launch {
            _state.update { it.copy(resetInProgress = true, resetMessage = null) }
            runCatching {
                syncTombstoneResetter.reset()
            }.onSuccess {
                _state.update {
                    it.copy(
                        resetInProgress = false,
                        resetMessage = "Deleted sync tombstones were permanently removed from this device."
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        resetInProgress = false,
                        resetMessage = error.message ?: "Unable to reset deleted tombstones."
                    )
                }
            }
        }
    }

    fun clearResetMessage() {
        _state.update { it.copy(resetMessage = null) }
    }

    companion object {
        fun getAiProvider(context: Context): AiProvider {
            val prefs = openSecurePrefs(context)
            return AiProvider.sanitize(prefs.getString(KEY_AI_PROVIDER, AiProvider.OPENAI.id))
        }

        fun getOpenAiKey(context: Context): String {
            val prefs = openSecurePrefs(context)
            return prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        }

        fun getGeminiKey(context: Context): String {
            val prefs = openSecurePrefs(context)
            return prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        }

        fun getActiveAiKey(context: Context): String {
            return when (getAiProvider(context)) {
                AiProvider.OPENAI -> getOpenAiKey(context)
                AiProvider.GEMINI -> getGeminiKey(context)
            }
        }

        fun hasConfiguredAiKey(context: Context): Boolean {
            return getActiveAiKey(context).isNotBlank() ||
                getOpenAiKey(context).isNotBlank() ||
                getGeminiKey(context).isNotBlank()
        }

        fun getMapsKey(context: Context): String {
            val prefs = openSecurePrefs(context)
            return prefs.getString(KEY_MAPS_API_KEY, "") ?: ""
        }

        fun getOpenAiModel(context: Context): String {
            val prefs = openSecurePrefs(context)
            return OpenAiModels.sanitize(prefs.getString(KEY_OPENAI_MODEL, OpenAiModels.DEFAULT_MODEL))
        }

        fun getOpenAiParsingModel(context: Context): String {
            val prefs = openSecurePrefs(context)
            return OpenAiModels.sanitizeParsingModel(
                prefs.getString(KEY_OPENAI_PARSING_MODEL, OpenAiModels.DEFAULT_PARSING_MODEL)
            )
        }

        fun getGeminiModel(context: Context): String {
            val prefs = openSecurePrefs(context)
            return GeminiModels.sanitize(prefs.getString(KEY_GEMINI_MODEL, GeminiModels.DEFAULT_MODEL))
        }

        fun getGeminiParsingModel(context: Context): String {
            val prefs = openSecurePrefs(context)
            return GeminiModels.sanitizeParsingModel(
                prefs.getString(KEY_GEMINI_PARSING_MODEL, GeminiModels.DEFAULT_PARSING_MODEL)
            )
        }

        fun getActiveAiModel(context: Context): String {
            return when (getAiProvider(context)) {
                AiProvider.OPENAI -> getOpenAiModel(context)
                AiProvider.GEMINI -> getGeminiModel(context)
            }
        }

        fun getActiveAiParsingModel(context: Context): String {
            return when (getAiProvider(context)) {
                AiProvider.OPENAI -> getOpenAiParsingModel(context)
                AiProvider.GEMINI -> getGeminiParsingModel(context)
            }
        }

        fun getBudgetDisplayCurrency(context: Context): String {
            val prefs = openSecurePrefs(context)
            return BudgetDisplayCurrencies.sanitize(
                prefs.getString(KEY_BUDGET_DISPLAY_CURRENCY, BudgetDisplayCurrencies.DEFAULT)
            )
        }
    }
}

private fun openSecurePrefs(context: Context): SharedPreferences {
    return runCatching {
        createEncryptedPrefs(context)
    }.recoverCatching {
        clearSecurePrefsState(context)
        createEncryptedPrefs(context)
    }.getOrElse {
        // Avoid blocking app startup if the device keystore or restored encrypted prefs are unusable.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

private fun createEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

private fun clearSecurePrefsState(context: Context) {
    runCatching { context.deleteSharedPreferences(PREFS_NAME) }
    runCatching {
        File(context.applicationInfo.dataDir, "shared_prefs/$PREFS_NAME.xml").delete()
    }
}
