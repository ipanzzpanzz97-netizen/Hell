package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.configureAppCheck
import com.example.ui.ChatViewModel
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CarbonProTheme

const val ROUTE_CHAT = "chat"
const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before rendering UI or creating ViewModels.
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val viewModel: ChatViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val sessions by viewModel.sessions.collectAsStateWithLifecycle()
            val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
            val messages by viewModel.messages.collectAsStateWithLifecycle()
            val generationState by viewModel.generationState.collectAsStateWithLifecycle()
            val connectionTestResult by viewModel.connectionTestState.collectAsStateWithLifecycle()
            val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()

            CarbonProTheme(themePreference = settings.themePreference) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_CHAT
                ) {
                    composable(ROUTE_CHAT) {
                        ChatScreen(
                            settings = settings,
                            sessions = sessions,
                            activeSessionId = activeSessionId,
                            messages = messages,
                            generationState = generationState,
                            onSendMessage = viewModel::sendMessage,
                            onStopGeneration = viewModel::stopGeneration,
                            onRegenerateLast = viewModel::regenerateLastResponse,
                            onSelectSession = viewModel::selectSession,
                            onNewSession = viewModel::startNewSession,
                            onDeleteSession = viewModel::deleteSession,
                            onToggleStructuredOutput = viewModel::setStructuredOutputEnabled,
                            onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            settings = settings,
                            connectionTestResult = connectionTestResult,
                            isTestingConnection = isTestingConnection,
                            onBack = { navController.popBackStack() },
                            onEngineModeChange = viewModel::setEngineMode,
                            onCloudModelChange = viewModel::setCloudModel,
                            onSafetyThresholdChange = viewModel::setSafetyThreshold,
                            onCustomServerSave = viewModel::updateCustomServerConfig,
                            onTestConnection = viewModel::testCustomServerConnection,
                            onSystemPromptChange = viewModel::setSystemPrompt,
                            onResetSystemPrompt = viewModel::resetSystemPrompt,
                            onStructuredOutputChange = viewModel::setStructuredOutputEnabled,
                            onTemperatureChange = viewModel::setTemperature,
                            onThemeChange = viewModel::setThemePreference
                        )
                    }
                }
            }
        }
    }
}
