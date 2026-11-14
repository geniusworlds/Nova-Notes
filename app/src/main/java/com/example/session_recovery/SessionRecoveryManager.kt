package com.example.session_recovery

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

val Context.novaDataStore: DataStore<Preferences> by preferencesDataStore(name = "nova_preferences")

data class NoteEditorSessionSnapshot(
    val activeNoteId: Long?,
    val unsavedTitle: String,
    val unsavedContent: String,
    val cursorPosition: Int,
    val selectionEnd: Int,
    val verticalScrollOffset: Int,
    val horizontalScrollOffset: Int,
    val textDirection: String,
    val textAlignment: String,
    val timestamp: Long,
    val checksum: String
) {
    fun isValid(): Boolean {
        if (activeNoteId == null || activeNoteId <= 0L) return false
        val expected = SessionRecoveryManager.computeChecksum("$activeNoteId|$unsavedTitle|$unsavedContent|$cursorPosition|$timestamp")
        return checksum == expected
    }
}

data class ModelingSessionSnapshot(
    val rawInput: String,
    val selectedTemplateId: Long,
    val sortMode: String,
    val startDateStr: String,
    val dateMode: String,
    val startNumber: Int,
    val endNumber: Int?,
    val recordsPerGroup: Int,
    val parentIndexInGroup: Int,
    val parentLabel: String,
    val childLabel: String,
    val separatorLine: String,
    val useArabicNumerals: Boolean,
    val removeEmptyLines: Boolean,
    val activeTabIndex: Int,
    val lastCursorPosition: Int,
    val timestamp: Long,
    val checksum: String
) {
    fun isValid(): Boolean {
        val expected = SessionRecoveryManager.computeChecksum("$selectedTemplateId|$rawInput|$startDateStr|$dateMode|$timestamp")
        return checksum == expected
    }
}

data class AppSettingsState(
    val autoBackupEnabled: Boolean = true,
    val backupFrequency: String = "DAILY", // DAILY, WEEKLY
    val backupRetentionCount: Int = 5,
    val lastBackupTimestamp: Long = 0L,
    val lastBackupSizeBytes: Long = 0L,
    val customBackupDirectoryUri: String = "",
    val pinLockEnabled: Boolean = false,
    val pinHash: String = "",
    val encryptBackups: Boolean = false,
    val defaultTextDirection: String = "AUTO"
)

class SessionRecoveryManager(private val dataStore: DataStore<Preferences>) {

    companion object {
        // Note Editor Recovery Keys
        private val KEY_NOTE_ID = longPreferencesKey("recovery_note_id")
        private val KEY_NOTE_TITLE = stringPreferencesKey("recovery_note_title")
        private val KEY_NOTE_CONTENT = stringPreferencesKey("recovery_note_content")
        private val KEY_NOTE_CURSOR = intPreferencesKey("recovery_note_cursor")
        private val KEY_NOTE_SELECTION_END = intPreferencesKey("recovery_note_sel_end")
        private val KEY_NOTE_V_SCROLL = intPreferencesKey("recovery_note_v_scroll")
        private val KEY_NOTE_H_SCROLL = intPreferencesKey("recovery_note_h_scroll")
        private val KEY_NOTE_DIR = stringPreferencesKey("recovery_note_dir")
        private val KEY_NOTE_ALIGN = stringPreferencesKey("recovery_note_align")
        private val KEY_NOTE_TS = longPreferencesKey("recovery_note_ts")
        private val KEY_NOTE_CHECKSUM = stringPreferencesKey("recovery_note_checksum")

        // Modeling Session Recovery Keys
        private val KEY_MOD_INPUT = stringPreferencesKey("recovery_mod_input")
        private val KEY_MOD_TEMPLATE_ID = longPreferencesKey("recovery_mod_template_id")
        private val KEY_MOD_SORT = stringPreferencesKey("recovery_mod_sort")
        private val KEY_MOD_START_DATE = stringPreferencesKey("recovery_mod_start_date")
        private val KEY_MOD_DATE_MODE = stringPreferencesKey("recovery_mod_date_mode")
        private val KEY_MOD_START_NUM = intPreferencesKey("recovery_mod_start_num")
        private val KEY_MOD_END_NUM = intPreferencesKey("recovery_mod_end_num")
        private val KEY_MOD_RECORDS_PER_GROUP = intPreferencesKey("recovery_mod_rec_per_group")
        private val KEY_MOD_PARENT_IDX = intPreferencesKey("recovery_mod_parent_idx")
        private val KEY_MOD_PARENT_LABEL = stringPreferencesKey("recovery_mod_parent_label")
        private val KEY_MOD_CHILD_LABEL = stringPreferencesKey("recovery_mod_child_label")
        private val KEY_MOD_SEPARATOR = stringPreferencesKey("recovery_mod_separator")
        private val KEY_MOD_ARABIC_NUM = booleanPreferencesKey("recovery_mod_arabic_num")
        private val KEY_MOD_REMOVE_EMPTY = booleanPreferencesKey("recovery_mod_remove_empty")
        private val KEY_MOD_TAB = intPreferencesKey("recovery_mod_tab")
        private val KEY_MOD_CURSOR = intPreferencesKey("recovery_mod_cursor")
        private val KEY_MOD_TS = longPreferencesKey("recovery_mod_ts")
        private val KEY_MOD_CHECKSUM = stringPreferencesKey("recovery_mod_checksum")

        // Settings & Backup Keys
        private val KEY_AUTO_BACKUP = booleanPreferencesKey("settings_auto_backup")
        private val KEY_BACKUP_FREQ = stringPreferencesKey("settings_backup_freq")
        private val KEY_BACKUP_RETENTION = intPreferencesKey("settings_backup_retention")
        private val KEY_LAST_BACKUP_TS = longPreferencesKey("settings_last_backup_ts")
        private val KEY_LAST_BACKUP_SIZE = longPreferencesKey("settings_last_backup_size")
        private val KEY_CUSTOM_BACKUP_URI = stringPreferencesKey("settings_custom_backup_uri")
        private val KEY_PIN_ENABLED = booleanPreferencesKey("settings_pin_enabled")
        private val KEY_PIN_HASH = stringPreferencesKey("settings_pin_hash")
        private val KEY_ENCRYPT_BACKUPS = booleanPreferencesKey("settings_encrypt_backups")
        private val KEY_DEFAULT_TEXT_DIR = stringPreferencesKey("settings_default_text_dir")

        fun computeChecksum(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }.take(16)
        }
    }

    val appSettingsFlow: Flow<AppSettingsState> = dataStore.data.map { prefs ->
        AppSettingsState(
            autoBackupEnabled = prefs[KEY_AUTO_BACKUP] ?: true,
            backupFrequency = prefs[KEY_BACKUP_FREQ] ?: "DAILY",
            backupRetentionCount = prefs[KEY_BACKUP_RETENTION] ?: 5,
            lastBackupTimestamp = prefs[KEY_LAST_BACKUP_TS] ?: 0L,
            lastBackupSizeBytes = prefs[KEY_LAST_BACKUP_SIZE] ?: 0L,
            customBackupDirectoryUri = prefs[KEY_CUSTOM_BACKUP_URI] ?: "",
            pinLockEnabled = prefs[KEY_PIN_ENABLED] ?: false,
            pinHash = prefs[KEY_PIN_HASH] ?: "",
            encryptBackups = prefs[KEY_ENCRYPT_BACKUPS] ?: false,
            defaultTextDirection = prefs[KEY_DEFAULT_TEXT_DIR] ?: "AUTO"
        )
    }

    suspend fun getAppSettingsSnapshot(): AppSettingsState = appSettingsFlow.first()

    suspend fun saveNoteEditorSession(
        noteId: Long,
        title: String,
        content: String,
        cursorPosition: Int,
        selectionEnd: Int,
        verticalScrollOffset: Int,
        horizontalScrollOffset: Int = 0,
        textDirection: String = "AUTO",
        textAlignment: String = "START",
        timestamp: Long = System.currentTimeMillis()
    ) {
        val checksum = computeChecksum("$noteId|$title|$content|$cursorPosition|$timestamp")
        dataStore.edit { prefs ->
            prefs[KEY_NOTE_ID] = noteId
            prefs[KEY_NOTE_TITLE] = title
            prefs[KEY_NOTE_CONTENT] = content
            prefs[KEY_NOTE_CURSOR] = cursorPosition
            prefs[KEY_NOTE_SELECTION_END] = selectionEnd
            prefs[KEY_NOTE_V_SCROLL] = verticalScrollOffset
            prefs[KEY_NOTE_H_SCROLL] = horizontalScrollOffset
            prefs[KEY_NOTE_DIR] = textDirection
            prefs[KEY_NOTE_ALIGN] = textAlignment
            prefs[KEY_NOTE_TS] = timestamp
            prefs[KEY_NOTE_CHECKSUM] = checksum
        }
    }

    suspend fun clearNoteEditorSession() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_NOTE_ID)
            prefs.remove(KEY_NOTE_TITLE)
            prefs.remove(KEY_NOTE_CONTENT)
            prefs.remove(KEY_NOTE_CURSOR)
            prefs.remove(KEY_NOTE_SELECTION_END)
            prefs.remove(KEY_NOTE_V_SCROLL)
            prefs.remove(KEY_NOTE_H_SCROLL)
            prefs.remove(KEY_NOTE_TS)
            prefs.remove(KEY_NOTE_CHECKSUM)
        }
    }

    suspend fun getNoteEditorSession(): NoteEditorSessionSnapshot? {
        val prefs = dataStore.data.first()
        val id = prefs[KEY_NOTE_ID] ?: return null
        val snapshot = NoteEditorSessionSnapshot(
            activeNoteId = id,
            unsavedTitle = prefs[KEY_NOTE_TITLE] ?: "",
            unsavedContent = prefs[KEY_NOTE_CONTENT] ?: "",
            cursorPosition = prefs[KEY_NOTE_CURSOR] ?: 0,
            selectionEnd = prefs[KEY_NOTE_SELECTION_END] ?: 0,
            verticalScrollOffset = prefs[KEY_NOTE_V_SCROLL] ?: 0,
            horizontalScrollOffset = prefs[KEY_NOTE_H_SCROLL] ?: 0,
            textDirection = prefs[KEY_NOTE_DIR] ?: "AUTO",
            textAlignment = prefs[KEY_NOTE_ALIGN] ?: "START",
            timestamp = prefs[KEY_NOTE_TS] ?: 0L,
            checksum = prefs[KEY_NOTE_CHECKSUM] ?: ""
        )
        return if (snapshot.isValid()) snapshot else null
    }

    suspend fun saveModelingSession(
        rawInput: String,
        selectedTemplateId: Long,
        sortMode: String,
        startDateStr: String,
        dateMode: String,
        startNumber: Int,
        endNumber: Int?,
        recordsPerGroup: Int,
        parentIndexInGroup: Int,
        parentLabel: String,
        childLabel: String,
        separatorLine: String,
        useArabicNumerals: Boolean,
        removeEmptyLines: Boolean,
        activeTabIndex: Int = 0,
        lastCursorPosition: Int = 0,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val checksum = computeChecksum("$selectedTemplateId|$rawInput|$startDateStr|$dateMode|$timestamp")
        dataStore.edit { prefs ->
            prefs[KEY_MOD_INPUT] = rawInput
            prefs[KEY_MOD_TEMPLATE_ID] = selectedTemplateId
            prefs[KEY_MOD_SORT] = sortMode
            prefs[KEY_MOD_START_DATE] = startDateStr
            prefs[KEY_MOD_DATE_MODE] = dateMode
            prefs[KEY_MOD_START_NUM] = startNumber
            if (endNumber != null) {
                prefs[KEY_MOD_END_NUM] = endNumber
            } else {
                prefs.remove(KEY_MOD_END_NUM)
            }
            prefs[KEY_MOD_RECORDS_PER_GROUP] = recordsPerGroup
            prefs[KEY_MOD_PARENT_IDX] = parentIndexInGroup
            prefs[KEY_MOD_PARENT_LABEL] = parentLabel
            prefs[KEY_MOD_CHILD_LABEL] = childLabel
            prefs[KEY_MOD_SEPARATOR] = separatorLine
            prefs[KEY_MOD_ARABIC_NUM] = useArabicNumerals
            prefs[KEY_MOD_REMOVE_EMPTY] = removeEmptyLines
            prefs[KEY_MOD_TAB] = activeTabIndex
            prefs[KEY_MOD_CURSOR] = lastCursorPosition
            prefs[KEY_MOD_TS] = timestamp
            prefs[KEY_MOD_CHECKSUM] = checksum
        }
    }

    suspend fun getModelingSession(): ModelingSessionSnapshot? {
        val prefs = dataStore.data.first()
        val templateId = prefs[KEY_MOD_TEMPLATE_ID] ?: return null
        val snapshot = ModelingSessionSnapshot(
            rawInput = prefs[KEY_MOD_INPUT] ?: "",
            selectedTemplateId = templateId,
            sortMode = prefs[KEY_MOD_SORT] ?: "ORIGINAL",
            startDateStr = prefs[KEY_MOD_START_DATE] ?: "30/11/2008",
            dateMode = prefs[KEY_MOD_DATE_MODE] ?: "INCREMENT_DAY",
            startNumber = prefs[KEY_MOD_START_NUM] ?: 1,
            endNumber = prefs[KEY_MOD_END_NUM],
            recordsPerGroup = prefs[KEY_MOD_RECORDS_PER_GROUP] ?: 4,
            parentIndexInGroup = prefs[KEY_MOD_PARENT_IDX] ?: 0,
            parentLabel = prefs[KEY_MOD_PARENT_LABEL] ?: "Parent",
            childLabel = prefs[KEY_MOD_CHILD_LABEL] ?: "Child",
            separatorLine = prefs[KEY_MOD_SEPARATOR] ?: "-----------------------------------------------------",
            useArabicNumerals = prefs[KEY_MOD_ARABIC_NUM] ?: false,
            removeEmptyLines = prefs[KEY_MOD_REMOVE_EMPTY] ?: true,
            activeTabIndex = prefs[KEY_MOD_TAB] ?: 0,
            lastCursorPosition = prefs[KEY_MOD_CURSOR] ?: 0,
            timestamp = prefs[KEY_MOD_TS] ?: 0L,
            checksum = prefs[KEY_MOD_CHECKSUM] ?: ""
        )
        return if (snapshot.isValid()) snapshot else null
    }

    suspend fun clearModelingSession() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_MOD_INPUT)
            prefs.remove(KEY_MOD_TEMPLATE_ID)
            prefs.remove(KEY_MOD_CHECKSUM)
            prefs.remove(KEY_MOD_TS)
        }
    }

    suspend fun updateBackupSettings(
        autoBackupEnabled: Boolean? = null,
        backupFrequency: String? = null,
        retentionCount: Int? = null,
        lastBackupTimestamp: Long? = null,
        lastBackupSize: Long? = null,
        customBackupUri: String? = null,
        encryptBackups: Boolean? = null
    ) {
        dataStore.edit { prefs ->
            autoBackupEnabled?.let { prefs[KEY_AUTO_BACKUP] = it }
            backupFrequency?.let { prefs[KEY_BACKUP_FREQ] = it }
            retentionCount?.let { prefs[KEY_BACKUP_RETENTION] = it.coerceIn(1, 50) }
            lastBackupTimestamp?.let { prefs[KEY_LAST_BACKUP_TS] = it }
            lastBackupSize?.let { prefs[KEY_LAST_BACKUP_SIZE] = it }
            customBackupUri?.let { prefs[KEY_CUSTOM_BACKUP_URI] = it }
            encryptBackups?.let { prefs[KEY_ENCRYPT_BACKUPS] = it }
        }
    }

    suspend fun updateSecuritySettings(
        pinLockEnabled: Boolean,
        rawPin: String? = null
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_PIN_ENABLED] = pinLockEnabled
            if (rawPin != null) {
                prefs[KEY_PIN_HASH] = if (rawPin.isBlank()) "" else computeChecksum("PIN_SALT_$rawPin")
            }
        }
    }

    fun verifyPin(rawPin: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return true
        return computeChecksum("PIN_SALT_$rawPin") == storedHash
    }

    suspend fun restoreAppSettings(settings: AppSettingsState) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_BACKUP] = settings.autoBackupEnabled
            prefs[KEY_BACKUP_FREQ] = settings.backupFrequency
            prefs[KEY_BACKUP_RETENTION] = settings.backupRetentionCount
            prefs[KEY_ENCRYPT_BACKUPS] = settings.encryptBackups
            prefs[KEY_DEFAULT_TEXT_DIR] = settings.defaultTextDirection
        }
    }
}
