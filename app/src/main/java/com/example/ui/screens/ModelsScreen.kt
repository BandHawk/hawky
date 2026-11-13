package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.DeviceNetworkState
import com.example.ai.GeminiNanoStatus
import com.example.ai.LiteRtLoadState
import com.example.ai.LocalModelFile
import com.example.ai.ModelTransferState
import com.example.ai.RemoteModelUpdateInfo
import com.example.data.guide.LiteRtModelSpec
import com.example.data.guide.OfflineAiCatalog
import com.example.data.preferences.AcceleratorMode
import com.example.data.preferences.PreferredBackend
import com.example.data.preferences.UserPreferences
import com.example.ui.components.SectionHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelsScreen(
    preferences: UserPreferences,
    networkState: DeviceNetworkState,
    geminiStatus: GeminiNanoStatus,
    liteRtState: LiteRtLoadState,
    localModels: List<LocalModelFile>,
    transferState: ModelTransferState,
    isSyncingOnline: Boolean,
    syncStatusMessage: String?,
    remoteModelUpdates: List<RemoteModelUpdateInfo>,
    onToggleStrictOffline: (Boolean) -> Unit,
    onToggleAutoSyncOnline: (Boolean) -> Unit,
    onToggleWifiOnlySync: (Boolean) -> Unit,
    onTriggerOnlineSync: (Boolean) -> Unit,
    onDismissSyncMessage: () -> Unit,
    onSelectBackend: (PreferredBackend) -> Unit,
    onSelectAccelerator: (AcceleratorMode) -> Unit,
    onUpdateSampling: (Float, Int, Float) -> Unit,
    onUpdateSystemPrompt: (String) -> Unit,
    onRefreshGeminiNano: () -> Unit,
    onDownloadGeminiNano: () -> Unit,
    onWarmupGeminiNano: () -> Unit,
    onImportModelUri: (Uri) -> Unit,
    onDownloadCuratedModel: (LiteRtModelSpec) -> Unit,
    onCancelTransfer: () -> Unit,
    onDismissTransfer: () -> Unit,
    onLoadLocalModel: (String) -> Unit,
    onUnloadLocalModel: () -> Unit,
    onDeleteLocalModel: (LocalModelFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportModelUri(uri)
        }
    }

    var tempSlider by remember(preferences.temperature) { mutableFloatStateOf(preferences.temperature) }
    var topKSlider by remember(preferences.topK) { mutableIntStateOf(preferences.topK) }
    var systemPromptDraft by remember(preferences.systemPrompt) { mutableStateOf(preferences.systemPrompt) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionHeader(
                    title = "Offline Engines & Auto-Upgrade",
                    subtitle = "Run 100% locally in RAM/NPU, and optionally auto-learn new topics & check for model upgrades when online.",
                )
            }

            // Strict Offline Lock & Opportunistic Online Auto-Upgrade Card
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(
                        1.dp,
                        if (preferences.strictOfflineMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (preferences.strictOfflineMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Strict Offline Lock",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Network: ${networkState.label}. When locked, zero outbound packets are sent.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = preferences.strictOfflineMode,
                                onCheckedChange = onToggleStrictOffline,
                                modifier = Modifier.testTag("strict_offline_switch"),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Learn & Upgrade When Online",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val lastSyncedText = if (preferences.lastSyncTimestamp > 0L) {
                                    "Last checked: ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(preferences.lastSyncTimestamp))}"
                                } else {
                                    "Automatically syncs queued topics & checks model weights on reconnect."
                                }
                                Text(
                                    text = lastSyncedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = preferences.autoSyncWhenOnline,
                                enabled = !preferences.strictOfflineMode,
                                onCheckedChange = onToggleAutoSyncOnline,
                                modifier = Modifier.testTag("auto_sync_switch"),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Sync on Wi-Fi Only",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Switch(
                                checked = preferences.wifiOnlySync,
                                enabled = !preferences.strictOfflineMode && preferences.autoSyncWhenOnline,
                                onCheckedChange = onToggleWifiOnlySync,
                                modifier = Modifier.testTag("wifi_only_sync_switch"),
                            )
                        }

                        if (isSyncingOnline) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        syncStatusMessage?.let { msg ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = onDismissSyncMessage) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onTriggerOnlineSync(false) },
                                enabled = !preferences.strictOfflineMode && !isSyncingOnline,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("check_updates_now_button"),
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Check Updates")
                            }
                            Button(
                                onClick = { onTriggerOnlineSync(true) },
                                enabled = !preferences.strictOfflineMode && !isSyncingOnline,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("auto_upgrade_now_button"),
                            ) {
                                Text("Sync & Upgrade")
                            }
                        }
                    }
                }
            }

            // Transfer progress banner
            if (transferState !is ModelTransferState.Idle) {
                item {
                    TransferProgressCard(
                        state = transferState,
                        onCancel = onCancelTransfer,
                        onDismiss = onDismissTransfer,
                    )
                }
            }

            // Preferred Engine Selector
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Active Chat Engine Routing",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PreferredBackend.entries.forEach { backend ->
                                FilterChip(
                                    selected = preferences.preferredBackend == backend,
                                    onClick = { onSelectBackend(backend) },
                                    label = { Text(backend.displayName) },
                                    modifier = Modifier.testTag("backend_chip_${backend.name.lowercase()}"),
                                )
                            }
                        }
                    }
                }
            }

            // Gemini Nano AICore Card
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gemini Nano (Android AICore)",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "OS-managed foundation model via ML Kit GenAI Prompt API (auto-updated by AICore).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = onRefreshGeminiNano,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("refresh_gemini_nano_button"),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Recheck Gemini Nano")
                            }
                        }

                        val statusLabel = when (geminiStatus) {
                            GeminiNanoStatus.Checking -> "Checking AICore availability…"
                            GeminiNanoStatus.Available -> "Ready & downloaded on device"
                            GeminiNanoStatus.Downloadable -> "Supported — ready for one-time system download"
                            is GeminiNanoStatus.Downloading -> "Downloading (${geminiStatus.bytesDownloaded / (1024 * 1024)} MB)…"
                            is GeminiNanoStatus.Unavailable -> geminiStatus.reason
                        }

                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = when (geminiStatus) {
                                GeminiNanoStatus.Available -> MaterialTheme.colorScheme.primary
                                is GeminiNanoStatus.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.tertiary
                            },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (geminiStatus is GeminiNanoStatus.Downloadable) {
                                Button(
                                    onClick = onDownloadGeminiNano,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("download_gemini_nano_button"),
                                ) {
                                    Text("Download via AICore")
                                }
                            }
                            if (geminiStatus is GeminiNanoStatus.Available) {
                                FilledTonalButton(
                                    onClick = onWarmupGeminiNano,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("warmup_gemini_nano_button"),
                                ) {
                                    Text("Warm Up Memory")
                                }
                            }
                        }
                    }
                }
            }

            // LiteRT-LM Local Files & Import
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "LiteRT-LM Open Weights",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Import any .litertlm file from device storage or cache curated models for offline chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        when (liteRtState) {
                            is LiteRtLoadState.Ready -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Loaded: ${liteRtState.modelName} (${liteRtState.activeBackendLabel})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    OutlinedButton(
                                        onClick = onUnloadLocalModel,
                                        modifier = Modifier.testTag("unload_litert_button"),
                                    ) {
                                        Text("Unload")
                                    }
                                }
                            }
                            is LiteRtLoadState.Loading -> {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Initializing ${liteRtState.modelName} on ${liteRtState.backendLabel}…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            is LiteRtLoadState.Error -> {
                                Text(
                                    text = "Load error: ${liteRtState.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            LiteRtLoadState.Unloaded -> Unit
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Backend:",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            AcceleratorMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = preferences.acceleratorMode == mode,
                                    onClick = { onSelectAccelerator(mode) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("accelerator_${mode.name.lowercase()}"),
                                )
                            }
                        }

                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("import_litertlm_button"),
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import Local .litertlm Model File")
                        }

                        if (localModels.isNotEmpty()) {
                            Text(
                                text = "Stored on Device (${localModels.size})",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            localModels.forEach { file ->
                                val isLoaded = (liteRtState as? LiteRtLoadState.Ready)?.modelPath == file.path
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (isLoaded) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isLoaded) Icons.Default.CheckCircle else Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = file.sizeFormatted,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (!isLoaded) {
                                            FilledTonalButton(
                                                onClick = { onLoadLocalModel(file.path) },
                                                modifier = Modifier.testTag("load_model_${file.name}"),
                                            ) {
                                                Text("Load")
                                            }
                                        }
                                        IconButton(
                                            onClick = { onDeleteLocalModel(file) },
                                            modifier = Modifier.minimumInteractiveComponentSize(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete local model",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Curated Downloadable Catalog
            item {
                Text(
                    text = "Curated Open-Weight Models (One-Time Cache)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            items(OfflineAiCatalog.downloadableLiteRtModels, key = { it.id }) { spec ->
                val alreadyCached = localModels.any { it.name == spec.fileName }
                val remoteInfo = remoteModelUpdates.firstOrNull { it.specId == spec.id }
                val upgradeReady = remoteInfo?.updateAvailable == true
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = spec.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${spec.sizeLabel} · ${spec.minRamGb}GB+ RAM",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (remoteInfo != null && remoteInfo.lastModified.isNotBlank()) {
                            Text(
                                text = if (upgradeReady) {
                                    "Upgrade available (updated ${remoteInfo.lastModified})"
                                } else {
                                    "Upstream checked: updated ${remoteInfo.lastModified}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (upgradeReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = spec.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            spec.strengths.forEach { tag ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                        Button(
                            onClick = { onDownloadCuratedModel(spec) },
                            enabled = (!alreadyCached || upgradeReady) && transferState !is ModelTransferState.InProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("download_curated_${spec.id}"),
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    upgradeReady -> "Upgrade Model (${spec.sizeLabel})"
                                    alreadyCached -> "Cached Locally"
                                    preferences.strictOfflineMode -> "Unlock Offline Mode to Download"
                                    else -> "Download Once (${spec.sizeLabel})"
                                }
                            )
                        }
                    }
                }
            }

            // Inference Sampling & System Persona Card
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text(
                                text = "Generation Tuning",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            text = "Temperature: ${String.format("%.2f", tempSlider)} (lower = deterministic Q&A, higher = creative)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = tempSlider,
                            onValueChange = { tempSlider = it },
                            onValueChangeFinished = {
                                onUpdateSampling(tempSlider, topKSlider, preferences.topP)
                            },
                            valueRange = 0.0f..1.2f,
                        )
                        Text(
                            text = "Top-K: $topKSlider",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = topKSlider.toFloat(),
                            onValueChange = { topKSlider = it.roundToInt() },
                            onValueChangeFinished = {
                                onUpdateSampling(tempSlider, topKSlider, preferences.topP)
                            },
                            valueRange = 1f..80f,
                        )
                        OutlinedTextField(
                            value = systemPromptDraft,
                            onValueChange = { systemPromptDraft = it },
                            label = { Text("System Instruction") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FilledTonalButton(
                            onClick = { onUpdateSystemPrompt(systemPromptDraft) },
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("save_system_prompt_button"),
                        ) {
                            Text("Save Persona")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferProgressCard(
    state: ModelTransferState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is ModelTransferState.InProgress -> {
                    val mbDone = state.bytesTransferred / (1024.0 * 1024.0)
                    val mbTotal = if (state.totalBytes > 0L) state.totalBytes / (1024.0 * 1024.0) else 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.label,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (mbTotal > 0.0) String.format("%.1f / %.1f MB", mbDone, mbTotal) else String.format("%.1f MB", mbDone),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (state.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { state.progressFraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Cancel")
                    }
                }
                is ModelTransferState.Completed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Ready locally: ${state.fileName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
                is ModelTransferState.Failed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
                ModelTransferState.Idle -> Unit
            }
        }
    }
}
