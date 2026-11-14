package com.example.core

import com.example.backup.BackupManager
import com.example.backup.BackupMetadata
import com.example.backup.BackupPayload
import com.example.backup.RestoreSelection
import com.example.database.FolderEntity
import com.example.database.ModelingTemplateEntity
import com.example.database.NoteEntity
import com.example.database.NovaDatabase
import com.example.database.TodoEntity
import com.example.modeling.ModelingEngine
import com.example.session_recovery.AppSettingsState
import com.example.session_recovery.ModelingSessionSnapshot
import com.example.session_recovery.NoteEditorSessionSnapshot
import com.example.session_recovery.SessionRecoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NovaRepository(
    val database: NovaDatabase,
    val sessionManager: SessionRecoveryManager,
    val backupManager: BackupManager
) {
    private val noteDao = database.noteDao()
    private val folderDao = database.folderDao()
    private val todoDao = database.todoDao()
    private val templateDao = database.modelingTemplateDao()

    val activeNotesFlow: Flow<List<NoteEntity>> = noteDao.getAllActiveNotes()
    val trashedNotesFlow: Flow<List<NoteEntity>> = noteDao.getTrashedNotes()
    val favoriteNotesFlow: Flow<List<NoteEntity>> = noteDao.getFavoriteNotes()
    val recentlyEditedNotesFlow: Flow<List<NoteEntity>> = noteDao.getRecentlyEditedNotes()
    val foldersFlow: Flow<List<FolderEntity>> = folderDao.getAllFolders()
    val todosFlow: Flow<List<TodoEntity>> = todoDao.getAllTodos()
    val templatesFlow: Flow<List<ModelingTemplateEntity>> = templateDao.getAllTemplates()
    val settingsFlow: Flow<AppSettingsState> = sessionManager.appSettingsFlow

    /**
     * Seeds default folders and the two built-in Modeling templates ("Parent / Children" and "Sequential List")
     * on first launch if none exist.
     */
    suspend fun ensureDefaultsInitialized() = withContext(Dispatchers.IO) {
        val existingTemplates = templateDao.getAllTemplatesSnapshot()
        if (existingTemplates.none { it.isBuiltIn }) {
            templateDao.insertTemplates(ModelingEngine.getDefaultBuiltInTemplates())
        }

        val existingFolders = folderDao.getAllFoldersSnapshot()
        if (existingFolders.isEmpty()) {
            val defaultFolders = listOf(
                FolderEntity(name = "Work", colorHex = "#00E5FF", iconName = "work"),
                FolderEntity(name = "Personal", colorHex = "#1DE9B6", iconName = "person"),
                FolderEntity(name = "Modeling Logs", colorHex = "#FFC400", iconName = "schema")
            )
            folderDao.insertFolders(defaultFolders)
        }
    }

    // --- Notes Operations ---
    suspend fun getNoteById(id: Long): NoteEntity? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(id)
    }

    suspend fun saveNoteTransactional(note: NoteEntity): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updated = note.copy(
            updatedAt = now,
            createdAt = if (note.id == 0L) now else note.createdAt
        )
        noteDao.upsertNoteTransaction(updated)
    }

    suspend fun togglePinNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        noteDao.updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleFavoriteNote(note: NoteEntity) = withContext(Dispatchers.IO) {
        noteDao.updateNote(note.copy(isFavorite = !note.isFavorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun moveNoteToFolder(note: NoteEntity, folderId: Long?) = withContext(Dispatchers.IO) {
        noteDao.updateNote(note.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun moveNoteToTrash(noteId: Long) = withContext(Dispatchers.IO) {
        noteDao.moveToTrash(noteId)
    }

    suspend fun restoreNoteFromTrash(noteId: Long) = withContext(Dispatchers.IO) {
        noteDao.restoreFromTrash(noteId)
    }

    suspend fun deleteNotePermanently(noteId: Long) = withContext(Dispatchers.IO) {
        noteDao.deletePermanently(noteId)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        noteDao.emptyTrash()
    }

    suspend fun importNotes(notes: List<NoteEntity>) = withContext(Dispatchers.IO) {
        noteDao.insertNotes(notes)
    }

    // --- Folder Operations ---
    suspend fun createFolder(name: String, colorHex: String, iconName: String): Long = withContext(Dispatchers.IO) {
        folderDao.insertFolder(
            FolderEntity(
                name = name.trim(),
                colorHex = colorHex,
                iconName = iconName
            )
        )
    }

    suspend fun updateFolder(folder: FolderEntity) = withContext(Dispatchers.IO) {
        folderDao.updateFolder(folder)
    }

    suspend fun deleteFolder(folder: FolderEntity) = withContext(Dispatchers.IO) {
        folderDao.deleteFolderAndDetachNotes(folder)
    }

    // --- To-do Operations ---
    suspend fun saveTodo(todo: TodoEntity): Long = withContext(Dispatchers.IO) {
        todoDao.insertTodo(todo.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleTodoCompleted(todo: TodoEntity) = withContext(Dispatchers.IO) {
        todoDao.updateTodo(
            todo.copy(
                isCompleted = !todo.isCompleted,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteTodo(todo: TodoEntity) = withContext(Dispatchers.IO) {
        todoDao.deleteTodo(todo)
    }

    suspend fun reorderTodos(todosInNewOrder: List<TodoEntity>) = withContext(Dispatchers.IO) {
        val updated = todosInNewOrder.mapIndexed { idx, item ->
            item.copy(sortOrder = idx, updatedAt = System.currentTimeMillis())
        }
        todoDao.insertTodos(updated)
    }

    suspend fun deleteCompletedTodos() = withContext(Dispatchers.IO) {
        todoDao.deleteCompletedTodos()
    }

    // --- Modeling Template Operations ---
    suspend fun saveTemplate(template: ModelingTemplateEntity): Long = withContext(Dispatchers.IO) {
        templateDao.insertTemplate(template.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicateTemplate(template: ModelingTemplateEntity): Long = withContext(Dispatchers.IO) {
        val copy = template.copy(
            id = 0L,
            name = "${template.name} (Copy)",
            isBuiltIn = false,
            isDefault = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        templateDao.insertTemplate(copy)
    }

    suspend fun deleteTemplate(template: ModelingTemplateEntity) = withContext(Dispatchers.IO) {
        if (!template.isBuiltIn) {
            templateDao.deleteTemplate(template)
        }
    }

    suspend fun setDefaultTemplate(id: Long) = withContext(Dispatchers.IO) {
        templateDao.setDefaultTemplate(id)
    }

    suspend fun importTemplates(templates: List<ModelingTemplateEntity>) = withContext(Dispatchers.IO) {
        templateDao.insertTemplates(templates)
    }

    // --- Session Recovery Operations ---
    /**
     * Reconciles a Note Editor recovery snapshot with the database's latest saved version.
     * NEVER overwrites a more recent database save with an older recovery snapshot.
     */
    suspend fun recoverNoteSessionIfApplicable(): Pair<NoteEntity, NoteEditorSessionSnapshot>? = withContext(Dispatchers.IO) {
        val snapshot = sessionManager.getNoteEditorSession() ?: return@withContext null
        val noteId = snapshot.activeNoteId ?: return@withContext null
        val dbNote = noteDao.getNoteById(noteId) ?: return@withContext null

        // Rule: Never overwrite a more recent saved version with an older recovery snapshot
        if (snapshot.timestamp > dbNote.updatedAt &&
            (snapshot.unsavedTitle != dbNote.title || snapshot.unsavedContent != dbNote.content)
        ) {
            val reconciled = dbNote.copy(
                title = snapshot.unsavedTitle,
                content = snapshot.unsavedContent,
                textDirection = snapshot.textDirection,
                textAlignment = snapshot.textAlignment,
                lastCursorPosition = snapshot.cursorPosition.coerceIn(0, snapshot.unsavedContent.length),
                lastSelectionEnd = snapshot.selectionEnd.coerceIn(0, snapshot.unsavedContent.length),
                lastScrollOffset = snapshot.verticalScrollOffset.coerceAtLeast(0),
                updatedAt = snapshot.timestamp
            )
            noteDao.updateNote(reconciled)
            reconciled to snapshot
        } else {
            // DB is already newer or identical; preserve cursor & scroll from snapshot if valid
            val updatedCursorNote = dbNote.copy(
                lastCursorPosition = snapshot.cursorPosition.coerceIn(0, dbNote.content.length),
                lastSelectionEnd = snapshot.selectionEnd.coerceIn(0, dbNote.content.length),
                lastScrollOffset = snapshot.verticalScrollOffset.coerceAtLeast(0)
            )
            updatedCursorNote to snapshot
        }
    }

    suspend fun getModelingSessionSnapshot(): ModelingSessionSnapshot? = withContext(Dispatchers.IO) {
        sessionManager.getModelingSession()
    }

    // --- Backup & Restore Operations ---
    suspend fun createBackupNow(isAuto: Boolean = false): BackupMetadata =
        backupManager.createLocalBackup(isAuto = isAuto)

    suspend fun listBackups(): List<BackupMetadata> =
        backupManager.listLocalBackups()

    suspend fun readBackupPayload(filePath: String): BackupPayload =
        backupManager.readBackupPayloadFromFile(filePath)

    suspend fun restoreBackup(payload: BackupPayload, selection: RestoreSelection) {
        backupManager.restoreFromPayload(payload, selection)
        ensureDefaultsInitialized()
    }
}
