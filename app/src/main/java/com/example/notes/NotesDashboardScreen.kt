package com.example.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.core.BidiTextHelper
import com.example.core.NoteFilterType
import com.example.database.FolderEntity
import com.example.database.NoteEntity
import com.example.ui.theme.MaterialTheme.colorScheme.surfaceVariant
import com.example.ui.theme.MaterialTheme.colorScheme.surface
import com.example.ui.theme.FolderColorAmber
import com.example.ui.theme.FolderColorBlue
import com.example.ui.theme.FolderColorCyan
import com.example.ui.theme.FolderColorPurple
import com.example.ui.theme.FolderColorRose
import com.example.ui.theme.FolderColorTeal
import com.example.ui.theme.MaterialTheme.colorScheme.primary
import com.example.ui.theme.MaterialTheme.colorScheme.primaryContainer
import com.example.ui.theme.MaterialTheme.colorScheme.background
import com.example.ui.theme.StatusError
import com.example.ui.theme.MaterialTheme.colorScheme.outline
import com.example.ui.theme.MaterialTheme.colorScheme.onSurfaceVariant
import com.example.ui.theme.MaterialTheme.colorScheme.onSurfaceVariant
import com.example.ui.theme.MaterialTheme.colorScheme.onSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesDashboardScreen(
    displayedNotes: List<NoteEntity>,
    allActiveNotesCount: Int,
    favoritesCount: Int,
    trashCount: Int,
    folders: List<FolderEntity>,
    folderCounts: Map<Long, Int>,
    selectedFilter: NoteFilterType,
    selectedFolderId: Long?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilter: (NoteFilterType, Long?) -> Unit,
    onOpenNote: (NoteEntity) -> Unit,
    onCreateNewNote: () -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onToggleFavorite: (NoteEntity) -> Unit,
    onMoveToTrash: (Long) -> Unit,
    onRestoreFromTrash: (Long) -> Unit,
    onDeleteForever: (Long) -> Unit,
    onEmptyTrash: () -> Unit,
    onCreateFolder: (String, String) -> Unit,
    onRenameFolder: (FolderEntity, String, String) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onExportAllNotesRequest: (String) -> Unit,
    onImportNotesDialogRequest: () -> Unit,
    isArabic: Boolean = false
) {
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    val folderMap = remember(folders) { folders.associateBy { it.id } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("notes_dashboard_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Hero Header Banner with Custom Canvas Geometric Grid & Cyan/Teal Glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), MaterialTheme.colorScheme.surfaceVariant)
                        )
                    )
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                ) {
                    val step = 36.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                            start = Offset(x, 0f),
                            end = Offset(x + 24f, size.height),
                            strokeWidth = 1.2f
                        )
                        x += step
                    }
                    drawCircle(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        radius = size.height * 0.8f,
                        center = Offset(size.width * 0.82f, size.height * 0.25f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NOVA NOTES",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isArabic) "$allActiveNotesCount ملاحظة • حفظ تلقائي فوري" else "$allActiveNotesCount Offline Notes • Instant Auto-Save",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onImportNotesDialogRequest,
                            modifier = Modifier.testTag("import_notes_button")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import Notes", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.testTag("export_notes_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export Notes", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { showCreateFolderDialog = true },
                            modifier = Modifier.testTag("create_folder_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Create New Folder", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("global_search_input"),
                placeholder = {
                    Text(
                        text = if (isArabic) "ابحث في الملاحظات والعناوين والمحتوى…" else "Search notes, titles, content (العربية / English)…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedFilter == NoteFilterType.ALL,
                    onClick = { onSelectFilter(NoteFilterType.ALL, null) },
                    label = { Text(if (isArabic) "كل الملاحظات ($allActiveNotesCount)" else "All Notes ($allActiveNotesCount)") },
                    colors = novaFilterChipColors(),
                    modifier = Modifier.testTag("filter_all_notes")
                )

                FilterChip(
                    selected = selectedFilter == NoteFilterType.FAVORITES,
                    onClick = { onSelectFilter(NoteFilterType.FAVORITES, null) },
                    label = { Text(if (isArabic) "المفضلة ($favoritesCount)" else "Favorites ($favoritesCount)") },
                    leadingIcon = {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = novaFilterChipColors(),
                    modifier = Modifier.testTag("filter_favorites")
                )

                FilterChip(
                    selected = selectedFilter == NoteFilterType.RECENT,
                    onClick = { onSelectFilter(NoteFilterType.RECENT, null) },
                    label = { Text(if (isArabic) "عُدّلت مؤخراً" else "Recently Edited") },
                    leadingIcon = {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = novaFilterChipColors(),
                    modifier = Modifier.testTag("filter_recent")
                )

                folders.forEach { folder ->
                    val count = folderCounts[folder.id] ?: 0
                    val folderColor = parseColorHex(folder.colorHex)
                    val isSelected = selectedFilter == NoteFilterType.FOLDER && selectedFolderId == folder.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFilter(NoteFilterType.FOLDER, folder.id) },
                        label = { Text("${folder.name} ($count)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = folder.name,
                                tint = folderColor,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { editingFolder = folder }
                            )
                        },
                        colors = novaFilterChipColors(),
                        modifier = Modifier.testTag("filter_folder_${folder.id}")
                    )
                }

                FilterChip(
                    selected = selectedFilter == NoteFilterType.TRASH,
                    onClick = { onSelectFilter(NoteFilterType.TRASH, null) },
                    label = { Text(if (isArabic) "سلة المحذوفات ($trashCount)" else "Trash ($trashCount)") },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = novaFilterChipColors(),
                    modifier = Modifier.testTag("filter_trash")
                )
            }

            AnimatedVisibility(visible = selectedFilter == NoteFilterType.TRASH && displayedNotes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "يمكن استعادة الملاحظات أو حذفها نهائياً" else "Notes in Trash can be restored or deleted forever",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onEmptyTrash,
                        modifier = Modifier.testTag("empty_trash_button")
                    ) {
                        Text(if (isArabic) "إفراغ السلة" else "Empty Trash", color = StatusError)
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val columns = when {
                    maxWidth >= 840.dp -> 3
                    maxWidth >= 560.dp -> 2
                    else -> 1
                }

                if (displayedNotes.isEmpty()) {
                    EmptyNotesView(
                        isTrash = selectedFilter == NoteFilterType.TRASH,
                        hasSearchQuery = searchQuery.isNotBlank(),
                        onCreateNewNote = onCreateNewNote
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("notes_grid")
                    ) {
                        items(displayedNotes, key = { it.id }) { note ->
                            NoteCardItem(
                                note = note,
                                folder = note.folderId?.let { folderMap[it] },
                                onClick = { onOpenNote(note) },
                                onTogglePin = { onTogglePin(note) },
                                onToggleFavorite = { onToggleFavorite(note) },
                                onMoveToTrash = { onMoveToTrash(note.id) },
                                onRestoreFromTrash = { onRestoreFromTrash(note.id) },
                                onDeleteForever = { onDeleteForever(note.id) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateNewNote,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_note_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Note")
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Note", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showCreateFolderDialog) {
        FolderEditorDialog(
            initialName = "",
            initialColorHex = "#00E5FF",
            title = "Create Folder",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, color ->
                onCreateFolder(name, color)
                showCreateFolderDialog = false
            },
            onDelete = null
        )
    }

    editingFolder?.let { folder ->
        FolderEditorDialog(
            initialName = folder.name,
            initialColorHex = folder.colorHex,
            title = "Edit Folder",
            onDismiss = { editingFolder = null },
            onConfirm = { name, color ->
                onRenameFolder(folder, name, color)
                editingFolder = null
            },
            onDelete = {
                onDeleteFolder(folder)
                editingFolder = null
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Export Displayed Notes (${displayedNotes.size})", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Choose an offline export format (preserves Arabic & English UTF-8):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        "TXT" to "Plain Text (.txt)",
                        "JSON" to "Structured JSON (.json)",
                        "CSV" to "Spreadsheet CSV (.csv)",
                        "PDF" to "Printable PDF Document (.pdf)"
                    ).forEach { (fmt, label) ->
                        OutlinedButton(
                            onClick = {
                                showExportDialog = false
                                onExportAllNotesRequest(fmt)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("export_format_$fmt"),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        ) {
                            Text(label, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCardItem(
    note: NoteEntity,
    folder: FolderEntity?,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestoreFromTrash: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val isRtl = remember(note.textDirection, note.title, note.content) {
        BidiTextHelper.resolveIsRtl(note.textDirection, "${note.title} ${note.content}")
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.US) }
    val formattedDate = remember(note.updatedAt) { dateFormat.format(Date(note.updatedAt)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onTogglePin)
            .testTag("note_card_${note.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (note.isPinned) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            width = if (note.isPinned) 1.5.dp else 1.dp,
            color = if (note.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.title.ifBlank { "Untitled Note" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (note.title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!note.isTrashed) {
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier.size(36.dp).testTag("favorite_note_${note.id}")
                            ) {
                                Icon(
                                    imageVector = if (note.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Toggle Favorite",
                                    tint = if (note.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onTogglePin,
                                modifier = Modifier.size(36.dp).testTag("pin_note_${note.id}")
                            ) {
                                Icon(
                                    imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = "Pin Note",
                                    tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onMoveToTrash,
                                modifier = Modifier.size(36.dp).testTag("trash_note_${note.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Move to Trash",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = onRestoreFromTrash,
                                modifier = Modifier.size(36.dp).testTag("restore_note_${note.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestoreFromTrash,
                                    contentDescription = "Restore Note",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onDeleteForever,
                                modifier = Modifier.size(36.dp).testTag("delete_forever_note_${note.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = "Delete Permanently",
                                    tint = StatusError,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (note.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (folder != null) {
                            val fColor = parseColorHex(folder.colorHex)
                            Surface(
                                color = fColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, fColor.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(fColor))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(text = folder.name, style = MaterialTheme.typography.labelSmall, color = fColor)
                                }
                            }
                        }
                        if (isRtl) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "RTL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(text = formattedDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyNotesView(
    isTrash: Boolean,
    hasSearchQuery: Boolean,
    onCreateNewNote: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = when {
                isTrash -> "Trash is Empty"
                hasSearchQuery -> "No Matching Notes Found"
                else -> "No Notes Yet"
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                isTrash -> "Deleted notes appear here for safe recovery."
                hasSearchQuery -> "Try searching with a different keyword in Arabic or English."
                else -> "Create your first offline note with continuous automatic saving."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isTrash && !hasSearchQuery) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onCreateNewNote,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.testTag("empty_state_create_note_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create First Note", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FolderEditorDialog(
    initialName: String,
    initialColorHex: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialName) }
    var colorHex by remember { mutableStateOf(initialColorHex) }
    val colorOptions = listOf(
        "#00E5FF" to FolderColorCyan,
        "#1DE9B6" to FolderColorTeal,
        "#FFC400" to FolderColorAmber,
        "#B388FF" to FolderColorPurple,
        "#FF80AB" to FolderColorRose,
        "#40C4FF" to FolderColorBlue
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("folder_name_input")
                )
                Text("Folder Accent Color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colorOptions.forEach { (hex, composeColor) ->
                        val selected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(composeColor)
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, colorHex) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.testTag("save_folder_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = StatusError) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    )
}

@Composable
private fun novaFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.primary,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)

fun parseColorHex(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
}
