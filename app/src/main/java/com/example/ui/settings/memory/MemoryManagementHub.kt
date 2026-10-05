package com.example.ui.settings.memory

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MemoryEntity
import com.example.data.repository.MemoryRepository
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SasukeCrimson
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceContainerDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MEMORY_CATEGORIES = listOf("Personal", "Education", "Preferences", "Projects", "Other")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryManagementHub(
    memories: List<MemoryEntity>,
    onSaveMemory: (String, String) -> Unit,
    onUpdateMemory: (String, String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onToggleMemory: (String, Boolean) -> Unit,
    onClearAllMemories: () -> Unit,
    exportTextProvider: suspend () -> String,
    exportJsonProvider: suspend () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Dialog state
    var newFactInput by remember { mutableStateOf("") }
    var newCategoryInput by remember { mutableStateOf("Personal") }

    val filteredMemories = remember(memories, selectedCategoryFilter, searchQuery) {
        memories.filter { mem ->
            val matchCategory = selectedCategoryFilter == "All" || mem.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchQuery = searchQuery.isBlank() || mem.fact.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    val activeCount = remember(memories) { memories.count { it.isEnabled } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Long-Term Memory Hub",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$activeCount active • Ultra-low token background memory",
                        color = TextMuted,
                        fontSize = 11.5.sp
                    )
                }
            }

            // Quick Add Button
            Button(
                onClick = {
                    newFactInput = ""
                    newCategoryInput = "Personal"
                    showAddDialog = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(7.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("add_memory_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Explanation text
        Text(
            text = "AI extracts and remembers key durable facts across chats (age, grade, name, goals, preferences) in ultra-short bullets so that prompt tokens stay minimal.",
            color = TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search memories...", color = TextMuted, fontSize = 12.5.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BorderMedium,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val categories = listOf("All") + MEMORY_CATEGORIES
            categories.forEach { cat ->
                val isSelected = selectedCategoryFilter == cat
                val chipBg = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.25f) else SurfaceDark
                val chipBorder = if (isSelected) Color(0xFF6366F1) else BorderSubtle
                val chipText = if (isSelected) Color(0xFFC7D2FE) else TextSecondary

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(chipBg)
                        .border(0.8.dp, chipBorder, RoundedCornerShape(6.dp))
                        .clickable { selectedCategoryFilter = cat }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = cat,
                        color = chipText,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Memory List
        if (filteredMemories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .border(0.8.dp, BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (memories.isEmpty()) "No memories saved yet.\nChat naturally or tap 'Add' to store facts." else "No memories matching filter.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredMemories.forEach { mem ->
                    MemoryItemCard(
                        memory = mem,
                        onToggle = { onToggleMemory(mem.id, it) },
                        onEdit = { editingMemory = mem },
                        onDelete = { onDeleteMemory(mem.id) },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Memory", mem.fact))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons: Copy All, Export, Clear All
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        val text = exportTextProvider()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("All Memories", text))
                        Toast.makeText(context, "Copied all memories", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = memories.isNotEmpty(),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp), tint = TextPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy All", fontSize = 11.5.sp, color = TextPrimary)
            }

            OutlinedButton(
                onClick = { showExportDialog = true },
                enabled = memories.isNotEmpty(),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp), tint = TextPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export", fontSize = 11.5.sp, color = TextPrimary)
            }

            OutlinedButton(
                onClick = { showClearConfirmDialog = true },
                enabled = memories.isNotEmpty(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(13.dp), tint = ErrorRed)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear", fontSize = 11.5.sp, color = ErrorRed)
            }
        }
    }

    // ADD MEMORY DIALOG
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Memory", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Add a concise, persistent fact about yourself (e.g. 'Sasuke is 17 years old', 'Studies Class 11th Math').",
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = newFactInput,
                        onValueChange = { newFactInput = it },
                        placeholder = { Text("Enter concise fact...", color = TextMuted, fontSize = 13.sp) },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderMedium,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Category:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MEMORY_CATEGORIES.forEach { cat ->
                            val isSelected = newCategoryInput == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF6366F1).copy(alpha = 0.3f) else SurfaceDark)
                                    .border(0.8.dp, if (isSelected) Color(0xFF6366F1) else BorderSubtle, RoundedCornerShape(6.dp))
                                    .clickable { newCategoryInput = cat }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color(0xFFC7D2FE) else TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFactInput.isNotBlank()) {
                            onSaveMemory(newFactInput.trim(), newCategoryInput)
                            showAddDialog = false
                            Toast.makeText(context, "Memory saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = newFactInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // EDIT MEMORY DIALOG
    editingMemory?.let { mem ->
        var editFactInput by remember(mem) { mutableStateOf(mem.fact) }
        var editCategoryInput by remember(mem) { mutableStateOf(mem.category) }

        AlertDialog(
            onDismissRequest = { editingMemory = null },
            title = { Text("Edit Memory", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editFactInput,
                        onValueChange = { editFactInput = it },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderMedium,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Category:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MEMORY_CATEGORIES.forEach { cat ->
                            val isSelected = editCategoryInput == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF6366F1).copy(alpha = 0.3f) else SurfaceDark)
                                    .border(0.8.dp, if (isSelected) Color(0xFF6366F1) else BorderSubtle, RoundedCornerShape(6.dp))
                                    .clickable { editCategoryInput = cat }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color(0xFFC7D2FE) else TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editFactInput.isNotBlank()) {
                            onUpdateMemory(mem.id, editFactInput.trim(), editCategoryInput)
                            editingMemory = null
                            Toast.makeText(context, "Memory updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMemory = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // EXPORT DIALOG
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Memories", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Text(
                    "Export all remembered facts about yourself as readable text or structured JSON for backup.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val text = exportTextProvider()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Memories as Text"))
                            showExportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("Share Text")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val json = exportJsonProvider()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, json)
                                type = "application/json"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Memories as JSON"))
                            showExportDialog = false
                        }
                    }
                ) {
                    Text("Share JSON", color = TextPrimary)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // CLEAR ALL CONFIRMATION DIALOG
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Memories", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            text = {
                Text(
                    "Are you sure you want to delete all saved background facts? The AI will forget personal details until you chat about them again.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllMemories()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "All memories cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = SurfaceElevated
        )
    }
}

@Composable
private fun MemoryItemCard(
    memory: MemoryEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = when (memory.category.lowercase()) {
        "personal" -> Color(0xFF3B82F6)
        "education" -> Color(0xFFA855F7)
        "preferences" -> Color(0xFF10B981)
        "projects" -> Color(0xFFF59E0B)
        else -> Color(0xFF64748B)
    }

    val formattedDate = remember(memory.timestamp) {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(memory.timestamp))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .border(0.8.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Category Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(categoryColor.copy(alpha = 0.2f))
                        .border(0.5.dp, categoryColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = memory.category,
                        color = categoryColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "• $formattedDate",
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
            }

            // Enable / Disable switch
            Switch(
                checked = memory.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF6366F1),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceContainerDark
                ),
                modifier = Modifier.size(width = 38.dp, height = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Fact Text
        Text(
            text = memory.fact,
            color = if (memory.isEnabled) TextPrimary else TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Action icons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextMuted, modifier = Modifier.size(14.dp))
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(14.dp))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
            }
        }
    }
}
