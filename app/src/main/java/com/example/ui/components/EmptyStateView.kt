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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceContainerHighDark
import com.example.ui.theme.SurfaceElevated
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
            title = "Code Architecture",
            subtitle = "Design a scalable Android repository pattern with Room & Flow",
            icon = Icons.Default.Code,
            prompt = "Design a clean, production-grade Android Room database architecture using Kotlin Coroutines, Flow, and Jetpack Compose."
        ),
        StarterPrompt(
            title = "System Design",
            subtitle = "Compare event-driven architecture vs REST for high concurrency",
            icon = Icons.Default.Lightbulb,
            prompt = "Compare event-driven architecture with REST API design for low-latency mobile backends."
        ),
        StarterPrompt(
            title = "Security Audit",
            subtitle = "KeyStore management & mobile encryption best practices",
            icon = Icons.Default.Security,
            prompt = "Provide a security checklist for Android apps focusing on hardware KeyStore, data encryption, and network transport."
        ),
        StarterPrompt(
            title = "Technical Writing",
            subtitle = "Draft a crisp release notes document for a major software update",
            icon = Icons.Default.Edit,
            prompt = "Write a clear, structured release notes document for version 2.0 of an AI application."
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Minimalist Emblem
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(SurfaceElevated)
                .border(1.dp, BorderMedium, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "S",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "SasukeX",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "How can I help you today?",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Clean Prompts List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 13.5.sp,
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
