package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceContainerHighDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

data class StarterPrompt(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val prompt: String
)

@Composable
fun EmptyStateView(
    activeModel: String,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val starterPrompts = listOf(
        StarterPrompt(
            title = "Android Architecture",
            subtitle = "Design a scalable Kotlin Room DB with Flow & M3",
            icon = Icons.Default.Code,
            prompt = "Design a clean, production-grade Android Room database architecture using Kotlin Coroutines, Flow, and Jetpack Compose. Include entities, DAO, and Repository patterns."
        ),
        StarterPrompt(
            title = "Technical Deep-Dive",
            subtitle = "Explain quantum computing algorithms in plain English",
            icon = Icons.Default.Lightbulb,
            prompt = "Explain quantum computing and Shor's algorithm in intuitive, structured terms with practical real-world implications."
        ),
        StarterPrompt(
            title = "Security & Hardening",
            subtitle = "Audit mobile apps for key storage & cryptographic pitfalls",
            icon = Icons.Default.Security,
            prompt = "Provide a comprehensive security checklist for modern Android applications, focusing on secure KeyStore management, network transport, and reverse-engineering prevention."
        ),
        StarterPrompt(
            title = "Executive Communication",
            subtitle = "Draft a crisp proposal for an AI agent platform",
            icon = Icons.Default.Edit,
            prompt = "Draft an executive summary and architecture pitch for building a personal AI agent platform that connects to GitHub, Drive, and Terminal tools."
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Futuristic Emblem
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(SurfaceContainerDark)
                .border(1.5.dp, AccentCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SX",
                color = AccentCyan,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "━━〔 ꜱᴀꜱᴜᴋᴇX 〕━━",
            color = TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Autonomous Personal AI Assistant",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Model Badge Chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceContainerHighDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(AccentCyan)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = activeModel,
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Starter Prompt Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            starterPrompts.forEachIndexed { index, item ->
                StarterCard(
                    item = item,
                    onClick = { onSelectPrompt(item.prompt) },
                    testTag = "starter_card_$index"
                )
            }
        }
    }
}

@Composable
private fun StarterCard(
    item: StarterPrompt,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceContainerDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainerHighDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = AccentIndigo,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
