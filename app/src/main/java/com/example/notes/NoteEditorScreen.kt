package com.example.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatTextdirectionLToR
import androidx.compose.material.icons.automirrored.filled.FormatTextdirectionRToL
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.BidiTextHelper
import com.example.core.EditorUiState
import com.example.core.SaveIndicatorState
import com.example.database.FolderEntity
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun NoteEditorScreen(
    editorState: EditorUiState,
    folders: List<FolderEntity>,
    onBackAndFlush: () -> Unit,
    onTextChanged: (String, String, Int, Int, Int) -> Unit,
    onViewportChanged: (Int, Int, Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSetDirection: (String) -> Unit,
    onSetAlignment: (String) -> Unit,
    onSetFolder: (Long?) -> Unit,
    onTogglePin: () -> Unit,
    onToggleInNoteSearch: () -> Unit,
    onUpdateInNoteSearch: (String) -> Unit,
    onMoveToTrash: () -> Unit,
    onExportSingleNote: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    BackHandler(onBack = onBackAndFlush)

    val clipboardManager = LocalClipboardManager.current
    val verticalScrollState = rememberScrollState(initial = editorState.verticalScrollOffset)

    var contentFieldValue by remember(editorState.noteId) {
        mutableStateOf(
            TextFieldValue(
                text = editorState.content,
                selection = TextRange(
                    editorState.cursorPosition.coerceIn(0, editorState.content.length),
                    editorState.selectionEnd.coerceIn(0, editorState.content.length)
                )
            )
        )
    }

    LaunchedEffect(editorState.content) {
        if (contentFieldValue.text != editorState.content) {
            contentFieldValue = TextFieldValue(
                text = editorState.content,
                selection = TextRange(
                    editorState.cursorPosition.coerceIn(0, editorState.content.length),
                    editorState.selectionEnd.coerceIn(0, editorState.content.length)
                )
            )
        }
    }

    LaunchedEffect(editorState.noteId) {
        if (editorState.verticalScrollOffset > 0) {
            verticalScrollState.scrollTo(editorState.verticalScrollOffset)
        }
    }

    LaunchedEffect(verticalScrollState.value) {
        onViewportChanged(
            contentFieldValue.selection.start,
            contentFieldValue.selection.end,
            verticalScrollState.value
        )
    }

    DisposableEffect(editorState.noteId) {
        onDispose {
            onViewportChanged(
                contentFieldValue.selection.start,
                contentFieldValue.selection.end,
                verticalScrollState.value
            )
        }
    }

    var showFolderDropdown by remember { mutableStateOf(false) }
    var showExportDropdown by remember { mutableStateOf(false) }

    val isRtl = remember(editorState.textDirection, editorState.title, editorState.content) {
        BidiTextHelper.resolveIsRtl(
            editorState.textDirection,
            "${editorState.title}\n${editorState.content}"
        )
    }

    val resolvedTextAlign = when (editorState.textAlignment.uppercase()) {
        "CENTER" -> TextAlign.Center
        "END" -> TextAlign.End
        else -> TextAlign.Start
    }

    val searchMatchesCount = remember(editorState.content, editorState.inNoteSearchQuery) {
        val q = editorState.inNoteSearchQuery.trim()
        if (q.isEmpty()) 0
        else Regex(Regex.escape(q), RegexOption.IGNORE_CASE).findAll(editorState.content).count()
    }

    val currentFolder = folders.find { it.id == editorState.folderId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("note_editor_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackAndFlush,
                    modifier = Modifier.testTag("editor_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back and Flush Save", tint = MaterialTheme.colorScheme.onSurface)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        when (editorState.saveState) {
                            SaveIndicatorState.SAVING -> StatusWarning.copy(alpha = 0.6f)
                            SaveIndicatorState.SAVED -> StatusSuccess.copy(alpha = 0.5f)
                            SaveIndicatorState.ERROR -> StatusError.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.outline
                        }
                    ),
                    modifier = Modifier.testTag("auto_save_indicator")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (editorState.saveState == SaveIndicatorState.SAVING) Icons.Default.Sync else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = when (editorState.saveState) {
                                SaveIndicatorState.SAVING -> StatusWarning
                                SaveIndicatorState.SAVED -> StatusSuccess
                                SaveIndicatorState.ERROR -> StatusError
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = when (editorState.saveState) {
                                SaveIndicatorState.SAVING -> "Saving…"
                                SaveIndicatorState.SAVED -> "Saved"
                                SaveIndicatorState.ERROR -> "Save Error"
                                else -> "Ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = editorState.canUndo,
                    modifier = Modifier.testTag("editor_undo_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (editorState.canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = onRedo,
                    enabled = editorState.canRedo,
                    modifier = Modifier.testTag("editor_redo_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (editorState.canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = onToggleInNoteSearch,
                    modifier = Modifier.testTag("editor_search_toggle_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search within note", tint = if (editorState.isSearchBarVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.testTag("editor_pin_button")
                ) {
                    Icon(
                        imageVector = if (editorState.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin Note",
                        tint = if (editorState.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(
                        onClick = { showExportDropdown = true },
                        modifier = Modifier.testTag("editor_export_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Note", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = showExportDropdown,
                        onDismissRequest = { showExportDropdown = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        listOf("TXT", "PDF", "JSON", "CSV").forEach { fmt ->
                            DropdownMenuItem(
                                text = { Text("Export as $fmt", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showExportDropdown = false
                                    onExportSingleNote(fmt)
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onMoveToTrash,
                    modifier = Modifier.testTag("editor_trash_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Move to Trash", tint = StatusError.copy(alpha = 0.85f))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                Surface(
                    onClick = { showFolderDropdown = true },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.testTag("editor_folder_selector")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = currentFolder?.name ?: "No Folder", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                DropdownMenu(
                    expanded = showFolderDropdown,
                    onDismissRequest = { showFolderDropdown = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    DropdownMenuItem(
                        text = { Text("No Folder (Unfiled)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = {
                            onSetFolder(null)
                            showFolderDropdown = false
                        }
                    )
                    folders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder.name, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                onSetFolder(folder.id)
                                showFolderDropdown = false
                            }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    onClick = {
                        val nextDir = when (editorState.textDirection.uppercase()) {
                            "AUTO" -> "RTL"
                            "RTL" -> "LTR"
                            else -> "AUTO"
                        }
                        onSetDirection(nextDir)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("editor_direction_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isRtl) Icons.AutoMirrored.Filled.FormatTextdirectionRToL else Icons.AutoMirrored.Filled.FormatTextdirectionLToR,
                            contentDescription = "Text Direction",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = editorState.textDirection.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                IconButton(
                    onClick = {
                        val nextAlign = when (editorState.textAlignment.uppercase()) {
                            "START" -> "CENTER"
                            "CENTER" -> "END"
                            else -> "START"
                        }
                        onSetAlignment(nextAlign)
                    },
                    modifier = Modifier.size(34.dp).testTag("editor_alignment_toggle")
                ) {
                    Icon(
                        imageVector = when (editorState.textAlignment.uppercase()) {
                            "CENTER" -> Icons.Default.FormatAlignCenter
                            "END" -> Icons.AutoMirrored.Filled.FormatAlignRight
                            else -> Icons.AutoMirrored.Filled.FormatAlignLeft
                        },
                        contentDescription = "Text Alignment",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(contentFieldValue.text))
                        onShowMessage("Copied note content to clipboard")
                    },
                    modifier = Modifier.size(34.dp).testTag("editor_copy_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Note", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text.orEmpty()
                        if (clip.isNotEmpty()) {
                            val currentText = contentFieldValue.text
                            val start = contentFieldValue.selection.min.coerceIn(0, currentText.length)
                            val end = contentFieldValue.selection.max.coerceIn(0, currentText.length)
                            val updatedText = currentText.replaceRange(start, end, clip)
                            val newCursor = start + clip.length
                            contentFieldValue = TextFieldValue(text = updatedText, selection = TextRange(newCursor))
                            onTextChanged(editorState.title, updatedText, newCursor, newCursor, verticalScrollState.value)
                        }
                    },
                    modifier = Modifier.size(34.dp).testTag("editor_paste_button")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste from Clipboard", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }

        AnimatedVisibility(visible = editorState.isSearchBarVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = editorState.inNoteSearchQuery,
                    onValueChange = onUpdateInNoteSearch,
                    placeholder = { Text("Find inside note…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("in_note_search_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "$searchMatchesCount matches", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(verticalScrollState)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = editorState.title,
                    onValueChange = { newTitle ->
                        onTextChanged(
                            newTitle,
                            contentFieldValue.text,
                            contentFieldValue.selection.start,
                            contentFieldValue.selection.end,
                            verticalScrollState.value
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = resolvedTextAlign,
                        textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().testTag("note_title_input"),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (editorState.title.isEmpty()) {
                                Text(
                                    text = if (isRtl) "عنوان الملاحظة..." else "Note Title…",
                                    style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = resolvedTextAlign),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "${contentFieldValue.text.length} chars", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${contentFieldValue.text.lines().size} lines", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Cursor: ${contentFieldValue.selection.start}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                BasicTextField(
                    value = contentFieldValue,
                    onValueChange = { newValue ->
                        contentFieldValue = newValue
                        onTextChanged(
                            editorState.title,
                            newValue.text,
                            newValue.selection.start,
                            newValue.selection.end,
                            verticalScrollState.value
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 25.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = resolvedTextAlign,
                        textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(680.dp)
                        .testTag("note_content_input"),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (contentFieldValue.text.isEmpty()) {
                                Text(
                                    text = if (isRtl) {
                                        "ابدأ الكتابة هنا... يتم الحفظ تلقائياً في قاعدة البيانات المحلية."
                                    } else {
                                        "Start writing… Every keystroke is automatically saved to the local database."
                                    },
                                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = resolvedTextAlign),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}
