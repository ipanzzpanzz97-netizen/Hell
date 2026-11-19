package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.settings.CarbonSettings
import com.example.data.settings.EngineMode
import com.example.ui.GenerationState
import com.example.ui.components.MarkdownMessageView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    settings: CarbonSettings,
    sessions: List<ChatSessionEntity>,
    activeSessionId: String?,
    messages: List<ChatMessageEntity>,
    generationState: GenerationState,
    onSendMessage: (String, String?) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateLast: () -> Unit,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onToggleStructuredOutput: (Boolean) -> Unit,
    onToggleDeepAgent: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var promptInput by rememberSaveable { mutableStateOf("") }
    var attachedImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedImageUri = uri.toString()
        }
    }

    val isGenerating = generationState is GenerationState.Thinking ||
        generationState is GenerationState.Streaming

    LaunchedEffect(messages.size, generationState) {
        if (messages.isNotEmpty() || generationState is GenerationState.Streaming) {
            val targetIndex = (messages.size + if (isGenerating) 1 else 0 - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatHistoryDrawerContent(
                sessions = sessions,
                activeSessionId = activeSessionId,
                settings = settings,
                onNewSession = {
                    onNewSession()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { id ->
                    onSelectSession(id)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = onDeleteSession,
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_open_drawer)
                            )
                        }
                    },
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "CARBON-PRO",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val subtitle = if (settings.engineMode == EngineMode.CLOUD_BUILTIN) {
                                "Cloud • ${settings.cloudModel}"
                            } else {
                                val model = settings.customModelName.ifBlank { "Custom API" }
                                "Pelayan Tersuai • $model"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onNewSession,
                            modifier = Modifier.testTag("new_chat_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.cd_new_chat)
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.cd_open_settings)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                ChatComposerBar(
                    promptInput = promptInput,
                    onPromptChange = { promptInput = it },
                    attachedImageUri = attachedImageUri,
                    onRemoveImage = { attachedImageUri = null },
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    isGenerating = isGenerating,
                    structuredOutputEnabled = settings.structuredOutputEnabled,
                    onToggleStructured = onToggleStructuredOutput,
                    deepAgentMode = settings.deepAgentMode,
                    onToggleDeepAgent = onToggleDeepAgent,
                    isCustomServer = settings.engineMode == EngineMode.CUSTOM_SERVER,
                    onSend = {
                        val text = promptInput
                        val img = attachedImageUri
                        promptInput = ""
                        attachedImageUri = null
                        onSendMessage(text, img)
                    },
                    onStop = onStopGeneration
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    EmptyWorkspaceHero(
                        settings = settings,
                        onSelectStarterPrompt = { starter ->
                            promptInput = starter
                        },
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier
                            .widthIn(max = 820.dp)
                            .fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .widthIn(max = 820.dp)
                            .fillMaxSize()
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val isLastAssistant = msg.role == "assistant" &&
                                messages.lastOrNull()?.id == msg.id
                            ChatMessageBubble(
                                message = msg,
                                showRegenerate = isLastAssistant && !isGenerating,
                                onRegenerate = onRegenerateLast
                            )
                        }

                        if (generationState is GenerationState.Thinking) {
                            item(key = "thinking_indicator") {
                                ThinkingPulseBubble(phaseLabel = generationState.phaseLabel)
                            }
                        } else if (generationState is GenerationState.Streaming) {
                            item(key = "streaming_bubble") {
                                StreamingAssistantBubble(
                                    phaseLabel = generationState.phaseLabel,
                                    visibleText = generationState.visibleText,
                                    thinkingText = generationState.thinkingText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryDrawerContent(
    sessions: List<ChatSessionEntity>,
    activeSessionId: String?,
    settings: CarbonSettings,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "CarbonPro AI",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (settings.engineMode == EngineMode.CLOUD_BUILTIN) {
                            "Awan Terbina Dalam"
                        } else {
                            "API Pelayan Tersuai"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                onClick = onNewSession,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_new_chat_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.new_chat_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "SEJARAH SEMBANG",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (sessions.isEmpty()) {
                    item {
                        Text(
                            text = "Tiada sesi lagi. Mulakan perbualan baharu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    items(sessions, key = { it.id }) { session ->
                        NavigationDrawerItem(
                            selected = session.id == activeSessionId,
                            onClick = { onSelectSession(session.id) },
                            label = {
                                Text(
                                    text = session.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            badge = {
                                IconButton(
                                    onClick = { onDeleteSession(session.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.cd_delete_session),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.testTag("session_item_${session.id}")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                selected = false,
                onClick = onOpenSettings,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                modifier = Modifier.testTag("drawer_settings_button")
            )
        }
    }
}

@Composable
private fun EmptyWorkspaceHero(
    settings: CarbonSettings,
    onSelectStarterPrompt: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.height(190.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_carbon_hero_1795059957433),
                        contentDescription = stringResource(R.string.cd_hero_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "CARBON-PRO • PENAAKULAN LANJUTAN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Enjin Sedia Digunakan",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (settings.engineMode == EngineMode.CLOUD_BUILTIN) {
                                "Bersambung ke Awan Terbina (${settings.cloudModel}). Boleh tukar ke Pelayan API Tersuai bila-bila masa."
                            } else {
                                "Mod Pelayan Tersuai aktif (${settings.customApiUrl.ifBlank { "URL belum diisi" }})."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (settings.engineMode == EngineMode.CUSTOM_SERVER && settings.customApiUrl.isBlank()) {
            item {
                Surface(
                    onClick = onOpenSettings,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "URL Pelayan API Tersuai belum diisi. Ketik untuk konfigurasi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "CADANGAN ARAHAN PANTAS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        val starters = listOf(
            Triple(
                Icons.Rounded.Code,
                "Seni Bina & Kod Kotlin / Backend",
                "Reka bentuk klien API Retrofit/OkHttp dengan SSE streaming dan pengendalian ralat yang tahan lasak dalam Kotlin."
            ),
            Triple(
                Icons.Rounded.Security,
                "Audit Keselamatan & Kerentanan",
                "Semak senarai amalan terbaik untuk mengesan kelemahan suntikan SQL, SSRF, dan kebocoran token pada API REST."
            ),
            Triple(
                Icons.Rounded.Functions,
                "Penaakulan Matematik & Algoritma",
                "Selesaikan masalah pengoptimuman laluan graf berwajaran dengan analisis kerumitan masa dan ruang langkah demi langkah."
            ),
            Triple(
                Icons.Rounded.Terminal,
                "Skrip Automasi Pelayan Linux & Docker",
                "Tulis konfigurasi Docker Compose lengkap untuk menghoskan pelayan model AI vLLM bersama reverse proxy Nginx."
            )
        )

        items(starters) { (icon, title, prompt) ->
            StarterPromptCard(
                icon = icon,
                title = title,
                prompt = prompt,
                onClick = { onSelectStarterPrompt(prompt) }
            )
        }
    }
}

@Composable
private fun StarterPromptCard(
    icon: ImageVector,
    title: String,
    prompt: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessageEntity,
    showRegenerate: Boolean,
    onRegenerate: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    if (message.role == "user") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.widthIn(max = 580.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!message.imageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = message.imageUri,
                            contentDescription = "Imej dilampirkan",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    } else if (message.isError) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ralat Sambungan / Penjanaan",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (showRegenerate) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onRegenerate,
                        modifier = Modifier.testTag("retry_error_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Cuba Semula")
                    }
                }
            }
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "CARBON-PRO",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(message.content))
                                copied = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy_text),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (showRegenerate) {
                            IconButton(
                                onClick = onRegenerate,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("regenerate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.cd_regenerate),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                MarkdownMessageView(
                    content = message.content,
                    thinkingTrace = message.thinkingTrace,
                    structuredJson = message.structuredJson,
                    isStreaming = false
                )
            }
        }
    }
}

@Composable
private fun ThinkingPulseBubble(phaseLabel: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    phaseLabel: String,
    visibleText: String,
    thinkingText: String?
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = phaseLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            MarkdownMessageView(
                content = visibleText,
                thinkingTrace = thinkingText,
                isStreaming = true
            )
        }
    }
}

@Composable
private fun ChatComposerBar(
    promptInput: String,
    onPromptChange: (String) -> Unit,
    attachedImageUri: String?,
    onRemoveImage: () -> Unit,
    onPickImage: () -> Unit,
    isGenerating: Boolean,
    structuredOutputEnabled: Boolean,
    onToggleStructured: (Boolean) -> Unit,
    deepAgentMode: Boolean,
    onToggleDeepAgent: (Boolean) -> Unit,
    isCustomServer: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 820.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = deepAgentMode,
                        onClick = { onToggleDeepAgent(!deepAgentMode) },
                        label = {
                            Text(
                                text = "Deep Agent 2-Fasa",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("toggle_deep_agent_chip")
                    )

                    FilterChip(
                        selected = structuredOutputEnabled,
                        onClick = { onToggleStructured(!structuredOutputEnabled) },
                        label = {
                            Text(
                                text = "JSON Ejen",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("toggle_structured_chip")
                    )

                    if (attachedImageUri != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                AsyncImage(
                                    model = attachedImageUri,
                                    contentDescription = "Pratonton imej",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Imej dilampir",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_remove_image),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRemoveImage() }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isCustomServer) {
                        IconButton(
                            onClick = onPickImage,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("attach_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.cd_attach_image),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = onPromptChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.prompt_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("prompt_input_field")
                    )

                    val canSend = promptInput.isNotBlank() || attachedImageUri != null
                    Surface(
                        onClick = {
                            if (isGenerating) onStop() else if (canSend) onSend()
                        },
                        enabled = isGenerating || canSend,
                        color = when {
                            isGenerating -> MaterialTheme.colorScheme.error
                            canSend -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(if (isGenerating) "stop_button" else "send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isGenerating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(
                                    if (isGenerating) R.string.cd_stop_generation else R.string.cd_send_prompt
                                ),
                                tint = when {
                                    isGenerating -> MaterialTheme.colorScheme.onError
                                    canSend -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
