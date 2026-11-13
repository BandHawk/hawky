package com.example.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Summarize
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AgentProfileEntity
import com.example.data.local.KnowledgeNoteEntity
import com.example.data.local.SelfImprovementLogEntity
import com.example.data.local.SyncQueueEntity
import com.example.ui.components.SectionHeader
import com.example.ui.viewmodel.ActiveResolvedEngine
import com.example.ui.viewmodel.RewriteTone
import com.example.ui.viewmodel.ToolsUiState
import kotlin.math.roundToInt

@Composable
fun ToolsScreen(
    toolsState: ToolsUiState,
    notes: List<KnowledgeNoteEntity>,
    syncQueue: List<SyncQueueEntity>,
    agents: List<AgentProfileEntity>,
    improvementLogs: List<SelfImprovementLogEntity>,
    isSyncingOnline: Boolean,
    syncStatusMessage: String?,
    activeEngine: ActiveResolvedEngine,
    onSummaryInputChange: (String) -> Unit,
    onBulletCountChange: (Int) -> Unit,
    onRunExtractiveSummary: () -> Unit,
    onRunLlmSummary: () -> Unit,
    onRewriteInputChange: (String) -> Unit,
    onSelectRewriteTone: (RewriteTone) -> Unit,
    onRunRewrite: () -> Unit,
    onSaveNote: (title: String, content: String, tags: String) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onNotesQueryChange: (String) -> Unit,
    onQueryNotes: () -> Unit,
    onLearnTopicInputChange: (String) -> Unit,
    onQueueOrLearnTopic: (String) -> Unit,
    onRemoveQueueItem: (Long) -> Unit,
    onSyncNow: () -> Unit,
    onAutoSpawnAgent: (String) -> Unit,
    onCreateCustomAgent: (name: String, role: String, prompt: String, focusTags: String) -> Unit,
    onActivateAgent: (AgentProfileEntity) -> Unit,
    onDeactivateAgents: () -> Unit,
    onDeleteAgent: (Long) -> Unit,
    onRunSelfImprovement: (goal: String, draft: String, applyUpgrade: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Summarize", "Vault", "Rewrite", "Agents")

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                title = "Offline AI Studio",
                subtitle = "Summarize, auto-learn into your Room Vault, rewrite drafts, or spawn self-improving AI agents.",
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                        modifier = Modifier.testTag("tools_tab_${label.lowercase()}"),
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }

            if (toolsState.isRunningTool || isSyncingOnline) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            syncStatusMessage?.let { statusMsg ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = statusMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                    )
                }
            }

            toolsState.toolError?.let { errorMsg ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                }
            }

            when (selectedTab) {
                0 -> SummarizerPane(
                    state = toolsState,
                    llmReady = activeEngine.isGenerativeReady,
                    onInputChange = onSummaryInputChange,
                    onBulletsChange = onBulletCountChange,
                    onRunExtractive = onRunExtractiveSummary,
                    onRunLlm = onRunLlmSummary,
                    modifier = Modifier.weight(1f),
                )
                1 -> NotesVaultPane(
                    state = toolsState,
                    notes = notes,
                    syncQueue = syncQueue,
                    llmReady = activeEngine.isGenerativeReady,
                    onSaveNote = onSaveNote,
                    onDeleteNote = onDeleteNote,
                    onQueryChange = onNotesQueryChange,
                    onQuery = onQueryNotes,
                    onLearnTopicChange = onLearnTopicInputChange,
                    onQueueOrLearnTopic = onQueueOrLearnTopic,
                    onRemoveQueueItem = onRemoveQueueItem,
                    onSyncNow = onSyncNow,
                    modifier = Modifier.weight(1f),
                )
                2 -> RewritePane(
                    state = toolsState,
                    llmReady = activeEngine.isGenerativeReady,
                    onInputChange = onRewriteInputChange,
                    onSelectTone = onSelectRewriteTone,
                    onRun = onRunRewrite,
                    modifier = Modifier.weight(1f),
                )
                else -> AgentsPane(
                    state = toolsState,
                    agents = agents,
                    improvementLogs = improvementLogs,
                    llmReady = activeEngine.isGenerativeReady,
                    onAutoSpawnAgent = onAutoSpawnAgent,
                    onCreateCustomAgent = onCreateCustomAgent,
                    onActivateAgent = onActivateAgent,
                    onDeactivateAgents = onDeactivateAgents,
                    onDeleteAgent = onDeleteAgent,
                    onRunSelfImprovement = onRunSelfImprovement,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummarizerPane(
    state: ToolsUiState,
    llmReady: Boolean,
    onInputChange: (String) -> Unit,
    onBulletsChange: (Int) -> Unit,
    onRunExtractive: () -> Unit,
    onRunLlm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.summaryInput,
                onValueChange = onInputChange,
                label = { Text("Paste article, meeting notes, or text") },
                minLines = 5,
                maxLines = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("summarizer_input"),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Key bullets: ${state.summaryBulletCount}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = state.summaryBulletCount.toFloat(),
                    onValueChange = { onBulletsChange(it.roundToInt()) },
                    valueRange = 2f..7f,
                    steps = 4,
                    modifier = Modifier.width(180.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRunExtractive,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("run_extractive_summary_button"),
                ) {
                    Icon(Icons.Outlined.Summarize, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Instant TextRank")
                }

                FilledTonalButton(
                    onClick = onRunLlm,
                    enabled = !state.isRunningTool,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("run_llm_summary_button"),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (llmReady) "LLM Rewrite" else "Auto Summary")
                }
            }
        }

        state.extractiveSummary?.let { summary ->
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
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
                            Text(
                                text = "TextRank Extractive Summary",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${summary.originalWordCount}w → ${summary.summaryWordCount}w (-${summary.compressionPercent}%)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        summary.bullets.forEach { sentence ->
                            Text(
                                text = "• $sentence",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        if (summary.keyTerms.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                summary.keyTerms.forEach { term ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(term, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.llmSummaryOutput.isNotBlank()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "On-Device LLM Summary",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = state.llmSummaryOutput,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesVaultPane(
    state: ToolsUiState,
    notes: List<KnowledgeNoteEntity>,
    syncQueue: List<SyncQueueEntity>,
    llmReady: Boolean,
    onSaveNote: (String, String, String) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onQuery: () -> Unit,
    onLearnTopicChange: (String) -> Unit,
    onQueueOrLearnTopic: (String) -> Unit,
    onRemoveQueueItem: (Long) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddNote by rememberSaveable { mutableStateOf(false) }
    var noteTitle by rememberSaveable { mutableStateOf("") }
    var noteContent by rememberSaveable { mutableStateOf("") }
    var noteTags by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Auto-Learn Topic Card (Online Ingestion / Offline Queue)
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Auto-Learn Topic for Offline Use",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "Enter any subject (e.g., \"CRISPR\", \"TCP/IP\", \"First aid\"). If online, PocketMind learns it now; if offline, it queues and auto-learns on reconnect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.learnTopicInput,
                            onValueChange = onLearnTopicChange,
                            label = { Text("Topic to learn") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("learn_topic_input"),
                        )
                        FilledTonalButton(
                            onClick = { onQueueOrLearnTopic(state.learnTopicInput) },
                            enabled = state.learnTopicInput.isNotBlank(),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("learn_topic_button"),
                        ) {
                            Text("Learn")
                        }
                    }

                    if (syncQueue.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Learning Queue (${syncQueue.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            FilledTonalButton(
                                onClick = onSyncNow,
                                modifier = Modifier.testTag("sync_queue_now_button"),
                            ) {
                                Text("Sync Now")
                            }
                        }
                        syncQueue.forEach { qItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${qItem.topic} · ${qItem.status}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (qItem.errorMessage.isNotBlank()) {
                                        Text(
                                            text = qItem.errorMessage,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveQueueItem(qItem.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove queued topic")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.notesQuery,
                    onValueChange = onQueryChange,
                    label = { Text("Ask a question across your offline notes") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("notes_query_input"),
                )
                Button(
                    onClick = onQuery,
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("query_notes_button"),
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Ask notes")
                }
            }
        }

        if (state.notesSynthesizedAnswer.isNotBlank()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Local RAG Synthesis",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = state.notesSynthesizedAnswer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (state.notesMatches.isNotEmpty()) {
            item {
                Text(
                    text = if (llmReady) "Retrieved Local Citations (BM25)" else "Best Local Matches (BM25 Offline Search)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.notesMatches) { match ->
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = match.noteTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Terms: ${match.matchedTerms.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = match.excerpt,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Stored Knowledge (${notes.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalButton(
                    onClick = { showAddNote = !showAddNote },
                    modifier = Modifier.testTag("toggle_add_note_button"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showAddNote) "Close" else "Add Note")
                }
            }
        }

        if (showAddNote) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = noteTitle,
                            onValueChange = { noteTitle = it },
                            label = { Text("Note Title") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_title_input"),
                        )
                        OutlinedTextField(
                            value = noteContent,
                            onValueChange = { noteContent = it },
                            label = { Text("Private Note Content (stored locally in Room)") },
                            minLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_content_input"),
                        )
                        OutlinedTextField(
                            value = noteTags,
                            onValueChange = { noteTags = it },
                            label = { Text("Tags (optional, comma-separated)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                if (noteTitle.isNotBlank() && noteContent.isNotBlank()) {
                                    onSaveNote(noteTitle, noteContent, noteTags)
                                    noteTitle = ""
                                    noteContent = ""
                                    noteTags = ""
                                    showAddNote = false
                                }
                            },
                            enabled = noteTitle.isNotBlank() && noteContent.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("save_note_button"),
                        ) {
                            Text("Save to Local Vault")
                        }
                    }
                }
            }
        }

        if (notes.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "No notes saved yet. Add private notes or learn topics above so PocketMind can answer questions completely offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(notes, key = { it.id }) { note ->
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                            if (note.tags.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "#${note.tags}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteNote(note.id) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete note",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RewritePane(
    state: ToolsUiState,
    llmReady: Boolean,
    onInputChange: (String) -> Unit,
    onSelectTone: (RewriteTone) -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!llmReady) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                ) {
                    Text(
                        text = "Load a .litertlm model or Gemini Nano in the Models tab to enable generative tone rewriting & proofreading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = state.rewriteInput,
                onValueChange = onInputChange,
                label = { Text("Draft text to proofread or rewrite") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rewrite_input"),
            )
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RewriteTone.entries.forEach { tone ->
                    FilterChip(
                        selected = state.rewriteTone == tone,
                        onClick = { onSelectTone(tone) },
                        label = { Text(tone.label) },
                        modifier = Modifier.testTag("tone_chip_${tone.name.lowercase()}"),
                    )
                }
            }
        }

        item {
            Button(
                onClick = onRun,
                enabled = !state.isRunningTool,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("run_rewrite_button"),
            ) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rewrite Locally (${state.rewriteTone.label})")
            }
        }

        if (state.rewriteOutput.isNotBlank()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Rewritten Output",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = state.rewriteOutput,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
