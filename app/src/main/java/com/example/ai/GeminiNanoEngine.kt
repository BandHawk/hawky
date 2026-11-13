package com.example.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

sealed interface GeminiNanoStatus {
    data object Checking : GeminiNanoStatus
    data object Available : GeminiNanoStatus
    data object Downloadable : GeminiNanoStatus
    data class Downloading(val bytesDownloaded: Long) : GeminiNanoStatus
    data class Unavailable(val reason: String) : GeminiNanoStatus
}

class GeminiNanoEngine {
    private val client: GenerativeModel by lazy { Generation.getClient() }
    private var clientCreated = false

    private val _status = MutableStateFlow<GeminiNanoStatus>(GeminiNanoStatus.Checking)
    val status: StateFlow<GeminiNanoStatus> = _status.asStateFlow()

    private fun getOrCreateClient(): GenerativeModel {
        clientCreated = true
        return client
    }

    suspend fun refreshStatus(): GeminiNanoStatus = withContext(Dispatchers.IO) {
        _status.value = GeminiNanoStatus.Checking
        val resolved = try {
            when (getOrCreateClient().checkStatus()) {
                FeatureStatus.AVAILABLE -> GeminiNanoStatus.Available
                FeatureStatus.DOWNLOADABLE -> GeminiNanoStatus.Downloadable
                FeatureStatus.DOWNLOADING -> GeminiNanoStatus.Downloading(0L)
                FeatureStatus.UNAVAILABLE -> GeminiNanoStatus.Unavailable(
                    "AICore / Gemini Nano is not supported on this device or emulator."
                )
                else -> GeminiNanoStatus.Unavailable("Unknown AICore feature status.")
            }
        } catch (t: Throwable) {
            GeminiNanoStatus.Unavailable(
                t.message ?: "AICore system service unavailable on this device."
            )
        }
        _status.value = resolved
        resolved
    }

    suspend fun downloadSystemModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            getOrCreateClient().download().collect { downloadStatus ->
                when (downloadStatus) {
                    is DownloadStatus.DownloadStarted -> {
                        _status.value = GeminiNanoStatus.Downloading(0L)
                    }
                    is DownloadStatus.DownloadProgress -> {
                        _status.value = GeminiNanoStatus.Downloading(downloadStatus.totalBytesDownloaded)
                    }
                    DownloadStatus.DownloadCompleted -> {
                        _status.value = GeminiNanoStatus.Available
                    }
                    is DownloadStatus.DownloadFailed -> {
                        _status.value = GeminiNanoStatus.Unavailable(
                            downloadStatus.e.message ?: "Gemini Nano download failed."
                        )
                    }
                }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            _status.value = GeminiNanoStatus.Unavailable(t.message ?: "Gemini Nano download failed.")
            Result.failure(t)
        }
    }

    suspend fun warmup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            getOrCreateClient().warmup()
            Unit
        }
    }

    fun generateStream(
        prompt: String,
        systemInstruction: String,
        temperature: Float,
        topK: Int,
    ): Flow<String> = flow {
        val fullPrompt = if (systemInstruction.isNotBlank()) {
            "Instructions: $systemInstruction\n\nUser request:\n$prompt"
        } else {
            prompt
        }
        val request = generateContentRequest(TextPart(fullPrompt)) {
            this.temperature = temperature
            this.topK = topK
            this.maxOutputTokens = 1024
        }
        getOrCreateClient().generateContentStream(request).collect { chunk ->
            val text = chunk.candidates.firstOrNull()?.text.orEmpty()
            if (text.isNotEmpty()) {
                emit(text)
            }
        }
    }.flowOn(Dispatchers.IO)

    fun close() {
        if (clientCreated) {
            runCatching { client.close() }
        }
    }
}
