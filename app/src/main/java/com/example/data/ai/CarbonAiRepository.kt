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
import com.google.firebase.FirebaseApp
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
    data class Phase(val label: String) : StreamChunk
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
        sessionMemorySummary: String,
        imageUri: String?,
        settings: CarbonSettings
    ): Flow<StreamChunk> {
        return when (settings.engineMode) {
            EngineMode.CLOUD_BUILTIN -> streamViaFirebaseAi(
                prompt, history, sessionMemorySummary, imageUri, settings
            )
            EngineMode.CUSTOM_SERVER -> streamViaCustomApi(
                prompt, history, sessionMemorySummary, settings
            )
        }
    }

    suspend fun compressSessionMemory(
        coldMessages: List<ChatMessageEntity>,
        existingSummary: String,
        settings: CarbonSettings
    ): String? = withContext(Dispatchers.IO) {
        if (coldMessages.isEmpty()) return@withContext null
        val transcript = coldMessages.joinToString("\n") { msg ->
            "${msg.role.uppercase()}: ${msg.content.take(500)}"
        }
        val compressPrompt = buildString {
            appendLine("Ringkaskan sejarah perbualan lama berikut menjadi memori teknikal padat (maksimum 180 patah perkataan).")
            appendLine("Kekalkan hanya: keputusan seni bina, pemboleh ubah/API penting, kekangan pengguna, dan matlamat semasa.")
            if (existingSummary.isNotBlank()) {
                appendLine("\nMemori Sedia Ada:\n$existingSummary")
            }
            appendLine("\nMesej Untuk Dimampatkan:\n$transcript")
        }

        return@withContext runCatching {
            when (settings.engineMode) {
                EngineMode.CLOUD_BUILTIN -> {
                    if (FirebaseApp.getApps(context).isEmpty()) {
                        FirebaseApp.initializeApp(context)
                    }
                    if (FirebaseApp.getApps(context).isEmpty()) return@runCatching null
                    val model = buildFirebaseModel(settings.copy(structuredOutputEnabled = false), forceFastModel = true)
                    withTimeout(30_000L) {
                        model.generateContent(compressPrompt).text?.trim()
                    }
                }
                EngineMode.CUSTOM_SERVER -> {
                    if (settings.customApiUrl.isBlank()) return@runCatching null
                    executeCustomSingleShot(compressPrompt, settings)
                }
            }
        }.getOrNull()
    }

    private fun streamViaFirebaseAi(
        prompt: String,
        history: List<ChatMessageEntity>,
        sessionMemorySummary: String,
        imageUri: String?,
        settings: CarbonSettings
    ): Flow<StreamChunk> = flow {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            throw IllegalStateException(
                "FirebaseApp belum diinisialisasi kerana fail konfigurasi app/google-services.json tiada semasa APK ini dibina.\n\n" +
                    "Penyelesaian:\n" +
                    "1. Jika anda bina APK melalui GitHub Actions, pastikan app/google-services.json wujud atau tambah secret GOOGLE_SERVICES_JSON di GitHub Repository Secrets.\n" +
                    "2. Atau eksport APK terus dari AI Studio (Settings > Generate APK).\n" +
                    "3. Atau buka Tetapan API & Enjin dan guna mod 'Pelayan Tersuai' dengan API_URL anda."
            )
        }

        val bitmap = imageUri?.let { loadScaledBitmap(Uri.parse(it)) }

        try {
            val hotHistory = buildFirebaseHistory(history, sessionMemorySummary)
            var deepAnalysisText: String? = null

            if (settings.deepAgentMode) {
                emit(StreamChunk.Phase("Fasa 1/2: Analisis, Hipotesis & Semakan Kekangan..."))
                val plannerModel = buildFirebaseModel(
                    settings = settings.copy(structuredOutputEnabled = false, temperature = 0.35f),
                    systemOverride = DEEP_PLANNER_INSTRUCTION
                )
                val analysisBuffer = StringBuilder()
                val plannerInput = content("user") {
                    if (bitmap != null) image(bitmap)
                    text(
                        buildString {
                            if (sessionMemorySummary.isNotBlank()) {
                                appendLine("[Memori Konteks Terkompresi]: $sessionMemorySummary\n")
                            }
                            append(prompt)
                        }
                    )
                }

                withTimeout(45_000L) {
                    plannerModel.startChat(history = hotHistory.takeLast(8))
                        .sendMessageStream(plannerInput)
                        .collect { chunk ->
                            currentCoroutineContext().ensureActive()
                            val delta = chunk.text.orEmpty()
                            if (delta.isNotEmpty()) {
                                analysisBuffer.append(delta)
                                emit(
                                    StreamChunk.Delta(
                                        visibleText = "Menjalankan Fasa 1/2 (Analisis & Penjanaan Hipotesis)...",
                                        thinkingText = analysisBuffer.toString()
                                    )
                                )
                            }
                        }
                }
                deepAnalysisText = analysisBuffer.toString().trim().ifBlank { null }
            }

            emit(
                StreamChunk.Phase(
                    if (settings.deepAgentMode) {
                        "Fasa 2/2: Simulasi, Semakan Kendiri & Sintesis Akhir..."
                    } else {
                        "Menjana jawapan..."
                    }
                )
            )

            val synthesizerModel = buildFirebaseModel(settings)
            val finalPromptText = if (deepAnalysisText != null) {
                buildString {
                    appendLine("Permintaan Pengguna:")
                    appendLine(prompt)
                    appendLine("\n[Draf Analisis & Hipotesis Dalaman (Fasa 1)]:")
                    appendLine(deepAnalysisText)
                    appendLine(
                        "\nArahan Fasa 2 (Build-and-Verify): Semak draf analisis di atas untuk sebarang pepijat logik, API usang, atau kelemahan keselamatan. Betulkan secara kendiri dan hasilkan jawapan akhir yang lengkap, tepat, serta sedia digunakan."
                    )
                }
            } else {
                prompt
            }

            val finalContent = content("user") {
                if (bitmap != null) image(bitmap)
                text(finalPromptText)
            }

            val rawAccumulator = StringBuilder()
            withTimeout(60_000L) {
                synthesizerModel.startChat(history = hotHistory)
                    .sendMessageStream(finalContent)
                    .collect { chunk ->
                        currentCoroutineContext().ensureActive()
                        val delta = chunk.text.orEmpty()
                        if (delta.isNotEmpty()) {
                            rawAccumulator.append(delta)
                            val parsed = parseReasoningAndContent(
                                raw = rawAccumulator.toString(),
                                explicitThinking = deepAnalysisText,
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
                explicitThinking = deepAnalysisText,
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

    private fun buildFirebaseHistory(
        history: List<ChatMessageEntity>,
        sessionMemorySummary: String
    ): List<Content> {
        val list = mutableListOf<Content>()
        if (sessionMemorySummary.isNotBlank()) {
            list.add(content("user") { text("[Memori Konteks Sesi Terdahulu]\n$sessionMemorySummary") })
            list.add(content("model") { text("Difahami. Saya mengekalkan konteks terkompresi tersebut dalam memori aktif.") })
        }
        history.filter { !it.isError && it.content.isNotBlank() }
            .takeLast(14)
            .forEach { msg ->
                val role = if (msg.role == "user") "user" else "model"
                list.add(content(role) { text(msg.structuredJson ?: msg.content) })
            }
        return list
    }

    private fun buildFirebaseModel(
        settings: CarbonSettings,
        systemOverride: String? = null,
        forceFastModel: Boolean = false
    ): GenerativeModel {
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

        val effectiveSystem = (systemOverride ?: settings.systemPrompt)
            .takeIf { it.isNotBlank() }
            ?.let { content { text(it) } }

        val targetModel = if (forceFastModel) "gemini-3.5-flash-lite" else settings.cloudModel

        return Firebase.ai.generativeModel(
            modelName = targetModel,
            generationConfig = config,
            safetySettings = safetySettings,
            systemInstruction = effectiveSystem
        )
    }

    private fun streamViaCustomApi(
        prompt: String,
        history: List<ChatMessageEntity>,
        sessionMemorySummary: String,
        settings: CarbonSettings
    ): Flow<StreamChunk> = flow {
        val rawUrl = settings.customApiUrl.trim()
        if (rawUrl.isBlank()) {
            throw IllegalStateException("API URL pelayan awan tersuai belum diisi. Sila tetapkan dalam Tetapan API.")
        }
        val endpoint = resolveChatEndpoint(rawUrl)

        var deepAnalysisText: String? = null
        if (settings.deepAgentMode) {
            emit(StreamChunk.Phase("Fasa 1/2: Analisis & Penjanaan Hipotesis (Pelayan Tersuai)..."))
            val planPrompt = buildString {
                if (sessionMemorySummary.isNotBlank()) {
                    appendLine("[Memori Konteks]: $sessionMemorySummary\n")
                }
                appendLine(DEEP_PLANNER_INSTRUCTION)
                appendLine("\nPermintaan Pengguna:\n$prompt")
            }
            deepAnalysisText = runCatching {
                executeCustomSingleShot(planPrompt, settings.copy(temperature = 0.35f))
            }.getOrNull()?.trim()?.ifBlank { null }
        }

        emit(StreamChunk.Phase(if (settings.deepAgentMode) "Fasa 2/2: Semakan Kendiri & Menstrim Jawapan..." else "Menghubungi pelayan API..."))

        val finalUserPrompt = if (deepAnalysisText != null) {
            "$prompt\n\n[Analisis Fasa 1]:\n$deepAnalysisText\n\nSemak analisis di atas, perbaiki sebarang ralat, dan berikan jawapan akhir yang lengkap."
        } else {
            prompt
        }

        val messagesArray = buildCustomMessagesArray(finalUserPrompt, history, sessionMemorySummary, settings)
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
        val rawThinking = StringBuilder(deepAnalysisText.orEmpty())

        try {
            withTimeout(120_000L) {
                okHttpClient.newCall(requestBuilder.build()).execute().use { resp ->
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
                                if (trimmed.isEmpty() || trimmed.startsWith(":") || !trimmed.startsWith("data:")) continue

                                val dataPart = trimmed.removePrefix("data:").trim()
                                if (dataPart == "[DONE]") break

                                val json = runCatching { JSONObject(dataPart) }.getOrNull() ?: continue
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    if (delta != null) {
                                        val reasoningDelta = delta.optString("reasoning_content", "")
                                        val contentDelta = delta.optString("content", "")
                                        if (reasoningDelta.isNotEmpty()) {
                                            if (rawThinking.isNotEmpty() && !rawThinking.endsWith("\n")) rawThinking.append("\n")
                                            rawThinking.append(reasoningDelta)
                                        }
                                        if (contentDelta.isNotEmpty()) rawContent.append(contentDelta)
                                    }
                                } else {
                                    val msgObj = json.optJSONObject("message")
                                    val contentDelta = msgObj?.optString("content", "") ?: json.optString("response", "")
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
                        val extracted = extractNonStreamingResponse(body.string())
                        rawContent.append(extracted.first)
                        if (!extracted.second.isNullOrBlank()) {
                            if (rawThinking.isNotEmpty()) rawThinking.append("\n\n")
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

    private fun executeCustomSingleShot(promptText: String, settings: CarbonSettings): String {
        val endpoint = resolveChatEndpoint(settings.customApiUrl)
        val payload = JSONObject().apply {
            put("model", settings.customModelName.ifBlank { "carbon-pro" })
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", promptText)))
            put("temperature", settings.temperature.toDouble())
            put("stream", false)
        }
        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (settings.customApiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer ${settings.customApiKey.trim()}")
        }
        okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty()
            return extractNonStreamingResponse(text).first
        }
    }

    private fun buildCustomMessagesArray(
        prompt: String,
        history: List<ChatMessageEntity>,
        sessionMemorySummary: String,
        settings: CarbonSettings
    ): JSONArray = JSONArray().apply {
        val sysPrompt = buildString {
            if (settings.systemPrompt.isNotBlank()) append(settings.systemPrompt)
            if (sessionMemorySummary.isNotBlank()) {
                append("\n\n[Memori Konteks Terkompresi]:\n").append(sessionMemorySummary)
            }
            if (settings.structuredOutputEnabled) {
                append("\n\nPulangkan output JSON berstruktur dengan medan: status, thought_tokens_used, action_taken, output_payload { code_snippet, explanation, security_checks }.")
            }
        }
        if (sysPrompt.isNotBlank()) {
            put(JSONObject().put("role", "system").put("content", sysPrompt))
        }
        history.filter { !it.isError && it.content.isNotBlank() }
            .takeLast(16)
            .forEach { msg ->
                put(
                    JSONObject()
                        .put("role", if (msg.role == "user") "user" else "assistant")
                        .put("content", msg.content)
                )
            }
        put(JSONObject().put("role", "user").put("content", prompt))
    }

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
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return rawJson to null
        val choices = obj.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val first = choices.getJSONObject(0)
            val message = first.optJSONObject("message")
            if (message != null) {
                return message.optString("content", "") to message.optString("reasoning_content", "").ifBlank { null }
            }
            val text = first.optString("text", "")
            if (text.isNotBlank()) return text to null
        }
        val messageObj = obj.optJSONObject("message")
        if (messageObj != null) {
            val content = messageObj.optString("content", "")
            if (content.isNotBlank()) return content to null
        }
        if (obj.has("output_payload")) return obj.toString(2) to null
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
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            val maxDim = max(options.outWidth, options.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 1280) sampleSize *= 2
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode attached image", e)
            null
        }
    }

    companion object {
        private val DEEP_PLANNER_INSTRUCTION = """
            Anda ialah Modul Pemikiran Fasa 1 CARBON-PRO (Thinking Protocol).
            Tugas anda HANYA menghasilkan pelan penaakulan dalaman yang ringkas dan tajam (maksimum 220 patah perkataan) dalam bentuk poin bernombor:
            1. Analisis kebergantungan, API, dan kekangan teknikal.
            2. Bandingkan 2 pendekatan/hipotesis penyelesaian terbaik dan pilih yang paling tepat.
            3. Kenal pasti potensi pepijat, kes pinggiran, atau risiko keselamatan yang mesti dielakkan dalam Fasa 2.
        """.trimIndent()
    }
}
