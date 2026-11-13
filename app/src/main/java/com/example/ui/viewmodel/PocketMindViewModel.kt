package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.ConnectivityMonitor
import com.example.ai.DeviceNetworkState
import com.example.ai.ExtractiveNlpEngine
import com.example.ai.ExtractiveSummaryResult
import com.example.ai.GeminiNanoEngine
import com.example.ai.GeminiNanoStatus
import com.example.ai.LiteRtLmEngine
import com.example.ai.LiteRtLoadState
import com.example.ai.LocalModelFile
import com.example.ai.ModelDownloadManager
import com.example.ai.ModelTransferState
import com.example.ai.NoteRetrievalMatch
import com.example.ai.OnlineKnowledgeSyncManager
import com.example.ai.RemoteModelUpdateInfo
import com.example.data.guide.LiteRtModelSpec
import com.example.data.guide.OfflineAiCatalog
import com.example.data.local.AgentProfileEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.KnowledgeNoteEntity
import com.example.data.local.PocketMindDatabase
import com.example.data.local.SelfImprovementLogEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.preferences.AcceleratorMode
import com.example.data.preferences.PreferredBackend
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.UserPreferences
import com.example.data.repository.ChatRepository
import com.example.data.repository.NotesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveResolvedEngine(val label: String, val isGenerativeReady: Boolean) {
    LITERT_LM("LiteRT-LM", true),
    GEMINI_NANO("Gemini Nano", true),
    EXTRACTIVE_NLP("Built-in NLP", false),
    NONE("No LLM Loaded", false),
}

enum class RewriteTone(val label: String, val instruction: String) {
    CONCISE("Concise", "Rewrite the text to be clear, tight, and concise while preserving all key facts."),
    PROFESSIONAL("Professional", "Rewrite the text in a polished, professional tone with improved grammar."),
    FRIENDLY("Friendly", "Rewrite the text in a warm, natural, conversational tone."),
    PROOFREAD("Fix Grammar", "Fix spelling, punctuation, and grammar errors with minimal changes to voice."),
}

data class ToolsUiState(
    val summaryInput: String = "",
    val summaryBulletCount: Int = 4,
    val extractiveSummary: ExtractiveSummaryResult? = null,
    val llmSummaryOutput: String = "",
    val isRunningTool: Boolean = false,
    val rewriteInput: String = "",
    val rewriteTone: RewriteTone = RewriteTone.CONCISE,
    val rewriteOutput: String = "",
    val notesQuery: String = "",
    val notesMatches: List<NoteRetrievalMatch> = emptyList(),
    val notesSynthesizedAnswer: String = "",
    val learnTopicInput: String = "",
    val toolError: String? = null,
)

class PocketMindViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PocketMindDatabase.getInstance(application)
    private val dao = database.pocketMindDao()
    private val chatRepository = ChatRepository(dao)
    private val notesRepository = NotesRepository(dao)
    private val settingsRepository = SettingsRepository(application)

    private val extractiveNlp = ExtractiveNlpEngine()
    private val geminiNanoEngine = GeminiNanoEngine()
    private val liteRtLmEngine = LiteRtLmEngine(application)
    private val modelDownloadManager = ModelDownloadManager(application)
    private val connectivityMonitor = ConnectivityMonitor(application)
    private val onlineSyncManager = OnlineKnowledgeSyncManager(dao)

    val preferences: StateFlow<UserPreferences> = settingsRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val sessions: StateFlow<List<ChatSessionEntity>> = chatRepository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<KnowledgeNoteEntity>> = notesRepository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncQueue: StateFlow<List<SyncQueueEntity>> = dao.observeSyncQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agents: StateFlow<List<AgentProfileEntity>> = dao.observeAgents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val improvementLogs: StateFlow<List<SelfImprovementLogEntity>> = dao.observeImprovementLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val geminiStatus: StateFlow<GeminiNanoStatus> = geminiNanoEngine.status
    val liteRtState: StateFlow<LiteRtLoadState> = liteRtLmEngine.loadState
    val localModels: StateFlow<List<LocalModelFile>> = modelDownloadManager.localModels
    val transferState: StateFlow<ModelTransferState> = modelDownloadManager.transferState
    val networkState: StateFlow<DeviceNetworkState> = connectivityMonitor.networkState

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else chatRepository.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingReply = MutableStateFlow("")
    val streamingReply: StateFlow<String> = _streamingReply.asStateFlow()

    private val _chatNotice = MutableStateFlow<String?>(null)
    val chatNotice: StateFlow<String?> = _chatNotice.asStateFlow()

    private val _isSyncingOnline = MutableStateFlow(false)
    val isSyncingOnline: StateFlow<Boolean> = _isSyncingOnline.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    private val _remoteModelUpdates = MutableStateFlow<List<RemoteModelUpdateInfo>>(emptyList())
    val remoteModelUpdates: StateFlow<List<RemoteModelUpdateInfo>> = _remoteModelUpdates.asStateFlow()

    private val _toolsState = MutableStateFlow(ToolsUiState())
    val toolsState: StateFlow<ToolsUiState> = _toolsState.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var activeTransferJob: Job? = null
    private var syncJob: Job? = null

    init {
        viewModelScope.launch {
            geminiNanoEngine.refreshStatus()
        }
        viewModelScope.launch {
            sessions.collect { list ->
                if (_activeSessionId.value == null && list.isNotEmpty()) {
                    _activeSessionId.value = list.first().id
                }
            }
        }
        // Opportunistic auto-learn & self-upgrade watcher when device goes online
        viewModelScope.launch {
            combine(networkState, preferences, syncQueue) { net, prefs, queue ->
                Triple(net, prefs, queue)
            }.collect { (net, prefs, queue) ->
                val canSyncOnNetwork = net.isConnected && (!prefs.wifiOnlySync || net == DeviceNetworkState.WIFI)
                val hasPending = queue.any { it.status == "PENDING" }
                val staleCheck = System.currentTimeMillis() - prefs.lastSyncTimestamp > 6 * 60 * 60 * 1000L
                if (!prefs.strictOfflineMode && prefs.autoSyncWhenOnline && canSyncOnNetwork && (hasPending || staleCheck)) {
                    triggerOnlineSync(autoUpgradeModels = true)
                }
            }
        }
    }

    fun resolveActiveEngine(): ActiveResolvedEngine {
        val pref = preferences.value.preferredBackend
        val liteRtReady = liteRtState.value is LiteRtLoadState.Ready
        val nanoReady = geminiStatus.value is GeminiNanoStatus.Available

        return when (pref) {
            PreferredBackend.LITERT_LM -> if (liteRtReady) ActiveResolvedEngine.LITERT_LM else ActiveResolvedEngine.NONE
            PreferredBackend.GEMINI_NANO -> if (nanoReady) ActiveResolvedEngine.GEMINI_NANO else ActiveResolvedEngine.NONE
            PreferredBackend.EXTRACTIVE_NLP -> ActiveResolvedEngine.EXTRACTIVE_NLP
            PreferredBackend.AUTO -> when {
                liteRtReady -> ActiveResolvedEngine.LITERT_LM
                nanoReady -> ActiveResolvedEngine.GEMINI_NANO
                else -> ActiveResolvedEngine.NONE
            }
        }
    }

    fun activeEngineDisplayLabel(): String {
        return when (val lite = liteRtState.value) {
            is LiteRtLoadState.Ready -> "${lite.modelName} (${lite.activeBackendLabel})"
            is LiteRtLoadState.Loading -> "Loading ${lite.modelName}…"
            else -> when (resolveActiveEngine()) {
                ActiveResolvedEngine.GEMINI_NANO -> "Gemini Nano · AICore"
                ActiveResolvedEngine.EXTRACTIVE_NLP -> "Built-in Local NLP"
                ActiveResolvedEngine.LITERT_LM -> "LiteRT-LM"
                ActiveResolvedEngine.NONE -> "Setup Local Model"
            }
        }
    }

    fun selectSession(sessionId: Long) {
        _activeSessionId.value = sessionId
        _chatNotice.value = null
    }

    fun startNewSession() {
        viewModelScope.launch {
            val prefs = preferences.value
            if (liteRtState.value is LiteRtLoadState.Ready) {
                liteRtLmEngine.resetConversation(
                    systemInstruction = prefs.systemPrompt,
                    temperature = prefs.temperature,
                    topK = prefs.topK,
                    topP = prefs.topP,
                )
            }
            val newId = chatRepository.createSession(
                title = "New Offline Chat",
                backendLabel = activeEngineDisplayLabel(),
            )
            _activeSessionId.value = newId
            _chatNotice.value = null
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = sessions.value.firstOrNull { it.id != sessionId }?.id
            }
        }
    }

    fun dismissChatNotice() {
        _chatNotice.value = null
    }

    fun sendChatPrompt(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() || _isGenerating.value) return

        val resolved = resolveActiveEngine()
        if (resolved == ActiveResolvedEngine.NONE) {
            _chatNotice.value = "No generative model is loaded yet. Open the Models tab to load a .litertlm model (or enable Gemini Nano), or switch to Built-in Local NLP to query your saved Notes Vault."
            return
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _chatNotice.value = null
            val sessionId = _activeSessionId.value ?: chatRepository.createSession(
                title = prompt.take(36),
                backendLabel = activeEngineDisplayLabel(),
            ).also { _activeSessionId.value = it }

            if (messages.value.isEmpty()) {
                chatRepository.updateSessionMeta(
                    sessionId = sessionId,
                    title = prompt.take(40).ifBlank { "Offline Chat" },
                    backendLabel = activeEngineDisplayLabel(),
                )
            }

            chatRepository.saveMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = "user",
                    content = prompt,
                    modelName = "You",
                    wordCount = countWords(prompt),
                )
            )

            _isGenerating.value = true
            _streamingReply.value = ""
            val startTime = System.currentTimeMillis()
            val prefs = preferences.value
            val modelLabel = activeEngineDisplayLabel()

            // Augment offline LLM prompts with relevant passages from learned Room Knowledge Vault (Local RAG)
            val learnedMatches = extractiveNlp.answerFromNotes(prompt, notes.value, maxMatches = 2)
            val ragAugmentedPrompt = if (learnedMatches.isNotEmpty() && resolved.isGenerativeReady) {
                val contextNotes = learnedMatches.joinToString("\n") { "[${it.noteTitle}]: ${it.excerpt}" }
                "Relevant local offline knowledge:\n$contextNotes\n\nUser question: $prompt"
            } else {
                prompt
            }

            try {
                when (resolved) {
                    ActiveResolvedEngine.EXTRACTIVE_NLP -> {
                        val answer = extractiveNlp.formatOfflineAnswer(prompt, notes.value)
                        val elapsed = System.currentTimeMillis() - startTime
                        chatRepository.saveMessage(
                            ChatMessageEntity(
                                sessionId = sessionId,
                                role = "assistant",
                                content = answer,
                                modelName = "Local Vault BM25",
                                latencyMs = elapsed,
                                wordCount = countWords(answer),
                            )
                        )
                    }
                    ActiveResolvedEngine.GEMINI_NANO -> {
                        val historyContext = buildCompactHistoryPrompt(messages.value, ragAugmentedPrompt)
                        val buffer = StringBuilder()
                        geminiNanoEngine.generateStream(
                            prompt = historyContext,
                            systemInstruction = prefs.systemPrompt,
                            temperature = prefs.temperature,
                            topK = prefs.topK,
                        ).collect { chunk ->
                            buffer.append(chunk)
                            _streamingReply.value = buffer.toString()
                        }
                        val finalText = buffer.toString().trim().ifEmpty {
                            "Gemini Nano returned an empty response."
                        }
                        val elapsed = System.currentTimeMillis() - startTime
                        chatRepository.saveMessage(
                            ChatMessageEntity(
                                sessionId = sessionId,
                                role = "assistant",
                                content = finalText,
                                modelName = "Gemini Nano",
                                latencyMs = elapsed,
                                wordCount = countWords(finalText),
                            )
                        )
                    }
                    ActiveResolvedEngine.LITERT_LM -> {
                        val buffer = StringBuilder()
                        liteRtLmEngine.generateStream(ragAugmentedPrompt).collect { chunk ->
                            buffer.append(chunk)
                            _streamingReply.value = buffer.toString()
                        }
                        val finalText = buffer.toString().trim().ifEmpty {
                            "Model finished without emitting tokens."
                        }
                        val elapsed = System.currentTimeMillis() - startTime
                        chatRepository.saveMessage(
                            ChatMessageEntity(
                                sessionId = sessionId,
                                role = "assistant",
                                content = finalText,
                                modelName = modelLabel,
                                latencyMs = elapsed,
                                wordCount = countWords(finalText),
                            )
                        )
                    }
                    ActiveResolvedEngine.NONE -> Unit
                }
            } catch (e: CancellationException) {
                val partial = _streamingReply.value.trim()
                if (partial.isNotEmpty()) {
                    chatRepository.saveMessage(
                        ChatMessageEntity(
                            sessionId = sessionId,
                            role = "assistant",
                            content = "$partial\n\n[Generation stopped]",
                            modelName = modelLabel,
                            latencyMs = System.currentTimeMillis() - startTime,
                            wordCount = countWords(partial),
                        )
                    )
                }
            } catch (e: Exception) {
                val errText = e.message ?: "On-device inference error."
                chatRepository.saveMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "assistant",
                        content = "Inference error: $errText",
                        modelName = modelLabel,
                        latencyMs = System.currentTimeMillis() - startTime,
                        isError = true,
                    )
                )
            } finally {
                _streamingReply.value = ""
                _isGenerating.value = false
            }
        }
    }

    fun stopGeneration() {
        if (resolveActiveEngine() == ActiveResolvedEngine.LITERT_LM) {
            liteRtLmEngine.cancelGeneration()
        }
        activeGenerationJob?.cancel()
    }

    // --- Auto-Learn & Self-Upgrade System ---

    fun updateLearnTopicInput(topic: String) {
        _toolsState.update { it.copy(learnTopicInput = topic, toolError = null) }
    }

    fun queueOrLearnTopic(rawTopic: String) {
        val topic = rawTopic.trim()
        if (topic.isEmpty()) return

        viewModelScope.launch {
            _toolsState.update { it.copy(learnTopicInput = "", toolError = null) }
            val prefs = preferences.value
            val net = networkState.value
            val canFetchNow = !prefs.strictOfflineMode &&
                net.isConnected &&
                (!prefs.wifiOnlySync || net == DeviceNetworkState.WIFI)

            if (canFetchNow) {
                _isSyncingOnline.value = true
                val result = onlineSyncManager.fetchTopicIntoVault(topic)
                _isSyncingOnline.value = false
                result.onSuccess { note ->
                    _syncStatusMessage.value = "Learned \"${note.title}\" and saved to local offline vault."
                    settingsRepository.setLastSyncTimestamp(System.currentTimeMillis())
                }.onFailure { err ->
                    dao.insertQueueItem(
                        SyncQueueEntity(
                            topic = topic,
                            status = "FAILED",
                            errorMessage = err.message ?: "Fetch failed",
                        )
                    )
                    _toolsState.update {
                        it.copy(toolError = err.message ?: "Queued \"$topic\" after network error.")
                    }
                }
            } else {
                dao.insertQueueItem(SyncQueueEntity(topic = topic, status = "PENDING"))
                _syncStatusMessage.value = "Queued \"$topic\" — PocketMind will automatically learn it when online."
            }
        }
    }

    fun removeQueueItem(id: Long) {
        viewModelScope.launch {
            dao.deleteQueueItem(id)
        }
    }

    fun triggerOnlineSync(autoUpgradeModels: Boolean = false) {
        if (syncJob?.isActive == true) return
        val prefs = preferences.value
        val net = networkState.value

        if (prefs.strictOfflineMode) {
            _syncStatusMessage.value = "Strict Offline Lock is enabled. Unlock it to sync updates."
            return
        }
        if (!net.isConnected) {
            _syncStatusMessage.value = "Device is offline. Queued items will sync automatically on reconnect."
            return
        }
        if (prefs.wifiOnlySync && net != DeviceNetworkState.WIFI) {
            _syncStatusMessage.value = "Waiting for Wi-Fi (Wi-Fi Only Sync is enabled)."
            return
        }

        syncJob = viewModelScope.launch {
            _isSyncingOnline.value = true
            try {
                geminiNanoEngine.refreshStatus()
                val report = onlineSyncManager.processPendingQueueAndCheckUpdates(localModels.value)
                _remoteModelUpdates.value = report.modelUpdates
                settingsRepository.setLastSyncTimestamp(System.currentTimeMillis())
                _syncStatusMessage.value = report.message

                if (autoUpgradeModels && transferState.value !is ModelTransferState.InProgress) {
                    val upgradeTarget = report.modelUpdates.firstOrNull { it.updateAvailable }
                    if (upgradeTarget != null) {
                        val spec = OfflineAiCatalog.downloadableLiteRtModels.firstOrNull { it.id == upgradeTarget.specId }
                        if (spec != null) {
                            _syncStatusMessage.value = "Upgrading ${spec.name} to latest version…"
                            downloadCuratedModel(spec)
                        }
                    }
                }
            } finally {
                _isSyncingOnline.value = false
            }
        }
    }

    fun dismissSyncMessage() {
        _syncStatusMessage.value = null
    }

    // --- Offline Tools Actions ---

    fun updateSummaryInput(text: String) {
        _toolsState.update { it.copy(summaryInput = text, toolError = null) }
    }

    fun updateSummaryBullets(count: Int) {
        _toolsState.update { it.copy(summaryBulletCount = count.coerceIn(2, 7)) }
    }

    fun runExtractiveSummary() {
        val text = _toolsState.value.summaryInput.trim()
        if (text.isEmpty()) {
            _toolsState.update { it.copy(toolError = "Paste or enter text above to summarize offline.") }
            return
        }
        val result = extractiveNlp.summarize(text, _toolsState.value.summaryBulletCount)
        _toolsState.update {
            it.copy(
                extractiveSummary = result,
                toolError = null,
            )
        }
    }

    fun runLlmSummary() {
        val text = _toolsState.value.summaryInput.trim()
        if (text.isEmpty()) {
            _toolsState.update { it.copy(toolError = "Paste or enter text above to summarize.") }
            return
        }
        val engine = resolveActiveEngine()
        if (!engine.isGenerativeReady) {
            runExtractiveSummary()
            _toolsState.update {
                it.copy(
                    toolError = "No generative LLM is loaded; generated an instant TextRank extractive summary instead."
                )
            }
            return
        }

        viewModelScope.launch {
            _toolsState.update { it.copy(isRunningTool = true, llmSummaryOutput = "", toolError = null) }
            val prompt = "Summarize the following text into ${_toolsState.value.summaryBulletCount} concise bullet points:\n\n$text"
            runOneShotLlm(
                prompt = prompt,
                onChunk = { partial -> _toolsState.update { it.copy(llmSummaryOutput = partial) } },
                onError = { err -> _toolsState.update { it.copy(toolError = err) } },
            )
            _toolsState.update { it.copy(isRunningTool = false) }
        }
    }

    fun updateRewriteInput(text: String) {
        _toolsState.update { it.copy(rewriteInput = text, toolError = null) }
    }

    fun selectRewriteTone(tone: RewriteTone) {
        _toolsState.update { it.copy(rewriteTone = tone) }
    }

    fun runRewriteTool() {
        val text = _toolsState.value.rewriteInput.trim()
        if (text.isEmpty()) {
            _toolsState.update { it.copy(toolError = "Enter a draft or message to rewrite.") }
            return
        }
        val engine = resolveActiveEngine()
        if (!engine.isGenerativeReady) {
            _toolsState.update {
                it.copy(toolError = "Rewriting requires Gemini Nano or a loaded .litertlm model in the Models tab.")
            }
            return
        }
        val tone = _toolsState.value.rewriteTone
        viewModelScope.launch {
            _toolsState.update { it.copy(isRunningTool = true, rewriteOutput = "", toolError = null) }
            val prompt = "${tone.instruction}\n\nText:\n$text"
            runOneShotLlm(
                prompt = prompt,
                onChunk = { partial -> _toolsState.update { it.copy(rewriteOutput = partial) } },
                onError = { err -> _toolsState.update { it.copy(toolError = err) } },
            )
            _toolsState.update { it.copy(isRunningTool = false) }
        }
    }

    fun saveNote(title: String, content: String, tags: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch {
            notesRepository.saveNote(title, content, tags)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            notesRepository.deleteNote(noteId)
        }
    }

    fun updateNotesQuery(query: String) {
        _toolsState.update { it.copy(notesQuery = query, toolError = null) }
    }

    fun queryNotesVault() {
        val query = _toolsState.value.notesQuery.trim()
        if (query.isEmpty()) {
            _toolsState.update { it.copy(toolError = "Type a question to search your local Knowledge Vault.") }
            return
        }
        val currentNotes = notes.value
        val matches = extractiveNlp.answerFromNotes(query, currentNotes, maxMatches = 3)
        _toolsState.update {
            it.copy(
                notesMatches = matches,
                notesSynthesizedAnswer = "",
                toolError = if (matches.isEmpty()) "No matching local notes found for \"$query\"." else null,
            )
        }

        val engine = resolveActiveEngine()
        if (matches.isNotEmpty() && engine.isGenerativeReady) {
            viewModelScope.launch {
                _toolsState.update { it.copy(isRunningTool = true) }
                val contextBlock = matches.joinToString("\n\n") { "[${it.noteTitle}]: ${it.excerpt}" }
                val prompt = "Answer the user's question using ONLY the local notes below. Be concise and cite the note title.\n\nNotes:\n$contextBlock\n\nQuestion: $query"
                runOneShotLlm(
                    prompt = prompt,
                    onChunk = { partial -> _toolsState.update { it.copy(notesSynthesizedAnswer = partial) } },
                    onError = { err -> _toolsState.update { it.copy(toolError = err) } },
                )
                _toolsState.update { it.copy(isRunningTool = false) }
            }
        }
    }

    private suspend fun runOneShotLlm(
        prompt: String,
        onChunk: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val prefs = preferences.value
        val resolved = resolveActiveEngine()
        try {
            val buffer = StringBuilder()
            when (resolved) {
                ActiveResolvedEngine.GEMINI_NANO -> {
                    geminiNanoEngine.generateStream(
                        prompt = prompt,
                        systemInstruction = "Follow instructions precisely and concisely.",
                        temperature = 0.3f,
                        topK = prefs.topK,
                    ).collect { chunk ->
                        buffer.append(chunk)
                        onChunk(buffer.toString())
                    }
                }
                ActiveResolvedEngine.LITERT_LM -> {
                    liteRtLmEngine.generateStream(prompt).collect { chunk ->
                        buffer.append(chunk)
                        onChunk(buffer.toString())
                    }
                }
                else -> Unit
            }
        } catch (e: Exception) {
            onError(e.message ?: "Inference failed.")
        }
    }

    // --- Model & Settings Actions ---

    fun setStrictOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStrictOfflineMode(enabled)
            if (enabled) {
                cancelTransfer()
                syncJob?.cancel()
            }
        }
    }

    fun setAutoSyncWhenOnline(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoSyncWhenOnline(enabled)
        }
    }

    fun setWifiOnlySync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWifiOnlySync(enabled)
        }
    }

    fun setPreferredBackend(backend: PreferredBackend) {
        viewModelScope.launch {
            settingsRepository.setPreferredBackend(backend)
        }
    }

    fun setAcceleratorMode(mode: AcceleratorMode) {
        viewModelScope.launch {
            settingsRepository.setAcceleratorMode(mode)
            val currentLoaded = (liteRtState.value as? LiteRtLoadState.Ready)?.modelPath
            if (!currentLoaded.isNullOrBlank()) {
                loadLiteRtModel(currentLoaded)
            }
        }
    }

    fun updateSampling(temperature: Float, topK: Int, topP: Float) {
        viewModelScope.launch {
            settingsRepository.updateSampling(temperature, topK, topP)
        }
    }

    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch {
            settingsRepository.setSystemPrompt(prompt)
        }
    }

    fun refreshGeminiNano() {
        viewModelScope.launch {
            geminiNanoEngine.refreshStatus()
        }
    }

    fun downloadGeminiNano() {
        if (preferences.value.strictOfflineMode) {
            _chatNotice.value = "Disable Strict Offline Lock in Models before downloading Gemini Nano via AICore."
            return
        }
        viewModelScope.launch {
            geminiNanoEngine.downloadSystemModel()
        }
    }

    fun warmupGeminiNano() {
        viewModelScope.launch {
            geminiNanoEngine.warmup()
        }
    }

    fun importLocalModel(uri: Uri) {
        activeTransferJob?.cancel()
        activeTransferJob = viewModelScope.launch {
            val result = modelDownloadManager.importModelFromUri(uri)
            result.onSuccess { local ->
                loadLiteRtModel(local.path)
            }
        }
    }

    fun downloadCuratedModel(spec: LiteRtModelSpec) {
        activeTransferJob?.cancel()
        activeTransferJob = viewModelScope.launch {
            val result = modelDownloadManager.downloadModel(
                spec = spec,
                strictOfflineMode = preferences.value.strictOfflineMode,
            )
            result.onSuccess { local ->
                loadLiteRtModel(local.path)
            }
        }
    }

    fun cancelTransfer() {
        activeTransferJob?.cancel()
        modelDownloadManager.cancelTransfer()
    }

    fun dismissTransferBanner() {
        modelDownloadManager.dismissTransferStatus()
    }

    fun loadLiteRtModel(path: String) {
        viewModelScope.launch {
            val prefs = preferences.value
            settingsRepository.setActiveLiteRtModelPath(path)
            liteRtLmEngine.load(
                modelPath = path,
                preferredAccelerator = prefs.acceleratorMode,
                systemInstruction = prefs.systemPrompt,
                temperature = prefs.temperature,
                topK = prefs.topK,
                topP = prefs.topP,
            )
        }
    }

    fun unloadLiteRtModel() {
        viewModelScope.launch {
            liteRtLmEngine.unload()
        }
    }

    fun deleteLocalModel(file: LocalModelFile) {
        viewModelScope.launch {
            val loadedPath = (liteRtState.value as? LiteRtLoadState.Ready)?.modelPath
            if (loadedPath == file.path) {
                liteRtLmEngine.unload()
            }
            modelDownloadManager.deleteModel(file.path)
        }
    }

    // --- Specialist AI Spawner & Self-Improvement Critic ---

    fun createCustomAgent(name: String, role: String, systemPrompt: String, focusTags: String) {
        viewModelScope.launch {
            val id = dao.insertAgent(
                AgentProfileEntity(
                    name = name,
                    role = role,
                    systemPrompt = systemPrompt,
                    temperature = 0.45f,
                    topK = 35,
                    focusTags = focusTags,
                    createdByAi = false,
                )
            )
            val created = dao.observeAgents()
            _syncStatusMessage.value = "Created specialist AI \"$name\" (#$id)."
        }
    }

    fun autoCreateSpecialistAgent(goal: String) {
        viewModelScope.launch {
            val trimmedGoal = goal.trim()
            if (trimmedGoal.isEmpty()) return@launch

            val engine = resolveActiveEngine()
            if (!engine.isGenerativeReady) {
                val lower = trimmedGoal.lowercase()
                val (name, role, temp, prompt) = when {
                    "critic" in lower || "verify" in lower || "improve" in lower -> Quad(
                        "Veritas Critic",
                        "Self-Improvement Critic",
                        0.2f,
                        "You are Veritas Critic, an analytical reviewer AI. Inspect drafts for factual gaps, unclear structure, and missing edge cases, then rewrite a sharper, more accurate version.",
                    )
                    "code" in lower || "debug" in lower || "dev" in lower -> Quad(
                        "Kernel Reviewer",
                        "Code & Architecture Specialist",
                        0.25f,
                        "You are Kernel Reviewer, a concise mobile and systems software engineer. Check logic, edge cases, memory safety, and provide clean, idiomatic fixes.",
                    )
                    "study" in lower || "tutor" in lower || "learn" in lower -> Quad(
                        "Socratic Tutor",
                        "Adaptive Study Coach",
                        0.5f,
                        "You are Socratic Tutor. Explain concepts clearly from first principles, ask one check-for-understanding question, and link ideas to saved offline notes.",
                    )
                    else -> Quad(
                        trimmedGoal.split(" ").take(2).joinToString(" ").replaceFirstChar { it.uppercase() } + " AI",
                        "Domain Specialist",
                        0.4f,
                        "You are a dedicated offline specialist AI focused on: $trimmedGoal. Give structured, accurate, concise answers grounded in local knowledge.",
                    )
                }
                val newId = dao.insertAgent(
                    AgentProfileEntity(
                        name = name,
                        role = role,
                        systemPrompt = prompt,
                        temperature = temp,
                        topK = 32,
                        focusTags = trimmedGoal.take(40),
                        createdByAi = true,
                    )
                )
                dao.setActiveAgent(newId)
                settingsRepository.setSystemPrompt(prompt)
                _syncStatusMessage.value = "Spawned & activated \"$name\" for \"$trimmedGoal\"."
                return@launch
            }

            _toolsState.update { it.copy(isRunningTool = true, toolError = null) }
            val architectPrompt = """
                Design a compact specialist AI sub-agent for this goal: "$trimmedGoal".
                Reply in exactly 4 lines:
                NAME: <2-3 word name>
                ROLE: <short role>
                TEMP: <number between 0.1 and 0.8>
                PROMPT: <concise 1-2 sentence system instruction>
            """.trimIndent()

            val buffer = StringBuilder()
            runOneShotLlm(
                prompt = architectPrompt,
                onChunk = { buffer.append(it.removePrefix(buffer.toString())) },
                onError = { err -> _toolsState.update { it.copy(toolError = err) } },
            )
            val raw = buffer.toString()
            val parsedName = raw.lineSequence().firstOrNull { it.startsWith("NAME:", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.ifBlank { null } ?: "Specialist AI"
            val parsedRole = raw.lineSequence().firstOrNull { it.startsWith("ROLE:", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.ifBlank { null } ?: "Sub-Agent"
            val parsedTemp = raw.lineSequence().firstOrNull { it.startsWith("TEMP:", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.toFloatOrNull()?.coerceIn(0.1f, 0.9f) ?: 0.4f
            val parsedPrompt = raw.lineSequence().firstOrNull { it.startsWith("PROMPT:", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.ifBlank { null }
                ?: "You are $parsedName ($parsedRole). Focus on: $trimmedGoal."

            val id = dao.insertAgent(
                AgentProfileEntity(
                    name = parsedName,
                    role = parsedRole,
                    systemPrompt = parsedPrompt,
                    temperature = parsedTemp,
                    topK = 35,
                    focusTags = trimmedGoal.take(40),
                    createdByAi = true,
                )
            )
            dao.setActiveAgent(id)
            settingsRepository.setSystemPrompt(parsedPrompt)
            settingsRepository.updateSampling(parsedTemp, 35, preferences.value.topP)
            _toolsState.update { it.copy(isRunningTool = false) }
            _syncStatusMessage.value = "AI Architect spawned & activated \"$parsedName\"."
        }
    }

    fun activateAgent(agent: AgentProfileEntity) {
        viewModelScope.launch {
            dao.setActiveAgent(agent.id)
            settingsRepository.setSystemPrompt(agent.systemPrompt)
            settingsRepository.updateSampling(agent.temperature, agent.topK, preferences.value.topP)
            if (liteRtState.value is LiteRtLoadState.Ready) {
                liteRtLmEngine.resetConversation(
                    systemInstruction = agent.systemPrompt,
                    temperature = agent.temperature,
                    topK = agent.topK,
                    topP = preferences.value.topP,
                )
            }
            _syncStatusMessage.value = "Activated ${agent.name} (${agent.role})."
        }
    }

    fun deactivateAllAgents() {
        viewModelScope.launch {
            dao.deactivateAllAgents()
            val defaultPrompt = "You are PocketMind, a concise, privacy-first AI assistant running completely offline on the user's Android device."
            settingsRepository.setSystemPrompt(defaultPrompt)
            _syncStatusMessage.value = "Restored default PocketMind persona."
        }
    }

    fun deleteAgent(agentId: Long) {
        viewModelScope.launch {
            dao.deleteAgent(agentId)
        }
    }

    fun runSelfImprovementLoop(goal: String, draft: String, applyUpgrade: Boolean) {
        val cleanDraft = draft.trim()
        if (cleanDraft.isEmpty()) return
        val cleanGoal = goal.trim().ifEmpty { "General user question" }

        viewModelScope.launch {
            val criticAgent = agents.value.firstOrNull {
                "critic" in it.role.lowercase() || "critic" in it.name.lowercase()
            }
            val criticName = criticAgent?.name ?: "Veritas Critic"
            val engine = resolveActiveEngine()

            if (!engine.isGenerativeReady) {
                val summary = extractiveNlp.summarize(cleanDraft, bulletCount = 3)
                val critique = "Local structural audit: ${summary.originalWordCount} words analyzed. Key terms: ${summary.keyTerms.joinToString(", ")}. Load a .litertlm model or Gemini Nano for full generative self-rewriting."
                val upgradedRule = "${preferences.value.systemPrompt.trim()} Always structure answers concisely with explicit key takeaways."
                if (applyUpgrade) {
                    settingsRepository.setSystemPrompt(upgradedRule)
                }
                dao.insertImprovementLog(
                    SelfImprovementLogEntity(
                        agentName = criticName,
                        targetPrompt = cleanGoal,
                        critiqueSummary = critique,
                        improvedResponse = summary.bullets.joinToString("\n• ", prefix = "• "),
                        upgradedInstruction = if (applyUpgrade) upgradedRule else "",
                    )
                )
                _syncStatusMessage.value = "Completed local structural self-audit."
                return@launch
            }

            _toolsState.update { it.copy(isRunningTool = true, toolError = null) }
            val criticPrompt = """
                You are $criticName, a self-improvement AI reviewing another AI's response.
                Original goal: $cleanGoal
                Draft response: $cleanDraft
                Current system instruction: ${preferences.value.systemPrompt}

                Provide in 3 labeled blocks:
                CRITIQUE: <1-2 sentences identifying weaknesses or missing clarity>
                IMPROVED: <a sharper, more accurate rewrite of the response>
                UPGRADE_RULE: <one concise system instruction rule to prevent this weakness next time>
            """.trimIndent()

            val buffer = StringBuilder()
            runOneShotLlm(
                prompt = criticPrompt,
                onChunk = { partial -> buffer.clear(); buffer.append(partial) },
                onError = { err -> _toolsState.update { it.copy(toolError = err) } },
            )
            val raw = buffer.toString()
            val critique = extractSection(raw, "CRITIQUE:", listOf("IMPROVED:", "UPGRADE_RULE:"))
                .ifBlank { "Reviewed clarity, precision, and structure." }
            val improved = extractSection(raw, "IMPROVED:", listOf("UPGRADE_RULE:"))
                .ifBlank { raw }
            val upgradeRule = extractSection(raw, "UPGRADE_RULE:", emptyList())

            var appliedRule = ""
            if (applyUpgrade && upgradeRule.isNotBlank()) {
                val updatedPrompt = "${preferences.value.systemPrompt.trim()} $upgradeRule".take(450)
                settingsRepository.setSystemPrompt(updatedPrompt)
                appliedRule = upgradeRule
            }

            dao.insertImprovementLog(
                SelfImprovementLogEntity(
                    agentName = criticName,
                    targetPrompt = cleanGoal,
                    critiqueSummary = critique,
                    improvedResponse = improved,
                    upgradedInstruction = appliedRule,
                )
            )
            _toolsState.update { it.copy(isRunningTool = false) }
            _syncStatusMessage.value = "$criticName logged critique & upgraded PocketMind."
        }
    }

    fun critiqueChatMessage(userPrompt: String, assistantReply: String) {
        viewModelScope.launch {
            val sessionId = _activeSessionId.value ?: return@launch
            runSelfImprovementLoop(userPrompt, assistantReply, applyUpgrade = true)
            val latest = improvementLogs.value.firstOrNull()
            if (latest != null) {
                chatRepository.saveMessage(
                    ChatMessageEntity(
                        sessionId = sessionId,
                        role = "assistant",
                        content = "Self-Improvement Pass (${latest.agentName}):\n${latest.critiqueSummary}\n\nRefined Answer:\n${latest.improvedResponse}",
                        modelName = "${latest.agentName} · Upgrade",
                        wordCount = countWords(latest.improvedResponse),
                    )
                )
            }
        }
    }

    private fun extractSection(text: String, marker: String, stopMarkers: List<String>): String {
        val start = text.indexOf(marker, ignoreCase = true)
        if (start == -1) return ""
        val afterStart = start + marker.length
        val end = stopMarkers
            .mapNotNull { stop -> text.indexOf(stop, afterStart, ignoreCase = true).takeIf { it != -1 } }
            .minOrNull() ?: text.length
        return text.substring(afterStart, end).trim()
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun buildCompactHistoryPrompt(
        history: List<ChatMessageEntity>,
        latestPrompt: String,
    ): String {
        val recent = history.takeLast(6)
        if (recent.isEmpty()) return latestPrompt
        return buildString {
            appendLine("Recent conversation context:")
            for (msg in recent) {
                val speaker = if (msg.role == "user") "User" else "Assistant"
                appendLine("$speaker: ${msg.content.take(400)}")
            }
            appendLine()
            append("User: $latestPrompt")
        }
    }

    private fun countWords(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    override fun onCleared() {
        activeGenerationJob?.cancel()
        activeTransferJob?.cancel()
        syncJob?.cancel()
        geminiNanoEngine.close()
        liteRtLmEngine.destroy()
        connectivityMonitor.release()
        super.onCleared()
    }
}
