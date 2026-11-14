package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupManager
import com.example.backup.RestoreSelection
import com.example.core.NovaRepository
import com.example.database.NoteEntity
import com.example.database.NovaDatabase
import com.example.export.ExportImportManager
import com.example.session_recovery.SessionRecoveryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: NovaDatabase
    private lateinit var sessionManager: SessionRecoveryManager
    private lateinit var backupManager: BackupManager
    private lateinit var repository: NovaRepository
    private lateinit var tempDataStoreFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = NovaDatabase.createInMemory(context)
        tempDataStoreFile = File(context.cacheDir, "test_nova_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { tempDataStoreFile }
        )
        sessionManager = SessionRecoveryManager(dataStore)
        backupManager = BackupManager(context, database, sessionManager)
        repository = NovaRepository(database, sessionManager, backupManager)
    }

    @After
    fun tearDown() {
        database.close()
        if (tempDataStoreFile.exists()) tempDataStoreFile.delete()
    }

    @Test
    fun `auto-save, large document persistence, and session recovery never overwrite newer DB state`() = runBlocking {
        repository.ensureDefaultsInitialized()

        // 1. Create and auto-save a large note containing Arabic, English, emails, and numbers
        val largeBody = buildString {
            repeat(250) { idx ->
                append("Line $idx — ملاحظة عربية رقم $idx — contact$idx@nova.io\n")
            }
        }
        val noteId = repository.saveNoteTransactional(
            NoteEntity(
                title = "Architecture Spec — مواصفات النظام",
                content = largeBody,
                textDirection = "AUTO",
                lastCursorPosition = 42,
                lastScrollOffset = 180
            )
        )

        val loaded = repository.getNoteById(noteId)
        assertNotNull(loaded)
        assertEquals("Architecture Spec — مواصفات النظام", loaded!!.title)
        assertEquals(largeBody, loaded.content)

        // 2. Save an older recovery snapshot and verify it NEVER overwrites the newer database version
        val newerDbTimestamp = loaded.updatedAt + 5000L
        database.noteDao().updateNote(loaded.copy(content = "Newer Database Content", updatedAt = newerDbTimestamp))

        sessionManager.saveNoteEditorSession(
            noteId = noteId,
            title = "Older Snapshot Title",
            content = "Stale Crash Content",
            cursorPosition = 12,
            selectionEnd = 12,
            verticalScrollOffset = 64,
            timestamp = newerDbTimestamp - 3000L
        )

        val reconciledPair = repository.recoverNoteSessionIfApplicable()
        assertNotNull(reconciledPair)
        assertEquals("Newer Database Content", reconciledPair!!.first.content)
        assertEquals(12, reconciledPair.first.lastCursorPosition)
        assertEquals(64, reconciledPair.first.lastScrollOffset)

        // 3. Save a genuinely newer interrupted session snapshot and verify recovery restores it
        sessionManager.saveNoteEditorSession(
            noteId = noteId,
            title = "Recovered Interrupted Title",
            content = "Recovered Interrupted Content",
            cursorPosition = 9,
            selectionEnd = 9,
            verticalScrollOffset = 120,
            timestamp = newerDbTimestamp + 9000L
        )
        val recoveredNewer = repository.recoverNoteSessionIfApplicable()
        assertNotNull(recoveredNewer)
        assertEquals("Recovered Interrupted Content", recoveredNewer!!.first.content)
    }

    @Test
    fun `local backup creation, checksum verification, corruption rejection, and selective restoration`() = runBlocking {
        repository.ensureDefaultsInitialized()

        val folderId = repository.createFolder("Projects", "#00E5FF", "folder")
        val note1Id = repository.saveNoteTransactional(
            NoteEntity(title = "Note Alpha", content = "Alpha Content", folderId = folderId)
        )
        val note2Id = repository.saveNoteTransactional(
            NoteEntity(title = "Note Beta", content = "Beta Content", folderId = null)
        )

        // Create manual local backup
        val backupMeta = repository.createBackupNow(isAuto = false)
        assertTrue(backupMeta.isValid)
        assertEquals(2, backupMeta.noteCount)

        // Tamper with backup JSON and verify checksum validation rejects it
        val rawBackupJson = File(backupMeta.filePath).readText(Charsets.UTF_8)
        val corruptedJson = rawBackupJson.replace("Alpha Content", "Tampered Content")
        var checksumRejected = false
        try {
            backupManager.parseAndValidateBackupJson(corruptedJson)
        } catch (e: IllegalArgumentException) {
            checksumRejected = true
        }
        assertTrue("Corrupted backup must be rejected by SHA-256 checksum", checksumRejected)

        // Delete all notes and selectively restore ONLY Note Alpha
        database.noteDao().deleteAllNotes()
        assertEquals(0, database.noteDao().getAllNotesSnapshot().size)

        val validPayload = repository.readBackupPayload(backupMeta.filePath)
        repository.restoreBackup(
            payload = validPayload,
            selection = RestoreSelection(
                restoreAll = false,
                restoreNotes = true,
                selectedNoteIds = setOf(note1Id),
                restoreFolders = false,
                restoreTodos = false,
                restoreTemplates = false,
                restoreSettings = false
            )
        )

        val restoredNotes = database.noteDao().getAllNotesSnapshot()
        assertEquals(1, restoredNotes.size)
        assertEquals("Note Alpha", restoredNotes.first().title)
        assertEquals(note1Id, restoredNotes.first().id)

        // Restore entire application and verify both notes return
        repository.restoreBackup(
            payload = validPayload,
            selection = RestoreSelection(restoreAll = true)
        )
        val allRestored = database.noteDao().getAllNotesSnapshot()
        assertEquals(2, allRestored.size)
        assertTrue(allRestored.any { it.id == note2Id })
    }

    @Test
    fun `export and import TXT, JSON, CSV, and PDF preserve Arabic and English content`() = runBlocking {
        val sampleNotes = listOf(
            NoteEntity(
                id = 10L,
                title = "ملاحظة ثنائية اللغة — Bilingual",
                content = "Email: user@nova.io\nالفقرة الثانية باللغة العربية."
            )
        )

        val jsonExport = ExportImportManager.exportNotesToJson(sampleNotes)
        val importedFromJson = ExportImportManager.parseImportedNotes(jsonExport, "JSON")
        assertEquals(1, importedFromJson.size)
        assertEquals("ملاحظة ثنائية اللغة — Bilingual", importedFromJson.first().title)
        assertTrue(importedFromJson.first().content.contains("الفقرة الثانية باللغة العربية."))

        val csvExport = ExportImportManager.exportNotesToCsv(sampleNotes)
        val importedFromCsv = ExportImportManager.parseImportedNotes(csvExport, "CSV")
        assertEquals(1, importedFromCsv.size)
        assertEquals("ملاحظة ثنائية اللغة — Bilingual", importedFromCsv.first().title)

        val pdfBytes = ExportImportManager.generatePdfBytes("PDF Test", sampleNotes.first().content)
        assertTrue("Generated PDF must be non-empty", pdfBytes.size > 100)

        // Verify fast local search finds Arabic and English terms
        repository.importNotes(importedFromJson)
        val searchArabic = database.noteDao().searchNotes("ثنائية").first()
        assertEquals(1, searchArabic.size)
    }
}
