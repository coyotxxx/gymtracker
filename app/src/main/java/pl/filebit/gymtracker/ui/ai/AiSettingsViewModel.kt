package pl.filebit.gymtracker.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pl.filebit.gymtracker.ai.AiClient
import pl.filebit.gymtracker.ai.AiConfig
import pl.filebit.gymtracker.ai.AiPreferences
import pl.filebit.gymtracker.ai.AiProvider
import javax.inject.Inject

data class AiSettingsUiState(
    val config: AiConfig = AiConfig(),
    val testing: Boolean = false,
    val testResult: String? = null,    // null = nie testowano; "" = OK, inne = błąd
    val saved: Boolean = false
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val prefs: AiPreferences,
    private val client: AiClient
) : ViewModel() {

    private val _state = MutableStateFlow(AiSettingsUiState(config = prefs.load()))
    val state: StateFlow<AiSettingsUiState> = _state.asStateFlow()

    // v1.24.17: każda zmiana ustawień AI auto-save'uje do prefs. Bez tego user
    // wpisuje klucz, wychodzi z ekranu i klucz znika (znalezione w symulacji
    // — 'Save button łatwo ominąć'). Save button zostaje jako redundant
    // confirmation ale nie jest już wymagany.
    private fun persistConfig(newConfig: AiConfig) {
        _state.value = _state.value.copy(
            config = newConfig,
            testResult = null,
            saved = true
        )
        viewModelScope.launch {
            prefs.save(newConfig)
        }
    }

    fun setProvider(p: AiProvider) {
        val cur = _state.value.config
        persistConfig(
            cur.copy(
                provider = p,
                model = if (p == AiProvider.ANTHROPIC) AiConfig.DEFAULT_ANTHROPIC
                else AiConfig.DEFAULT_OPENAI
            )
        )
    }

    fun setApiKey(key: String) {
        persistConfig(_state.value.config.copy(apiKey = key))
    }

    fun setModel(model: String) {
        persistConfig(_state.value.config.copy(model = model))
    }

    fun setSystemPrompt(prompt: String) {
        persistConfig(_state.value.config.copy(systemPrompt = prompt))
    }

    fun resetSystemPrompt() {
        persistConfig(_state.value.config.copy(systemPrompt = AiConfig.DEFAULT_SYSTEM_PROMPT))
    }

    fun save() {
        viewModelScope.launch {
            prefs.save(_state.value.config)
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            prefs.clear()
            _state.value = AiSettingsUiState(config = prefs.load())
        }
    }

    fun testConnection() {
        if (_state.value.config.apiKey.isBlank()) {
            _state.value = _state.value.copy(testResult = "Wpisz klucz API")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(testing = true, testResult = null)
            // ZAPISZ przed testem żeby user nie stracił wpisanego klucza po crashu
            prefs.save(_state.value.config)
            val result = client.ping(_state.value.config)
            _state.value = _state.value.copy(
                testing = false,
                testResult = result.fold(
                    onSuccess = { "" },
                    onFailure = { it.message ?: "Nieznany błąd" }
                ),
                saved = true
            )
        }
    }
}
