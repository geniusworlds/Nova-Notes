package com.example.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.backup.BackupMetadata
import com.example.backup.BackupPayload
import com.example.backup.RestoreSelection
import com.example.session_recovery.AppSettingsState
import com.example.ui.theme.CardDarkGray
import com.example.ui.theme.CardElevatedGray
import com.example.ui.theme.NovaCyan
import com.example.ui.theme.NovaDeepTeal
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettingsScreen(
    settings: AppSettingsState,
    backups: List<BackupMetadata>,
    previewPayload: BackupPayload?,
    onToggleAutoBackup: (Boolean) -> Unit,
    onChangeBackupFrequency: (String) -> Unit,
    onChangeRetentionCount: (Int) -> Unit,
    onCreateBackupNow: () -> Unit,
    onInspectBackupForRestore: (BackupMetadata) -> Unit,
    onDeleteBackup: (BackupMetadata) -> Unit,
    onExportBackupToSaf: () -> Unit,
    onImportBackupFromSaf: () -> Unit,
    onConfirmRestore: (RestoreSelection) -> Unit,
    onCloseRestorePreview: () -> Unit,
    onConfigurePinLock: (Boolean, String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale.US) }
    var showPinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("backup_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "LOCAL BACKUP & SECURITY",
            style = MaterialTheme.typography.headlineMedium,
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "100% Offline • Atomic SHA-256 Checksum Verified Backups",
            style = MaterialTheme.typography.bodySmall,
            color = NovaCyan
        )

        // Automatic Local Backup Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDarkGray),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic Local Backup", style = MaterialTheme.typography.titleMedium, color = TextWhite)
                        Text(
                            text = if (settings.lastBackupTimestamp > 0L) {
                                "Last backup: ${dateFormat.format(Date(settings.lastBackupTimestamp))} (${settings.lastBackupSizeBytes / 1024} KB)"
                            } else {
                                "No automatic backup created yet"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = settings.autoBackupEnabled,
                        onCheckedChange = onToggleAutoBackup,
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBlack, checkedTrackColor = NovaCyan),
                        modifier = Modifier.testTag("auto_backup_switch")
                    )
                }

                HorizontalDivider(color = SurfaceBorder)

                // Frequency Selector (DAILY / WEEKLY)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Backup Schedule", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("DAILY", "WEEKLY").forEach { freq ->
                            FilterChip(
                                selected = settings.backupFrequency.uppercase() == freq,
                                onClick = { onChangeBackupFrequency(freq) },
                                label = { Text(freq) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NovaDeepTeal,
                                    selectedLabelColor = NovaCyan
                                )
                            )
                        }
                    }
                }

                // Retention Versions Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Backup Versions Retained", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3, 5, 10, 20).forEach { count ->
                            FilterChip(
                                selected = settings.backupRetentionCount == count,
                                onClick = { onChangeRetentionCount(count) },
                                label = { Text("$count") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NovaDeepTeal,
                                    selectedLabelColor = NovaCyan
                                )
                            )
                        }
                    }
                }

                // Action Buttons: Create Backup Now, Export Backup, Import Backup
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCreateBackupNow,
                        colors = ButtonDefaults.buttonColors(containerColor = NovaCyan, contentColor = ObsidianBlack),
                        modifier = Modifier.testTag("create_backup_now_button")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Backup Now", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onExportBackupToSaf,
                        modifier = Modifier.testTag("export_backup_saf_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = NovaCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Backup", color = NovaCyan)
                    }

                    OutlinedButton(
                        onClick = onImportBackupFromSaf,
                        modifier = Modifier.testTag("import_backup_saf_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = TextWhite, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import Backup", color = TextWhite)
                    }
                }
            }
        }

        // Security & Privacy Card (PIN Lock)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDarkGray),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = NovaCyan)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Local PIN Protection", style = MaterialTheme.typography.titleMedium, color = TextWhite)
                        Text(
                            text = if (settings.pinLockEnabled) "PIN Lock Active on Launch" else "Optional offline PIN lock",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.testTag("configure_pin_button")
                ) {
                    Text(if (settings.pinLockEnabled) "Manage PIN" else "Set PIN", color = NovaCyan)
                }
            }
        }

        // Saved Local Backup Versions List
        Text(
            text = "Local Backup Versions (${backups.size})",
            style = MaterialTheme.typography.titleMedium,
            color = TextWhite
        )

        if (backups.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDarkGray),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No local backups stored yet. Tap 'Create Backup Now' to snapshot all notes, folders, tasks, and templates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            backups.forEach { meta ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDarkGray),
                    border = BorderStroke(
                        1.dp,
                        if (meta.isValid) NovaCyan.copy(alpha = 0.3f) else StatusError
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_item_${meta.fileName}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (meta.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (meta.isValid) StatusSuccess else StatusError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = dateFormat.format(Date(meta.createdAt)),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextWhite
                                )
                            }
                            Text(
                                text = "${(meta.fileSizeBytes / 1024).coerceAtLeast(1)} KB • SHA: ${meta.checksum}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${meta.noteCount} Notes • ${meta.folderCount} Folders • ${meta.todoCount} Tasks • ${meta.templateCount} Templates",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaCyan
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onInspectBackupForRestore(meta) },
                                enabled = meta.isValid,
                                colors = ButtonDefaults.buttonColors(containerColor = NovaDeepTeal, contentColor = NovaCyan),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("restore_backup_button_${meta.fileName}")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preview & Restore", style = MaterialTheme.typography.labelLarge)
                            }

                            IconButton(
                                onClick = { onDeleteBackup(meta) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Backup", tint = StatusError, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Selective Restore Preview & Confirmation Dialog
    if (previewPayload != null) {
        var restoreAll by remember { mutableStateOf(true) }
        var restoreNotes by remember { mutableStateOf(true) }
        var restoreFolders by remember { mutableStateOf(true) }
        var restoreTodos by remember { mutableStateOf(true) }
        var restoreTemplates by remember { mutableStateOf(true) }
        var restoreSettings by remember { mutableStateOf(true) }

        val selectedNoteIds = remember(previewPayload) {
            mutableStateListOf<Long>().apply { addAll(previewPayload.notes.map { it.id }) }
        }
        val selectedFolderIds = remember(previewPayload) {
            mutableStateListOf<Long>().apply { addAll(previewPayload.folders.map { it.id }) }
        }

        AlertDialog(
            onDismissRequest = onCloseRestorePreview,
            containerColor = CardElevatedGray,
            title = { Text("Backup Preview & Selective Restore", color = TextWhite) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = NovaDeepTeal.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Checksum Verified (${previewPayload.checksum.take(16)}…)\n" +
                                "Contains ${previewPayload.notes.size} notes, ${previewPayload.folders.size} folders, " +
                                "${previewPayload.todos.size} tasks, ${previewPayload.templates.size} templates.",
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaCyan,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = restoreAll,
                            onCheckedChange = {
                                restoreAll = it
                                if (it) {
                                    restoreNotes = true
                                    restoreFolders = true
                                    restoreTodos = true
                                    restoreTemplates = true
                                    restoreSettings = true
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = NovaCyan)
                        )
                        Text("1. Restore Entire Application", color = TextWhite, fontWeight = FontWeight.SemiBold)
                    }

                    if (!restoreAll) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = restoreFolders,
                                onCheckedChange = { restoreFolders = it },
                                colors = CheckboxDefaults.colors(checkedColor = NovaCyan)
                            )
                            Text("2. Restore Folders (${selectedFolderIds.size}/${previewPayload.folders.size})", color = TextWhite)
                        }
                        if (restoreFolders && previewPayload.folders.isNotEmpty()) {
                            previewPayload.folders.forEach { folder ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 24.dp)
                                ) {
                                    Checkbox(
                                        checked = folder.id in selectedFolderIds,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedFolderIds.add(folder.id) else selectedFolderIds.remove(folder.id)
                                        }
                                    )
                                    Text(folder.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = restoreNotes,
                                onCheckedChange = { restoreNotes = it },
                                colors = CheckboxDefaults.colors(checkedColor = NovaCyan)
                            )
                            Text("3. Restore Notes (${selectedNoteIds.size}/${previewPayload.notes.size})", color = TextWhite)
                        }
                        if (restoreNotes && previewPayload.notes.isNotEmpty()) {
                            previewPayload.notes.take(12).forEach { note ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 24.dp)
                                ) {
                                    Checkbox(
                                        checked = note.id in selectedNoteIds,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedNoteIds.add(note.id) else selectedNoteIds.remove(note.id)
                                        }
                                    )
                                    Text(
                                        note.title.ifBlank { "Untitled #${note.id}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = restoreTemplates,
                                onCheckedChange = { restoreTemplates = it },
                                colors = CheckboxDefaults.colors(checkedColor = NovaCyan)
                            )
                            Text("4. Restore Modeling Templates (${previewPayload.templates.size})", color = TextWhite)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = restoreSettings,
                                onCheckedChange = { restoreSettings = it },
                                colors = CheckboxDefaults.colors(checkedColor = NovaCyan)
                            )
                            Text("5. Restore Application Settings", color = TextWhite)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmRestore(
                            RestoreSelection(
                                restoreAll = restoreAll,
                                restoreNotes = restoreNotes,
                                selectedNoteIds = if (restoreAll) null else selectedNoteIds.toSet(),
                                restoreFolders = restoreFolders,
                                selectedFolderIds = if (restoreAll) null else selectedFolderIds.toSet(),
                                restoreTodos = restoreTodos,
                                restoreTemplates = restoreTemplates,
                                restoreSettings = restoreSettings
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaCyan, contentColor = ObsidianBlack),
                    modifier = Modifier.testTag("confirm_restore_backup_button")
                ) {
                    Text("Confirm Restore", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onCloseRestorePreview) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = CardElevatedGray,
            title = { Text("Configure Local PIN Lock", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter a 4–8 digit PIN to protect Nova Notes locally, or leave empty to disable PIN lock:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("PIN Code") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pin_code_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfigurePinLock(pinInput.length >= 4, pinInput)
                        showPinDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaCyan, contentColor = ObsidianBlack)
                ) {
                    Text(if (pinInput.length >= 4) "Enable PIN" else "Disable PIN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
