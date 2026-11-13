package com.example.ai

import android.app.Application
import android.util.Log
import com.example.data.preferences.AcceleratorMode
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

sealed interface LiteRtLoadState {
    data object Unloaded : LiteRtLoadState
    data class Loading(val modelName: String, val backendLabel: String) : LiteRtLoadState
    data class Ready(
        val modelPath: String,
        val modelName: String,
        val activeBackendLabel: String,
    ) : LiteRtLoadState
    data class Error(val message: String) : LiteRtLoadState
}

/**
 * Manages the on-device LiteRT-LM Engine and Conversation on a dedicated single-thread executor.
 * Guarantees safe single-close semantics, GPU->CPU automatic fallback, and non-blocking initialization.
 */
class LiteRtLmEngine(private val application: Application) {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "litert-lm-owner").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedPath: String? = null
    private var loadedSystemPrompt: String? = null
    private var isGenerating = false

    private val _loadState = MutableStateFlow<LiteRtLoadState>(LiteRtLoadState.Unloaded)
    val loadState: StateFlow<LiteRtLoadState> = _loadState.asStateFlow()

    suspend fun load(
        modelPath: String,
        preferredAccelerator: AcceleratorMode,
        systemInstruction: String,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): Result<Unit> = withContext(dispatcher) {
        mutex.withLock {
            val file = File(modelPath)
            if (!file.exists() || file.length() <= 0L) {
                val msg = "Model file not found at ${file.name}"
                _loadState.value = LiteRtLoadState.Error(msg)
                return@withContext Result.failure(IllegalArgumentException(msg))
            }

            if (loadedPath == modelPath && engine != null && conversation != null && loadedSystemPrompt == systemInstruction) {
                return@withContext Result.success(Unit)
            }

            releaseLocked()

            val primaryBackend = if (preferredAccelerator == AcceleratorMode.GPU) Backend.GPU() else Backend.CPU()
            val primaryLabel = if (preferredAccelerator == AcceleratorMode.GPU) "GPU" else "CPU"
            _loadState.value = LiteRtLoadState.Loading(file.name, primaryLabel)

            try {
                initializeEngineAndConversation(
                    modelPath = modelPath,
                    backend = primaryBackend,
                    backendLabel = primaryLabel,
                    systemInstruction = systemInstruction,
                    temperature = temperature,
                    topK = topK,
                    topP = topP,
                )
                Result.success(Unit)
            } catch (primaryError: Throwable) {
                Log.w("LiteRtLmEngine", "Primary backend $primaryLabel failed, checking fallback", primaryError)
                if (preferredAccelerator == AcceleratorMode.GPU) {
                    try {
                        _loadState.value = LiteRtLoadState.Loading(file.name, "CPU Fallback")
                        initializeEngineAndConversation(
                            modelPath = modelPath,
                            backend = Backend.CPU(),
                            backendLabel = "CPU (Fallback)",
                            systemInstruction = systemInstruction,
                            temperature = temperature,
                            topK = topK,
                            topP = topP,
                        )
                        Result.success(Unit)
                    } catch (cpuError: Throwable) {
                        releaseLocked()
                        val msg = cpuError.message ?: "Failed to initialize LiteRT-LM model."
                        _loadState.value = LiteRtLoadState.Error(msg)
                        Result.failure(cpuError)
                    }
                } else {
                    releaseLocked()
                    val msg = primaryError.message ?: "Failed to initialize LiteRT-LM model."
                    _loadState.value = LiteRtLoadState.Error(msg)
                    Result.failure(primaryError)
                }
            }
        }
    }

    private fun initializeEngineAndConversation(
        modelPath: String,
        backend: Backend,
        backendLabel: String,
        systemInstruction: String,
        temperature: Float,
        topK: Int,
        topP: Float,
    ) {
        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = application.cacheDir.path,
        )
        val createdEngine = Engine(config)
        createdEngine.initialize()
        engine = createdEngine

        val conv = createdEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                samplerConfig = SamplerConfig(
                    topK = topK,
                    topP = topP.toDouble(),
                    temperature = temperature.toDouble(),
                ),
            )
        )
        conversation = conv
        loadedPath = modelPath
        loadedSystemPrompt = systemInstruction
        _loadState.value = LiteRtLoadState.Ready(
            modelPath = modelPath,
            modelName = File(modelPath).nameWithoutExtension,
            activeBackendLabel = backendLabel,
        )
    }

    fun generateStream(prompt: String): Flow<String> = flow {
        val activeConv = conversation ?: error("LiteRT-LM model is not loaded.")
        isGenerating = true
        try {
            activeConv.sendMessageAsync(prompt).collect { chunk ->
                emit(chunk.toString())
            }
        } finally {
            isGenerating = false
        }
    }.flowOn(dispatcher)

    fun cancelGeneration() {
        scope.launch {
            if (isGenerating) {
                runCatching { conversation?.cancelProcess() }
            }
        }
    }

    suspend fun resetConversation(
        systemInstruction: String,
        temperature: Float,
        topK: Int,
        topP: Float,
    ) = withContext(dispatcher) {
        mutex.withLock {
            val activeEngine = engine ?: return@withLock
            while (isGenerating) {
                runCatching { conversation?.cancelProcess() }
                delay(100)
            }
            runCatching { conversation?.close() }
            conversation = null
            conversation = activeEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction),
                    samplerConfig = SamplerConfig(
                        topK = topK,
                        topP = topP.toDouble(),
                        temperature = temperature.toDouble(),
                    ),
                )
            )
            loadedSystemPrompt = systemInstruction
        }
    }

    suspend fun unload() = withContext(dispatcher) {
        mutex.withLock {
            releaseLocked()
        }
    }

    private suspend fun releaseLocked() {
        while (isGenerating) {
            runCatching { conversation?.cancelProcess() }
            delay(100)
        }
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
        loadedSystemPrompt = null
        _loadState.value = LiteRtLoadState.Unloaded
    }

    fun destroy() {
        scope.launch {
            try {
                mutex.withLock {
                    releaseLocked()
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                executor.shutdown()
            }
        }.invokeOnCompletion {
            scope.cancel()
        }
    }
}
