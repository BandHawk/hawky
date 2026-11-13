package com.example.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.guide.LiteRtModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class LocalModelFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
) {
    val sizeFormatted: String
        get() = when {
            sizeBytes >= 1_000_000_000L -> String.format("%.2f GB", sizeBytes / 1_000_000_000.0)
            sizeBytes >= 1_000_000L -> String.format("%.1f MB", sizeBytes / 1_000_000.0)
            else -> "${sizeBytes / 1024} KB"
        }
}

sealed interface ModelTransferState {
    data object Idle : ModelTransferState
    data class InProgress(
        val label: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
    ) : ModelTransferState {
        val progressFraction: Float
            get() = if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    }
    data class Completed(val filePath: String, val fileName: String) : ModelTransferState
    data class Failed(val message: String) : ModelTransferState
}

class ModelDownloadManager(private val context: Context) {

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply { mkdirs() }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val _localModels = MutableStateFlow<List<LocalModelFile>>(emptyList())
    val localModels: StateFlow<List<LocalModelFile>> = _localModels.asStateFlow()

    private val _transferState = MutableStateFlow<ModelTransferState>(ModelTransferState.Idle)
    val transferState: StateFlow<ModelTransferState> = _transferState.asStateFlow()

    private var activeCall: okhttp3.Call? = null

    init {
        refreshLocalModels()
    }

    fun refreshLocalModels() {
        val files = modelsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?.map { LocalModelFile(name = it.name, path = it.absolutePath, sizeBytes = it.length()) }
            ?: emptyList()
        _localModels.value = files
    }

    suspend fun importModelFromUri(uri: Uri): Result<LocalModelFile> = withContext(Dispatchers.IO) {
        try {
            val fileName = resolveDisplayName(uri) ?: "imported_model_${System.currentTimeMillis()}.litertlm"
            val sanitizedName = if (fileName.endsWith(".litertlm", ignoreCase = true)) {
                fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            } else {
                fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".litertlm"
            }

            val totalSize = resolveFileSize(uri)
            val partFile = File(modelsDir, "$sanitizedName.part")
            val finalFile = File(modelsDir, sanitizedName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        _transferState.value = ModelTransferState.InProgress(
                            label = "Importing $sanitizedName",
                            bytesTransferred = copied,
                            totalBytes = totalSize,
                        )
                    }
                    output.flush()
                }
            } ?: error("Unable to open selected file.")

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            refreshLocalModels()
            _transferState.value = ModelTransferState.Completed(finalFile.absolutePath, finalFile.name)
            Result.success(LocalModelFile(finalFile.name, finalFile.absolutePath, finalFile.length()))
        } catch (e: CancellationException) {
            _transferState.value = ModelTransferState.Idle
            throw e
        } catch (e: Exception) {
            _transferState.value = ModelTransferState.Failed(e.message ?: "Import failed.")
            Result.failure(e)
        }
    }

    suspend fun downloadModel(
        spec: LiteRtModelSpec,
        strictOfflineMode: Boolean,
    ): Result<LocalModelFile> = withContext(Dispatchers.IO) {
        if (strictOfflineMode) {
            val msg = "Strict Offline Mode is locked. Disable it in Models to perform a one-time weight download, or import a .litertlm file locally."
            _transferState.value = ModelTransferState.Failed(msg)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val partFile = File(modelsDir, "${spec.fileName}.part")
        val finalFile = File(modelsDir, spec.fileName)

        try {
            val request = Request.Builder().url(spec.downloadUrl).build()
            val call = client.newCall(request)
            activeCall = call

            _transferState.value = ModelTransferState.InProgress(
                label = "Downloading ${spec.fileName}",
                bytesTransferred = 0L,
                totalBytes = spec.approxSizeBytes,
            )

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: Unable to fetch ${spec.fileName}")
                }
                val body = response.body ?: error("Empty response body.")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: spec.approxSizeBytes

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastUiUpdate = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 150L) {
                                lastUiUpdate = now
                                _transferState.value = ModelTransferState.InProgress(
                                    label = "Downloading ${spec.fileName}",
                                    bytesTransferred = downloaded,
                                    totalBytes = totalBytes,
                                )
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (partFile.length() < 1_000_000L) {
                partFile.delete()
                error("Downloaded file is unexpectedly small; verify network or Hugging Face model link.")
            }

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            refreshLocalModels()
            _transferState.value = ModelTransferState.Completed(finalFile.absolutePath, finalFile.name)
            Result.success(LocalModelFile(finalFile.name, finalFile.absolutePath, finalFile.length()))
        } catch (e: CancellationException) {
            partFile.delete()
            _transferState.value = ModelTransferState.Idle
            throw e
        } catch (e: Exception) {
            partFile.delete()
            _transferState.value = ModelTransferState.Failed(e.message ?: "Download failed.")
            Result.failure(e)
        } finally {
            activeCall = null
        }
    }

    fun cancelTransfer() {
        activeCall?.cancel()
        activeCall = null
        _transferState.value = ModelTransferState.Idle
    }

    fun dismissTransferStatus() {
        _transferState.value = ModelTransferState.Idle
    }

    fun deleteModel(path: String): Boolean {
        val deleted = File(path).delete()
        refreshLocalModels()
        return deleted
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun resolveFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
            }
        }
        return -1L
    }
}
