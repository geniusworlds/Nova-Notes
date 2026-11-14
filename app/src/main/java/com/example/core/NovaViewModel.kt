package com.example.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupMetadata
import com.example.backup.BackupPayload
import com.example.backup.RestoreSelection
import com.example.database.FolderEntity
import com.example.database.ModelingTemplateEntity
import com.example.database.NoteEntity
import com.example.database.TodoEntity
import com.example.modeling.ModelingEngine
import com.example.modeling.ModelingProcessResult
import com.example.session_recovery.AppSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab {
    NOTES,
    TODO,
    MODELING,
    SETTINGS
}

enum class NoteFilterType {
    ALL,
    FAVORITES,
    RECENT,
    TRASH,
    FOLDER
}

enum class SaveIndicatorState {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}

data class UndoRedoSnapshot(
    val title: String,
    val content: String,
    val cursor: Int
)

data class EditorUiState(
    val isOpen: Boolean = false,
    val noteId: Long = 0L,
    val title: String = "",
    val content: String = "",
    val folderId: Long? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val textDirection: String = "AUTO",
    val textAlignment: String = "START",
    val cursorPosition: Int = 0,
    val selectionEnd: Int = 0,
    val verticalScrollOffset: Int = 0,
    val inNoteSearchQuery: String = "",
    val isSearchBarVisible: Boolean = false,
    val saveState: SaveIndicatorState = SaveIndicatorState.SAVED,
    val lastSavedTimestamp: Long = System.currentTimeMillis(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

data class ModelingUiState(
    val rawInput: String = "admin@nova.io:Sup3rS3cur3!\nuser1@nova.io:Pass#1001\nuser2@nova.io:Pass#1002\nuser3@nova.io:Pass#1003\nlead@nova.io:LeadKey#99\ndev1@nova.io:DevKey#01\ndev2@nova.io:DevKey#02\ndev3@nova.io:DevKey#03",
    val selectedTemplate: ModelingTemplateEntity = ModelingEngine.getDefaultBuiltInTemplates().first(),
    val processResult: ModelingProcessResult = ModelingEngine.process(
        rawInput = "admin@nova.io:Sup3rS3cur3!\nuser1@nova.io:Pass#1001\nuser2@nova.io:Pass#1002\nuser3@nova.io:Pass#1003\nlead@nova.io:LeadKey#99\ndev1@nova.io:DevKey#01\ndev2@nova.io:DevKey#02\ndev3@nova.io:DevKey#03",
        template = ModelingEngine.getDefaultBuiltInTemplates().first()
    ),
    val activeSubTab: Int = 0,
    val isProcessing: Boolean = false,
    val lastRestoredSessionTimestamp: Long? = null
)

class NovaViewModel(
    val repository: NovaRepository
) : ViewModel() {

    private val _currentTab = MutableStateFlow(MainTab.NOTES)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    private val _selectedNoteFilter = MutableStateFlow(NoteFilterType.ALL)
    val selectedNoteFilter: StateFlow<NoteFilterType> = _selectedNoteFilter.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    val selectedFolderId: StateFlow<Long?> = _selectedFolderId.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    val allActiveNotes: StateFlow<List<NoteEntity>> = repository.activeNotesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedNotes: StateFlow<List<NoteEntity>> = repository.trashedNotesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteNotes: StateFlow<List<NoteEntity>> = repository.favoriteNotesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentNotes: StateFlow<List<NoteEntity>> = repository.recentlyEditedNotesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = repository.foldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todos: StateFlow<List<TodoEntity>> = repository.todosFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<ModelingTemplateEntity>> = repository.templatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelingEngine.getDefaultBuiltInTemplates())

    val appSettings: StateFlow<AppSettingsState> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsState())

    val displayedNotes: StateFlow<List<NoteEntity>> = combine(
        allActiveNotes,
        trashedNotes,
        favoriteNotes,
        recentNotes,
        _selectedNoteFilter,
        _selectedFolderId,
        _globalSearchQuery
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val active = values[0] as List<NoteEntity>
        @Suppress("UNCHECKED_CAST")
        val trash = values[1] as List<NoteEntity>
        @Suppress("UNCHECKED_CAST")
        val favs = values[2] as List<NoteEntity>
        @Suppress("UNCHECKED_CAST")
        val recent = values[3] as List<NoteEntity>
        val filter = values[4] as NoteFilterType
        val folderId = values[5] as Long?
        val query = (values[6] as String).trim()

        val baseList = when (filter) {
            NoteFilterType.ALL -> active
            NoteFilterType.FAVORITES -> favs
            NoteFilterType.RECENT -> recent
            NoteFilterType.TRASH -> trash
            NoteFilterType.FOLDER -> active.filter { it.folderId == folderId }
        }

        if (query.isBlank()) {
            baseList
        } else {
            baseList.filter { note ->
                note.title.contains(query, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val undoStack = ArrayDeque<UndoRedoSnapshot>()
    private val redoStack = ArrayDeque<UndoRedoSnapshot>()
    private var autoSaveDebounceJob: Job? = null

    private val _modelingState = MutableStateFlow(ModelingUiState())
    val modelingState: StateFlow<ModelingUiState> = _modelingState.asStateFlow()
    private var modelingDebounceJob: Job? = null

    private val _backupsList = MutableStateFlow<List<BackupMetadata>>(emptyList())
    val backupsList: StateFlow<List<BackupMetadata>> = _backupsList.asStateFlow()

    private val _previewBackupPayload = MutableStateFlow<BackupPayload?>(null)
    val previewBackupPayload: StateFlow<BackupPayload?> = _previewBackupPayload.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultsInitialized()
            restoreSessionsOnStartup()
            refreshBackupsList()
            repository.backupManager.checkAndRunScheduledBackup()?.let {
                refreshBackupsList()
            }
        }
    }

    private suspend fun restoreSessionsOnStartup() {
        val modSnapshot = repository.getModelingSessionSnapshot()
        if (modSnapshot != null && modSnapshot.rawInput.isNotBlank()) {
            val savedTemplates = repository.database.modelingTemplateDao().getAllTemplatesSnapshot()
            val matchedTemplate = savedTemplates.find { it.id == modSnapshot.selectedTemplateId }
                ?: ModelingEngine.getDefaultBuiltInTemplates().first()
            val configuredTemplate = matchedTemplate.copy(
                sortMode = modSnapshot.sortMode,
                startDateStr = modSnapshot.startDateStr,
                dateMode = modSnapshot.dateMode,
                showDateOnEveryGroup = modSnapshot.showDateOnEveryGroup,
                combineGroupsIntoOneList = modSnapshot.combineGroupsIntoOneList,
                startNumber = modSnapshot.startNumber,
                endNumber = modSnapshot.endNumber,
                recordsPerGroup = modSnapshot.recordsPerGroup,
                parentIndexInGroup = modSnapshot.parentIndexInGroup,
                parentLabel = modSnapshot.parentLabel,
                childLabel = modSnapshot.childLabel,
                separatorLine = modSnapshot.separatorLine,
                useArabicNumerals = modSnapshot.useArabicNumerals,
                removeEmptyLines = modSnapshot.removeEmptyLines
            )
            val result = withContext(Dispatchers.Default) {
                ModelingEngine.process(modSnapshot.rawInput, configuredTemplate)
            }
            _modelingState.value = ModelingUiState(
                rawInput = modSnapshot.rawInput,
                selectedTemplate = configuredTemplate,
                processResult = result,
                activeSubTab = modSnapshot.activeTabIndex,
                isProcessing = false,
                lastRestoredSessionTimestamp = modSnapshot.timestamp
            )
        }

        val recoveredNotePair = repository.recoverNoteSessionIfApplicable()
        if (recoveredNotePair != null) {
            val (note, snapshot) = recoveredNotePair
            _editorState.value = EditorUiState(
                isOpen = true,
                noteId = note.id,
                title = note.title,
                content = note.content,
                folderId = note.folderId,
                isPinned = note.isPinned,
                isFavorite = note.isFavorite,
                isTrashed = note.isTrashed,
                textDirection = note.textDirection,
                textAlignment = note.textAlignment,
                cursorPosition = snapshot.cursorPosition.coerceIn(0, note.content.length),
                selectionEnd = snapshot.selectionEnd.coerceIn(0, note.content.length),
                verticalScrollOffset = snapshot.verticalScrollOffset.coerceAtLeast(0),
                saveState = SaveIndicatorState.SAVED,
                lastSavedTimestamp = note.updatedAt
            )
        }
    }

    fun selectTab(tab: MainTab) {
        if (_editorState.value.isOpen) {
            flushEditorSaveImmediately(closeAfterFlush = true)
        }
        _currentTab.value = tab
    }

    fun updateGlobalSearch(query: String) {
        _globalSearchQuery.value = query
    }

    fun selectNoteFilter(filter: NoteFilterType, folderId: Long? = null) {
        _selectedNoteFilter.value = filter
        _selectedFolderId.value = folderId
    }

    fun showStatusMessage(message: String) {
        _statusBannerMessage.value = message
        viewModelScope.launch {
            delay(3200)
            if (_statusBannerMessage.value == message) {
                _statusBannerMessage.value = null
            }
        }
    }

    fun dismissStatusMessage() {
        _statusBannerMessage.value = null
    }

    fun openNewNote(defaultFolderId: Long? = _selectedFolderId.value) {
        viewModelScope.launch {
            val defaultDir = appSettings.value.defaultTextDirection
            val initialNote = NoteEntity(
                title = "",
                content = "",
                folderId = defaultFolderId,
                textDirection = defaultDir,
                textAlignment = "START"
            )
            val newId = repository.saveNoteTransactional(initialNote)
            undoStack.clear()
            redoStack.clear()
            _editorState.value = EditorUiState(
                isOpen = true,
                noteId = newId,
                title = "",
                content = "",
                folderId = defaultFolderId,
                textDirection = defaultDir,
                textAlignment = "START",
                saveState = SaveIndicatorState.SAVED
            )
            repository.sessionManager.saveNoteEditorSession(
                noteId = newId,
                title = "",
                content = "",
                cursorPosition = 0,
                selectionEnd = 0,
                verticalScrollOffset = 0,
                textDirection = defaultDir,
                textAlignment = "START"
            )
        }
    }

    fun openExistingNote(note: NoteEntity) {
        undoStack.clear()
        redoStack.clear()
        _editorState.value = EditorUiState(
            isOpen = true,
            noteId = note.id,
            title = note.title,
            content = note.content,
            folderId = note.folderId,
            isPinned = note.isPinned,
            isFavorite = note.isFavorite,
            isTrashed = note.isTrashed,
            textDirection = note.textDirection,
            textAlignment = note.textAlignment,
            cursorPosition = note.lastCursorPosition.coerceIn(0, note.content.length),
            selectionEnd = note.lastSelectionEnd.coerceIn(0, note.content.length),
            verticalScrollOffset = note.lastScrollOffset.coerceAtLeast(0),
            saveState = SaveIndicatorState.SAVED,
            lastSavedTimestamp = note.updatedAt
        )
        viewModelScope.launch {
            repository.sessionManager.saveNoteEditorSession(
                noteId = note.id,
                title = note.title,
                content = note.content,
                cursorPosition = note.lastCursorPosition,
                selectionEnd = note.lastSelectionEnd,
                verticalScrollOffset = note.lastScrollOffset,
                textDirection = note.textDirection,
                textAlignment = note.textAlignment,
                timestamp = note.updatedAt
            )
        }
    }

    fun onEditorTextChanged(
        newTitle: String,
        newContent: String,
        cursorPosition: Int = _editorState.value.cursorPosition,
        selectionEnd: Int = _editorState.value.selectionEnd,
        scrollOffset: Int = _editorState.value.verticalScrollOffset
    ) {
        val current = _editorState.value
        if (!current.isOpen) return

        val contentChanged = current.title != newTitle || current.content != newContent
        if (contentChanged) {
            if (undoStack.isEmpty() ||
                (undoStack.last().title != current.title || undoStack.last().content != current.content)
            ) {
                if (undoStack.size >= 50) undoStack.removeFirst()
                undoStack.addLast(UndoRedoSnapshot(current.title, current.content, current.cursorPosition))
            }
            redoStack.clear()
        }

        _editorState.value = current.copy(
            title = newTitle,
            content = newContent,
            cursorPosition = cursorPosition.coerceIn(0, newContent.length),
            selectionEnd = selectionEnd.coerceIn(0, newContent.length),
            verticalScrollOffset = scrollOffset.coerceAtLeast(0),
            saveState = SaveIndicatorState.SAVING,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )

        scheduleDebouncedNoteSave()
    }

    fun onEditorViewportChanged(cursorPosition: Int, selectionEnd: Int, verticalScrollOffset: Int) {
        val current = _editorState.value
        if (!current.isOpen) return
        _editorState.value = current.copy(
            cursorPosition = cursorPosition.coerceIn(0, current.content.length),
            selectionEnd = selectionEnd.coerceIn(0, current.content.length),
            verticalScrollOffset = verticalScrollOffset.coerceAtLeast(0)
        )
        scheduleDebouncedNoteSave(delayMs = 400L)
    }

    fun undoEditorChange() {
        val current = _editorState.value
        if (!current.isOpen || undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        redoStack.addLast(UndoRedoSnapshot(current.title, current.content, current.cursorPosition))
        _editorState.value = current.copy(
            title = previous.title,
            content = previous.content,
            cursorPosition = previous.cursor.coerceIn(0, previous.content.length),
            selectionEnd = previous.cursor.coerceIn(0, previous.content.length),
            saveState = SaveIndicatorState.SAVING,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
        scheduleDebouncedNoteSave(delayMs = 150L)
    }

    fun redoEditorChange() {
        val current = _editorState.value
        if (!current.isOpen || redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(UndoRedoSnapshot(current.title, current.content, current.cursorPosition))
        _editorState.value = current.copy(
            title = next.title,
            content = next.content,
            cursorPosition = next.cursor.coerceIn(0, next.content.length),
            selectionEnd = next.cursor.coerceIn(0, next.content.length),
            saveState = SaveIndicatorState.SAVING,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )
        scheduleDebouncedNoteSave(delayMs = 150L)
    }

    fun setEditorTextDirection(direction: String) {
        val current = _editorState.value
        if (!current.isOpen) return
        _editorState.value = current.copy(textDirection = direction, saveState = SaveIndicatorState.SAVING)
        scheduleDebouncedNoteSave(delayMs = 150L)
    }

    fun setEditorTextAlignment(alignment: String) {
        val current = _editorState.value
        if (!current.isOpen) return
        _editorState.value = current.copy(textAlignment = alignment, saveState = SaveIndicatorState.SAVING)
        scheduleDebouncedNoteSave(delayMs = 150L)
    }

    fun setEditorFolder(folderId: Long?) {
        val current = _editorState.value
        if (!current.isOpen) return
        _editorState.value = current.copy(folderId = folderId, saveState = SaveIndicatorState.SAVING)
        scheduleDebouncedNoteSave(delayMs = 100L)
    }

    fun toggleEditorPin() {
        val current = _editorState.value
        if (!current.isOpen) return
        _editorState.value = current.copy(isPinned = !current.isPinned, saveState = SaveIndicatorState.SAVING)
        scheduleDebouncedNoteSave(delayMs = 100L)
    }

    fun toggleEditorFavorite() {
        val current = _editorState.value
        if (!current.isOpen) return
        _editorState.value = current.copy(isFavorite = !current.isFavorite, saveState = SaveIndicatorState.SAVING)
        scheduleDebouncedNoteSave(delayMs = 100L)
    }

    fun toggleEditorInNoteSearch() {
        val current = _editorState.value
        _editorState.value = current.copy(
            isSearchBarVisible = !current.isSearchBarVisible,
            inNoteSearchQuery = if (current.isSearchBarVisible) "" else current.inNoteSearchQuery
        )
    }

    fun updateInNoteSearchQuery(query: String) {
        _editorState.value = _editorState.value.copy(inNoteSearchQuery = query)
    }

    private fun scheduleDebouncedNoteSave(delayMs: Long = 250L) {
        autoSaveDebounceJob?.cancel()
        autoSaveDebounceJob = viewModelScope.launch {
            delay(delayMs)
            persistCurrentEditorState()
        }
    }

    private suspend fun persistCurrentEditorState() {
        val snapshot = _editorState.value
        if (!snapshot.isOpen || snapshot.noteId <= 0L) return
        try {
            val now = System.currentTimeMillis()
            repository.sessionManager.saveNoteEditorSession(
                noteId = snapshot.noteId,
                title = snapshot.title,
                content = snapshot.content,
                cursorPosition = snapshot.cursorPosition,
                selectionEnd = snapshot.selectionEnd,
                verticalScrollOffset = snapshot.verticalScrollOffset,
                textDirection = snapshot.textDirection,
                textAlignment = snapshot.textAlignment,
                timestamp = now
            )
            val entity = NoteEntity(
                id = snapshot.noteId,
                title = snapshot.title,
                content = snapshot.content,
                folderId = snapshot.folderId,
                isPinned = snapshot.isPinned,
                isFavorite = snapshot.isFavorite,
                isTrashed = snapshot.isTrashed,
                textDirection = snapshot.textDirection,
                textAlignment = snapshot.textAlignment,
                lastCursorPosition = snapshot.cursorPosition,
                lastSelectionEnd = snapshot.selectionEnd,
                lastScrollOffset = snapshot.verticalScrollOffset,
                updatedAt = now
            )
            repository.saveNoteTransactional(entity)
            _editorState.value = _editorState.value.copy(
                saveState = SaveIndicatorState.SAVED,
                lastSavedTimestamp = now
            )
        } catch (_: Exception) {
            _editorState.value = _editorState.value.copy(saveState = SaveIndicatorState.ERROR)
        }
    }

    fun flushEditorSaveImmediately(closeAfterFlush: Boolean = false) {
        autoSaveDebounceJob?.cancel()
        val snapshot = _editorState.value
        if (!snapshot.isOpen) return

        if (closeAfterFlush) {
            _editorState.value = snapshot.copy(isOpen = false, saveState = SaveIndicatorState.SAVED)
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entity = NoteEntity(
                id = snapshot.noteId,
                title = snapshot.title,
                content = snapshot.content,
                folderId = snapshot.folderId,
                isPinned = snapshot.isPinned,
                isFavorite = snapshot.isFavorite,
                isTrashed = snapshot.isTrashed,
                textDirection = snapshot.textDirection,
                textAlignment = snapshot.textAlignment,
                lastCursorPosition = snapshot.cursorPosition,
                lastSelectionEnd = snapshot.selectionEnd,
                lastScrollOffset = snapshot.verticalScrollOffset,
                updatedAt = now
            )
            repository.saveNoteTransactional(entity)
            if (closeAfterFlush) {
                repository.sessionManager.clearNoteEditorSession()
            } else {
                repository.sessionManager.saveNoteEditorSession(
                    noteId = snapshot.noteId,
                    title = snapshot.title,
                    content = snapshot.content,
                    cursorPosition = snapshot.cursorPosition,
                    selectionEnd = snapshot.selectionEnd,
                    verticalScrollOffset = snapshot.verticalScrollOffset,
                    textDirection = snapshot.textDirection,
                    textAlignment = snapshot.textAlignment,
                    timestamp = now
                )
            }
        }
    }

    fun togglePinNote(note: NoteEntity) {
        viewModelScope.launch { repository.togglePinNote(note) }
    }

    fun toggleFavoriteNote(note: NoteEntity) {
        viewModelScope.launch { repository.toggleFavoriteNote(note) }
    }

    fun trashNote(noteId: Long) {
        if (_editorState.value.isOpen && _editorState.value.noteId == noteId) {
            _editorState.value = _editorState.value.copy(isOpen = false)
        }
        viewModelScope.launch {
            repository.moveNoteToTrash(noteId)
            repository.sessionManager.clearNoteEditorSession()
            showStatusMessage("Note moved to Trash")
        }
    }

    fun restoreNote(noteId: Long) {
        viewModelScope.launch {
            repository.restoreNoteFromTrash(noteId)
            showStatusMessage("Note restored")
        }
    }

    fun deleteNoteForever(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNotePermanently(noteId)
            showStatusMessage("Note permanently deleted")
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            showStatusMessage("Trash emptied")
        }
    }

    fun createFolder(name: String, colorHex: String, iconName: String = "folder") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createFolder(name, colorHex, iconName)
            showStatusMessage("Folder '${name.trim()}' created")
        }
    }

    fun renameFolder(folder: FolderEntity, newName: String, newColorHex: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateFolder(folder.copy(name = newName.trim(), colorHex = newColorHex))
            showStatusMessage("Folder updated")
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            if (_selectedFolderId.value == folder.id) {
                selectNoteFilter(NoteFilterType.ALL, null)
            }
            repository.deleteFolder(folder)
            showStatusMessage("Folder '${folder.name}' deleted")
        }
    }

    fun addOrUpdateTodo(
        id: Long = 0L,
        title: String,
        description: String,
        category: String
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val currentList = todos.value
            val existing = currentList.find { it.id == id }
            val entity = existing?.copy(
                title = title.trim(),
                description = description.trim(),
                category = category.trim().ifBlank { "General" }
            ) ?: TodoEntity(
                title = title.trim(),
                description = description.trim(),
                category = category.trim().ifBlank { "General" },
                sortOrder = (currentList.maxOfOrNull { it.sortOrder } ?: 0) + 1
            )
            repository.saveTodo(entity)
        }
    }

    fun toggleTodo(todo: TodoEntity) {
        viewModelScope.launch { repository.toggleTodoCompleted(todo) }
    }

    fun deleteTodo(todo: TodoEntity) {
        viewModelScope.launch { repository.deleteTodo(todo) }
    }

    fun moveTodoOrder(todo: TodoEntity, moveUp: Boolean) {
        val current = todos.value.toMutableList()
        val index = current.indexOfFirst { it.id == todo.id }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in current.indices) return
        val swapped = current[targetIndex]
        current[targetIndex] = current[index]
        current[index] = swapped
        viewModelScope.launch {
            repository.reorderTodos(current)
        }
    }

    fun clearCompletedTodos() {
        viewModelScope.launch {
            repository.deleteCompletedTodos()
            showStatusMessage("Completed tasks cleared")
        }
    }

    fun updateModelingInput(newInput: String) {
        _modelingState.value = _modelingState.value.copy(rawInput = newInput, isProcessing = true)
        val delayMs = if (newInput.length > 20_000) 280L else 60L
        triggerLiveModelingProcess(delayMs)
    }

    fun updateSingleModelingRecord(originalOneBasedLine: Int, updatedRecordText: String) {
        val lines = _modelingState.value.rawInput.split("\r\n", "\n").toMutableList()
        val idx = originalOneBasedLine - 1
        if (idx in lines.indices) {
            lines[idx] = updatedRecordText
            updateModelingInput(lines.joinToString("\n"))
        }
    }

    fun selectModelingTemplate(template: ModelingTemplateEntity) {
        _modelingState.value = _modelingState.value.copy(selectedTemplate = template, isProcessing = true)
        triggerLiveModelingProcess(delayMs = 20L)
    }

    fun updateActiveTemplateConfig(transform: (ModelingTemplateEntity) -> ModelingTemplateEntity) {
        val updated = transform(_modelingState.value.selectedTemplate)
        _modelingState.value = _modelingState.value.copy(selectedTemplate = updated, isProcessing = true)
        triggerLiveModelingProcess(delayMs = 30L)
    }

    fun setModelingSubTab(tabIndex: Int) {
        _modelingState.value = _modelingState.value.copy(activeSubTab = tabIndex)
        persistModelingSessionSnapshot()
    }

    fun startNewModelingSession() {
        val defaultTemplate = templates.value.find { it.isDefault }
            ?: ModelingEngine.getDefaultBuiltInTemplates().first()
        _modelingState.value = ModelingUiState(
            rawInput = "",
            selectedTemplate = defaultTemplate,
            processResult = ModelingEngine.process("", defaultTemplate),
            activeSubTab = 0,
            isProcessing = false,
            lastRestoredSessionTimestamp = null
        )
        viewModelScope.launch {
            repository.sessionManager.clearModelingSession()
            showStatusMessage("Started fresh Modeling session")
        }
    }

    fun runModelingProcessNow() {
        triggerLiveModelingProcess(delayMs = 0L)
    }

    private fun triggerLiveModelingProcess(delayMs: Long = 50L) {
        modelingDebounceJob?.cancel()
        modelingDebounceJob = viewModelScope.launch {
            if (delayMs > 0L) delay(delayMs)
            val snapshot = _modelingState.value
            val result = withContext(Dispatchers.Default) {
                ModelingEngine.process(snapshot.rawInput, snapshot.selectedTemplate)
            }
            _modelingState.value = _modelingState.value.copy(
                processResult = result,
                isProcessing = false
            )
            persistModelingSessionSnapshot()
        }
    }

    private fun persistModelingSessionSnapshot() {
        val state = _modelingState.value
        val tmpl = state.selectedTemplate
        viewModelScope.launch {
            repository.sessionManager.saveModelingSession(
                rawInput = state.rawInput,
                selectedTemplateId = tmpl.id,
                sortMode = tmpl.sortMode,
                startDateStr = tmpl.startDateStr,
                dateMode = tmpl.dateMode,
                showDateOnEveryGroup = tmpl.showDateOnEveryGroup,
                combineGroupsIntoOneList = tmpl.combineGroupsIntoOneList,
                startNumber = tmpl.startNumber,
                endNumber = tmpl.endNumber,
                recordsPerGroup = tmpl.recordsPerGroup,
                parentIndexInGroup = tmpl.parentIndexInGroup,
                parentLabel = tmpl.parentLabel,
                childLabel = tmpl.childLabel,
                separatorLine = tmpl.separatorLine,
                useArabicNumerals = tmpl.useArabicNumerals,
                removeEmptyLines = tmpl.removeEmptyLines,
                activeTabIndex = state.activeSubTab
            )
        }
    }

    fun saveModelingOutputAsNote() {
        val current = _modelingState.value
        val out = current.processResult.fullOutput
        if (out.isBlank()) {
            showStatusMessage("Modeling output is empty")
            return
        }
        viewModelScope.launch {
            val modelingFolder = folders.value.find { it.name.contains("Modeling", ignoreCase = true) }
            val noteTitle = "Modeling — ${current.selectedTemplate.name} (${current.processResult.validCount} records)"
            val noteId = repository.saveNoteTransactional(
                NoteEntity(
                    title = noteTitle,
                    content = out,
                    folderId = modelingFolder?.id
                )
            )
            showStatusMessage("Saved Modeling result as Note #$noteId")
        }
    }

    fun saveCustomTemplate(template: ModelingTemplateEntity) {
        val validationError = ModelingEngine.validateTemplate(template)
        if (validationError != null) {
            showStatusMessage(validationError)
            return
        }
        viewModelScope.launch {
            val savedId = repository.saveTemplate(template)
            val reloaded = repository.database.modelingTemplateDao().getTemplateById(savedId) ?: template.copy(id = savedId)
            selectModelingTemplate(reloaded)
            showStatusMessage("Template '${reloaded.name}' saved")
        }
    }

    fun duplicateTemplate(template: ModelingTemplateEntity) {
        viewModelScope.launch {
            val newId = repository.duplicateTemplate(template)
            val duplicated = repository.database.modelingTemplateDao().getTemplateById(newId)
            if (duplicated != null) {
                selectModelingTemplate(duplicated)
                showStatusMessage("Duplicated '${duplicated.name}'")
            }
        }
    }

    fun deleteTemplate(template: ModelingTemplateEntity) {
        if (template.isBuiltIn) {
            showStatusMessage("Built-in templates cannot be deleted")
            return
        }
        viewModelScope.launch {
            repository.deleteTemplate(template)
            val fallback = ModelingEngine.getDefaultBuiltInTemplates().first()
            selectModelingTemplate(fallback)
            showStatusMessage("Template '${template.name}' deleted")
        }
    }

    fun markTemplateAsDefault(template: ModelingTemplateEntity) {
        viewModelScope.launch {
            repository.setDefaultTemplate(template.id)
            showStatusMessage("'${template.name}' set as default template")
        }
    }

    fun refreshBackupsList() {
        viewModelScope.launch {
            _backupsList.value = repository.listBackups()
        }
    }

    fun triggerManualBackup() {
        viewModelScope.launch {
            try {
                val meta = repository.createBackupNow(isAuto = false)
                refreshBackupsList()
                showStatusMessage("Local backup created (${meta.noteCount} notes, ${meta.fileSizeBytes / 1024} KB)")
            } catch (e: Exception) {
                showStatusMessage("Backup failed: ${e.message}")
            }
        }
    }

    fun updateBackupPreferences(
        autoEnabled: Boolean? = null,
        frequency: String? = null,
        retentionCount: Int? = null,
        customUri: String? = null
    ) {
        viewModelScope.launch {
            repository.sessionManager.updateBackupSettings(
                autoBackupEnabled = autoEnabled,
                backupFrequency = frequency,
                retentionCount = retentionCount,
                customBackupUri = customUri
            )
            if (retentionCount != null) {
                repository.backupManager.pruneOldBackups(retentionCount)
                refreshBackupsList()
            }
        }
    }

    fun inspectBackupForRestore(metadata: BackupMetadata) {
        viewModelScope.launch {
            try {
                val payload = repository.readBackupPayload(metadata.filePath)
                _previewBackupPayload.value = payload
            } catch (e: Exception) {
                showStatusMessage("Integrity verification failed: ${e.message}")
            }
        }
    }

    fun inspectRawBackupJsonForRestore(rawJson: String) {
        try {
            val payload = repository.backupManager.parseAndValidateBackupJson(rawJson)
            _previewBackupPayload.value = payload
        } catch (e: Exception) {
            showStatusMessage("Backup validation failed: ${e.message}")
        }
    }

    fun closeBackupPreview() {
        _previewBackupPayload.value = null
    }

    fun confirmRestoreBackup(selection: RestoreSelection) {
        val payload = _previewBackupPayload.value ?: return
        viewModelScope.launch {
            try {
                repository.restoreBackup(payload, selection)
                _previewBackupPayload.value = null
                showStatusMessage("Backup restored successfully")
            } catch (e: Exception) {
                showStatusMessage("Restore failed: ${e.message}")
            }
        }
    }

    fun deleteBackupFile(metadata: BackupMetadata) {
        viewModelScope.launch {
            repository.backupManager.deleteBackupFile(metadata.filePath)
            refreshBackupsList()
            showStatusMessage("Backup deleted")
        }
    }

    fun configurePinLock(enable: Boolean, rawPin: String) {
        viewModelScope.launch {
            repository.sessionManager.updateSecuritySettings(enable, rawPin)
            _isUnlocked.value = true
            showStatusMessage(if (enable) "PIN Lock enabled" else "PIN Lock disabled")
        }
    }

    fun attemptUnlockWithPin(enteredPin: String): Boolean {
        val settings = appSettings.value
        val valid = repository.sessionManager.verifyPin(enteredPin, settings.pinHash)
        if (valid) {
            _isUnlocked.value = true
        }
        return valid
    }

    class Factory(private val repository: NovaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NovaViewModel(repository) as T
        }
    }
}
