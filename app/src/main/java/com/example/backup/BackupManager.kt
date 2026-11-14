package com.example.backup

import android.content.Context
import com.example.database.FolderEntity
import com.example.database.ModelingTemplateEntity
import com.example.database.NoteEntity
import com.example.database.NovaDatabase
import com.example.database.TodoEntity
import com.example.session_recovery.AppSettingsState
import com.example.session_recovery.SessionRecoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupMetadata(
    val fileName: String,
    val filePath: String,
    val createdAt: Long,
    val fileSizeBytes: Long,
    val noteCount: Int,
    val folderCount: Int,
    val todoCount: Int,
    val templateCount: Int,
    val checksum: String,
    val isValid: Boolean
)

data class BackupPayload(
    val version: Int,
    val createdAt: Long,
    val checksum: String,
    val notes: List<NoteEntity>,
    val folders: List<FolderEntity>,
    val todos: List<TodoEntity>,
    val templates: List<ModelingTemplateEntity>,
    val settings: AppSettingsState
)

data class RestoreSelection(
    val restoreAll: Boolean = true,
    val restoreNotes: Boolean = true,
    val selectedNoteIds: Set<Long>? = null,
    val restoreFolders: Boolean = true,
    val selectedFolderIds: Set<Long>? = null,
    val restoreTodos: Boolean = true,
    val restoreTemplates: Boolean = true,
    val restoreSettings: Boolean = true
)

class BackupManager(
    private val context: Context,
    private val database: NovaDatabase,
    private val sessionManager: SessionRecoveryManager
) {
    private val backupMutex = Mutex()
    private val backupDir: File
        get() = File(context.filesDir, "nova_backups").apply {
            if (!exists()) mkdirs()
        }

    companion object {
        const val BACKUP_FORMAT_VERSION = 1

        fun computeSha256(content: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    suspend fun checkAndRunScheduledBackup(): BackupMetadata? = withContext(Dispatchers.IO) {
        val settings = sessionManager.getAppSettingsSnapshot()
        if (!settings.autoBackupEnabled) return@withContext null

        val now = System.currentTimeMillis()
        val intervalMs = if (settings.backupFrequency.uppercase() == "WEEKLY") {
            7L * 24L * 60L * 60L * 1000L
        } else {
            24L * 60L * 60L * 1000L
        }

        if (now - settings.lastBackupTimestamp >= intervalMs) {
            createLocalBackup(isAuto = true)
        } else {
            null
        }
    }

    suspend fun createLocalBackup(isAuto: Boolean = false): BackupMetadata = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            val timestamp = System.currentTimeMillis()
            val jsonString = serializeFullBackupJson(timestamp)

            val dateFormatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            val prefix = if (isAuto) "nova_auto_backup_" else "nova_manual_backup_"
            val finalFileName = "${prefix}${dateFormatter.format(Date(timestamp))}.json"
            val tempFile = File(backupDir, "$finalFileName.tmp")
            val finalFile = File(backupDir, finalFileName)

            tempFile.writeText(jsonString, Charsets.UTF_8)
            if (finalFile.exists()) finalFile.delete()
            val renamed = tempFile.renameTo(finalFile)
            if (!renamed) {
                finalFile.writeText(jsonString, Charsets.UTF_8)
                tempFile.delete()
            }

            val settings = sessionManager.getAppSettingsSnapshot()
            pruneOldBackups(settings.backupRetentionCount.coerceAtLeast(1))

            sessionManager.updateBackupSettings(
                lastBackupTimestamp = timestamp,
                lastBackupSize = finalFile.length()
            )

            inspectBackupFile(finalFile)
                ?: throw IllegalStateException("Backup verification failed immediately after creation")
        }
    }

    suspend fun serializeFullBackupJson(timestamp: Long = System.currentTimeMillis()): String = withContext(Dispatchers.IO) {
        val notes = database.noteDao().getAllNotesSnapshot()
        val folders = database.folderDao().getAllFoldersSnapshot()
        val todos = database.todoDao().getAllTodosSnapshot()
        val templates = database.modelingTemplateDao().getAllTemplatesSnapshot()
        val settings = sessionManager.getAppSettingsSnapshot()

        val dataObject = JSONObject().apply {
            put("notes", JSONArray().apply {
                notes.forEach { n ->
                    put(JSONObject().apply {
                        put("id", n.id)
                        put("title", n.title)
                        put("content", n.content)
                        put("folderId", n.folderId ?: JSONObject.NULL)
                        put("isPinned", n.isPinned)
                        put("isFavorite", n.isFavorite)
                        put("isTrashed", n.isTrashed)
                        put("textDirection", n.textDirection)
                        put("textAlignment", n.textAlignment)
                        put("lastCursorPosition", n.lastCursorPosition)
                        put("lastSelectionEnd", n.lastSelectionEnd)
                        put("lastScrollOffset", n.lastScrollOffset)
                        put("createdAt", n.createdAt)
                        put("updatedAt", n.updatedAt)
                    })
                }
            })
            put("folders", JSONArray().apply {
                folders.forEach { f ->
                    put(JSONObject().apply {
                        put("id", f.id)
                        put("name", f.name)
                        put("colorHex", f.colorHex)
                        put("iconName", f.iconName)
                        put("createdAt", f.createdAt)
                    })
                }
            })
            put("todos", JSONArray().apply {
                todos.forEach { t ->
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("description", t.description)
                        put("category", t.category)
                        put("isCompleted", t.isCompleted)
                        put("sortOrder", t.sortOrder)
                        put("createdAt", t.createdAt)
                        put("updatedAt", t.updatedAt)
                    })
                }
            })
            put("templates", JSONArray().apply {
                templates.forEach { tmpl ->
                    put(serializeTemplateToJson(tmpl))
                }
            })
            put("settings", JSONObject().apply {
                put("autoBackupEnabled", settings.autoBackupEnabled)
                put("backupFrequency", settings.backupFrequency)
                put("backupRetentionCount", settings.backupRetentionCount)
                put("encryptBackups", settings.encryptBackups)
                put("defaultTextDirection", settings.defaultTextDirection)
            })
        }

        val canonicalDataStr = dataObject.toString()
        val checksum = computeSha256(canonicalDataStr)

        JSONObject().apply {
            put("app", "NOVA_NOTES")
            put("version", BACKUP_FORMAT_VERSION)
            put("createdAt", timestamp)
            put("checksum", checksum)
            put("data", dataObject)
        }.toString(2)
    }

    fun serializeTemplateToJson(tmpl: ModelingTemplateEntity): JSONObject {
        return JSONObject().apply {
            put("id", tmpl.id)
            put("name", tmpl.name)
            put("category", tmpl.category)
            put("templateType", tmpl.templateType)
            put("isBuiltIn", tmpl.isBuiltIn)
            put("isDefault", tmpl.isDefault)
            put("headerPattern", tmpl.headerPattern)
            put("parentPattern", tmpl.parentPattern)
            put("itemPattern", tmpl.itemPattern)
            put("footerPattern", tmpl.footerPattern)
            put("separatorLine", tmpl.separatorLine)
            put("parentLabel", tmpl.parentLabel)
            put("childLabel", tmpl.childLabel)
            put("recordsPerGroup", tmpl.recordsPerGroup)
            put("parentIndexInGroup", tmpl.parentIndexInGroup)
            put("startNumber", tmpl.startNumber)
            put("endNumber", tmpl.endNumber ?: JSONObject.NULL)
            put("useArabicNumerals", tmpl.useArabicNumerals)
            put("restartNumberingPerGroup", tmpl.restartNumberingPerGroup)
            put("dateMode", tmpl.dateMode)
            put("startDateStr", tmpl.startDateStr)
            put("showDateOnEveryGroup", tmpl.showDateOnEveryGroup)
            put("combineGroupsIntoOneList", tmpl.combineGroupsIntoOneList)
            put("sortMode", tmpl.sortMode)
            put("removeEmptyLines", tmpl.removeEmptyLines)
            put("prefixText", tmpl.prefixText)
            put("suffixText", tmpl.suffixText)
            put("createdAt", tmpl.createdAt)
            put("updatedAt", tmpl.updatedAt)
        }
    }

    fun parseTemplateFromJson(obj: JSONObject): ModelingTemplateEntity {
        val endNum = if (obj.isNull("endNumber")) null else obj.optInt("endNumber")
        return ModelingTemplateEntity(
            id = obj.optLong("id", 0L),
            name = obj.optString("name", "Imported Template"),
            category = obj.optString("category", "Custom"),
            templateType = obj.optString("templateType", "CUSTOM"),
            isBuiltIn = obj.optBoolean("isBuiltIn", false),
            isDefault = obj.optBoolean("isDefault", false),
            headerPattern = obj.optString("headerPattern", ""),
            parentPattern = obj.optString("parentPattern", ""),
            itemPattern = obj.optString("itemPattern", "{number}: {record}"),
            footerPattern = obj.optString("footerPattern", ""),
            separatorLine = obj.optString("separatorLine", "-----------------------------------------------------"),
            parentLabel = obj.optString("parentLabel", "Parent"),
            childLabel = obj.optString("childLabel", "Child"),
            recordsPerGroup = obj.optInt("recordsPerGroup", 4).coerceAtLeast(1),
            parentIndexInGroup = obj.optInt("parentIndexInGroup", 0).coerceAtLeast(0),
            startNumber = obj.optInt("startNumber", 1),
            endNumber = endNum,
            useArabicNumerals = obj.optBoolean("useArabicNumerals", false),
            restartNumberingPerGroup = obj.optBoolean("restartNumberingPerGroup", true),
            dateMode = obj.optString("dateMode", "INCREMENT_DAY"),
            startDateStr = obj.optString("startDateStr", "30/11/2008"),
            showDateOnEveryGroup = obj.optBoolean("showDateOnEveryGroup", true),
            combineGroupsIntoOneList = obj.optBoolean("combineGroupsIntoOneList", false),
            sortMode = obj.optString("sortMode", "ORIGINAL"),
            removeEmptyLines = obj.optBoolean("removeEmptyLines", true),
            prefixText = obj.optString("prefixText", ""),
            suffixText = obj.optString("suffixText", ""),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    fun parseAndValidateBackupJson(rawJson: String): BackupPayload {
        val root = try {
            JSONObject(rawJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed JSON backup file: ${e.message}")
        }

        if (root.optString("app") != "NOVA_NOTES") {
            throw IllegalArgumentException("Invalid backup header: Not a Nova Notes backup file.")
        }

        val version = root.optInt("version", -1)
        if (version <= 0) {
            throw IllegalArgumentException("Unsupported backup version: $version")
        }

        val createdAt = root.optLong("createdAt", 0L)
        val expectedChecksum = root.optString("checksum", "")
        val dataObj = root.optJSONObject("data")
            ?: throw IllegalArgumentException("Backup file is missing 'data' payload.")

        val actualChecksum = computeSha256(dataObj.toString())
        if (expectedChecksum.isBlank() || expectedChecksum != actualChecksum) {
            throw IllegalArgumentException("Backup checksum verification failed! File may be corrupted or incomplete.")
        }

        val notesArr = dataObj.optJSONArray("notes") ?: JSONArray()
        val notes = ArrayList<NoteEntity>(notesArr.length())
        for (i in 0 until notesArr.length()) {
            val n = notesArr.getJSONObject(i)
            val fId = if (n.isNull("folderId")) null else n.optLong("folderId")
            notes.add(
                NoteEntity(
                    id = n.optLong("id", 0L),
                    title = n.optString("title", ""),
                    content = n.optString("content", ""),
                    folderId = fId,
                    isPinned = n.optBoolean("isPinned", false),
                    isFavorite = n.optBoolean("isFavorite", false),
                    isTrashed = n.optBoolean("isTrashed", false),
                    textDirection = n.optString("textDirection", "AUTO"),
                    textAlignment = n.optString("textAlignment", "START"),
                    lastCursorPosition = n.optInt("lastCursorPosition", 0),
                    lastSelectionEnd = n.optInt("lastSelectionEnd", 0),
                    lastScrollOffset = n.optInt("lastScrollOffset", 0),
                    createdAt = n.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = n.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        val foldersArr = dataObj.optJSONArray("folders") ?: JSONArray()
        val folders = ArrayList<FolderEntity>(foldersArr.length())
        for (i in 0 until foldersArr.length()) {
            val f = foldersArr.getJSONObject(i)
            folders.add(
                FolderEntity(
                    id = f.optLong("id", 0L),
                    name = f.optString("name", "Folder"),
                    colorHex = f.optString("colorHex", "#00E5FF"),
                    iconName = f.optString("iconName", "folder"),
                    createdAt = f.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        val todosArr = dataObj.optJSONArray("todos") ?: JSONArray()
        val todos = ArrayList<TodoEntity>(todosArr.length())
        for (i in 0 until todosArr.length()) {
            val t = todosArr.getJSONObject(i)
            todos.add(
                TodoEntity(
                    id = t.optLong("id", 0L),
                    title = t.optString("title", ""),
                    description = t.optString("description", ""),
                    category = t.optString("category", "General"),
                    isCompleted = t.optBoolean("isCompleted", false),
                    sortOrder = t.optInt("sortOrder", i),
                    createdAt = t.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = t.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        val templatesArr = dataObj.optJSONArray("templates") ?: JSONArray()
        val templates = ArrayList<ModelingTemplateEntity>(templatesArr.length())
        for (i in 0 until templatesArr.length()) {
            val tmpl = templatesArr.getJSONObject(i)
            templates.add(parseTemplateFromJson(tmpl))
        }

        val settingsObj = dataObj.optJSONObject("settings") ?: JSONObject()
        val settings = AppSettingsState(
            autoBackupEnabled = settingsObj.optBoolean("autoBackupEnabled", true),
            backupFrequency = settingsObj.optString("backupFrequency", "DAILY"),
            backupRetentionCount = settingsObj.optInt("backupRetentionCount", 5),
            encryptBackups = settingsObj.optBoolean("encryptBackups", false),
            defaultTextDirection = settingsObj.optString("defaultTextDirection", "AUTO")
        )

        return BackupPayload(
            version = version,
            createdAt = createdAt,
            checksum = expectedChecksum,
            notes = notes,
            folders = folders,
            todos = todos,
            templates = templates,
            settings = settings
        )
    }

    suspend fun restoreFromPayload(
        payload: BackupPayload,
        selection: RestoreSelection
    ) = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            if (selection.restoreAll || selection.restoreFolders) {
                val foldersToRestore = if (selection.selectedFolderIds != null) {
                    payload.folders.filter { it.id in selection.selectedFolderIds }
                } else {
                    payload.folders
                }
                if (selection.restoreAll && selection.selectedFolderIds == null) {
                    database.folderDao().deleteAllFolders()
                }
                database.folderDao().insertFolders(foldersToRestore)
            }

            if (selection.restoreAll || selection.restoreNotes) {
                val notesToRestore = when {
                    selection.selectedNoteIds != null -> payload.notes.filter { it.id in selection.selectedNoteIds }
                    selection.selectedFolderIds != null && !selection.restoreAll ->
                        payload.notes.filter { it.folderId != null && it.folderId in selection.selectedFolderIds }
                    else -> payload.notes
                }
                if (selection.restoreAll && selection.selectedNoteIds == null) {
                    database.noteDao().deleteAllNotes()
                }
                database.noteDao().insertNotes(notesToRestore)
            }

            if (selection.restoreAll || selection.restoreTodos) {
                if (selection.restoreAll) {
                    database.todoDao().deleteAllTodos()
                }
                database.todoDao().insertTodos(payload.todos)
            }

            if (selection.restoreAll || selection.restoreTemplates) {
                if (selection.restoreAll) {
                    database.modelingTemplateDao().deleteAllTemplates()
                }
                database.modelingTemplateDao().insertTemplates(payload.templates)
            }

            if (selection.restoreAll || selection.restoreSettings) {
                sessionManager.restoreAppSettings(payload.settings)
            }
        }
    }

    suspend fun listLocalBackups(): List<BackupMetadata> = withContext(Dispatchers.IO) {
        val files = backupDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray()
        files.mapNotNull { inspectBackupFile(it) }
            .sortedByDescending { it.createdAt }
    }

    fun inspectBackupFile(file: File): BackupMetadata? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val content = file.readText(Charsets.UTF_8)
            val payload = parseAndValidateBackupJson(content)
            BackupMetadata(
                fileName = file.name,
                filePath = file.absolutePath,
                createdAt = payload.createdAt,
                fileSizeBytes = file.length(),
                noteCount = payload.notes.size,
                folderCount = payload.folders.size,
                todoCount = payload.todos.size,
                templateCount = payload.templates.size,
                checksum = payload.checksum.take(12),
                isValid = true
            )
        } catch (_: Exception) {
            BackupMetadata(
                fileName = file.name,
                filePath = file.absolutePath,
                createdAt = file.lastModified(),
                fileSizeBytes = file.length(),
                noteCount = 0,
                folderCount = 0,
                todoCount = 0,
                templateCount = 0,
                checksum = "INVALID",
                isValid = false
            )
        }
    }

    suspend fun readBackupPayloadFromFile(filePath: String): BackupPayload = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) throw IllegalArgumentException("Backup file does not exist: $filePath")
        val content = file.readText(Charsets.UTF_8)
        parseAndValidateBackupJson(content)
    }

    suspend fun deleteBackupFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (file.exists()) file.delete() else false
    }

    suspend fun pruneOldBackups(retainCount: Int): Int = withContext(Dispatchers.IO) {
        backupDir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }

        val validFiles = backupDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: return@withContext 0

        var deleted = 0
        if (validFiles.size > retainCount) {
            for (file in validFiles.drop(retainCount)) {
                if (file.delete()) deleted++
            }
        }
        deleted
    }
}
