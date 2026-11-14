package com.example.modeling

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ModelingUiState
import com.example.database.ModelingTemplateEntity
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalNovaArabic
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun ModelingScreen(
    modelingState: ModelingUiState,
    templates: List<ModelingTemplateEntity>,
    onInputChanged: (String) -> Unit,
    onSingleRecordEdited: (Int, String) -> Unit,
    onSelectTemplate: (ModelingTemplateEntity) -> Unit,
    onUpdateConfig: ((ModelingTemplateEntity) -> ModelingTemplateEntity) -> Unit,
    onSelectSubTab: (Int) -> Unit,
    onProcessNow: () -> Unit,
    onStartNewSession: () -> Unit,
    onSaveOutputAsNote: () -> Unit,
    onSaveCustomTemplate: (ModelingTemplateEntity) -> Unit,
    onDuplicateTemplate: (ModelingTemplateEntity) -> Unit,
    onDeleteTemplate: (ModelingTemplateEntity) -> Unit,
    onSetDefaultTemplate: (ModelingTemplateEntity) -> Unit,
    onExportModelingResult: (String) -> Unit,
    onExportTemplatesJson: () -> Unit,
    onImportTemplatesDialogRequest: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val isArabic = LocalNovaArabic.current

    val clipboard = LocalClipboardManager.current
    val result = modelingState.processResult
    val activeTemplate = modelingState.selectedTemplate

    var editingLineRecord by remember { mutableStateOf<ParsedRecord?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("modeling_screen")
    ) {
        // Top Bar: Modeling Title, Session Actions, Template Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MODELING ENGINE",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Valid: ${result.validCount} • Invalid: ${result.invalidCount} • Dup: ${result.duplicateCount} • Groups: ${result.groups.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onStartNewSession,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("modeling_new_session_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Button(
                    onClick = onProcessNow,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("modeling_process_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Process", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Template Selector Horizontal Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Template:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            templates.forEach { tmpl ->
                val selected = activeTemplate.id == tmpl.id && activeTemplate.name == tmpl.name
                FilterChip(
                    selected = selected,
                    onClick = { onSelectTemplate(tmpl) },
                    label = { Text("${tmpl.name} (${tmpl.category})") },
                    leadingIcon = if (tmpl.isDefault) {
                        { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("select_template_${tmpl.id}")
                )
            }
        }

        // Validation Error Banner if Custom Template has invalid syntax
        if (result.templateValidationError != null) {
            Surface(
                color = StatusError.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, StatusError),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("template_validation_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = result.templateValidationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Sub-navigation Tabs: 0 = Input & Config, 1 = Live Preview & Diagnostics, 2 = Custom Template Builder
        TabRow(
            selectedTabIndex = modelingState.activeSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (modelingState.activeSubTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[modelingState.activeSubTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            Tab(
                selected = modelingState.activeSubTab == 0,
                onClick = { onSelectSubTab(0) },
                text = { Text("Input & Controls") },
                modifier = Modifier.testTag("modeling_tab_input")
            )
            Tab(
                selected = modelingState.activeSubTab == 1,
                onClick = { onSelectSubTab(1) },
                text = { Text("Live Preview (${result.groups.size})") },
                modifier = Modifier.testTag("modeling_tab_preview")
            )
            Tab(
                selected = modelingState.activeSubTab == 2,
                onClick = { onSelectSubTab(2) },
                text = { Text("Template Builder") },
                modifier = Modifier.testTag("modeling_tab_builder")
            )
        }

        // Responsive Split View on Expanded/Tablet screens or Tabbed View on Compact screens
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWideScreen = maxWidth >= 720.dp

            if (modelingState.activeSubTab == 2) {
                CustomTemplateBuilderPanel(
                    activeTemplate = activeTemplate,
                    onUpdateConfig = onUpdateConfig,
                    onSaveCustomTemplate = onSaveCustomTemplate,
                    onDuplicateTemplate = onDuplicateTemplate,
                    onDeleteTemplate = onDeleteTemplate,
                    onSetDefaultTemplate = onSetDefaultTemplate,
                    onExportTemplatesJson = onExportTemplatesJson,
                    onImportTemplatesDialogRequest = onImportTemplatesDialogRequest
                )
            } else if (isWideScreen) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ModelingInputAndConfigPanel(
                            modelingState = modelingState,
                            onInputChanged = onInputChanged,
                            onUpdateConfig = onUpdateConfig,
                            onPasteClipboard = {
                                val clipText = clipboard.getText()?.text.orEmpty()
                                if (clipText.isNotEmpty()) {
                                    val combined = if (modelingState.rawInput.isBlank()) clipText else "${modelingState.rawInput}\n$clipText"
                                    onInputChanged(combined)
                                }
                            },
                            onEditSingleRecord = { editingLineRecord = it }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colorScheme.outline)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ModelingLivePreviewPanel(
                            result = result,
                            onCopyAll = {
                                clipboard.setText(AnnotatedString(result.fullOutput))
                                onShowMessage("Copied complete Modeling output (${result.fullOutput.length} chars)")
                            },
                            onCopyGroup = { grp ->
                                clipboard.setText(AnnotatedString(grp.formattedText))
                                onShowMessage("Copied Group #${grp.groupNumber} (${grp.dateString})")
                            },
                            onSaveAsNote = onSaveOutputAsNote,
                            showExportMenu = showExportMenu,
                            onToggleExportMenu = { showExportMenu = it },
                            onExportFormat = onExportModelingResult
                        )
                    }
                }
            } else {
                if (modelingState.activeSubTab == 0) {
                    ModelingInputAndConfigPanel(
                        modelingState = modelingState,
                        onInputChanged = onInputChanged,
                        onUpdateConfig = onUpdateConfig,
                        onPasteClipboard = {
                            val clipText = clipboard.getText()?.text.orEmpty()
                            if (clipText.isNotEmpty()) {
                                val combined = if (modelingState.rawInput.isBlank()) clipText else "${modelingState.rawInput}\n$clipText"
                                onInputChanged(combined)
                            }
                        },
                        onEditSingleRecord = { editingLineRecord = it }
                    )
                } else {
                    ModelingLivePreviewPanel(
                        result = result,
                        onCopyAll = {
                            clipboard.setText(AnnotatedString(result.fullOutput))
                            onShowMessage("Copied complete Modeling output")
                        },
                        onCopyGroup = { grp ->
                            clipboard.setText(AnnotatedString(grp.formattedText))
                            onShowMessage("Copied Group #${grp.groupNumber}")
                        },
                        onSaveAsNote = onSaveOutputAsNote,
                        showExportMenu = showExportMenu,
                        onToggleExportMenu = { showExportMenu = it },
                        onExportFormat = onExportModelingResult
                    )
                }
            }
        }
    }

    editingLineRecord?.let { rec ->
        var updatedLine by remember(rec) { mutableStateOf(rec.rawLine) }
        AlertDialog(
            onDismissRequest = { editingLineRecord = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Edit Record Line #${rec.originalIndex}", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = updatedLine,
                    onValueChange = { updatedLine = it },
                    label = { Text("Record Content") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_single_record_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSingleRecordEdited(rec.originalIndex, updatedLine)
                        editingLineRecord = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background)
                ) {
                    Text("Update & Reprocess", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLineRecord = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun ModelingInputAndConfigPanel(
    modelingState: ModelingUiState,
    onInputChanged: (String) -> Unit,
    onUpdateConfig: ((ModelingTemplateEntity) -> ModelingTemplateEntity) -> Unit,
    onPasteClipboard: () -> Unit,
    onEditSingleRecord: (ParsedRecord) -> Unit
) {
    val tmpl = modelingState.selectedTemplate
    val result = modelingState.processResult
    val flaggedRecords = remember(result.records) {
        result.records.filter { !it.isEmpty && (it.isInvalid || it.isDuplicate) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick Action Row: Paste, Sample 50, Sample 1000, Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Structured Input (${result.totalInputLines} lines)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onPasteClipboard,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("modeling_paste_button")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(
                    onClick = {
                        val batch = (1..50).joinToString("\n") { "account$it@nova.org:KeyPass#$it" }
                        onInputChanged(batch)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("modeling_load_50_button")
                ) {
                    Text("Load 50", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(
                    onClick = { onInputChanged("") },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("modeling_clear_input_button")
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall, color = StatusError)
                }
            }
        }

        // Multiline Input Field
        OutlinedTextField(
            value = modelingState.rawInput,
            onValueChange = onInputChanged,
            placeholder = {
                Text(
                    "Enter structured records (e.g. email:password), one per line…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            ),
            minLines = 7,
            maxLines = 12,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("modeling_raw_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // Highlighted Invalid & Duplicate Records Inspector (allows individual record editing)
        if (flaggedRecords.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Validation & Duplicate Diagnostics (Tap to edit record)",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusWarning
                    )
                    flaggedRecords.take(8).forEach { rec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditSingleRecord(rec) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "L${rec.originalIndex}: ${rec.rawLine}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (rec.isInvalid) StatusError else StatusWarning,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = when {
                                    rec.isInvalid -> "INVALID"
                                    rec.isDuplicate -> "DUPLICATE"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (rec.isInvalid) StatusError else StatusWarning
                            )
                        }
                    }
                }
            }
        }

        // Configuration Controls Card: Date, Sorting, Grouping, Numbering
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Live Modeling Configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                // Date Configuration Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tmpl.startDateStr,
                        onValueChange = { newDate -> onUpdateConfig { it.copy(startDateStr = newDate) } },
                        label = { Text("Start Date (DD/MM/YYYY)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("modeling_start_date_input")
                    )

                    FilterChip(
                        selected = tmpl.dateMode.uppercase() == "INCREMENT_DAY",
                        onClick = {
                            val nextMode = if (tmpl.dateMode.uppercase() == "INCREMENT_DAY") "FIXED" else "INCREMENT_DAY"
                            onUpdateConfig { it.copy(dateMode = nextMode) }
                        },
                        label = {
                            Text(if (tmpl.dateMode.uppercase() == "INCREMENT_DAY") "+1 Day / Group" else "Fixed Date")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("modeling_date_mode_toggle")
                    )
                }

                // Group Date Header & Unified Single-List Options Row
                Text(
                    "Group Date Header & Single-List Output Options",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = tmpl.showDateOnEveryGroup,
                        onClick = { onUpdateConfig { it.copy(showDateOnEveryGroup = !it.showDateOnEveryGroup) } },
                        label = { Text("Date [ ${tmpl.startDateStr} ] on Every Group") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("modeling_date_every_group_toggle")
                    )

                    FilterChip(
                        selected = tmpl.combineGroupsIntoOneList,
                        onClick = { onUpdateConfig { it.copy(combineGroupsIntoOneList = !it.combineGroupsIntoOneList) } },
                        label = { Text("Groups on One List") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("modeling_combine_one_list_toggle")
                    )
                }

                // Sorting Controls
                Text("Record Ordering & Sorting", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ORIGINAL" to "Preserve Original",
                        "ASC" to "A → Z",
                        "DESC" to "Z → A",
                        "REVERSE" to "Reverse Order"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = tmpl.sortMode.uppercase() == mode,
                            onClick = { onUpdateConfig { it.copy(sortMode = mode) } },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("modeling_sort_$mode")
                        )
                    }
                }

                // Grouping Size (shown for all templates so Sequential List can also group by N records with dates)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tmpl.recordsPerGroup.toString(),
                        onValueChange = { v ->
                            val parsed = v.toIntOrNull()
                            if (parsed != null && parsed >= 1) {
                                onUpdateConfig { it.copy(recordsPerGroup = parsed) }
                            }
                        },
                        label = { Text("Records/Group") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("modeling_records_per_group_input")
                    )
                    if (tmpl.templateType.uppercase() != "SEQUENTIAL_LIST") {
                        OutlinedTextField(
                            value = tmpl.parentIndexInGroup.toString(),
                            onValueChange = { v ->
                                val parsed = v.toIntOrNull()
                                if (parsed != null && parsed >= 0) {
                                    onUpdateConfig { it.copy(parentIndexInGroup = parsed) }
                                }
                            },
                            label = { Text("Parent Index (0-based)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("modeling_parent_index_input")
                        )
                    }
                }

                if (tmpl.templateType.uppercase() != "SEQUENTIAL_LIST") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tmpl.parentLabel,
                            onValueChange = { v -> onUpdateConfig { it.copy(parentLabel = v) } },
                            label = { Text("Parent Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("modeling_parent_label_input")
                        )
                        OutlinedTextField(
                            value = tmpl.childLabel,
                            onValueChange = { v -> onUpdateConfig { it.copy(childLabel = v) } },
                            label = { Text("Child Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("modeling_child_label_input")
                        )
                    }
                }

                // Numbering & Separator Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tmpl.startNumber.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { num -> onUpdateConfig { it.copy(startNumber = num) } }
                        },
                        label = { Text("Start #") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("modeling_start_number_input")
                    )
                    OutlinedTextField(
                        value = tmpl.endNumber?.toString() ?: "",
                        onValueChange = { v ->
                            val end = if (v.isBlank()) null else v.toIntOrNull()
                            onUpdateConfig { it.copy(endNumber = end) }
                        },
                        label = { Text("End # (Optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("modeling_end_number_input")
                    )
                }

                OutlinedTextField(
                    value = tmpl.separatorLine,
                    onValueChange = { v -> onUpdateConfig { it.copy(separatorLine = v) } },
                    label = { Text("Custom Divider / Separator Line") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("modeling_separator_input")
                )

                // Checkboxes: Arabic Numerals, Remove Empty Lines, Restart Numbering
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = tmpl.useArabicNumerals,
                            onCheckedChange = { c -> onUpdateConfig { it.copy(useArabicNumerals = c) } },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("modeling_arabic_numerals_checkbox")
                        )
                        Text("Arabic Numerals (١٢٣)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = tmpl.removeEmptyLines,
                            onCheckedChange = { c -> onUpdateConfig { it.copy(removeEmptyLines = c) } },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("modeling_remove_empty_checkbox")
                        )
                        Text("Ignore Empty Lines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = tmpl.restartNumberingPerGroup,
                            onCheckedChange = { c -> onUpdateConfig { it.copy(restartNumberingPerGroup = c) } },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("modeling_restart_numbering_checkbox")
                        )
                        Text("Restart # Per Group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelingLivePreviewPanel(
    result: ModelingProcessResult,
    onCopyAll: () -> Unit,
    onCopyGroup: (ModelingGroupOutput) -> Unit,
    onSaveAsNote: () -> Unit,
    showExportMenu: Boolean,
    onToggleExportMenu: (Boolean) -> Unit,
    onExportFormat: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("modeling_live_preview_panel")
    ) {
        // Action Bar: Copy All, Save as Note, Export
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Output (${result.groups.size} groups • ${result.processingTimeMs} ms)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onCopyAll,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("modeling_copy_all_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy All", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                OutlinedButton(
                    onClick = onSaveAsNote,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("modeling_save_as_note_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Note", style = MaterialTheme.typography.labelSmall, color = StatusSuccess)
                }

                Box {
                    OutlinedButton(
                        onClick = { onToggleExportMenu(true) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("modeling_export_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { onToggleExportMenu(false) },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        listOf("TXT", "PDF", "JSON", "CSV").forEach { fmt ->
                            DropdownMenuItem(
                                text = { Text("Export Result as $fmt", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    onToggleExportMenu(false)
                                    onExportFormat(fmt)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (result.groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Enter records in the Input panel to see real-time formatted output.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("modeling_groups_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                items(result.groups, key = { it.groupNumber }) { group ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("modeling_group_card_${group.groupNumber}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Group #${group.groupNumber} • Date: ${group.dateString}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { onCopyGroup(group) },
                                    modifier = Modifier
                                        .size(30.dp)
                                        .testTag("copy_group_${group.groupNumber}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Group",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = group.formattedText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomTemplateBuilderPanel(
    activeTemplate: ModelingTemplateEntity,
    onUpdateConfig: ((ModelingTemplateEntity) -> ModelingTemplateEntity) -> Unit,
    onSaveCustomTemplate: (ModelingTemplateEntity) -> Unit,
    onDuplicateTemplate: (ModelingTemplateEntity) -> Unit,
    onDeleteTemplate: (ModelingTemplateEntity) -> Unit,
    onSetDefaultTemplate: (ModelingTemplateEntity) -> Unit,
    onExportTemplatesJson: () -> Unit,
    onImportTemplatesDialogRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("custom_template_builder_panel"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Custom Template Visual Builder", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Click any placeholder chip below to insert into patterns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onImportTemplatesDialogRequest, modifier = Modifier.testTag("import_templates_button")) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Import Templates", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onExportTemplatesJson, modifier = Modifier.testTag("export_templates_button")) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export Templates", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Placeholder Quick-Insert Reference Chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ModelingEngine.SUPPORTED_PLACEHOLDERS.forEach { token ->
                Surface(
                    onClick = {
                        onUpdateConfig {
                            it.copy(
                                templateType = "CUSTOM",
                                itemPattern = "${it.itemPattern} $token".trim()
                            )
                        }
                    },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = token,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = activeTemplate.name,
                onValueChange = { v -> onUpdateConfig { it.copy(name = v) } },
                label = { Text("Template Name") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("builder_template_name_input")
            )
            OutlinedTextField(
                value = activeTemplate.category,
                onValueChange = { v -> onUpdateConfig { it.copy(category = v) } },
                label = { Text("Category") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("builder_template_category_input")
            )
        }

        // Pattern Mode Selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("PARENT_CHILDREN", "SEQUENTIAL_LIST", "CUSTOM").forEach { type ->
                FilterChip(
                    selected = activeTemplate.templateType.uppercase() == type,
                    onClick = { onUpdateConfig { it.copy(templateType = type) } },
                    label = { Text(type) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        OutlinedTextField(
            value = activeTemplate.headerPattern,
            onValueChange = { v -> onUpdateConfig { it.copy(headerPattern = v, templateType = "CUSTOM") } },
            label = { Text("Group Header Pattern (supports {date}, {group_number}, {separator})") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("builder_header_pattern_input")
        )

        OutlinedTextField(
            value = activeTemplate.parentPattern,
            onValueChange = { v -> onUpdateConfig { it.copy(parentPattern = v, templateType = "CUSTOM") } },
            label = { Text("Parent Record Pattern (supports {parent_label}, {parent}, {separator})") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("builder_parent_pattern_input")
        )

        OutlinedTextField(
            value = activeTemplate.itemPattern,
            onValueChange = { v -> onUpdateConfig { it.copy(itemPattern = v, templateType = "CUSTOM") } },
            label = { Text("Item / Child Pattern (supports {number}, {child_label}, {child}, {record})") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("builder_item_pattern_input")
        )

        OutlinedTextField(
            value = activeTemplate.footerPattern,
            onValueChange = { v -> onUpdateConfig { it.copy(footerPattern = v, templateType = "CUSTOM") } },
            label = { Text("Group Footer Pattern (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("builder_footer_pattern_input")
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = activeTemplate.prefixText,
                onValueChange = { v -> onUpdateConfig { it.copy(prefixText = v) } },
                label = { Text("Record Prefix") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = activeTemplate.suffixText,
                onValueChange = { v -> onUpdateConfig { it.copy(suffixText = v) } },
                label = { Text("Record Suffix") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Template Management Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val toSave = if (activeTemplate.isBuiltIn) {
                        activeTemplate.copy(id = 0L, isBuiltIn = false, name = "${activeTemplate.name} Custom")
                    } else {
                        activeTemplate
                    }
                    onSaveCustomTemplate(toSave)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.testTag("builder_save_template_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Template", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    val fresh = ModelingTemplateEntity(
                        id = 0L,
                        name = "Custom Template ${(10..99).random()}",
                        category = "Custom",
                        templateType = "CUSTOM",
                        isBuiltIn = false
                    )
                    onSaveCustomTemplate(fresh)
                },
                modifier = Modifier.testTag("builder_new_template_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create New", color = MaterialTheme.colorScheme.primary)
            }

            OutlinedButton(
                onClick = { onDuplicateTemplate(activeTemplate) },
                modifier = Modifier.testTag("builder_duplicate_template_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Duplicate", color = MaterialTheme.colorScheme.onSurface)
            }

            OutlinedButton(
                onClick = { onSetDefaultTemplate(activeTemplate) },
                modifier = Modifier.testTag("builder_set_default_button")
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Set Default", color = MaterialTheme.colorScheme.primary)
            }

            if (!activeTemplate.isBuiltIn) {
                OutlinedButton(
                    onClick = { onDeleteTemplate(activeTemplate) },
                    modifier = Modifier.testTag("builder_delete_template_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = StatusError)
                }
            }
        }
        Spacer(modifier = Modifier.height(72.dp))
    }
}
