package com.example.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.model.ToolActivity
import com.example.agent.model.ToolStatus
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SasukeCrimson
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun AgentActivityCard(
    activity: ToolActivity,
    modifier: Modifier = Modifier,
    onConfirmAction: ((ToolActivity) -> Unit)? = null,
    onCancelAction: ((ToolActivity) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (activity.status) {
        ToolStatus.PREPARING, ToolStatus.RUNNING -> SasukeCrimson
        ToolStatus.SUCCESS -> SuccessGreen
        ToolStatus.FAILED -> ErrorRed
        ToolStatus.WAITING_CONFIRMATION -> Color(0xFFF59E0B) // Amber
    }

    val containerBackground = when (activity.status) {
        ToolStatus.WAITING_CONFIRMATION -> Color(0xFF1E1710)
        else -> SurfaceElevated
    }

    val borderColor = when (activity.status) {
        ToolStatus.WAITING_CONFIRMATION -> Color(0xFFF59E0B).copy(alpha = 0.4f)
        else -> BorderSubtle
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerBackground)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .animateContentSize()
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    when (activity.status) {
                        ToolStatus.PREPARING, ToolStatus.RUNNING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = SasukeCrimson,
                                strokeWidth = 2.dp
                            )
                        }
                        ToolStatus.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Success",
                                tint = SuccessGreen,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        ToolStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Failed",
                                tint = ErrorRed,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        ToolStatus.WAITING_CONFIRMATION -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Confirmation Required",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = activity.title,
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = activity.resultSummary ?: activity.subtitle,
                            color = if (activity.status == ToolStatus.WAITING_CONFIRMATION) Color(0xFFFBBF24) else TextMuted,
                            fontSize = 11.sp,
                            maxLines = if (expanded) 3 else 1
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand details",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Expanded details & arguments view
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    if (activity.arguments.isNotEmpty()) {
                        Text(
                            text = "ARGUMENTS",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceDark)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = activity.arguments.entries.joinToString("\n") { (k, v) -> "$k: $v" },
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (!activity.details.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "OUTPUT",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceDark)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = activity.details.take(300),
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Confirmation Action Card (when waiting for confirmation)
            if (activity.status == ToolStatus.WAITING_CONFIRMATION && activity.confirmationPayload != null) {
                val payload = activity.confirmationPayload
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (payload.isDestructive) "⚠️ DESTRUCTIVE ACTION" else "✏️ ACTION REQUIRES APPROVAL",
                            color = if (payload.isDestructive) ErrorRed else Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = payload.actionTitle,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Target: ${payload.target}",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )

                    if (!payload.previewTitle.isNullOrBlank()) {
                        Text(
                            text = payload.previewTitle,
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!payload.previewContent.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF111116))
                                .padding(6.dp)
                        ) {
                            Text(
                                text = payload.previewContent,
                                color = TextPrimary.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 4
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { onCancelAction?.invoke(activity) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Cancel", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { onConfirmAction?.invoke(activity) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (payload.isDestructive) ErrorRed else SasukeCrimson,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(if (payload.isDestructive) "Confirm Delete" else "Confirm & Execute", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
