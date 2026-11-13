package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.preferences.PreferredBackend
import com.example.ui.navigation.PocketMindRoute
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.GuideScreen
import com.example.ui.screens.ModelsScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.PocketMindTheme
import com.example.ui.viewmodel.PocketMindViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketMindTheme {
                PocketMindApp()
            }
        }
    }
}

private data class NavItem(
    val route: PocketMindRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@Composable
fun PocketMindApp(
    viewModel: PocketMindViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val chatNotice by viewModel.chatNotice.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val syncQueue by viewModel.syncQueue.collectAsStateWithLifecycle()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val improvementLogs by viewModel.improvementLogs.collectAsStateWithLifecycle()
    val geminiStatus by viewModel.geminiStatus.collectAsStateWithLifecycle()
    val liteRtState by viewModel.liteRtState.collectAsStateWithLifecycle()
    val localModels by viewModel.localModels.collectAsStateWithLifecycle()
    val transferState by viewModel.transferState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val toolsState by viewModel.toolsState.collectAsStateWithLifecycle()
    val isSyncingOnline by viewModel.isSyncingOnline.collectAsStateWithLifecycle()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsStateWithLifecycle()
    val remoteModelUpdates by viewModel.remoteModelUpdates.collectAsStateWithLifecycle()

    val navItems = listOf(
        NavItem(
            route = PocketMindRoute.Chat,
            labelRes = R.string.nav_chat,
            selectedIcon = Icons.Filled.AutoAwesome,
            unselectedIcon = Icons.Outlined.AutoAwesome,
            testTag = "nav_chat",
        ),
        NavItem(
            route = PocketMindRoute.Tools,
            labelRes = R.string.nav_studio,
            selectedIcon = Icons.Filled.Build,
            unselectedIcon = Icons.Outlined.Build,
            testTag = "nav_tools",
        ),
        NavItem(
            route = PocketMindRoute.Models,
            labelRes = R.string.nav_models,
            selectedIcon = Icons.Filled.Memory,
            unselectedIcon = Icons.Outlined.Memory,
            testTag = "nav_models",
        ),
        NavItem(
            route = PocketMindRoute.Guide,
            labelRes = R.string.nav_guide,
            selectedIcon = Icons.Filled.MenuBook,
            unselectedIcon = Icons.Outlined.MenuBook,
            testTag = "nav_guide",
        ),
    )

    fun navigateTopLevel(route: PocketMindRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes),
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes),
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = PocketMindRoute.Chat,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    composable<PocketMindRoute.Chat> {
                        ChatScreen(
                            sessions = sessions,
                            activeSessionId = activeSessionId,
                            messages = messages,
                            isGenerating = isGenerating,
                            streamingReply = streamingReply,
                            activeEngine = viewModel.resolveActiveEngine(),
                            activeEngineLabel = viewModel.activeEngineDisplayLabel(),
                            strictOfflineMode = preferences.strictOfflineMode,
                            networkState = networkState,
                            chatNotice = chatNotice,
                            onSendPrompt = viewModel::sendChatPrompt,
                            onStopGeneration = viewModel::stopGeneration,
                            onNewChat = viewModel::startNewSession,
                            onSelectSession = viewModel::selectSession,
                            onDeleteSession = viewModel::deleteSession,
                            onDismissNotice = viewModel::dismissChatNotice,
                            onSwitchToBuiltInNlp = {
                                viewModel.setPreferredBackend(PreferredBackend.EXTRACTIVE_NLP)
                            },
                            onNavigateToModels = { navigateTopLevel(PocketMindRoute.Models) },
                            onNavigateToTools = { navigateTopLevel(PocketMindRoute.Tools) },
                            onSelfImproveMessage = viewModel::critiqueChatMessage,
                        )
                    }

                    composable<PocketMindRoute.Tools> {
                        ToolsScreen(
                            toolsState = toolsState,
                            notes = notes,
                            syncQueue = syncQueue,
                            agents = agents,
                            improvementLogs = improvementLogs,
                            isSyncingOnline = isSyncingOnline,
                            syncStatusMessage = syncStatusMessage,
                            activeEngine = viewModel.resolveActiveEngine(),
                            onSummaryInputChange = viewModel::updateSummaryInput,
                            onBulletCountChange = viewModel::updateSummaryBullets,
                            onRunExtractiveSummary = viewModel::runExtractiveSummary,
                            onRunLlmSummary = viewModel::runLlmSummary,
                            onRewriteInputChange = viewModel::updateRewriteInput,
                            onSelectRewriteTone = viewModel::selectRewriteTone,
                            onRunRewrite = viewModel::runRewriteTool,
                            onSaveNote = viewModel::saveNote,
                            onDeleteNote = viewModel::deleteNote,
                            onNotesQueryChange = viewModel::updateNotesQuery,
                            onQueryNotes = viewModel::queryNotesVault,
                            onLearnTopicInputChange = viewModel::updateLearnTopicInput,
                            onQueueOrLearnTopic = viewModel::queueOrLearnTopic,
                            onRemoveQueueItem = viewModel::removeQueueItem,
                            onSyncNow = { viewModel.triggerOnlineSync(false) },
                            onAutoSpawnAgent = viewModel::autoCreateSpecialistAgent,
                            onCreateCustomAgent = viewModel::createCustomAgent,
                            onActivateAgent = viewModel::activateAgent,
                            onDeactivateAgents = viewModel::deactivateAllAgents,
                            onDeleteAgent = viewModel::deleteAgent,
                            onRunSelfImprovement = viewModel::runSelfImprovementLoop,
                        )
                    }

                    composable<PocketMindRoute.Models> {
                        ModelsScreen(
                            preferences = preferences,
                            networkState = networkState,
                            geminiStatus = geminiStatus,
                            liteRtState = liteRtState,
                            localModels = localModels,
                            transferState = transferState,
                            isSyncingOnline = isSyncingOnline,
                            syncStatusMessage = syncStatusMessage,
                            remoteModelUpdates = remoteModelUpdates,
                            onToggleStrictOffline = viewModel::setStrictOfflineMode,
                            onToggleAutoSyncOnline = viewModel::setAutoSyncWhenOnline,
                            onToggleWifiOnlySync = viewModel::setWifiOnlySync,
                            onTriggerOnlineSync = viewModel::triggerOnlineSync,
                            onDismissSyncMessage = viewModel::dismissSyncMessage,
                            onSelectBackend = viewModel::setPreferredBackend,
                            onSelectAccelerator = viewModel::setAcceleratorMode,
                            onUpdateSampling = viewModel::updateSampling,
                            onUpdateSystemPrompt = viewModel::updateSystemPrompt,
                            onRefreshGeminiNano = viewModel::refreshGeminiNano,
                            onDownloadGeminiNano = viewModel::downloadGeminiNano,
                            onWarmupGeminiNano = viewModel::warmupGeminiNano,
                            onImportModelUri = viewModel::importLocalModel,
                            onDownloadCuratedModel = viewModel::downloadCuratedModel,
                            onCancelTransfer = viewModel::cancelTransfer,
                            onDismissTransfer = viewModel::dismissTransferBanner,
                            onLoadLocalModel = viewModel::loadLiteRtModel,
                            onUnloadLocalModel = viewModel::unloadLiteRtModel,
                            onDeleteLocalModel = viewModel::deleteLocalModel,
                        )
                    }

                    composable<PocketMindRoute.Guide> {
                        GuideScreen()
                    }
                }
            }
        }
    }
}
