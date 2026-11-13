package com.example.ai

import com.example.data.guide.LiteRtModelSpec
import com.example.data.guide.OfflineAiCatalog
import com.example.data.local.KnowledgeNoteEntity
import com.example.data.local.PocketMindDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class RemoteModelUpdateInfo(
    val specId: String,
    val fileName: String,
    val lastModified: String,
    val remoteSizeBytes: Long,
    val updateAvailable: Boolean,
)

data class OnlineSyncReport(
    val syncedTopicsCount: Int,
    val modelUpdates: List<RemoteModelUpdateInfo>,
    val message: String,
)

/**
 * Opportunistic online updater that runs ONLY when connectivity is available and Strict Offline Lock is disabled.
 * 1) Checks Hugging Face model repositories for upstream .litertlm updates.
 * 2) Fetches concise Wikipedia reference summaries for queued topics into the local Room Notes Vault for offline Q&A.
 */
class OnlineKnowledgeSyncManager(private val dao: PocketMindDao) {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchTopicIntoVault(topic: String): Result<KnowledgeNoteEntity> = withContext(Dispatchers.IO) {
        try {
            val trimmed = topic.trim()
            val encoded = URLEncoder.encode(trimmed.replace(' ', '_'), StandardCharsets.UTF_8.name())
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "PocketMindOfflineAI/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("No reference article found for \"$trimmed\" (HTTP ${response.code}).")
                }
                val body = response.body?.string() ?: error("Empty response.")
                val json = JSONObject(body)
                val title = json.optString("title", trimmed)
                val extract = json.optString("extract", "").trim()
                val description = json.optString("description", "").trim()
                if (extract.isBlank()) {
                    error("Reference entry for \"$trimmed\" had no text summary.")
                }

                val fullContent = buildString {
                    if (description.isNotBlank()) {
                        appendLine("Topic: $description")
                        appendLine()
                    }
                    append(extract)
                }
                val note = KnowledgeNoteEntity(
                    title = title,
                    content = fullContent,
                    tags = "online-synced,reference",
                )
                val id = dao.insertNote(note)
                Result.success(note.copy(id = id))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkModelUpdates(localModels: List<LocalModelFile>): List<RemoteModelUpdateInfo> =
        withContext(Dispatchers.IO) {
            OfflineAiCatalog.downloadableLiteRtModels.mapNotNull { spec ->
                fetchHuggingFaceModelInfo(spec, localModels)
            }
        }

    private fun fetchHuggingFaceModelInfo(
        spec: LiteRtModelSpec,
        localModels: List<LocalModelFile>,
    ): RemoteModelUpdateInfo? {
        val repoPath = spec.downloadUrl
            .removePrefix("https://huggingface.co/")
            .substringBefore("/resolve/")
        val apiUrl = "https://huggingface.co/api/models/$repoPath?blobs=true"

        return runCatching {
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "PocketMindOfflineAI/1.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val raw = response.body?.string() ?: return@runCatching null
                val json = JSONObject(raw)
                val lastModified = json.optString("lastModified", "").take(10)
                var remoteSize = spec.approxSizeBytes
                val siblings = json.optJSONArray("siblings")
                if (siblings != null) {
                    for (i in 0 until siblings.length()) {
                        val entry = siblings.getJSONObject(i)
                        if (entry.optString("rfilename") == spec.fileName) {
                            val size = entry.optLong("size", 0L)
                            if (size > 0L) remoteSize = size
                            break
                        }
                    }
                }
                val localMatch = localModels.firstOrNull { it.name == spec.fileName }
                val updateAvailable = localMatch != null && remoteSize > 0L && localMatch.sizeBytes != remoteSize
                RemoteModelUpdateInfo(
                    specId = spec.id,
                    fileName = spec.fileName,
                    lastModified = lastModified,
                    remoteSizeBytes = remoteSize,
                    updateAvailable = updateAvailable,
                )
            }
        }.getOrNull()
    }

    suspend fun processPendingQueueAndCheckUpdates(localModels: List<LocalModelFile>): OnlineSyncReport {
        val pending = dao.getPendingSyncQueue()
        var syncedCount = 0
        for (item in pending) {
            dao.updateQueueItemStatus(item.id, "SYNCING", "")
            val res = fetchTopicIntoVault(item.topic)
            if (res.isSuccess) {
                dao.deleteQueueItem(item.id)
                syncedCount++
            } else {
                dao.updateQueueItemStatus(
                    item.id,
                    "FAILED",
                    res.exceptionOrNull()?.message ?: "Sync failed",
                )
            }
        }
        val modelUpdates = checkModelUpdates(localModels)
        val updatesCount = modelUpdates.count { it.updateAvailable }
        val summary = buildString {
            append("Synced $syncedCount queued topic(s) into offline vault")
            if (modelUpdates.isNotEmpty()) {
                append(" · Checked ${modelUpdates.size} model(s)")
                if (updatesCount > 0) append(" ($updatesCount update available)")
            }
        }
        return OnlineSyncReport(
            syncedTopicsCount = syncedCount,
            modelUpdates = modelUpdates,
            message = summary,
        )
    }
}
