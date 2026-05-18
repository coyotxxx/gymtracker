package pl.filebit.gymtracker.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AiProvider { ANTHROPIC, OPENAI }

data class AiConfig(
    val provider: AiProvider = AiProvider.ANTHROPIC,
    val apiKey: String = "",
    val model: String = DEFAULT_ANTHROPIC,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {
    val isConnected: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_ANTHROPIC = "claude-sonnet-4-6"
        const val DEFAULT_OPENAI = "gpt-5.5"

        // Domyślny system prompt po polsku — opisuje rolę trenera
        val DEFAULT_SYSTEM_PROMPT = """
Jesteś trenerem personalnym specjalizującym się w treningu siłowym i hipertrofii.
Masz dostęp do pełnych danych użytkownika: profilu, historii treningów, pomiarów ciała,
planów, statystyk, poziomów siły i celów. Odpowiadaj zawsze po polsku.

Twoja rola:
- Analizuj dane użytkownika konkretnie (cytuj liczby z kontekstu — wagi, powtórzenia, daty)
- Bądź motywujący ale realistyczny, nie bagatelizuj kontuzji
- Sugeruj konkretne progresje (kg, reps, RPE) zamiast ogólników
- Wskazuj słabe punkty (stagnacje, dysbalanse mięśniowe) i propozycje korekty
- Dostosuj sugestie do dostępnego sprzętu (lista ćwiczeń w bibliotece)
- Gdy proponujesz pełny plan treningowy — zwróć go jako blok JSON wewnątrz ```json ... ```
  z polami: name, description, daysOfWeek (lista 1-7), exercises (per dzień: dayOfWeek,
  exerciseName z biblioteki użytkownika, sets [{reps, weightKg, restSec}])
- Gdy zwracasz plan — wybieraj nazwy ćwiczeń DOKŁADNIE z biblioteki użytkownika

Masz też dostęp do narzędzi (tools) gdy potrzebujesz głębszych danych
niż w głównym kontekście. Używaj ich gdy:
- User pyta o konkretne ćwiczenie z dłuższego okresu → get_exercise_history
- User pyta o trend wagi >7 dni → get_body_history
- User pyta o eventy (PR-y, kontuzje, deloady) >15 ostatnich → get_events
- User pyta o porównanie kwartałów / lat → get_rollups (period=QUARTER)
- User pyta o szczegóły konkretnych dni starszych niż 5 ostatnich treningów → get_workouts

NIE używaj narzędzi jeśli odpowiedź jest w głównym kontekście (recent_workouts,
event_log, weekly_volume_trend_12w, body_inflections, historical_summary).
""".trimIndent()
    }
}

@Singleton
class AiPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    // v1.27: masterKey + secure jako `by lazy` — Android KeyStore (crypto)
    // tworzony dopiero przy PIERWSZYM dostępie do klucza AI, nie w konstruktorze.
    // Bez tego AiPreferences nie da się zbudować w teście JVM (Robolectric nie
    // ma AndroidKeyStore). Zachowanie aplikacji bez zmian — lazy init.
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    // v1.27: gdy AndroidKeyStore niedostępny (środowisko testowe JVM lub
    // skrajna awaria crypto na urządzeniu) — fallback do zwykłych prefs
    // zamiast crashu. Na realnym Androidzie 6+ KeyStore zawsze działa.
    private val secure: android.content.SharedPreferences by lazy {
        runCatching {
            EncryptedSharedPreferences.create(
                context,
                "ai_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            context.getSharedPreferences("ai_secure_fallback", Context.MODE_PRIVATE)
        }
    }

    private val plain = context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)

    fun load(): AiConfig {
        val provider = runCatching {
            AiProvider.valueOf(plain.getString(KEY_PROVIDER, AiProvider.ANTHROPIC.name) ?: "")
        }.getOrDefault(AiProvider.ANTHROPIC)
        val apiKey = secure.getString(KEY_API_KEY, "") ?: ""
        val model = plain.getString(KEY_MODEL, defaultModelFor(provider)) ?: defaultModelFor(provider)
        val sys = plain.getString(KEY_SYSTEM_PROMPT, AiConfig.DEFAULT_SYSTEM_PROMPT)
            ?: AiConfig.DEFAULT_SYSTEM_PROMPT
        return AiConfig(provider, apiKey, model, sys)
    }

    fun save(config: AiConfig) {
        plain.edit()
            .putString(KEY_PROVIDER, config.provider.name)
            .putString(KEY_MODEL, config.model)
            .putString(KEY_SYSTEM_PROMPT, config.systemPrompt)
            .apply()
        secure.edit().putString(KEY_API_KEY, config.apiKey).apply()
    }

    fun clear() {
        plain.edit().clear().apply()
        secure.edit().clear().apply()
    }

    /** Logging — toggle on/off. Default OFF (privacy). */
    fun isLoggingEnabled(): Boolean = plain.getBoolean(KEY_LOGGING_ENABLED, false)
    fun setLoggingEnabled(enabled: Boolean) {
        plain.edit().putBoolean(KEY_LOGGING_ENABLED, enabled).apply()
    }

    fun defaultModelFor(p: AiProvider) = when (p) {
        AiProvider.ANTHROPIC -> AiConfig.DEFAULT_ANTHROPIC
        AiProvider.OPENAI -> AiConfig.DEFAULT_OPENAI
    }

    companion object {
        private const val KEY_PROVIDER = "provider"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
    }
}
