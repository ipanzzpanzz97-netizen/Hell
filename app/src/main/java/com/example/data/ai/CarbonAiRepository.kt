package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.local.ChatMessageEntity
import com.example.data.settings.CarbonSettings
import com.example.data.settings.EngineMode
import com.example.data.settings.SafetyThresholdOption
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.system.measureTimeMillis

private const val TAG = "CarbonAiRepository"

sealed interface StreamChunk {
    data class Delta(
        val visibleText: String,
        val thinkingText: String? = null,
        val structuredJson: String? = null
    ) : StreamChunk

    data class Completed(
        val visibleText: String,
        val thinkingText: String? = null,
        val structuredJson: String? = null
    ) : StreamChunk
}

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long = 0L
)

class CarbonAiRepository(private val context: Context) {

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val carbonPayloadSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "status" to Schema.enumeration(
                    values = listOf("success", "error"),
                    description = "Execution status of the reasoning task"
                ),
                "thought_tokens_used" to Schema.integer(
                    description = "Estimated reasoning tokens used"
                ),
                "action_taken" to Schema.string(
                    description = "Primary agent action performed"
                ),
                "output_payload" to Schema.obj(
                    properties = mapOf(
                        "code_snippet" to Schema.string(
                            description = "Generated or repaired code snippet, or empty string if none"
                        ),
                        "explanation" to Schema.string(
                            description = "Detailed technical explanation in the user's language"
                        ),
                        "security_checks" to Schema.string(
                            description = "Security, edge-case, or bug verification summary"
                        )
                    ),
                    optionalProperties = listOf("code_snippet")
                )
            )
        )
    }

    fun streamReply(
        prompt: String,
        history: List<ChatMessageEntity>,
        imageUri: String?,
        settings: CarbonSettings
    ): Flow<StreamChunk> {
        return when (settings.engineMode) {
            EngineMode.CLOUD_BUILTIN -> streamViaFirebaseAi(prompt, history, imageUri, settings)
            EngineMode.CUSTOM_SERVER -> streamViaCustomApi(prompt, history, settings)
        }
    }

    private fun streamViaFirebaseAi(
        prompt: String,
        history: List<ChatMessageEntity>,
        imageUri: String?,
        settings: CarbonSettings
    ): Flow<StreamChunk> = flow {
        val model = buildFirebaseModel(settings)
        val bitmap = imageUri?.let { loadScaledBitmap(Uri.parse(it)) }

        val chatHistory: List<Content> = history
            .filter { !it.isError && it.content.isNotBlank() }
            .takeLast(24)
            .map { msg ->
                val role = if (msg.role == "user") "user" else "model"
                content(role) {
                    text(msg.structuredJson ?: msg.content)
                }
            }

        val userContent = content("user") {
            if (bitmap != null) {
                image(bitmap)
            }
            text(prompt)
        }

        val rawAccumulator = StringBuilder()

        try {
            withTimeout(60_000L) {
                val chat = model.startChat(history = chatHistory)
                chat.sendMessageStream(userContent).collect { chunk ->
                    currentCoroutineContext().ensureActive()
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        rawAccumulator.append(delta)
                        val parsed = parseReasoningAndContent(
                            raw = rawAccumulator.toString(),
                            isStructuredMode = settings.structuredOutputEnabled
                        )
                        emit(
                            StreamChunk.Delta(
                                visibleText = parsed.visibleText,
                                thinkingText = parsed.thinkingText,
                                structuredJson = parsed.structuredJson
                            )
                        )
                    }
                }
            }

            val finalRaw = rawAccumulator.toString().trim()
            if (finalRaw.isBlank()) {
                throw IllegalStateException("Model tidak memulangkan sebarang teks jawapan.")
            }

            val finalParsed = parseReasoningAndContent(
                raw = finalRaw,
                isStructuredMode = settings.structuredOutputEnabled
            )
            emit(
                StreamChunk.Completed(
                    visibleText = finalParsed.visibleText,
                    thinkingText = finalParsed.thinkingText,
                    structuredJson = finalParsed.structuredJson
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI inference timed out", e)
            throw IllegalStateException("Masa tamat selepas 60 saat. Cuba arahan yang lebih ringkas atau tukar model.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI inference failed", e)
            throw e
        } finally {
            bitmap?.recycle()
        }
    }.flowOn(Dispatchers.IO)

    private fun buildFirebaseModel(settings: CarbonSettings): GenerativeModel {
        val threshold = when (settings.safetyThreshold) {
            SafetyThresholdOption.ONLY_HIGH -> HarmBlockThreshold.ONLY_HIGH
            SafetyThresholdOption.MEDIUM_AND_ABOVE -> HarmBlockThreshold.MEDIUM_AND_ABOVE
            SafetyThresholdOption.LOW_AND_ABOVE -> HarmBlockThreshold.LOW_AND_ABOVE
        }

        val safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, threshold),
            SafetySetting(HarmCategory.HATE_SPEECH, threshold),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, threshold),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, threshold)
        )

        val config = generationConfig {
            temperature = settings.temperature.coerceIn(0f, 2f)
            if (settings.structuredOutputEnabled) {
                responseMimeType = "application/json"
                responseSchema = carbonPayloadSchema
            }
        }

        val systemInstructionContent = settings.systemPrompt
            .takeIf { it.isNotBlank() }
            ?.let { promptText ->
                content { text(promptText) }
            }

        return Firebase.ai.generativeModel(
            modelName = settings.cloudModel,
            generationConfig = config,
            safetySettings = safetySettings,
            systemInstruction = systemInstructionContent
        )
    }

    private fun streamViaCustomApi(
        prompt: String,
        history: List<ChatMessageEntity>,
        settings: CarbonSettings
    ): Flow<StreamChunk> = flow {
        val rawUrl = settings.customApiUrl.trim()
        if (rawUrl.isBlank()) {
            throw IllegalStateException("API URL pelayan awan tersuai belum diisi. Sila tetapkan dalam Tetapan API.")
        }
        val endpoint = resolveChatEndpoint(rawUrl)

        val messagesArray = JSONArray().apply {
            if (settings.systemPrompt.isNotBlank()) {
                val sysText = if (settings.structuredOutputEnabled) {
                    "${settings.systemPrompt}\n\nPulangkan output JSON berstruktur dengan medan: status, thought_tokens_used, action_taken, output_payload { code_snippet, explanation, security_checks }."
                } else {
                    settings.systemPrompt
                }
                put(JSONObject().put("role", "system").put("content", sysText))
            }
            history.filter { !it.isError && it.content.isNotBlank() }
                .takeLast(24)
                .forEach { msg ->
                    put(
                        JSONObject()
                            .put("role", if (msg.role == "user") "user" else "assistant")
                            .put("content", msg.content)
                    )
                }
            put(JSONObject().put("role", "user").put("content", prompt))
        }

        val payload = JSONObject().apply {
            put("model", settings.customModelName.ifBlank { "carbon-pro" })
            put("messages", messagesArray)
            put("temperature", settings.temperature.toDouble())
            put("stream", settings.customUseSse)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addHeader("Accept", if (settings.customUseSse) "text/event-stream" else "application/json")

        if (settings.customApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${settings.customApiKey.trim()}")
        }

        val rawContent = StringBuilder()
        val rawThinking = StringBuilder()

        try {
            withTimeout(120_000L) {
                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body?.string().orEmpty().take(300)
                        throw IllegalStateException("Ralat pelayan (${resp.code}): ${errBody.ifBlank { resp.message }}")
                    }

                    val body = resp.body ?: throw IllegalStateException("Pelayan memulangkan respons kosong.")
                    val contentType = resp.header("Content-Type").orEmpty()

                    if (settings.customUseSse && contentType.contains("text/event-stream", ignoreCase = true)) {
                        BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                currentCoroutineContext().ensureActive()
                                val trimmed = line?.trim().orEmpty()
                                if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                                if (!trimmed.startsWith("data:")) continue

                                val dataPart = trimmed.removePrefix("data:").trim()
                                if (dataPart == "[DONE]") break

                                val json = runCatching { JSONObject(dataPart) }.getOrNull() ?: continue
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    if (delta != null) {
                                        val reasoningDelta = delta.optString("reasoning_content", "")
                                        val contentDelta = delta.optString("content", "")
                                        if (reasoningDelta.isNotEmpty()) rawThinking.append(reasoningDelta)
                                        if (contentDelta.isNotEmpty()) rawContent.append(contentDelta)
                                    }
                                } else {
                                    // Ollama /api/chat style chunk fallback
                                    val msgObj = json.optJSONObject("message")
                                    val contentDelta = msgObj?.optString("content", "")
                                        ?: json.optString("response", "")
                                    if (contentDelta.isNotEmpty()) rawContent.append(contentDelta)
                                }

                                val parsed = parseReasoningAndContent(
                                    raw = rawContent.toString(),
                                    explicitThinking = rawThinking.toString().ifBlank { null },
                                    isStructuredMode = settings.structuredOutputEnabled
                                )
                                emit(
                                    StreamChunk.Delta(
                                        visibleText = parsed.visibleText,
                                        thinkingText = parsed.thinkingText,
                                        structuredJson = parsed.structuredJson
                                    )
                                )
                            }
                        }
                    } else {
                        val responseText = body.string()
                        val extracted = extractNonStreamingResponse(responseText)
                        rawContent.append(extracted.first)
                        if (!extracted.second.isNullOrBlank()) {
                            rawThinking.append(extracted.second)
                        }
                    }
                }
            }

            val finalText = rawContent.toString().trim()
            if (finalText.isBlank()) {
                throw IllegalStateException("Pelayan API tidak memulangkan sebarang teks jawapan.")
            }

            val finalParsed = parseReasoningAndContent(
                raw = finalText,
                explicitThinking = rawThinking.toString().ifBlank { null },
                isStructuredMode = settings.structuredOutputEnabled
            )
            emit(
                StreamChunk.Completed(
                    visibleText = finalParsed.visibleText,
                    thinkingText = finalParsed.thinkingText,
                    structuredJson = finalParsed.structuredJson
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Custom API request timed out", e)
            throw IllegalStateException("Sambungan ke pelayan tersuai tamat masa.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Custom API request failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    suspend fun testCustomConnection(apiUrl: String, apiKey: String): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val cleaned = apiUrl.trim().trimEnd('/')
            if (cleaned.isBlank()) {
                return@withContext ConnectionTestResult(false, "Sila masukkan API URL terlebih dahulu.")
            }
            val probeUrl = when {
                cleaned.endsWith("/v1/chat/completions") -> cleaned.removeSuffix("/chat/completions") + "/models"
                cleaned.endsWith("/v1") -> "$cleaned/models"
                else -> "$cleaned/v1/models"
            }

            try {
                var code = 0
                var msg = ""
                val elapsed = measureTimeMillis {
                    val reqBuilder = Request.Builder().url(probeUrl).get()
                    if (apiKey.isNotBlank()) {
                        reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    }
                    okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        code = resp.code
                        msg = resp.message
                    }
                }
                if (code in 200..299) {
                    ConnectionTestResult(true, "Berjaya berhubung (HTTP $code)", elapsed)
                } else {
                    ConnectionTestResult(false, "Pelayan membalas HTTP $code ($msg)", elapsed)
                }
            } catch (e: Exception) {
                ConnectionTestResult(false, e.localizedMessage ?: "Gagal menyambung ke pelayan.")
            }
        }

    private fun resolveChatEndpoint(rawUrl: String): String {
        val clean = rawUrl.trim().trimEnd('/')
        return when {
            clean.endsWith("/chat/completions") || clean.endsWith("/api/chat") -> clean
            clean.endsWith("/v1") -> "$clean/chat/completions"
            else -> "$clean/v1/chat/completions"
        }
    }

    private fun extractNonStreamingResponse(rawJson: String): Pair<String, String?> {
        val obj = runCatching { JSONObject(rawJson) }.getOrNull()
            ?: return rawJson to null

        // OpenAI / vLLM / TGI compatible
        val choices = obj.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val first = choices.getJSONObject(0)
            val message = first.optJSONObject("message")
            if (message != null) {
                val content = message.optString("content", "")
                val reasoning = message.optString("reasoning_content", "").ifBlank { null }
                return content to reasoning
            }
            val text = first.optString("text", "")
            if (text.isNotBlank()) return text to null
        }

        // Ollama /api/chat
        val messageObj = obj.optJSONObject("message")
        if (messageObj != null) {
            val content = messageObj.optString("content", "")
            if (content.isNotBlank()) return content to null
        }

        // Direct CARBON-PRO JSON payload
        if (obj.has("output_payload")) {
            return obj.toString(2) to null
        }

        return rawJson to null
    }

    private data class ParsedOutput(
        val visibleText: String,
        val thinkingText: String?,
        val structuredJson: String?
    )

    private fun parseReasoningAndContent(
        raw: String,
        explicitThinking: String? = null,
        isStructuredMode: Boolean
    ): ParsedOutput {
        var thinking = explicitThinking
        var visible = raw

        // Extract <thought>...</thought> or <think>...</think> if emitted by reasoning models
        val tagRegex = Regex("<(thought|think)>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
        val match = tagRegex.find(raw)
        if (match != null) {
            val extracted = match.groupValues[2].trim()
            thinking = listOfNotNull(thinking, extracted).joinToString("\n\n").ifBlank { null }
            visible = raw.removeRange(match.range).trim()
        } else if (raw.contains("<think>") && !raw.contains("</think>")) {
            val idx = raw.indexOf("<think>")
            val streamingThought = raw.substring(idx + "<think>".length).trim()
            thinking = listOfNotNull(thinking, streamingThought).joinToString("\n\n").ifBlank { null }
            visible = raw.substring(0, idx).trim().ifBlank { "Sedang menaakul..." }
        }

        var structuredJson: String? = null
        if (isStructuredMode) {
            val trimmed = visible.trim()
            val candidateJson = if (trimmed.startsWith("```")) {
                trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            } else {
                trimmed
            }
            val obj = runCatching { JSONObject(candidateJson) }.getOrNull()
            if (obj != null && obj.has("output_payload")) {
                structuredJson = obj.toString(2)
                val payload = obj.optJSONObject("output_payload")
                val explanation = payload?.optString("explanation").orEmpty()
                val code = payload?.optString("code_snippet").orEmpty()
                visible = buildString {
                    if (explanation.isNotBlank()) append(explanation)
                    if (code.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append("```\n").append(code).append("\n```")
                    }
                }.ifBlank { structuredJson }
            }
        }

        return ParsedOutput(
            visibleText = visible,
            thinkingText = thinking,
            structuredJson = structuredJson
        )
    }

    private fun loadScaledBitmap(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val maxDim = max(options.outWidth, options.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 1280) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode attached image", e)
            null
        }
    }
}
