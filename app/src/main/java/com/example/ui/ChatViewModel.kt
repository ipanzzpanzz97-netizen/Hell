package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.CarbonAiRepository
import com.example.data.ai.ConnectionTestResult
import com.example.data.ai.StreamChunk
import com.example.data.local.ChatDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.settings.CarbonSettings
import com.example.data.settings.EngineMode
import com.example.data.settings.SafetyThresholdOption
import com.example.data.settings.SettingsRepository
import com.example.data.settings.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "ChatViewModel"

sealed interface GenerationState {
    data object Idle : GenerationState
    data class Thinking(val phaseLabel: String = "CarbonPro sedang menganalisis & menaakul...") : GenerationState
    data class Streaming(
        val messageId: String,
        val phaseLabel: String,
        val visibleText: String,
        val thinkingText: String?
    ) : GenerationState
    data class Error(val message: String) : GenerationState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ChatDatabase.getInstance(application).chatDao()
    private val settingsRepo = SettingsRepository(application)
    private val aiRepository = CarbonAiRepository(application)

    val settings: StateFlow<CarbonSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarbonSettings())

    val sessions: StateFlow<List<ChatSessionEntity>> = dao.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    val messages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else dao.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _connectionTestState = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestState: StateFlow<ConnectionTestResult?> = _connectionTestState.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            sessions.collect { list ->
                if (_activeSessionId.value == null && list.isNotEmpty()) {
                    _activeSessionId.value = list.first().id
                }
            }
        }
    }

    fun startNewSession() {
        stopGeneration()
        _activeSessionId.value = null
        _generationState.value = GenerationState.Idle
    }

    fun selectSession(sessionId: String) {
        if (_activeSessionId.value == sessionId) return
        stopGeneration()
        _activeSessionId.value = sessionId
        _generationState.value = GenerationState.Idle
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_activeSessionId.value == sessionId) {
                stopGeneration()
                _activeSessionId.value = null
            }
            dao.deleteSession(sessionId)
        }
    }

    fun clearCurrentSessionMessages() {
        val currentId = _activeSessionId.value ?: return
        viewModelScope.launch {
            stopGeneration()
            dao.clearMessagesInSession(currentId)
            _generationState.value = GenerationState.Idle
        }
    }

    fun sendMessage(promptText: String, imageUri: String? = null) {
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty() && imageUri == null) return
        val effectivePrompt = cleanPrompt.ifEmpty { "Analisis imej yang dilampirkan secara terperinci." }

        stopGeneration()

        activeGenerationJob = viewModelScope.launch {
            val currentSettings = settings.value
            val now = System.currentTimeMillis()
            val sessionId = _activeSessionId.value ?: UUID.randomUUID().toString().also { newId ->
                val title = effectivePrompt.take(44).let {
                    if (effectivePrompt.length > 44) "$it..." else it
                }
                val engineLabel = if (currentSettings.engineMode == EngineMode.CLOUD_BUILTIN) {
                    currentSettings.cloudModel
                } else {
                    currentSettings.customModelName.ifBlank { "custom-api" }
                }
                dao.upsertSession(
                    ChatSessionEntity(
                        id = newId,
                        title = title,
                        engineLabel = engineLabel,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                _activeSessionId.value = newId
            }

            val historyBeforeUser = dao.getMessagesOnce(sessionId)

            val userMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "user",
                content = effectivePrompt,
                imageUri = imageUri,
                timestamp = now
            )
            dao.upsertMessage(userMsg)

            val existingSession = dao.getSession(sessionId)
            if (existingSession != null) {
                dao.upsertSession(existingSession.copy(updatedAt = now))
            }

            runInference(
                sessionId = sessionId,
                prompt = effectivePrompt,
                history = historyBeforeUser,
                sessionMemorySummary = existingSession?.memorySummary.orEmpty(),
                imageUri = imageUri,
                currentSettings = currentSettings
            )
        }
    }

    fun regenerateLastResponse() {
        val currentId = _activeSessionId.value ?: return
        stopGeneration()
        activeGenerationJob = viewModelScope.launch {
            val allMessages = dao.getMessagesOnce(currentId)
            val lastUserIndex = allMessages.indexOfLast { it.role == "user" }
            if (lastUserIndex == -1) return@launch

            val lastUserMsg = allMessages[lastUserIndex]
            allMessages.drop(lastUserIndex + 1).forEach { dao.deleteMessage(it.id) }

            val historyBeforeUser = allMessages.take(lastUserIndex)
            val session = dao.getSession(currentId)
            runInference(
                sessionId = currentId,
                prompt = lastUserMsg.content,
                history = historyBeforeUser,
                sessionMemorySummary = session?.memorySummary.orEmpty(),
                imageUri = lastUserMsg.imageUri,
                currentSettings = settings.value
            )
        }
    }

    private suspend fun runInference(
        sessionId: String,
        prompt: String,
        history: List<ChatMessageEntity>,
        sessionMemorySummary: String,
        imageUri: String?,
        currentSettings: CarbonSettings
    ) {
        val assistantMsgId = UUID.randomUUID().toString()
        var currentPhase = if (currentSettings.deepAgentMode) {
            "Fasa 1/2: Analisis & Penjanaan Hipotesis..."
        } else {
            "CarbonPro sedang menjana jawapan..."
        }
        _generationState.value = GenerationState.Thinking(currentPhase)

        var latestVisible = ""
        var latestThinking: String? = null
        var latestStructured: String? = null

        try {
            aiRepository.streamReply(
                prompt = prompt,
                history = history,
                sessionMemorySummary = sessionMemorySummary,
                imageUri = imageUri,
                settings = currentSettings
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Phase -> {
                        currentPhase = chunk.label
                        if (latestVisible.isBlank()) {
                            _generationState.value = GenerationState.Thinking(currentPhase)
                        } else {
                            _generationState.value = GenerationState.Streaming(
                                messageId = assistantMsgId,
                                phaseLabel = currentPhase,
                                visibleText = latestVisible,
                                thinkingText = latestThinking
                            )
                        }
                    }
                    is StreamChunk.Delta -> {
                        latestVisible = chunk.visibleText
                        latestThinking = chunk.thinkingText
                        latestStructured = chunk.structuredJson
                        _generationState.value = GenerationState.Streaming(
                            messageId = assistantMsgId,
                            phaseLabel = currentPhase,
                            visibleText = chunk.visibleText,
                            thinkingText = chunk.thinkingText
                        )
                    }
                    is StreamChunk.Completed -> {
                        latestVisible = chunk.visibleText
                        latestThinking = chunk.thinkingText
                        latestStructured = chunk.structuredJson
                        val ts = System.currentTimeMillis()
                        dao.upsertMessage(
                            ChatMessageEntity(
                                id = assistantMsgId,
                                sessionId = sessionId,
                                role = "assistant",
                                content = chunk.visibleText,
                                thinkingTrace = chunk.thinkingText,
                                structuredJson = chunk.structuredJson,
                                timestamp = ts
                            )
                        )
                        dao.getSession(sessionId)?.let { session ->
                            dao.upsertSession(session.copy(updatedAt = ts))
                        }
                        _generationState.value = GenerationState.Idle
                        maybeCompressColdMemory(sessionId, currentSettings)
                    }
                }
            }
        } catch (e: CancellationException) {
            if (latestVisible.isNotBlank() && !latestVisible.startsWith("Menjalankan Fasa 1/2")) {
                dao.upsertMessage(
                    ChatMessageEntity(
                        id = assistantMsgId,
                        sessionId = sessionId,
                        role = "assistant",
                        content = "$latestVisible\n\n*(Penjanaan dihentikan oleh pengguna)*",
                        thinkingTrace = latestThinking,
                        structuredJson = latestStructured,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            _generationState.value = GenerationState.Idle
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            val errMsg = e.localizedMessage ?: "Ralat tidak diketahui semasa penjanaan AI."
            dao.upsertMessage(
                ChatMessageEntity(
                    id = assistantMsgId,
                    sessionId = sessionId,
                    role = "assistant",
                    content = errMsg,
                    timestamp = System.currentTimeMillis(),
                    isError = true
                )
            )
            _generationState.value = GenerationState.Error(errMsg)
        }
    }

    private fun maybeCompressColdMemory(sessionId: String, currentSettings: CarbonSettings) {
        viewModelScope.launch {
            val all = dao.getMessagesOnce(sessionId).filter { !it.isError }
            if (all.size <= 16) return@launch
            val session = dao.getSession(sessionId) ?: return@launch
            val coldSlice = all.dropLast(12).takeLast(6)
            val compressed = aiRepository.compressSessionMemory(
                coldMessages = coldSlice,
                existingSummary = session.memorySummary,
                settings = currentSettings
            )
            if (!compressed.isNullOrBlank()) {
                dao.upsertSession(session.copy(memorySummary = compressed))
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        if (_generationState.value !is GenerationState.Idle) {
            _generationState.value = GenerationState.Idle
        }
    }

    fun dismissError() {
        if (_generationState.value is GenerationState.Error) {
            _generationState.value = GenerationState.Idle
        }
    }

    // Settings updates
    fun setEngineMode(mode: EngineMode) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(engineMode = mode) } }
    }

    fun setCloudModel(model: String) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(cloudModel = model) } }
    }

    fun setSafetyThreshold(option: SafetyThresholdOption) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(safetyThreshold = option) } }
    }

    fun updateCustomServerConfig(url: String, apiKey: String, modelName: String, useSse: Boolean) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(
                    customApiUrl = url,
                    customApiKey = apiKey,
                    customModelName = modelName,
                    customUseSse = useSse
                )
            }
        }
    }

    fun setSystemPrompt(prompt: String) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(systemPrompt = prompt) } }
    }

    fun resetSystemPrompt() {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(systemPrompt = CarbonSettings.DEFAULT_CARBON_SYSTEM_PROMPT) }
        }
    }

    fun setStructuredOutputEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(structuredOutputEnabled = enabled) } }
    }

    fun setDeepAgentMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(deepAgentMode = enabled) } }
    }

    fun setTemperature(temp: Float) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(temperature = temp) } }
    }

    fun setThemePreference(pref: ThemePreference) {
        viewModelScope.launch { settingsRepo.updateSettings { it.copy(themePreference = pref) } }
    }

    fun testCustomServerConnection(url: String, apiKey: String) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestState.value = null
            _connectionTestState.value = aiRepository.testCustomConnection(url, apiKey)
            _isTestingConnection.value = false
        }
    }
}
