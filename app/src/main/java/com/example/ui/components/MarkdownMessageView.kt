package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CodeBlockBg
import com.example.ui.theme.CodeBlockBorder
import com.example.ui.theme.CodeBlockHeaderBg
import com.example.ui.theme.JetBrainsMonoFontFamily
import org.json.JSONObject

private sealed interface MarkdownSegment {
    data class TextBlock(val markdown: String) : MarkdownSegment
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment
}

@Composable
fun MarkdownMessageView(
    content: String,
    thinkingTrace: String? = null,
    structuredJson: String? = null,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!thinkingTrace.isNullOrBlank()) {
            ThinkingTraceCard(thinkingTrace = thinkingTrace, isStreaming = isStreaming)
        }

        if (!structuredJson.isNullOrBlank()) {
            StructuredPayloadMetaCard(rawJson = structuredJson)
        }

        val segments = remember(content) { splitMarkdownSegments(content) }
        segments.forEachIndexed { idx, segment ->
            when (segment) {
                is MarkdownSegment.CodeBlock -> {
                    CodeTerminalCard(
                        language = segment.language,
                        code = segment.code,
                        index = idx
                    )
                }
                is MarkdownSegment.TextBlock -> {
                    RichTextBlock(markdown = segment.markdown)
                }
            }
        }
    }
}

@Composable
private fun ThinkingTraceCard(
    thinkingTrace: String,
    isStreaming: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("thinking_trace_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isStreaming) "Protokol Pemikiran Aktif..." else "Jejak Penaakulan (Chain of Thought)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = thinkingTrace,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StructuredPayloadMetaCard(rawJson: String) {
    val parsed = remember(rawJson) { runCatching { JSONObject(rawJson) }.getOrNull() }
    if (parsed == null) return

    val status = parsed.optString("status", "success")
    val action = parsed.optString("action_taken", "-")
    val tokens = parsed.optInt("thought_tokens_used", 0)
    val securityChecks = parsed.optJSONObject("output_payload")?.optString("security_checks").orEmpty()
    var showRawJson by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "CARBON AGENT • ${status.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Surface(
                    onClick = { showRawJson = !showRawJson },
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (showRawJson) "Tutup JSON" else "Lihat JSON",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (action.isNotBlank()) {
                Text(
                    text = "Tindakan: $action • Token Pemikiran: $tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (securityChecks.isNotBlank()) {
                Text(
                    text = "Semakan Keselamatan: $securityChecks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            AnimatedVisibility(visible = showRawJson) {
                CodeTerminalCard(language = "json", code = rawJson, index = 99)
            }
        }
    }
}

@Composable
private fun CodeTerminalCard(
    language: String,
    code: String,
    index: Int
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        color = CodeBlockBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CodeBlockBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Surface(color = CodeBlockHeaderBg) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = language.ifBlank { "code" }.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            copied = true
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("copy_code_button_$index")
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy_code),
                            tint = if (copied) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun RichTextBlock(markdown: String) {
    val lines = remember(markdown) { markdown.lines() }
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.startsWith("### ") -> {
                    Text(
                        text = buildInlineAnnotated(line.removePrefix("### "), primaryColor, inlineCodeBg),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = primaryColor,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = buildInlineAnnotated(line.removePrefix("## "), primaryColor, inlineCodeBg),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                line.startsWith("# ") -> {
                    Text(
                        text = buildInlineAnnotated(line.removePrefix("# "), primaryColor, inlineCodeBg),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                line.startsWith("> ") -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = buildInlineAnnotated(line.removePrefix("> "), primaryColor, inlineCodeBg),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = mutedColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val trimmed = line.trimStart()
                    val bulletContent = trimmed.drop(2)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge,
                            color = primaryColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = buildInlineAnnotated(bulletContent, primaryColor, inlineCodeBg),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }
                }
                else -> {
                    Text(
                        text = buildInlineAnnotated(line, primaryColor, inlineCodeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
            }
        }
    }
}

private fun buildInlineAnnotated(
    text: String,
    accentColor: androidx.compose.ui.graphics.Color,
    codeBgColor: androidx.compose.ui.graphics.Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                            color = accentColor,
                            background = codeBgColor
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '*' && (i + 1 < text.length && text[i + 1] != ' ') -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

private fun splitMarkdownSegments(raw: String): List<MarkdownSegment> {
    if (raw.isBlank()) return emptyList()
    val segments = mutableListOf<MarkdownSegment>()
    val lines = raw.lines()
    val textBuf = StringBuilder()
    val codeBuf = StringBuilder()
    var inCode = false
    var currentLang = ""

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                if (textBuf.isNotBlank()) {
                    segments.add(MarkdownSegment.TextBlock(textBuf.toString().trimEnd()))
                    textBuf.clear()
                }
                inCode = true
                currentLang = trimmed.removePrefix("```").trim()
                codeBuf.clear()
            } else {
                inCode = false
                segments.add(MarkdownSegment.CodeBlock(currentLang, codeBuf.toString().trimEnd()))
                codeBuf.clear()
                currentLang = ""
            }
        } else {
            if (inCode) {
                if (codeBuf.isNotEmpty()) codeBuf.append('\n')
                codeBuf.append(line)
            } else {
                if (textBuf.isNotEmpty()) textBuf.append('\n')
                textBuf.append(line)
            }
        }
    }

    if (inCode && codeBuf.isNotEmpty()) {
        segments.add(MarkdownSegment.CodeBlock(currentLang, codeBuf.toString().trimEnd()))
    } else if (textBuf.isNotBlank()) {
        segments.add(MarkdownSegment.TextBlock(textBuf.toString().trimEnd()))
    }
    return segments
}
