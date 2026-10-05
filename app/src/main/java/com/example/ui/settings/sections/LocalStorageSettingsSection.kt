package com.example.ui.settings.sections

import android.content.Intent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettingsManager
import com.example.storage.FileSearchResult
import com.example.storage.LocalStorageManager
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
fun LocalStorageSettingsSection(
    appSettingsManager: AppSettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isEnabled by appSettingsManager.localFilesEnabledFlow.collectAsState()
    var isIndexing by remember { mutableStateOf(false) }
    var indexedCount by remember { mutableStateOf<Int?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FileSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // SAF folder picker launcher
    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                Toast.makeText(context, "Granted access to folder: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    isIndexing = true
                    val count = LocalStorageManager.indexStorage(context)
                    indexedCount = count
                    isIndexing = false
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                Text("Local Storage Agent", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Deterministic indexing, SAF folders, and zero-token fuzzy search",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { appSettingsManager.setLocalFilesEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SasukeCrimson,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF2A2A2A)
                )
            )
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // Add SAF Root Folder & Index Storage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { treeLauncher.launch(null) },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Folder (SAF)", fontSize = 11.5.sp)
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isIndexing = true
                        val count = LocalStorageManager.indexStorage(context)
                        indexedCount = count
                        isIndexing = false
                        Toast.makeText(context, "Indexed $count local files", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isIndexing,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                if (isIndexing) {
                    CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp, color = Color.Black)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (indexedCount != null) "Re-Index ($indexedCount)" else "Index Storage", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Search Test Input
        Column {
            Text("Test Local File Search Engine (Zero AI Tokens)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.isNotBlank()) {
                        coroutineScope.launch {
                            isSearching = true
                            searchResults = LocalStorageManager.searchFiles(context, it)
                            isSearching = false
                        }
                    } else {
                        searchResults = emptyList()
                    }
                },
                placeholder = { Text("Try: 'sasuke.py', 'python files', 'readme'...", color = TextMuted, fontSize = 11.5.sp) },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                },
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

        // Live Search Results
        if (searchResults.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceElevated)
                    .border(0.5.dp, BorderSubtle, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Search Matches (${searchResults.size}):", color = TextSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                searchResults.take(4).forEach { res ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = SasukeCrimson, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(res.name, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Text(
                            text = "${(res.score * 100).toInt()}% • ${res.matchType}",
                            color = SuccessGreen,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
