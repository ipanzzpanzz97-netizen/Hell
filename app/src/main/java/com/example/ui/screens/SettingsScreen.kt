package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ai.ConnectionTestResult
import com.example.data.settings.CarbonSettings
import com.example.data.settings.EngineMode
import com.example.data.settings.SafetyThresholdOption
import com.example.data.settings.ThemePreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: CarbonSettings,
    connectionTestResult: ConnectionTestResult?,
    isTestingConnection: Boolean,
    onBack: () -> Unit,
    onEngineModeChange: (EngineMode) -> Unit,
    onCloudModelChange: (String) -> Unit,
    onSafetyThresholdChange: (SafetyThresholdOption) -> Unit,
    onCustomServerSave: (url: String, key: String, model: String, useSse: Boolean) -> Unit,
    onTestConnection: (url: String, key: String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onResetSystemPrompt: () -> Unit,
    onStructuredOutputChange: (Boolean) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onThemeChange: (ThemePreference) -> Unit
) {
    var apiUrlDraft by remember(settings.customApiUrl) { mutableStateOf(settings.customApiUrl) }
    var apiKeyDraft by remember(settings.customApiKey) { mutableStateOf(settings.customApiKey) }
    var customModelDraft by remember(settings.customModelName) { mutableStateOf(settings.customModelName) }
    var useSseDraft by remember(settings.customUseSse) { mutableStateOf(settings.customUseSse) }
    var showApiKey by remember { mutableStateOf(false) }

    var systemPromptDraft by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
            ) {
                item {
                    SectionCard(title = "1. Mod Enjin AI") {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = settings.engineMode == EngineMode.CLOUD_BUILTIN,
                                onClick = { onEngineModeChange(EngineMode.CLOUD_BUILTIN) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Cloud,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("mode_cloud_builtin")
                            ) {
                                Text("Awan Terbina")
                            }
                            SegmentedButton(
                                selected = settings.engineMode == EngineMode.CUSTOM_SERVER,
                                onClick = { onEngineModeChange(EngineMode.CUSTOM_SERVER) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Dns,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("mode_custom_server")
                            ) {
                                Text("Pelayan Tersuai")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (settings.engineMode == EngineMode.CLOUD_BUILTIN) {
                                "Awan Terbina menggunakan Firebase AI Logic terus daripada APK tanpa perlu mengurus pelayan GPU atau API Key luaran."
                            } else {
                                "Mod Pelayan Tersuai menyambung ke pelayan awan peribadi anda (Ollama / vLLM / TGI / OpenAI-compatible) melalui API_URL dan API_KEY."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (settings.engineMode == EngineMode.CLOUD_BUILTIN) {
                    item {
                        SectionCard(title = "2. Model Awan & Kawalan Tapisan") {
                            CarbonSettings.AVAILABLE_CLOUD_MODELS.forEach { (modelId, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = settings.cloudModel == modelId,
                                        onClick = { onCloudModelChange(modelId) }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = modelId,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Tahap Penapisan Keselamatan",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            SafetyThresholdOption.entries.forEach { option ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    RadioButton(
                                        selected = settings.safetyThreshold == option,
                                        onClick = { onSafetyThresholdChange(option) }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.label,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Nota ketelusan: Model Gemini awan masih mematuhi dasar teras Google. Jika anda mahu kawalan tapisan kendiri 100%, gunakan mod Pelayan Tersuai dengan model Ollama/vLLM anda sendiri.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item {
                        SectionCard(title = "2. Tetapan Pelayan API Tersuai") {
                            OutlinedTextField(
                                value = apiUrlDraft,
                                onValueChange = { apiUrlDraft = it },
                                label = { Text("API_URL (Contoh: http://192.168.1.50:8000)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_api_url_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = apiKeyDraft,
                                onValueChange = { apiKeyDraft = it },
                                label = { Text("API_KEY (Bearer Token)") },
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = stringResource(R.string.cd_toggle_key_visibility)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_api_key_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customModelDraft,
                                onValueChange = { customModelDraft = it },
                                label = { Text("Nama Model (cth: carbon-pro / deepseek-r1)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_model_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Penstriman SSE (Real-Time Typing)",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Menyokong Server-Sent Events (/v1/chat/completions)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = useSseDraft,
                                    onCheckedChange = { useSseDraft = it }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onTestConnection(apiUrlDraft, apiKeyDraft) },
                                    enabled = !isTestingConnection,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("test_connection_button")
                                ) {
                                    if (isTestingConnection) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Uji Sambungan")
                                    }
                                }
                                Button(
                                    onClick = {
                                        onCustomServerSave(
                                            apiUrlDraft.trim(),
                                            apiKeyDraft.trim(),
                                            customModelDraft.trim(),
                                            useSseDraft
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_custom_api_button")
                                ) {
                                    Text("Simpan API")
                                }
                            }

                            if (connectionTestResult != null) {
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    color = if (connectionTestResult.success) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (connectionTestResult.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (connectionTestResult.success) {
                                                MaterialTheme.colorScheme.secondary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "${connectionTestResult.message} (${connectionTestResult.latencyMs}ms)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "3. Protokol Penaakulan & Parameter") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Format Output JSON Ejen Berstruktur",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Pulangkan status, thought_tokens_used, code_snippet & security_checks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.structuredOutputEnabled,
                                onCheckedChange = onStructuredOutputChange
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        val tempDisplay = ((settings.temperature * 10).roundToInt() / 10f)
                        Text(
                            text = "Kreativiti / Temperature: $tempDisplay",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Slider(
                            value = settings.temperature,
                            onValueChange = onTemperatureChange,
                            valueRange = 0f..1.5f
                        )

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Arahan Sistem (System Prompt)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            IconButton(
                                onClick = onResetSystemPrompt,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = "Set semula arahan sistem"
                                )
                            }
                        }
                        OutlinedTextField(
                            value = systemPromptDraft,
                            onValueChange = {
                                systemPromptDraft = it
                                onSystemPromptChange(it)
                            },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("system_prompt_input")
                        )
                    }
                }

                item {
                    SectionCard(title = "4. Tema Antaramuka") {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemePreference.entries.forEachIndexed { idx, pref ->
                                SegmentedButton(
                                    selected = settings.themePreference == pref,
                                    onClick = { onThemeChange(pref) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = ThemePreference.entries.size
                                    )
                                ) {
                                    Text(pref.label, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
