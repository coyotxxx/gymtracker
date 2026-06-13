package pl.filebit.gymtracker.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class AiRole { USER, ASSISTANT }

data class AiMessage(val role: AiRole, val content: String)

/**
 * v1.25.3 — sentinel oddzielający stable context (cached) od dynamic (fresh)
 * w prompt caching Anthropic. Stosowany w AiTrainerViewModel.combined między
 * stałą biblioteką ćw + profile (TOP, cached ephemeral 5min) a recent_workouts
 * + pytaniem (BOTTOM, fresh). Top-level żeby był łatwo dostępny z innych pakietów.
 */
const val CACHE_BREAKPOINT_MARKER = "<<<CACHE_BREAKPOINT>>>"

interface AiClient {
    /**
     * @param source nazwa wywołującego service (np. "DietAi", "WorkoutPlanAi") — używana do logowania
     */
    suspend fun chat(
        config: AiConfig,
        messages: List<AiMessage>,
        source: String = "unknown"
    ): Result<String>

    /**
     * Multimodal — dodaje obraz (base64 jpeg/png) do user message.
     * Anthropic: image content block (base64).
     * OpenAI: image_url z data URI.
     */
    suspend fun chatWithImage(
        config: AiConfig,
        imageBase64: String,
        mimeType: String,           // np. "image/jpeg"
        userPrompt: String,
        source: String = "unknown"
    ): Result<String>

    /**
     * v1.11.67 — chat z możliwością wywoływania narzędzi (Anthropic Tool Use).
     * AI może w odpowiedzi użyć tool_use bloku zamiast text. Klient wykonuje
     * tool przez [toolHandler] i zwraca wynik jako kolejną wiadomość. Loop
     * iteruje aż AI zwróci finalny tekst lub osiągniemy MAX_TOOL_ITERATIONS.
     *
     * Dla OpenAI: fallback do zwykłego chat (function calling ma inny format,
     * implementacja może być dodana w przyszłej wersji).
     */
    suspend fun chatWithTools(
        config: AiConfig,
        messages: List<AiMessage>,
        toolHandler: AiToolHandler,
        source: String = "unknown"
    ): Result<String>

    suspend fun ping(config: AiConfig): Result<Unit>
}

@Singleton
class AiClientImpl @Inject constructor(
    private val aiLogRepo: pl.filebit.gymtracker.data.repository.AiLogRepository,
    private val aiPrefs: AiPreferences,
    private val diag: pl.filebit.gymtracker.data.repository.DiagnosticLogger
) : AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private fun mapTimeoutToFriendly(err: Throwable): Throwable =
        if (err is TimeoutCancellationException) {
            IllegalStateException(
                "Timeout: AI nie odpowiedziała w ciągu ${CHAT_TIMEOUT_MS / 1000}s. " +
                "Spróbuj ponownie lub zadaj krótsze pytanie."
            )
        } else err

    /** Zapisuje wywołanie AI do bazy jeśli logging enabled. */
    private suspend fun logCall(
        source: String,
        config: AiConfig,
        prompt: String,
        response: String,
        success: Boolean,
        errorMsg: String?,
        durationMs: Long
    ) {
        // v2.27.0: lekki sygnał diagnostyczny niezależny od pref AiLog (pełne transkrypty
        // mają swój przełącznik, ale strumień zdarzeń AI ma być zawsze widoczny).
        if (success) {
            diag.info(pl.filebit.gymtracker.data.entity.DiagnosticCategory.AI, "AiClient", "ai_call_ok",
                "Wywołanie AI OK ($source, ${config.model}, ${durationMs}ms)",
                dataJson = """{"source":"$source","model":"${config.model}","durationMs":$durationMs}""", success = true)
        } else {
            diag.error(pl.filebit.gymtracker.data.entity.DiagnosticCategory.AI, "AiClient", "ai_call_failed",
                "Wywołanie AI nieudane ($source): ${errorMsg?.take(200)}")
        }
        if (!aiPrefs.isLoggingEnabled()) return
        runCatching {
            aiLogRepo.log(
                pl.filebit.gymtracker.data.entity.AiLog(
                    service = source,
                    provider = config.provider.name,
                    model = config.model,
                    fullPrompt = prompt,
                    fullResponse = response,
                    success = success,
                    errorMessage = errorMsg,
                    durationMs = durationMs
                )
            )
        }
    }

    /**
     * Sanityzacja klucza API: usuwa białe znaki + trailing kropki/przecinki/cudzysłowy
     * (częste artefakty kopiowania z dokumentów/maili).
     */
    private fun sanitizeKey(raw: String): String =
        raw.trim().trimEnd('.', ',', ';', ':', ' ', '\t', '\n', '\r', '\'', '"')

    override suspend fun chat(
        config: AiConfig,
        messages: List<AiMessage>,
        source: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val combinedPrompt = messages.joinToString("\n\n") { "[${it.role.name}]\n${it.content}" }
        val result = runCatching {
            withTimeout(CHAT_TIMEOUT_MS) {
                when (config.provider) {
                    AiProvider.ANTHROPIC -> callAnthropic(config, messages)
                    AiProvider.OPENAI -> callOpenAi(config, messages)
                }
            }
        }.mapError(::mapTimeoutToFriendly)
        val duration = System.currentTimeMillis() - startMs
        result.fold(
            onSuccess = { response ->
                logCall(source, config, combinedPrompt, response, true, null, duration)
            },
            onFailure = { err ->
                logCall(source, config, combinedPrompt, "", false, err.message, duration)
            }
        )
        result
    }

    override suspend fun chatWithTools(
        config: AiConfig,
        messages: List<AiMessage>,
        toolHandler: AiToolHandler,
        source: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val combinedPrompt = messages.joinToString("\n\n") { "[${it.role.name}]\n${it.content}" }
        val result = runCatching {
            withTimeout(CHAT_TIMEOUT_MS) {
                when (config.provider) {
                    AiProvider.ANTHROPIC -> callAnthropicWithTools(config, messages, toolHandler)
                    AiProvider.OPENAI -> callOpenAi(config, messages)  // fallback bez tools
                }
            }
        }.mapError(::mapTimeoutToFriendly)
        val duration = System.currentTimeMillis() - startMs
        result.fold(
            onSuccess = { logCall(source, config, combinedPrompt, it, true, null, duration) },
            onFailure = { logCall(source, config, combinedPrompt, "", false, it.message, duration) }
        )
        result
    }

    override suspend fun ping(config: AiConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            chat(
                config,
                listOf(AiMessage(AiRole.USER, "Odpowiedz jednym słowem: pong")),
                source = "Ping"
            ).getOrThrow()
            Unit
        }
    }

    override suspend fun chatWithImage(
        config: AiConfig,
        imageBase64: String,
        mimeType: String,
        userPrompt: String,
        source: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val result = runCatching {
            when (config.provider) {
                AiProvider.ANTHROPIC -> callAnthropicWithImage(config, imageBase64, mimeType, userPrompt)
                AiProvider.OPENAI -> callOpenAiWithImage(config, imageBase64, mimeType, userPrompt)
            }
        }
        val duration = System.currentTimeMillis() - startMs
        val promptForLog = "[IMG ${mimeType}]\n[USER]\n$userPrompt"
        result.fold(
            onSuccess = { response -> logCall(source, config, promptForLog, response, true, null, duration) },
            onFailure = { err -> logCall(source, config, promptForLog, "", false, err.message, duration) }
        )
        result
    }

    private fun callAnthropicWithImage(
        config: AiConfig,
        imageBase64: String,
        mimeType: String,
        userPrompt: String
    ): String {
        // Anthropic format: messages → content array → mix text + image blocks
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 2048)
            put("system", config.systemPrompt)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "image")
                            put("source", buildJsonObject {
                                put("type", "base64")
                                put("media_type", mimeType)
                                put("data", imageBase64)
                            })
                        })
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", userPrompt)
                        })
                    })
                })
            })
        }.toString()

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", sanitizeKey(config.apiKey))
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return execute(req) { obj ->
            val content = obj["content"]?.jsonArray ?: error("Brak pola content")
            val textBlock = content.firstOrNull {
                it.jsonObject["type"]?.jsonPrimitive?.content == "text"
            }?.jsonObject ?: error("Brak text block w odpowiedzi")
            textBlock["text"]?.jsonPrimitive?.content ?: error("Brak text w odpowiedzi")
        }
    }

    private fun callOpenAiWithImage(
        config: AiConfig,
        imageBase64: String,
        mimeType: String,
        userPrompt: String
    ): String {
        val dataUri = "data:$mimeType;base64,$imageBase64"
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 2048)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", config.systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", userPrompt)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", dataUri)
                            })
                        })
                    })
                })
            })
        }.toString()

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer ${sanitizeKey(config.apiKey)}")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return execute(req) { obj ->
            val choices = obj["choices"]?.jsonArray ?: error("Brak pola choices")
            val first = choices.firstOrNull()?.jsonObject ?: error("Pusta odpowiedź")
            val msg = first["message"]?.jsonObject ?: error("Brak message")
            msg["content"]?.jsonPrimitive?.content ?: error("Brak content w message")
        }
    }

    /**
     * v1.11.67: chat z tool use, multi-turn loop dla Anthropic.
     *
     * Format API: assistant content może być array z text/tool_use blokami.
     * Klient wykonuje tool i zwraca user message z tool_result blokiem.
     * Iteruje aż AI zwróci finalny text (stop_reason == "end_turn") lub do MAX.
     */
    private suspend fun callAnthropicWithTools(
        config: AiConfig,
        initialMessages: List<AiMessage>,
        toolHandler: AiToolHandler
    ): String {
        // Convert AiMessage -> List<JsonObject> w API format. Każda wiadomość ma
        // content jako string (zwykły tekst). W trakcie loop'u dodajemy
        // wiadomości z content array (z tool_use / tool_result).
        // v1.25.3: prompt caching — last user message ma marker <<<CACHE_BREAKPOINT>>>
        // rozdzielający stable (biblioteka ćw + profile) od dynamic (recent + pytanie).
        val messagesArray = mutableListOf<kotlinx.serialization.json.JsonElement>()
        val lastUserIdx = initialMessages.indexOfLast { it.role == AiRole.USER }
        initialMessages.forEachIndexed { idx, m ->
            messagesArray.add(buildJsonObject {
                put("role", if (m.role == AiRole.USER) "user" else "assistant")
                put("content", buildContentArrayWithCacheBreak(m.content, applyCache = idx == lastUserIdx))
            })
        }

        val maxIterations = 6  // bezpieczna granica multi-turn
        var iteration = 0
        while (iteration < maxIterations) {
            iteration++
            val body = buildJsonObject {
                put("model", config.model)
                put("max_tokens", maxTokensForModel(config.model))
                put("system", config.systemPrompt)
                put("tools", AiTools.toolsForApi())
                put("messages", buildJsonArray { messagesArray.forEach { add(it) } })
            }.toString()

            val req = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", sanitizeKey(config.apiKey))
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val responseObj = http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Błąd API (${resp.code}): ${raw.take(500)}")
                }
                json.parseToJsonElement(raw).jsonObject
            }

            val stopReason = responseObj["stop_reason"]?.jsonPrimitive?.content
            val contentArray = responseObj["content"]?.jsonArray
                ?: throw IllegalStateException("Brak pola content w odpowiedzi Anthropic")

            if (stopReason == "tool_use") {
                // AI chce użyć narzędzi - wykonujemy wszystkie tool_use bloki
                // i zwracamy tool_result jako user message
                val toolUseBlocks = contentArray.filter {
                    it.jsonObject["type"]?.jsonPrimitive?.content == "tool_use"
                }
                if (toolUseBlocks.isEmpty()) {
                    // stop_reason=tool_use ale brak tool_use bloków - dziwne, return raw text
                    return extractText(contentArray)
                }

                // Dodaj assistant message z całym content array (raw)
                messagesArray.add(buildJsonObject {
                    put("role", "assistant")
                    put("content", contentArray)
                })

                // Wykonaj każdy tool i zbuduj tool_result content
                val toolResults = buildJsonArray {
                    toolUseBlocks.forEach { block ->
                        val obj = block.jsonObject
                        val toolName = obj["name"]?.jsonPrimitive?.content ?: ""
                        val toolUseId = obj["id"]?.jsonPrimitive?.content ?: ""
                        val toolInput = obj["input"]?.jsonObject ?: buildJsonObject { }
                        val toolResult = if (toolName in AiTools.TOOL_NAMES) {
                            runCatching { toolHandler.execute(toolName, toolInput) }
                                .getOrElse { "{\"error\":\"${it.message?.take(200)}\"}" }
                        } else {
                            "{\"error\":\"unknown tool: $toolName\"}"
                        }
                        add(buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", toolUseId)
                            put("content", toolResult)
                        })
                    }
                }
                messagesArray.add(buildJsonObject {
                    put("role", "user")
                    put("content", toolResults)
                })
                // Continue loop - kolejne wywołanie Anthropic API
            } else {
                // end_turn / max_tokens / stop_sequence - finalna odpowiedź
                return extractText(contentArray)
            }
        }
        throw IllegalStateException("Multi-turn loop przekroczył max iteracji ($maxIterations)")
    }

    /** Wyciąga połączony tekst z content array (pomija tool_use bloki). */
    private fun extractText(contentArray: kotlinx.serialization.json.JsonArray): String {
        return contentArray
            .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            .joinToString("\n")
            .ifBlank { "(AI nie zwróciło tekstu)" }
    }

    private fun callAnthropic(config: AiConfig, messages: List<AiMessage>): String {
        val body = buildJsonObject {
            put("model", config.model)
            // v1.11.62: max_tokens model-aware. Plan 7-dniowy potrzebuje ~8000-9200
            // tokenow (56 cwiczen × 120 tokenow + opis). Haiku ma limit modelu 8192,
            // Sonnet/Opus mogą więcej. Zostawiamy zapas dla zlozonych planow.
            put("max_tokens", maxTokensForModel(config.model))
            put("system", config.systemPrompt)
            // v1.25.3: prompt caching — last user message zawiera marker
            // <<<CACHE_BREAKPOINT>>> rozdzielający stable context (biblioteka ćwiczeń,
            // user profile) od dynamic (recent_workouts, pytanie). Stable cached
            // ephemeral 5min, koszt cache read ≈ 10% standardowego input.
            put("messages", buildJsonArray {
                val lastUserIdx = messages.indexOfLast { it.role == AiRole.USER }
                messages.forEachIndexed { idx, m ->
                    add(buildJsonObject {
                        put("role", if (m.role == AiRole.USER) "user" else "assistant")
                        // Cache_control tylko na OSTATNIEJ user message (przy aktywnym pytaniu)
                        put("content", buildContentArrayWithCacheBreak(m.content, applyCache = idx == lastUserIdx))
                    })
                }
            })
        }.toString()

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", sanitizeKey(config.apiKey))
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return execute(req) { obj ->
            val content = obj["content"]?.jsonArray ?: error("Brak pola content")
            val first = content.firstOrNull()?.jsonObject ?: error("Pusta odpowiedź")
            first["text"]?.jsonPrimitive?.content ?: error("Brak text w odpowiedzi")
        }
    }

    private fun callOpenAi(config: AiConfig, messages: List<AiMessage>): String {
        val body = buildJsonObject {
            put("model", config.model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", config.systemPrompt)
                })
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", if (m.role == AiRole.USER) "user" else "assistant")
                        put("content", m.content)
                    })
                }
            })
        }.toString()

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer ${sanitizeKey(config.apiKey)}")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return execute(req) { obj ->
            val choices = obj["choices"]?.jsonArray ?: error("Brak pola choices")
            val first = choices.firstOrNull()?.jsonObject ?: error("Pusta odpowiedź")
            val msg = first["message"]?.jsonObject ?: error("Brak message")
            msg["content"]?.jsonPrimitive?.content ?: error("Brak content w message")
        }
    }

    private fun execute(req: Request, parse: (JsonObject) -> String): String {
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val (errMsg, errType) = runCatching {
                    val obj = json.parseToJsonElement(raw).jsonObject
                    val err = obj["error"]
                    when (err) {
                        is JsonObject -> {
                            val m = err["message"]?.jsonPrimitive?.content
                            val t = err["type"]?.jsonPrimitive?.content
                            m to t
                        }
                        is JsonArray -> {
                            val first = err.firstOrNull()?.jsonObject
                            first?.get("message")?.jsonPrimitive?.content to first?.get("type")?.jsonPrimitive?.content
                        }
                        else -> null to null
                    }
                }.getOrNull() ?: (raw.take(500) to null)

                val hint = buildErrorHint(resp.code, errMsg, errType)
                val baseMsg = errMsg ?: raw.take(300)
                val full = if (hint.isNotBlank()) "$baseMsg\n\n$hint" else baseMsg
                throw IllegalStateException("Błąd API (${resp.code}): $full")
            }
            val obj = json.parseToJsonElement(raw).jsonObject
            return parse(obj)
        }
    }

    /**
     * Diagnostyka błędu Anthropic/OpenAI z polską poradą — co sprawdzić.
     * Dotyczy najczęstszych przyczyn (workspace spend limit, klucz w innym koncie itd.).
     */
    private fun buildErrorHint(code: Int, message: String?, type: String?): String {
        val msgLower = message?.lowercase().orEmpty()
        return when {
            // Anthropic: credit balance / workspace spend limit
            msgLower.contains("credit balance") ||
                msgLower.contains("insufficient") ||
                msgLower.contains("billing") -> """
                Najczęstsze przyczyny (mimo wpłaty):
                1. Workspace Spend Limit = 0
                   → console.anthropic.com → Settings → Workspaces
                   → otwórz workspace klucza → Spend Limit (musi być >0 lub Unlimited)
                2. Klucz z innego konta niż to na które wpłaciłeś
                   → console.anthropic.com → API Keys → sprawdź workspace klucza
                3. Promo credits wygasły / Pay-as-you-go nie aktywny
                   → console.anthropic.com → Plans & Billing
                """.trimIndent()
            // 401 / authentication
            code == 401 || type == "authentication_error" || msgLower.contains("invalid api key") -> """
                Klucz API nieprawidłowy lub odwołany.
                → Wygeneruj nowy: console.anthropic.com → API Keys → Create Key
                → Skopiuj DOKŁADNIE (bez spacji/nowych linii) i wklej w Profil → Połączenie AI
                """.trimIndent()
            code == 429 || type == "rate_limit_error" -> """
                Przekroczyłeś limit zapytań na minutę/godzinę.
                → Poczekaj kilka minut i spróbuj ponownie
                → Lub przejdź na model tańszy/szybszy (Haiku 4.5)
                """.trimIndent()
            code == 529 || code == 503 -> """
                Anthropic API tymczasowo niedostępne (przeciążenie).
                → Spróbuj za 1-2 minuty
                → Status: status.anthropic.com
                """.trimIndent()
            code in 500..599 -> "Błąd po stronie serwera. Spróbuj ponownie za chwilę."
            else -> ""
        }
    }

    /**
     * v1.11.62: max_tokens output zaleznie od modelu.
     * Limity z dokumentacji Anthropic / OpenAI:
     *  - Claude Haiku 4.5 → 8192 (limit modelu, nie da sie wiecej)
     *  - Claude Sonnet 4.6 → 64000 (wymaga headera anthropic-beta, default 8192)
     *  - Claude Opus 4.7 → 32000
     *  - GPT-5/4o → standardowo 16384
     *
     * Dla planu 7-dniowego potrzeba ~8000-9200 tokenow output (56 cwiczen × 120 tokenow + opis).
     * Haiku starczy ledwo - lepiej Sonnet/Opus. Tu liczy ile daje API jako bezpieczny pulap;
     * faktyczna odpowiedz moze byc krotsza.
     */
    private fun maxTokensForModel(modelId: String): Int {
        val m = modelId.lowercase()
        return when {
            "opus" in m -> 32000
            "sonnet" in m -> 16384
            "haiku" in m -> 8192
            "gpt-5" in m || "gpt-4" in m || "o3" in m -> 16384
            else -> 8192  // bezpieczny default
        }
    }

    companion object {
        /**
         * Twardy limit całego flow chat (multi-turn z tool_use). Po przekroczeniu
         * Result.failure z user-friendly msg.
         *
         * v1.24.8: 180s → 240s (4 min). ZAPROPONUJ PLAN dla pełnego tygodnia
         * (5-7 dni × 5-8 ćwiczeń × 3-5 setów + tool calls) może wymagać >3 min
         * na Opus model. Maciej testował — timeout strzelał przed odpowiedzią.
         */
        const val CHAT_TIMEOUT_MS = 240_000L
    }

    /**
     * v1.25.3 — buduje content array dla Anthropic API z opcjonalnym cache_control.
     * Jeśli content zawiera CACHE_BREAKPOINT_MARKER i applyCache=true → dwa text blocks:
     *   - Block 1 (stable, cache_control:ephemeral)
     *   - Block 2 (dynamic, no cache)
     * Inaczej: jeden zwykły text block.
     *
     * Anthropic min cache size: 1024 tokens dla Opus/Sonnet, 2048 dla Haiku.
     * Mniejszy block jest po prostu nie cached (no error).
     */
    private fun buildContentArrayWithCacheBreak(
        content: String,
        applyCache: Boolean
    ): kotlinx.serialization.json.JsonArray {
        val marker = pl.filebit.gymtracker.ai.CACHE_BREAKPOINT_MARKER
        if (!applyCache || !content.contains(marker)) {
            return buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", content)
                })
            }
        }
        val parts = content.split(marker, limit = 2)
        val stable = parts[0].trimEnd()
        val dynamic = parts.getOrNull(1)?.trimStart() ?: ""
        return buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", stable)
                putJsonObject("cache_control") {
                    put("type", "ephemeral")
                }
            })
            if (dynamic.isNotEmpty()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", dynamic)
                })
            }
        }
    }
}

/** Mapuje Throwable w Result.failure. Pozwala zamienić wewnętrzny błąd na user-friendly bez fold/recover boilerplate. */
private inline fun <T> Result<T>.mapError(transform: (Throwable) -> Throwable): Result<T> =
    fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(transform(it)) })
