package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun getTrashedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isFavorite = 1 ORDER BY isPinned DESC, updatedAt DESC")
    fun getFavoriteNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY updatedAt DESC LIMIT 25")
    fun getRecentlyEditedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND folderId = :folderId ORDER BY isPinned DESC, updatedAt DESC")
    fun getNotesByFolder(folderId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeNoteById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSnapshot(): List<NoteEntity>

    @Query(
        """
        SELECT * FROM notes 
        WHERE isTrashed = 0 
        AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY isPinned DESC, updatedAt DESC
        """
    )
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Transaction
    suspend fun upsertNoteTransaction(note: NoteEntity): Long {
        return insertNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    @Query("UPDATE notes SET isTrashed = 1, isPinned = 0, updatedAt = :timestamp WHERE id = :noteId")
    suspend fun moveToTrash(noteId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isTrashed = 0, updatedAt = :timestamp WHERE id = :noteId")
    suspend fun restoreFromTrash(noteId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deletePermanently(noteId: Long)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    suspend fun getAllFoldersSnapshot(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachNotesFromFolder(folderId: Long)

    @Transaction
    suspend fun deleteFolderAndDetachNotes(folder: FolderEntity) {
        detachNotesFromFolder(folder.id)
        deleteFolder(folder)
    }

    @Query("DELETE FROM folders")
    suspend fun deleteAllFolders()
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY isCompleted ASC, sortOrder ASC, createdAt DESC")
    fun getAllTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos ORDER BY sortOrder ASC")
    suspend fun getAllTodosSnapshot(): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodos(todos: List<TodoEntity>)

    @Update
    suspend fun updateTodo(todo: TodoEntity)

    @Delete
    suspend fun deleteTodo(todo: TodoEntity)

    @Query("DELETE FROM todos WHERE isCompleted = 1")
    suspend fun deleteCompletedTodos()

    @Query("DELETE FROM todos")
    suspend fun deleteAllTodos()
}

@Dao
interface ModelingTemplateDao {
    @Query("SELECT * FROM modeling_templates ORDER BY isBuiltIn DESC, isDefault DESC, name ASC")
    fun getAllTemplates(): Flow<List<ModelingTemplateEntity>>

    @Query("SELECT * FROM modeling_templates ORDER BY isBuiltIn DESC, name ASC")
    suspend fun getAllTemplatesSnapshot(): List<ModelingTemplateEntity>

    @Query("SELECT * FROM modeling_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateById(id: Long): ModelingTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ModelingTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<ModelingTemplateEntity>)

    @Update
    suspend fun updateTemplate(template: ModelingTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: ModelingTemplateEntity)

    @Query("UPDATE modeling_templates SET isDefault = 0")
    suspend fun clearDefaultFlags()

    @Query("UPDATE modeling_templates SET isDefault = 1 WHERE id = :id")
    suspend fun markDefaultTemplate(id: Long)

    @Transaction
    suspend fun setDefaultTemplate(id: Long) {
        clearDefaultFlags()
        markDefaultTemplate(id)
    }

    @Query("DELETE FROM modeling_templates WHERE isBuiltIn = 0")
    suspend fun deleteAllCustomTemplates()

    @Query("DELETE FROM modeling_templates")
    suspend fun deleteAllTemplates()
}
