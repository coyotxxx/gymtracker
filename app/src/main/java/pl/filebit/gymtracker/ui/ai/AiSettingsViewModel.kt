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

    fun setProvider(p: AiProvider) {
        val cur = _state.value.config
        _state.value = _state.value.copy(
            config = cur.copy(
                provider = p,
                model = if (p == AiProvider.ANTHROPIC) AiConfig.DEFAULT_ANTHROPIC
                else AiConfig.DEFAULT_OPENAI
            ),
            testResult = null,
            saved = false
        )
    }

    fun setApiKey(key: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(apiKey = key),
            testResult = null,
            saved = false
        )
    }

    fun setModel(model: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(model = model),
            testResult = null,
            saved = false
        )
    }

    fun setSystemPrompt(prompt: String) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(systemPrompt = prompt),
            saved = false
        )
    }

    fun resetSystemPrompt() {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(systemPrompt = AiConfig.DEFAULT_SYSTEM_PROMPT),
            saved = false
        )
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
