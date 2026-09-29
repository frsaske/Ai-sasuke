package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentIndigoLight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CodeBlockBg
import com.example.ui.theme.CodeBlockHeader
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

sealed class MarkdownElement {
    data class Paragraph(val text: String) : MarkdownElement()
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class BulletList(val items: List<String>) : MarkdownElement()
    data class CodeBlock(val language: String, val code: String) : MarkdownElement()
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = TextPrimary
) {
    val elements = remember(content) { parseMarkdown(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        elements.forEach { element ->
            when (element) {
                is MarkdownElement.Header -> {
                    val fontSize = when (element.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = element.text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = if (element.level == 1) AccentCyan else textColor,
                        lineHeight = (fontSize.value * 1.3).sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownElement.Paragraph -> {
                    RichFormattedText(
                        text = element.text,
                        textColor = textColor
                    )
                }
                is MarkdownElement.BulletList -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        element.items.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "•",
                                    color = AccentCyan,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    RichFormattedText(text = item, textColor = textColor)
                                }
                            }
                        }
                    }
                }
                is MarkdownElement.CodeBlock -> {
                    CodeBlockCard(
                        language = element.language,
                        code = element.code
                    )
                }
            }
        }
    }
}

@Composable
fun RichFormattedText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val annotatedString = remember(text, textColor) {
        buildAnnotatedString {
            var i = 0
            val len = text.length
            while (i < len) {
                // Check for inline code `...`
                if (text[i] == '`' && i + 1 < len) {
                    val nextBacktick = text.indexOf('`', i + 1)
                    if (nextBacktick != -1) {
                        val inlineCode = text.substring(i + 1, nextBacktick)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = SurfaceContainerDark,
                                color = AccentIndigoLight,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append(" $inlineCode ")
                        }
                        i = nextBacktick + 1
                        continue
                    }
                }

                // Check for bold **...**
                if (i + 1 < len && text[i] == '*' && text[i + 1] == '*') {
                    val endBold = text.indexOf("**", i + 2)
                    if (endBold != -1) {
                        val boldText = text.substring(i + 2, endBold)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                            append(boldText)
                        }
                        i = endBold + 2
                        continue
                    }
                }

                // Check for italic *...*
                if (text[i] == '*' && (i + 1 < len && text[i + 1] != '*')) {
                    val endItalic = text.indexOf('*', i + 1)
                    if (endItalic != -1 && endItalic > i + 1) {
                        val italicText = text.substring(i + 1, endItalic)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                            append(italicText)
                        }
                        i = endItalic + 1
                        continue
                    }
                }

                append(text[i])
                i++
            }
        }
    }

    Text(
        text = annotatedString,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = textColor,
        modifier = modifier
    )
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CodeBlockBg)
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CodeBlockHeader)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = language.ifBlank { "code" }.lowercase(),
                color = AccentCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Code", code)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("copy_code_button")
                ) {
                    if (copied) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Copied",
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Code",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                AnimatedVisibility(
                    visible = copied,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = "Copied!",
                        color = SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Code body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = TextPrimary
            )
        }
    }
}

private fun parseMarkdown(raw: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code block start
        if (line.trim().startsWith("```")) {
            val language = line.trim().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            elements.add(MarkdownElement.CodeBlock(language, codeLines.joinToString("\n")))
            i++
            continue
        }

        // Headers
        if (line.startsWith("### ")) {
            elements.add(MarkdownElement.Header(3, line.removePrefix("### ")))
            i++
            continue
        }
        if (line.startsWith("## ")) {
            elements.add(MarkdownElement.Header(2, line.removePrefix("## ")))
            i++
            continue
        }
        if (line.startsWith("# ")) {
            elements.add(MarkdownElement.Header(1, line.removePrefix("# ")))
            i++
            continue
        }

        // Bullet lists
        if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
            val listItems = mutableListOf<String>()
            while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* "))) {
                val itemText = lines[i].trim().let {
                    if (it.startsWith("- ")) it.removePrefix("- ") else it.removePrefix("* ")
                }
                listItems.add(itemText)
                i++
            }
            elements.add(MarkdownElement.BulletList(listItems))
            continue
        }

        // Empty lines
        if (line.isBlank()) {
            i++
            continue
        }

        // Paragraph accumulation
        val paraLines = mutableListOf<String>()
        while (i < lines.size &&
            lines[i].isNotBlank() &&
            !lines[i].trim().startsWith("```") &&
            !lines[i].startsWith("#") &&
            !lines[i].trim().startsWith("- ") &&
            !lines[i].trim().startsWith("* ")
        ) {
            paraLines.add(lines[i])
            i++
        }
        if (paraLines.isNotEmpty()) {
            elements.add(MarkdownElement.Paragraph(paraLines.joinToString(" ")))
        }
    }

    return elements
}
