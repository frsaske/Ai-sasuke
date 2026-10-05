package com.example.ui.settings.sections

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettingsManager
import com.example.termux.TermuxExecutor
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SasukeCrimson
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun TermuxSettingsSection(
    appSettingsManager: AppSettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isTermuxEnabled by appSettingsManager.termuxEnabledFlow.collectAsState()
    val isInstalled = remember { TermuxExecutor.isTermuxInstalled(context) }
    var hasPermission by remember { mutableStateOf(TermuxExecutor.hasRunCommandPermission(context)) }

    var testOutput by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            Toast.makeText(context, "Termux RUN_COMMAND permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .border(0.8.dp, BorderSubtle, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Toggle Switch & Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Termux Command Runner", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (isInstalled) "Termux App Detected on Device" else "Termux Not Installed (Local Process Fallback)",
                    color = if (isInstalled) SuccessGreen else TextMuted,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = isTermuxEnabled,
                onCheckedChange = { appSettingsManager.setTermuxEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SasukeCrimson,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF2A2A2A)
                )
            )
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // Permission Card
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (hasPermission) SuccessGreen else Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "RUN_COMMAND Permission",
                        color = TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (hasPermission) "Granted • Background execution authorized" else "Needs explicit permission for Termux execution",
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                }
            }

            if (!hasPermission) {
                Button(
                    onClick = { permissionLauncher.launch(TermuxExecutor.PERMISSION_RUN_COMMAND) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Grant", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Safety Classifications Note
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF161B22))
                .border(0.5.dp, Color(0xFF30363D), RoundedCornerShape(6.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Safety Architecture (3 Categories)",
                        color = Color(0xFFF0F6FC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "• READ_ONLY (ls, pwd, git status): Auto-executed\n• NORMAL (python, git pull, npm): Standard\n• DESTRUCTIVE (rm, git reset --hard): Requires explicit confirmation",
                        color = Color(0xFF8B949E),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Quick Test Command Runner
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Test Shell Execution", color = TextSecondary, fontSize = 12.sp)

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        isTesting = true
                        testOutput = null
                        val res = TermuxExecutor.execute(context, "pwd && ls -la")
                        isTesting = false
                        testOutput = "Exit ${res.exitCode}:\n${if (res.stdout.isNotBlank()) res.stdout else res.stderr}"
                    }
                },
                enabled = !isTesting,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(30.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
                } else {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run test", fontSize = 11.sp)
                }
            }
        }

        if (testOutput != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF040D14))
                    .border(0.5.dp, Color(0xFF21262D), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = testOutput!!,
                    color = Color(0xFF7EE787),
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 6
                )
            }
        }
    }
}
