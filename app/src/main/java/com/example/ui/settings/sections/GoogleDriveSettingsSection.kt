package com.example.ui.settings.sections

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettingsManager
import com.example.data.local.SecureStorageManager
import com.example.drive.GoogleDriveService
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SasukeCrimson
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun GoogleDriveSettingsSection(
    appSettingsManager: AppSettingsManager,
    secureStorageManager: SecureStorageManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isEnabled by appSettingsManager.driveEnabledFlow.collectAsState()
    var driveTokenInput by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(secureStorageManager.hasDriveToken()) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

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
                Text("Google Drive Agent", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (hasToken) "Drive Connected (OAuth Active)" else "OAuth Token Required for Cloud Sync",
                    color = if (hasToken) SuccessGreen else TextMuted,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { appSettingsManager.setDriveEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SasukeCrimson,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF2A2A2A)
                )
            )
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // Custom or Synced OAuth Token Input
        Column {
            Text("Google Drive Access Token (OAuth ya29...)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = driveTokenInput,
                onValueChange = { driveTokenInput = it },
                placeholder = { Text(if (hasToken) "Token active (paste to update)" else "Paste OAuth Access Token", color = TextMuted, fontSize = 11.5.sp) },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BorderMedium,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (driveTokenInput.isNotBlank()) {
                        secureStorageManager.saveDriveToken(driveTokenInput.trim())
                        driveTokenInput = ""
                        hasToken = true
                        Toast.makeText(context, "Drive token saved", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = driveTokenInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(32.dp)
            ) {
                Text("Save Token", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        isTesting = true
                        testResult = null
                        try {
                            val svc = GoogleDriveService(context)
                            val files = svc.listFolder("root", 5)
                            testResult = "Success! Found ${files.size} root items"
                        } catch (e: Exception) {
                            testResult = "Error: ${e.message}"
                        }
                        isTesting = false
                    }
                },
                enabled = !isTesting && hasToken,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(32.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
                } else {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Connection", fontSize = 11.sp)
                }
            }
        }

        if (testResult != null) {
            Text(
                text = testResult!!,
                color = if (testResult!!.startsWith("Success")) SuccessGreen else Color(0xFFF85149),
                fontSize = 11.sp
            )
        }
    }
}
