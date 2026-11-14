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
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
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
    onConfigurePinLock: (Boolean, String) -> Unit,
    isArabic: Boolean,
    isDarkTheme: Boolean,
    onLanguageChange: (Boolean) -> Unit,
    onThemeChange: (Boolean) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale.US) }
    var showPinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("backup_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (isArabic) "التخصيص والمظهر" else "Personalization", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isArabic) "المظهر" else "Appearance", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(if (isArabic) "الوضع الليلي أو النهاري" else "Choose light or dark mode", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = isDarkTheme, onCheckedChange = onThemeChange)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isArabic) "لغة التطبيق" else "App language", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(if (isArabic) "العربية" else "English", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = isArabic, onClick = { onLanguageChange(true) }, label = { Text("العربية") })
                        FilterChip(selected = !isArabic, onClick = { onLanguageChange(false) }, label = { Text("English") })
                    }
                }
            }
        }
        Text(
            text = if (isArabic) "النسخ الاحتياطي المحلي والأمان" else "LOCAL BACKUP & SECURITY",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isArabic) "يعمل دون اتصال • نسخ احتياطية موثّقة ببصمة SHA-256" else "100% Offline • Atomic SHA-256 Checksum Verified Backups",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        // Automatic Local Backup Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                        Text("Automatic Local Backup", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = if (settings.lastBackupTimestamp > 0L) {
                                "Last backup: ${dateFormat.format(Date(settings.lastBackupTimestamp))} (${settings.lastBackupSizeBytes / 1024} KB)"
                            } else {
                                "No automatic backup created yet"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoBackupEnabled,
                        onCheckedChange = onToggleAutoBackup,
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.background, checkedTrackColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("auto_backup_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Frequency Selector (DAILY / WEEKLY)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Backup Schedule", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("DAILY", "WEEKLY").forEach { freq ->
                            FilterChip(
                                selected = settings.backupFrequency.uppercase() == freq,
                                onClick = { onChangeBackupFrequency(freq) },
                                label = { Text(freq) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
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
                    Text("Backup Versions Retained", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3, 5, 10, 20).forEach { count ->
                            FilterChip(
                                selected = settings.backupRetentionCount == count,
                                onClick = { onChangeRetentionCount(count) },
                                label = { Text("$count") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background),
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
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Backup", color = MaterialTheme.colorScheme.primary)
                    }

                    OutlinedButton(
                        onClick = onImportBackupFromSaf,
                        modifier = Modifier.testTag("import_backup_saf_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import Backup", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Security & Privacy Card (PIN Lock)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Local PIN Protection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = if (settings.pinLockEnabled) "PIN Lock Active on Launch" else "Optional offline PIN lock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.testTag("configure_pin_button")
                ) {
                    Text(if (settings.pinLockEnabled) "Manage PIN" else "Set PIN", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Saved Local Backup Versions List
        Text(
            text = "Local Backup Versions (${backups.size})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (backups.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No local backups stored yet. Tap 'Create Backup Now' to snapshot all notes, folders, tasks, and templates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            backups.forEach { meta ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(
                        1.dp,
                        if (meta.isValid) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else StatusError
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${(meta.fileSizeBytes / 1024).coerceAtLeast(1)} KB • SHA: ${meta.checksum}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${meta.noteCount} Notes • ${meta.folderCount} Folders • ${meta.todoCount} Tasks • ${meta.templateCount} Templates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onInspectBackupForRestore(meta) },
                                enabled = meta.isValid,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary),
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
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Backup Preview & Selective Restore", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Checksum Verified (${previewPayload.checksum.take(16)}…)\n" +
                                "Contains ${previewPayload.notes.size} notes, ${previewPayload.folders.size} folders, " +
                                "${previewPayload.todos.size} tasks, ${previewPayload.templates.size} templates.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
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
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text("1. Restore Entire Application", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }

                    if (!restoreAll) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = restoreFolders,
                                onCheckedChange = { restoreFolders = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text("2. Restore Folders (${selectedFolderIds.size}/${previewPayload.folders.size})", color = MaterialTheme.colorScheme.onSurface)
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
                                    Text(folder.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = restoreNotes,
                                onCheckedChange = { restoreNotes = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text("3. Restore Notes (${selectedNoteIds.size}/${previewPayload.notes.size})", color = MaterialTheme.colorScheme.onSurface)
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = restoreTemplates,
                                onCheckedChange = { restoreTemplates = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text("4. Restore Modeling Templates (${previewPayload.templates.size})", color = MaterialTheme.colorScheme.onSurface)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = restoreSettings,
                                onCheckedChange = { restoreSettings = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text("5. Restore Application Settings", color = MaterialTheme.colorScheme.onSurface)
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.testTag("confirm_restore_backup_button")
                ) {
                    Text("Confirm Restore", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onCloseRestorePreview) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Configure Local PIN Lock", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter a 4–8 digit PIN to protect Nova Notes locally, or leave empty to disable PIN lock:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background)
                ) {
                    Text(if (pinInput.length >= 4) "Enable PIN" else "Disable PIN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
