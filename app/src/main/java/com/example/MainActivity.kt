package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.backup.BackupManager
import com.example.core.MainTab
import com.example.core.NovaRepository
import com.example.core.NovaViewModel
import com.example.database.NovaDatabase
import com.example.export.ExportImportManager
import com.example.modeling.ModelingScreen
import com.example.notes.NoteEditorScreen
import com.example.notes.NotesDashboardScreen
import com.example.session_recovery.SessionRecoveryManager
import com.example.session_recovery.novaDataStore
import com.example.settings.BackupSettingsScreen
import com.example.todo.TodoScreen
import com.example.ui.theme.NovaNotesTheme
import com.example.ui.theme.LocalNovaArabic
import com.example.ui.theme.StatusError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = NovaDatabase.getInstance(applicationContext)
        val sessionManager = SessionRecoveryManager(applicationContext.novaDataStore)
        val backupManager = BackupManager(applicationContext, database, sessionManager)
        val repository = NovaRepository(database, sessionManager, backupManager)

        setContent {
            val preferences = remember { getSharedPreferences("nova_appearance", MODE_PRIVATE) }
            var isDarkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", true)) }
            var isArabic by remember { mutableStateOf(preferences.getString("language", "en") == "ar") }
            CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr, LocalNovaArabic provides isArabic) {
                NovaNotesTheme(darkTheme = isDarkTheme) {
                    val novaViewModel: NovaViewModel = viewModel(factory = NovaViewModel.Factory(repository))
                    NovaNotesApp(
                        viewModel = novaViewModel,
                        isDarkTheme = isDarkTheme,
                        isArabic = isArabic,
                        onThemeChange = { dark ->
                            isDarkTheme = dark
                            preferences.edit().putBoolean("dark_theme", dark).apply()
                        },
                        onLanguageChange = { arabic ->
                            isArabic = arabic
                            preferences.edit().putString("language", if (arabic) "ar" else "en").apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NovaNotesApp(viewModel: NovaViewModel, isDarkTheme: Boolean, isArabic: Boolean, onThemeChange: (Boolean) -> Unit, onLanguageChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Flush any pending auto-save immediately when app enters background (ON_STOP / ON_PAUSE)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushEditorSaveImmediately(closeAfterFlush = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val displayedNotes by viewModel.displayedNotes.collectAsStateWithLifecycle()
    val allActiveNotes by viewModel.allActiveNotes.collectAsStateWithLifecycle()
    val favoriteNotes by viewModel.favoriteNotes.collectAsStateWithLifecycle()
    val trashedNotes by viewModel.trashedNotes.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedNoteFilter.collectAsStateWithLifecycle()
    val selectedFolderId by viewModel.selectedFolderId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.globalSearchQuery.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val modelingState by viewModel.modelingState.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val backupsList by viewModel.backupsList.collectAsStateWithLifecycle()
    val previewBackupPayload by viewModel.previewBackupPayload.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()

    val folderCounts = remember(allActiveNotes) {
        allActiveNotes.groupingBy { it.folderId ?: -1L }.eachCount()
    }

    // Pending SAF Export State
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showImportTextDialog by remember { mutableStateOf<String?>(null) } // "NOTES", "TEMPLATES", "BACKUP"

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val bytes = pendingExportBytes
        if (uri != null && bytes != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                    withContext(Dispatchers.Main) {
                        viewModel.showStatusMessage("Exported file successfully")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        viewModel.showStatusMessage("Export failed: ${e.message}")
                    }
                }
            }
        }
        pendingExportBytes = null
    }

    var activeSafImportMode by remember { mutableStateOf("NOTES") }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalArgumentException("Unable to read selected file")
                    withContext(Dispatchers.Main) {
                        handleImportedRawContent(activeSafImportMode, content, viewModel)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        viewModel.showStatusMessage("Import failed: ${e.message}")
                    }
                }
            }
        }
    }

    fun triggerExport(fileName: String, bytes: ByteArray) {
        pendingExportBytes = bytes
        try {
            createDocumentLauncher.launch(fileName)
        } catch (_: Exception) {
            viewModel.showStatusMessage("Prepared export ($fileName, ${bytes.size} bytes)")
        }
    }

    // PIN Lock Gate if enabled
    if (appSettings.pinLockEnabled && !isUnlocked) {
        PinLockGateScreen(
            onUnlockAttempt = { pin -> viewModel.attemptUnlockWithPin(pin) }
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useNavigationRail && !editorState.isOpen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .testTag("bottom_navigation_bar")
                    ) {
                        NavigationBarItem(
                            selected = currentTab == MainTab.NOTES,
                            onClick = { viewModel.selectTab(MainTab.NOTES) },
                            icon = { Icon(Icons.Default.Description, contentDescription = "Notes") },
                            label = { Text(if (isArabic) "الملاحظات" else "Notes") },
                            colors = novaNavBarItemColors(),
                            modifier = Modifier.testTag("nav_tab_notes")
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.TODO,
                            onClick = { viewModel.selectTab(MainTab.TODO) },
                            icon = { Icon(Icons.Default.CheckBox, contentDescription = "To-do") },
                            label = { Text(if (isArabic) "المهام" else "To-do") },
                            colors = novaNavBarItemColors(),
                            modifier = Modifier.testTag("nav_tab_todo")
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.MODELING,
                            onClick = { viewModel.selectTab(MainTab.MODELING) },
                            icon = { Icon(Icons.Default.Schema, contentDescription = "Modeling") },
                            label = { Text(if (isArabic) "تنسيق البيانات" else "Modeling") },
                            colors = novaNavBarItemColors(),
                            modifier = Modifier.testTag("nav_tab_modeling")
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.SETTINGS,
                            onClick = { viewModel.selectTab(MainTab.SETTINGS) },
                            icon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Backup") },
                            label = { Text(if (isArabic) "الإعدادات" else "Settings") },
                            colors = novaNavBarItemColors(),
                            modifier = Modifier.testTag("nav_tab_settings")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && !editorState.isOpen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxHeight().testTag("side_navigation_rail")
                    ) {
                        NavigationRailItem(
                            selected = currentTab == MainTab.NOTES,
                            onClick = { viewModel.selectTab(MainTab.NOTES) },
                            icon = { Icon(Icons.Default.Description, contentDescription = "Notes") },
                            label = { Text("Notes") },
                            colors = novaNavRailItemColors(),
                            modifier = Modifier.testTag("rail_tab_notes")
                        )
                        NavigationRailItem(
                            selected = currentTab == MainTab.TODO,
                            onClick = { viewModel.selectTab(MainTab.TODO) },
                            icon = { Icon(Icons.Default.CheckBox, contentDescription = "To-do") },
                            label = { Text("To-do") },
                            colors = novaNavRailItemColors(),
                            modifier = Modifier.testTag("rail_tab_todo")
                        )
                        NavigationRailItem(
                            selected = currentTab == MainTab.MODELING,
                            onClick = { viewModel.selectTab(MainTab.MODELING) },
                            icon = { Icon(Icons.Default.Schema, contentDescription = "Modeling") },
                            label = { Text("Modeling") },
                            colors = novaNavRailItemColors(),
                            modifier = Modifier.testTag("rail_tab_modeling")
                        )
                        NavigationRailItem(
                            selected = currentTab == MainTab.SETTINGS,
                            onClick = { viewModel.selectTab(MainTab.SETTINGS) },
                            icon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Backup") },
                            label = { Text("Backup") },
                            colors = novaNavRailItemColors(),
                            modifier = Modifier.testTag("rail_tab_settings")
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (editorState.isOpen) {
                        NoteEditorScreen(
                            editorState = editorState,
                            folders = folders,
                            onBackAndFlush = { viewModel.flushEditorSaveImmediately(closeAfterFlush = true) },
                            onTextChanged = { title, content, cursor, selEnd, scroll ->
                                viewModel.onEditorTextChanged(title, content, cursor, selEnd, scroll)
                            },
                            onViewportChanged = { cursor, selEnd, scroll ->
                                viewModel.onEditorViewportChanged(cursor, selEnd, scroll)
                            },
                            onUndo = { viewModel.undoEditorChange() },
                            onRedo = { viewModel.redoEditorChange() },
                            onSetDirection = { viewModel.setEditorTextDirection(it) },
                            onSetAlignment = { viewModel.setEditorTextAlignment(it) },
                            onSetFolder = { viewModel.setEditorFolder(it) },
                            onTogglePin = { viewModel.toggleEditorPin() },
                            onToggleInNoteSearch = { viewModel.toggleEditorInNoteSearch() },
                            onUpdateInNoteSearch = { viewModel.updateInNoteSearchQuery(it) },
                            onMoveToTrash = { viewModel.trashNote(editorState.noteId) },
                            onExportSingleNote = { fmt ->
                                val singleNote = com.example.database.NoteEntity(
                                    id = editorState.noteId,
                                    title = editorState.title,
                                    content = editorState.content,
                                    folderId = editorState.folderId,
                                    isPinned = editorState.isPinned,
                                    isFavorite = editorState.isFavorite,
                                    textDirection = editorState.textDirection
                                )
                                val safeName = editorState.title.ifBlank { "note_${editorState.noteId}" }.take(24)
                                when (fmt) {
                                    "PDF" -> triggerExport("$safeName.pdf", ExportImportManager.generatePdfBytes(singleNote.title, singleNote.content))
                                    "JSON" -> triggerExport("$safeName.json", ExportImportManager.exportNotesToJson(listOf(singleNote)).toByteArray(Charsets.UTF_8))
                                    "CSV" -> triggerExport("$safeName.csv", ExportImportManager.exportNotesToCsv(listOf(singleNote)).toByteArray(Charsets.UTF_8))
                                    else -> triggerExport("$safeName.txt", ExportImportManager.exportNotesToTxt(listOf(singleNote)).toByteArray(Charsets.UTF_8))
                                }
                            },
                            onShowMessage = { viewModel.showStatusMessage(it) }
                        )
                    } else {
                        when (currentTab) {
                            MainTab.NOTES -> {
                                NotesDashboardScreen(
                                    displayedNotes = displayedNotes,
                                    allActiveNotesCount = allActiveNotes.size,
                                    favoritesCount = favoriteNotes.size,
                                    trashCount = trashedNotes.size,
                                    folders = folders,
                                    folderCounts = folderCounts,
                                    selectedFilter = selectedFilter,
                                    selectedFolderId = selectedFolderId,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { viewModel.updateGlobalSearch(it) },
                                    onSelectFilter = { f, id -> viewModel.selectNoteFilter(f, id) },
                                    onOpenNote = { viewModel.openExistingNote(it) },
                                    onCreateNewNote = { viewModel.openNewNote() },
                                    onTogglePin = { viewModel.togglePinNote(it) },
                                    onToggleFavorite = { viewModel.toggleFavoriteNote(it) },
                                    onMoveToTrash = { viewModel.trashNote(it) },
                                    onRestoreFromTrash = { viewModel.restoreNote(it) },
                                    onDeleteForever = { viewModel.deleteNoteForever(it) },
                                    onEmptyTrash = { viewModel.emptyTrash() },
                                    onCreateFolder = { name, color -> viewModel.createFolder(name, color) },
                                    onRenameFolder = { folder, name, color -> viewModel.renameFolder(folder, name, color) },
                                    onDeleteFolder = { viewModel.deleteFolder(it) },
                                    onExportAllNotesRequest = { fmt ->
                                        val notesToExport = displayedNotes
                                        if (notesToExport.isEmpty()) {
                                            viewModel.showStatusMessage("No notes to export")
                                        } else {
                                            when (fmt) {
                                                "PDF" -> triggerExport(
                                                    "nova_notes_export.pdf",
                                                    ExportImportManager.generatePdfBytes("Nova Notes Export", ExportImportManager.exportNotesToTxt(notesToExport))
                                                )
                                                "JSON" -> triggerExport(
                                                    "nova_notes_export.json",
                                                    ExportImportManager.exportNotesToJson(notesToExport).toByteArray(Charsets.UTF_8)
                                                )
                                                "CSV" -> triggerExport(
                                                    "nova_notes_export.csv",
                                                    ExportImportManager.exportNotesToCsv(notesToExport).toByteArray(Charsets.UTF_8)
                                                )
                                                else -> triggerExport(
                                                    "nova_notes_export.txt",
                                                    ExportImportManager.exportNotesToTxt(notesToExport).toByteArray(Charsets.UTF_8)
                                                )
                                            }
                                        }
                                    },
                                    onImportNotesDialogRequest = {
                                        showImportTextDialog = "NOTES"
                                    },
                                    isArabic = isArabic
                                )
                            }

                            MainTab.TODO -> {
                                TodoScreen(
                                    todos = todos,
                                    searchQuery = searchQuery,
                                    onAddOrUpdateTodo = { id, t, d, c -> viewModel.addOrUpdateTodo(id, t, d, c) },
                                    onToggleTodo = { viewModel.toggleTodo(it) },
                                    onDeleteTodo = { viewModel.deleteTodo(it) },
                                    onMoveOrder = { todo, up -> viewModel.moveTodoOrder(todo, up) },
                                    onClearCompleted = { viewModel.clearCompletedTodos() }
                                )
                            }

                            MainTab.MODELING -> {
                                ModelingScreen(
                                    modelingState = modelingState,
                                    templates = templates,
                                    onInputChanged = { viewModel.updateModelingInput(it) },
                                    onSingleRecordEdited = { line, text -> viewModel.updateSingleModelingRecord(line, text) },
                                    onSelectTemplate = { viewModel.selectModelingTemplate(it) },
                                    onUpdateConfig = { viewModel.updateActiveTemplateConfig(it) },
                                    onSelectSubTab = { viewModel.setModelingSubTab(it) },
                                    onProcessNow = { viewModel.runModelingProcessNow() },
                                    onStartNewSession = { viewModel.startNewModelingSession() },
                                    onSaveOutputAsNote = { viewModel.saveModelingOutputAsNote() },
                                    onSaveCustomTemplate = { viewModel.saveCustomTemplate(it) },
                                    onDuplicateTemplate = { viewModel.duplicateTemplate(it) },
                                    onDeleteTemplate = { viewModel.deleteTemplate(it) },
                                    onSetDefaultTemplate = { viewModel.markTemplateAsDefault(it) },
                                    onExportModelingResult = { fmt ->
                                        val res = modelingState.processResult
                                        when (fmt) {
                                            "PDF" -> triggerExport("modeling_result.pdf", ExportImportManager.generatePdfBytes("Modeling Result", res.fullOutput))
                                            "JSON" -> triggerExport("modeling_result.json", ExportImportManager.exportModelingResultToJson(res).toByteArray(Charsets.UTF_8))
                                            "CSV" -> triggerExport("modeling_result.csv", ExportImportManager.exportModelingResultToCsv(res).toByteArray(Charsets.UTF_8))
                                            else -> triggerExport("modeling_result.txt", res.fullOutput.toByteArray(Charsets.UTF_8))
                                        }
                                    },
                                    onExportTemplatesJson = {
                                        val json = ExportImportManager.exportTemplatesToJson(templates) {
                                            viewModel.repository.backupManager.serializeTemplateToJson(it)
                                        }
                                        triggerExport("nova_modeling_templates.json", json.toByteArray(Charsets.UTF_8))
                                    },
                                    onImportTemplatesDialogRequest = {
                                        showImportTextDialog = "TEMPLATES"
                                    },
                                    onShowMessage = { viewModel.showStatusMessage(it) }
                                )
                            }

                            MainTab.SETTINGS -> {
                                BackupSettingsScreen(
                                    settings = appSettings,
                                    backups = backupsList,
                                    previewPayload = previewBackupPayload,
                                    onToggleAutoBackup = { viewModel.updateBackupPreferences(autoEnabled = it) },
                                    onChangeBackupFrequency = { viewModel.updateBackupPreferences(frequency = it) },
                                    onChangeRetentionCount = { viewModel.updateBackupPreferences(retentionCount = it) },
                                    onCreateBackupNow = { viewModel.triggerManualBackup() },
                                    onInspectBackupForRestore = { viewModel.inspectBackupForRestore(it) },
                                    onDeleteBackup = { viewModel.deleteBackupFile(it) },
                                    onExportBackupToSaf = {
                                        scope.launch {
                                            val json = viewModel.repository.backupManager.serializeFullBackupJson()
                                            triggerExport("nova_full_backup.json", json.toByteArray(Charsets.UTF_8))
                                        }
                                    },
                                    onImportBackupFromSaf = {
                                        showImportTextDialog = "BACKUP"
                                    },
                                    onConfirmRestore = { viewModel.confirmRestoreBackup(it) },
                                    onCloseRestorePreview = { viewModel.closeBackupPreview() },
                                    onConfigurePinLock = { enabled, pin -> viewModel.configurePinLock(enabled, pin) },
                                    isArabic = isArabic,
                                    isDarkTheme = isDarkTheme,
                                    onLanguageChange = onLanguageChange,
                                    onThemeChange = onThemeChange
                                )
                            }
                        }
                    }

                    // Floating Status Toast Banner
                    androidx.compose.animation.AnimatedVisibility(
                        visible = statusBannerMessage != null,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 18.dp, start = 20.dp, end = 20.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("status_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = statusBannerMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Unified Import Dialog (supports choosing SAF file OR pasting TXT/JSON/CSV directly)
    showImportTextDialog?.let { mode ->
        var rawImportText by remember(mode) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportTextDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    when (mode) {
                        "TEMPLATES" -> "Import Modeling Templates (JSON)"
                        "BACKUP" -> "Import & Validate Full Backup (JSON)"
                        else -> "Import Notes (TXT / JSON / CSV)"
                    },
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            activeSafImportMode = mode
                            showImportTextDialog = null
                            try {
                                openDocumentLauncher.launch(arrayOf("*/*"))
                            } catch (_: Exception) {
                                viewModel.showStatusMessage("File picker unavailable")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select File from Device Storage…", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "Or paste content directly below (validated before import):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rawImportText,
                        onValueChange = { rawImportText = it },
                        placeholder = { Text("Paste TXT, JSON, or CSV content here…") },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth().testTag("import_raw_text_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rawImportText.isNotBlank()) {
                            handleImportedRawContent(mode, rawImportText, viewModel)
                            showImportTextDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.testTag("confirm_import_button")
                ) {
                    Text("Validate & Import", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportTextDialog = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

private fun handleImportedRawContent(
    mode: String,
    content: String,
    viewModel: NovaViewModel
) {
    try {
        when (mode) {
            "BACKUP" -> {
                viewModel.inspectRawBackupJsonForRestore(content)
            }
            "TEMPLATES" -> {
                val parsed = ExportImportManager.parseImportedTemplates(content) {
                    viewModel.repository.backupManager.parseTemplateFromJson(it)
                }
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    viewModel.repository.importTemplates(parsed)
                    viewModel.showStatusMessage("Imported ${parsed.size} modeling template(s)")
                }
            }
            else -> {
                val notes = ExportImportManager.parseImportedNotes(content)
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    viewModel.repository.importNotes(notes)
                    viewModel.showStatusMessage("Imported ${notes.size} note(s) without overwriting existing data")
                }
            }
        }
    } catch (e: Exception) {
        viewModel.showStatusMessage("Import error: ${e.message}")
    }
}

@Composable
private fun PinLockGateScreen(
    onUnlockAttempt: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .testTag("pin_lock_gate_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("NOVA NOTES LOCKED", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Enter your offline PIN to unlock your notes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter { c -> c.isDigit() }.take(8)
                    errorText = null
                },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.width(240.dp).testTag("unlock_pin_input")
            )

            if (errorText != null) {
                Text(errorText!!, color = StatusError, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    if (!onUnlockAttempt(pin)) {
                        errorText = "Incorrect PIN"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.testTag("unlock_pin_button")
            ) {
                Text("Unlock", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun novaNavBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun novaNavRailItemColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)
