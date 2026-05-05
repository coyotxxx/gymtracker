package pl.filebit.gymtracker.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class AiRole { USER, ASSISTANT }

data class AiMessage(val role: AiRole, val content: String)

interface AiClient {
    suspend fun chat(
        config: AiConfig,
        messages: List<AiMessage>
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
        userPrompt: String
    ): Result<String>

    suspend fun ping(config: AiConfig): Result<Unit>
}

@Singleton
class AiClientImpl @Inject constructor() : AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Sanityzacja klucza API: usuwa białe znaki + trailing kropki/przecinki/cudzysłowy
     * (częste artefakty kopiowania z dokumentów/maili).
     */
    private fun sanitizeKey(raw: String): String =
        raw.trim().trimEnd('.', ',', ';', ':', ' ', '\t', '\n', '\r', '\'', '"')

    override suspend fun chat(
        config: AiConfig,
        messages: List<AiMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            when (config.provider) {
                AiProvider.ANTHROPIC -> callAnthropic(config, messages)
                AiProvider.OPENAI -> callOpenAi(config, messages)
            }
        }
    }

    override suspend fun ping(config: AiConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            chat(
                config,
                listOf(AiMessage(AiRole.USER, "Odpowiedz jednym słowem: pong"))
            ).getOrThrow()
            Unit
        }
    }

    override suspend fun chatWithImage(
        config: AiConfig,
        imageBase64: String,
        mimeType: String,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            when (config.provider) {
                AiProvider.ANTHROPIC -> callAnthropicWithImage(config, imageBase64, mimeType, userPrompt)
                AiProvider.OPENAI -> callOpenAiWithImage(config, imageBase64, mimeType, userPrompt)
            }
        }
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

    private fun callAnthropic(config: AiConfig, messages: List<AiMessage>): String {
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 4096)
            put("system", config.systemPrompt)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", if (m.role == AiRole.USER) "user" else "assistant")
                        put("content", m.content)
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
}
