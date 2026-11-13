package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AgentProfileEntity
import com.example.data.local.SelfImprovementLogEntity
import com.example.ui.viewmodel.ToolsUiState

@Composable
fun AgentsPane(
    state: ToolsUiState,
    agents: List<AgentProfileEntity>,
    improvementLogs: List<SelfImprovementLogEntity>,
    llmReady: Boolean,
    onAutoSpawnAgent: (String) -> Unit,
    onCreateCustomAgent: (name: String, role: String, prompt: String, focusTags: String) -> Unit,
    onActivateAgent: (AgentProfileEntity) -> Unit,
    onDeactivateAgents: () -> Unit,
    onDeleteAgent: (Long) -> Unit,
    onRunSelfImprovement: (goal: String, draft: String, applyUpgrade: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var spawnGoal by rememberSaveable { mutableStateOf("") }
    var showManualCreator by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf("") }
    var customRole by rememberSaveable { mutableStateOf("") }
    var customPrompt by rememberSaveable { mutableStateOf("") }
    var customTags by rememberSaveable { mutableStateOf("") }

    var critiquePromptInput by rememberSaveable { mutableStateOf("") }
    var critiqueDraftInput by rememberSaveable { mutableStateOf("") }
    var autoApplyUpgrade by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // AI Spawner Card
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Spawn a Specialist AI Sub-Agent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = if (llmReady) {
                            "Describe what helper AI you want. PocketMind's active local LLM will design a specialized sub-agent (role, instructions, sampling) to assist and improve its own outputs."
                        } else {
                            "Describe a specialist goal (e.g., \"Accuracy Critic\", \"Study Tutor\", \"Code Reviewer\") to spawn a tuned sub-agent profile locally."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = spawnGoal,
                        onValueChange = { spawnGoal = it },
                        label = { Text("Goal for the new AI (e.g., Critique & verify my answers)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("spawn_agent_goal_input"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                if (spawnGoal.isNotBlank()) {
                                    onAutoSpawnAgent(spawnGoal.trim())
                                    spawnGoal = ""
                                }
                            },
                            enabled = spawnGoal.isNotBlank() && !state.isRunningTool,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("auto_spawn_agent_button"),
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Specialist AI")
                        }
                        OutlinedButton(
                            onClick = { showManualCreator = !showManualCreator },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("toggle_manual_agent_button"),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showManualCreator) "Hide" else "Manual")
                        }
                    }
                }
            }
        }

        if (showManualCreator) {
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Custom Sub-Agent Blueprint", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Agent Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = customRole,
                            onValueChange = { customRole = it },
                            label = { Text("Role (e.g., Fact Verifier)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = customPrompt,
                            onValueChange = { customPrompt = it },
                            label = { Text("System Instructions") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = customTags,
                            onValueChange = { customTags = it },
                            label = { Text("Focus Tags (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                if (customName.isNotBlank() && customPrompt.isNotBlank()) {
                                    onCreateCustomAgent(
                                        customName.trim(),
                                        customRole.trim().ifBlank { "Specialist" },
                                        customPrompt.trim(),
                                        customTags.trim(),
                                    )
                                    customName = ""
                                    customRole = ""
                                    customPrompt = ""
                                    customTags = ""
                                    showManualCreator = false
                                }
                            },
                            enabled = customName.isNotBlank() && customPrompt.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text("Save Specialist AI")
                        }
                    }
                }
            }
        }

        // Self-Improvement Critic Loop Card
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
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
                            imageVector = Icons.Default.Upgrade,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = "Self-Improvement Critic Loop",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "Run a spawned Critic AI to evaluate a draft answer, produce a stronger revision, and upgrade PocketMind's active system instruction.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = critiquePromptInput,
                        onValueChange = { critiquePromptInput = it },
                        label = { Text("Original question / goal") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("critic_goal_input"),
                    )
                    OutlinedTextField(
                        value = critiqueDraftInput,
                        onValueChange = { critiqueDraftInput = it },
                        label = { Text("Draft response to critique & improve") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("critic_draft_input"),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = autoApplyUpgrade,
                            onCheckedChange = { autoApplyUpgrade = it },
                        )
                        Text(
                            text = "Automatically upgrade PocketMind's system instruction",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = {
                            onRunSelfImprovement(
                                critiquePromptInput.trim(),
                                critiqueDraftInput.trim(),
                                autoApplyUpgrade,
                            )
                        },
                        enabled = critiqueDraftInput.isNotBlank() && !state.isRunningTool,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("run_self_improvement_button"),
                    ) {
                        Text("Run Critic AI & Upgrade")
                    }
                }
            }
        }

        // Spawned Agents List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Spawned Sub-Agents (${agents.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (agents.any { it.isActive }) {
                    OutlinedButton(onClick = onDeactivateAgents) {
                        Text("Reset Default")
                    }
                }
            }
        }

        if (agents.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = "No sub-agents spawned yet. Create a Critic, Tutor, or Domain Specialist above to customize and upgrade PocketMind.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            items(agents, key = { it.id }) { agent ->
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = if (agent.isActive) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (agent.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (agent.isActive) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Active agent",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text = "${agent.name} · ${agent.role}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (!agent.isActive) {
                                FilledTonalButton(
                                    onClick = { onActivateAgent(agent) },
                                    modifier = Modifier.testTag("activate_agent_${agent.id}"),
                                ) {
                                    Text("Activate")
                                }
                            }
                            IconButton(
                                onClick = { onDeleteAgent(agent.id) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete agent")
                            }
                        }
                        Text(
                            text = agent.systemPrompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Temp ${agent.temperature} · Top-K ${agent.topK}" +
                                if (agent.createdByAi) " · AI-Synthesized" else " · Custom",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (improvementLogs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Self-Improvement History (${improvementLogs.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(improvementLogs.take(6), key = { it.id }) { log ->
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Critic: ${log.agentName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = "Critique: ${log.critiqueSummary}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (log.improvedResponse.isNotBlank()) {
                            Text(
                                text = "Refined Output: ${log.improvedResponse}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (log.upgradedInstruction.isNotBlank()) {
                            Text(
                                text = "Upgraded Rule: ${log.upgradedInstruction}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
