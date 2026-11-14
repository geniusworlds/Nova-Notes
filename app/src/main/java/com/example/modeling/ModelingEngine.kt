package com.example.modeling

import com.example.core.BidiTextHelper
import com.example.database.ModelingTemplateEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class ParsedRecord(
    val originalIndex: Int,
    val rawLine: String,
    val normalizedLine: String,
    val isEmpty: Boolean,
    val isInvalid: Boolean,
    val invalidReason: String? = null,
    val isDuplicate: Boolean = false
)

data class ModelingGroupOutput(
    val groupNumber: Int,
    val dateString: String,
    val formattedText: String,
    val recordIndices: List<Int>
)

data class ModelingProcessResult(
    val records: List<ParsedRecord>,
    val totalInputLines: Int,
    val validCount: Int,
    val invalidCount: Int,
    val duplicateCount: Int,
    val emptyLineCount: Int,
    val groups: List<ModelingGroupOutput>,
    val fullOutput: String,
    val templateValidationError: String? = null,
    val processingTimeMs: Long = 0L
)

object ModelingDateHelper {
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        .withResolverStyle(ResolverStyle.STRICT)

    fun parseDateOrDefault(dateStr: String, fallback: LocalDate = LocalDate.of(2008, 11, 30)): LocalDate {
        val cleaned = dateStr.trim()
        val parts = cleaned.split("/", "-", ".")
        if (parts.size == 3) {
            val d = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val y = parts[2].toIntOrNull()
            if (d != null && m != null && y != null) {
                return try {
                    LocalDate.of(y, m, d)
                } catch (_: Exception) {
                    fallback
                }
            }
        }
        return try {
            LocalDate.parse(cleaned, formatter)
        } catch (_: Exception) {
            fallback
        }
    }

    fun isValidDateString(dateStr: String): Boolean {
        val cleaned = dateStr.trim()
        val parts = cleaned.split("/", "-", ".")
        if (parts.size != 3) return false
        val d = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val y = parts[2].toIntOrNull() ?: return false
        if (y < 1000 || y > 9999) return false
        return try {
            LocalDate.of(y, m, d)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun formatDate(date: LocalDate): String {
        return "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
    }

    fun calculateGroupDate(startDateStr: String, groupIndexZeroBased: Int, incrementMode: String): String {
        val baseDate = parseDateOrDefault(startDateStr)
        val targetDate = if (incrementMode.uppercase() == "INCREMENT_DAY") {
            baseDate.plusDays(groupIndexZeroBased.toLong())
        } else {
            baseDate
        }
        return formatDate(targetDate)
    }
}

object ModelingEngine {

    val SUPPORTED_PLACEHOLDERS = setOf(
        "{number}",
        "{date}",
        "{record}",
        "{parent}",
        "{child}",
        "{separator}",
        "{group_number}",
        "{parent_label}",
        "{child_label}"
    )

    private val placeholderRegex = Regex("\\{[a-zA-Z0-9_]+\\}")

    fun validateTemplate(template: ModelingTemplateEntity): String? {
        if (template.name.isBlank()) {
            return "Template name cannot be empty."
        }
        if (template.recordsPerGroup < 1) {
            return "Records per group must be at least 1."
        }
        if (template.endNumber != null && template.endNumber < template.startNumber) {
            return "Ending number (${template.endNumber}) cannot be less than starting number (${template.startNumber})."
        }
        if (!ModelingDateHelper.isValidDateString(template.startDateStr)) {
            return "Invalid starting date '${template.startDateStr}'. Expected valid DD/MM/YYYY calendar date."
        }

        val patternsToInspect = listOf(
            "Header" to template.headerPattern,
            "Parent Pattern" to template.parentPattern,
            "Item Pattern" to template.itemPattern,
            "Footer" to template.footerPattern
        )

        for ((label, pattern) in patternsToInspect) {
            val openBraces = pattern.count { it == '{' }
            val closeBraces = pattern.count { it == '}' }
            if (openBraces != closeBraces) {
                return "$label contains unbalanced placeholder braces '{' and '}'."
            }
            val matches = placeholderRegex.findAll(pattern).map { it.value }.toList()
            for (token in matches) {
                if (token !in SUPPORTED_PLACEHOLDERS) {
                    return "$label contains unsupported placeholder '$token'. Allowed: ${SUPPORTED_PLACEHOLDERS.joinToString(", ")}"
                }
            }
        }

        if (template.templateType == "CUSTOM" && template.itemPattern.isBlank() && template.parentPattern.isBlank()) {
            return "Custom template must define at least an Item Pattern or Parent Pattern."
        }

        return null
    }

    fun process(
        rawInput: String,
        template: ModelingTemplateEntity
    ): ModelingProcessResult {
        val startTime = System.currentTimeMillis()

        val validationError = validateTemplate(template)
        if (validationError != null) {
            return ModelingProcessResult(
                records = emptyList(),
                totalInputLines = 0,
                validCount = 0,
                invalidCount = 0,
                duplicateCount = 0,
                emptyLineCount = 0,
                groups = emptyList(),
                fullOutput = "",
                templateValidationError = validationError,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }

        if (rawInput.isEmpty()) {
            return ModelingProcessResult(
                records = emptyList(),
                totalInputLines = 0,
                validCount = 0,
                invalidCount = 0,
                duplicateCount = 0,
                emptyLineCount = 0,
                groups = emptyList(),
                fullOutput = "",
                templateValidationError = null,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val rawLines = rawInput.split("\r\n", "\n")
        val seenNormalized = HashSet<String>(rawLines.size)
        val parsedRecords = ArrayList<ParsedRecord>(rawLines.size)

        var validCount = 0
        var invalidCount = 0
        var duplicateCount = 0
        var emptyCount = 0

        for ((idx, line) in rawLines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                emptyCount++
                parsedRecords.add(
                    ParsedRecord(
                        originalIndex = idx + 1,
                        rawLine = line,
                        normalizedLine = "",
                        isEmpty = true,
                        isInvalid = false,
                        invalidReason = null,
                        isDuplicate = false
                    )
                )
                continue
            }

            val normalized = trimmed.replace(Regex("\\s+"), " ")
            val isDelimiterOnly = normalized.all { it == ':' || it == ';' || it == '|' || it == '-' || it == ',' }
            val hasEdgeColonOnly = (normalized.startsWith(":") || normalized.endsWith(":")) &&
                normalized.split(":").any { it.isBlank() }

            val isInvalid = isDelimiterOnly || hasEdgeColonOnly
            val invalidReason = when {
                isDelimiterOnly -> "Record contains only delimiter characters"
                hasEdgeColonOnly -> "Incomplete key:value pair (missing side around ':')"
                else -> null
            }

            val isDuplicate = !seenNormalized.add(normalized)
            if (isDuplicate) duplicateCount++
            if (isInvalid) invalidCount++ else validCount++

            parsedRecords.add(
                ParsedRecord(
                    originalIndex = idx + 1,
                    rawLine = line,
                    normalizedLine = normalized,
                    isEmpty = false,
                    isInvalid = isInvalid,
                    invalidReason = invalidReason,
                    isDuplicate = isDuplicate
                )
            )
        }

        val workingRecords = if (template.removeEmptyLines) {
            parsedRecords.filter { !it.isEmpty }
        } else {
            parsedRecords.map { if (it.isEmpty) it.copy(normalizedLine = "") else it }
        }

        if (workingRecords.isEmpty()) {
            return ModelingProcessResult(
                records = parsedRecords,
                totalInputLines = rawLines.size,
                validCount = validCount,
                invalidCount = invalidCount,
                duplicateCount = duplicateCount,
                emptyLineCount = emptyCount,
                groups = emptyList(),
                fullOutput = "",
                templateValidationError = null,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val sortedRecords = when (template.sortMode.uppercase()) {
            "ASC" -> workingRecords.sortedBy { it.normalizedLine.lowercase() }
            "DESC" -> workingRecords.sortedByDescending { it.normalizedLine.lowercase() }
            "REVERSE" -> workingRecords.asReversed()
            else -> workingRecords
        }

        val maxItems = if (template.endNumber != null && template.endNumber >= template.startNumber) {
            (template.endNumber - template.startNumber + 1).coerceAtLeast(1)
        } else {
            sortedRecords.size
        }
        val effectiveRecords = sortedRecords.take(maxItems)

        val groups = ArrayList<ModelingGroupOutput>()
        val fullBuilder = StringBuilder(effectiveRecords.size * 96)

        when (template.templateType.uppercase()) {
            "PARENT_CHILDREN" -> {
                val groupSize = template.recordsPerGroup.coerceAtLeast(1)
                val chunks = effectiveRecords.chunked(groupSize)
                var globalChildCounter = template.startNumber

                for ((groupIdx, chunk) in chunks.withIndex()) {
                    val groupNum = groupIdx + 1
                    val dateStr = ModelingDateHelper.calculateGroupDate(
                        startDateStr = template.startDateStr,
                        groupIndexZeroBased = groupIdx,
                        incrementMode = template.dateMode
                    )
                    val groupBuilder = StringBuilder()

                    val sep = template.separatorLine.ifEmpty { "-----------------------------------------------------" }
                    val childSep = if (sep.length == 53) "$sep-" else sep

                    val shouldPrintDateHeader = template.showDateOnEveryGroup || groupIdx == 0
                    if (shouldPrintDateHeader) {
                        groupBuilder.append(sep).append("\n")
                        groupBuilder.append("[ ").append(dateStr).append(" ]\n")
                        groupBuilder.append(sep).append("\n")
                    } else if (!template.combineGroupsIntoOneList) {
                        groupBuilder.append(sep).append("\n")
                    }

                    val parentIdx = template.parentIndexInGroup.coerceIn(0, chunk.lastIndex)
                    val parentRecord = chunk[parentIdx]
                    val parentFormatted = applyAffixes(parentRecord.normalizedLine, template)

                    groupBuilder.append(template.parentLabel).append(": ").append(parentFormatted).append("\n")
                    groupBuilder.append(childSep).append("\n")

                    var childCounter = if (template.restartNumberingPerGroup) {
                        template.startNumber
                    } else {
                        globalChildCounter
                    }

                    for ((idxInChunk, rec) in chunk.withIndex()) {
                        if (idxInChunk == parentIdx && chunk.size > 1) continue
                        if (chunk.size == 1 && idxInChunk == parentIdx) break
                        val numStr = BidiTextHelper.formatNumber(childCounter, template.useArabicNumerals)
                        val childFormatted = applyAffixes(rec.normalizedLine, template)
                        groupBuilder.append(numStr).append(" ").append(template.childLabel).append(": ")
                            .append(childFormatted).append("\n")
                        groupBuilder.append(childSep).append("\n")
                        childCounter++
                        globalChildCounter++
                    }

                    val groupText = groupBuilder.toString().trimEnd()
                    groups.add(
                        ModelingGroupOutput(
                            groupNumber = groupNum,
                            dateString = dateStr,
                            formattedText = groupText,
                            recordIndices = chunk.map { it.originalIndex }
                        )
                    )
                    if (fullBuilder.isNotEmpty()) fullBuilder.append("\n")
                    fullBuilder.append(groupText)
                }
            }

            "SEQUENTIAL_LIST" -> {
                val sep = template.separatorLine.ifEmpty { "-----------------------------------------------------" }
                val itemSep = if (sep.length == 53) "$sep-" else sep
                val groupSize = if (template.recordsPerGroup in 1..99999) template.recordsPerGroup else effectiveRecords.size.coerceAtLeast(1)
                val chunks = effectiveRecords.chunked(groupSize)
                var currentNum = template.startNumber

                for ((groupIdx, chunk) in chunks.withIndex()) {
                    val groupNum = groupIdx + 1
                    val dateStr = ModelingDateHelper.calculateGroupDate(
                        startDateStr = template.startDateStr,
                        groupIndexZeroBased = groupIdx,
                        incrementMode = template.dateMode
                    )
                    val listBuilder = StringBuilder()

                    if (template.showDateOnEveryGroup) {
                        listBuilder.append(sep).append("\n")
                        listBuilder.append("[ ").append(dateStr).append(" ]\n")
                        listBuilder.append(sep).append("\n")
                    } else if (groupIdx == 0 || !template.combineGroupsIntoOneList) {
                        listBuilder.append(sep).append("\n")
                    }

                    if (template.restartNumberingPerGroup && groupIdx > 0) {
                        currentNum = template.startNumber
                    }

                    for (rec in chunk) {
                        val numStr = BidiTextHelper.formatNumber(currentNum, template.useArabicNumerals)
                        val formattedLine = applyAffixes(rec.normalizedLine, template)
                        listBuilder.append(numStr).append(": ").append(formattedLine).append("\n")
                        listBuilder.append(itemSep).append("\n")
                        currentNum++
                    }

                    val outputText = listBuilder.toString().trimEnd()
                    groups.add(
                        ModelingGroupOutput(
                            groupNumber = groupNum,
                            dateString = dateStr,
                            formattedText = outputText,
                            recordIndices = chunk.map { it.originalIndex }
                        )
                    )
                    if (fullBuilder.isNotEmpty()) fullBuilder.append("\n")
                    fullBuilder.append(outputText)
                }
            }

            else -> {
                val groupSize = template.recordsPerGroup.coerceAtLeast(1)
                val chunks = effectiveRecords.chunked(groupSize)
                var globalNumber = template.startNumber

                for ((groupIdx, chunk) in chunks.withIndex()) {
                    val groupNum = groupIdx + 1
                    val groupNumStr = BidiTextHelper.formatNumber(groupNum, template.useArabicNumerals)
                    val dateStr = ModelingDateHelper.calculateGroupDate(
                        startDateStr = template.startDateStr,
                        groupIndexZeroBased = groupIdx,
                        incrementMode = template.dateMode
                    )
                    val parentIdx = template.parentIndexInGroup.coerceIn(0, chunk.lastIndex)
                    val parentRecordText = applyAffixes(chunk[parentIdx].normalizedLine, template)

                    val groupBuilder = StringBuilder()
                    val shouldPrintHeader = template.showDateOnEveryGroup || groupIdx == 0

                    if (shouldPrintHeader && template.headerPattern.isNotBlank()) {
                        val headerRendered = renderPlaceholders(
                            pattern = template.headerPattern,
                            numberStr = BidiTextHelper.formatNumber(globalNumber, template.useArabicNumerals),
                            dateStr = dateStr,
                            recordText = parentRecordText,
                            parentText = parentRecordText,
                            childText = parentRecordText,
                            separator = template.separatorLine,
                            groupNumStr = groupNumStr,
                            parentLabel = template.parentLabel,
                            childLabel = template.childLabel
                        )
                        groupBuilder.append(headerRendered).append("\n")
                    }

                    val hasExplicitParentSection = template.parentPattern.isNotBlank() &&
                        (template.parentPattern.contains("{parent}") || template.recordsPerGroup > 1)

                    if (hasExplicitParentSection) {
                        val parentRendered = renderPlaceholders(
                            pattern = template.parentPattern,
                            numberStr = BidiTextHelper.formatNumber(template.startNumber, template.useArabicNumerals),
                            dateStr = dateStr,
                            recordText = parentRecordText,
                            parentText = parentRecordText,
                            childText = parentRecordText,
                            separator = template.separatorLine,
                            groupNumStr = groupNumStr,
                            parentLabel = template.parentLabel,
                            childLabel = template.childLabel
                        )
                        groupBuilder.append(parentRendered).append("\n")
                    }

                    var itemCounter = if (template.restartNumberingPerGroup) {
                        template.startNumber
                    } else {
                        globalNumber
                    }

                    if (template.itemPattern.isNotBlank()) {
                        for ((idxInChunk, rec) in chunk.withIndex()) {
                            if (hasExplicitParentSection && idxInChunk == parentIdx && chunk.size > 1) continue
                            val recText = applyAffixes(rec.normalizedLine, template)
                            val numStr = BidiTextHelper.formatNumber(itemCounter, template.useArabicNumerals)
                            val itemRendered = renderPlaceholders(
                                pattern = template.itemPattern,
                                numberStr = numStr,
                                dateStr = dateStr,
                                recordText = recText,
                                parentText = parentRecordText,
                                childText = recText,
                                separator = template.separatorLine,
                                groupNumStr = groupNumStr,
                                parentLabel = template.parentLabel,
                                childLabel = template.childLabel
                            )
                            groupBuilder.append(itemRendered).append("\n")
                            itemCounter++
                            globalNumber++
                        }
                    }

                    if (template.footerPattern.isNotBlank()) {
                        val footerRendered = renderPlaceholders(
                            pattern = template.footerPattern,
                            numberStr = BidiTextHelper.formatNumber(itemCounter, template.useArabicNumerals),
                            dateStr = dateStr,
                            recordText = parentRecordText,
                            parentText = parentRecordText,
                            childText = parentRecordText,
                            separator = template.separatorLine,
                            groupNumStr = groupNumStr,
                            parentLabel = template.parentLabel,
                            childLabel = template.childLabel
                        )
                        groupBuilder.append(footerRendered).append("\n")
                    }

                    val groupText = groupBuilder.toString().trimEnd()
                    groups.add(
                        ModelingGroupOutput(
                            groupNumber = groupNum,
                            dateString = dateStr,
                            formattedText = groupText,
                            recordIndices = chunk.map { it.originalIndex }
                        )
                    )
                    if (fullBuilder.isNotEmpty()) fullBuilder.append("\n")
                    fullBuilder.append(groupText)
                }
            }
        }

        // If the user enabled "Groups on One List" (combineGroupsIntoOneList), merge all generated groups into a single unified list card
        val finalGroups = if (template.combineGroupsIntoOneList && groups.size > 1) {
            val firstDate = groups.first().dateString
            val lastDate = groups.last().dateString
            val dateRangeLabel = if (firstDate == lastDate) firstDate else "$firstDate → $lastDate"
            listOf(
                ModelingGroupOutput(
                    groupNumber = 1,
                    dateString = dateRangeLabel,
                    formattedText = fullBuilder.toString(),
                    recordIndices = groups.flatMap { it.recordIndices }
                )
            )
        } else {
            groups
        }

        return ModelingProcessResult(
            records = parsedRecords,
            totalInputLines = rawLines.size,
            validCount = validCount,
            invalidCount = invalidCount,
            duplicateCount = duplicateCount,
            emptyLineCount = emptyCount,
            groups = finalGroups,
            fullOutput = fullBuilder.toString(),
            templateValidationError = null,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun applyAffixes(text: String, template: ModelingTemplateEntity): String {
        if (template.prefixText.isEmpty() && template.suffixText.isEmpty()) return text
        return "${template.prefixText}$text${template.suffixText}"
    }

    private fun renderPlaceholders(
        pattern: String,
        numberStr: String,
        dateStr: String,
        recordText: String,
        parentText: String,
        childText: String,
        separator: String,
        groupNumStr: String,
        parentLabel: String,
        childLabel: String
    ): String {
        return pattern
            .replace("{number}", numberStr)
            .replace("{date}", dateStr)
            .replace("{record}", recordText)
            .replace("{parent}", parentText)
            .replace("{child}", childText)
            .replace("{separator}", separator)
            .replace("{group_number}", groupNumStr)
            .replace("{parent_label}", parentLabel)
            .replace("{child_label}", childLabel)
    }

    fun getDefaultBuiltInTemplates(): List<ModelingTemplateEntity> {
        return listOf(
            ModelingTemplateEntity(
                id = 1L,
                name = "Parent / Children",
                category = "Built-in",
                templateType = "PARENT_CHILDREN",
                isBuiltIn = true,
                isDefault = true,
                headerPattern = "-----------------------------------------------------\n[ {date} ]\n-----------------------------------------------------",
                parentPattern = "{parent_label}: {parent}\n------------------------------------------------------",
                itemPattern = "{number} {child_label}: {child}\n------------------------------------------------------",
                separatorLine = "-----------------------------------------------------",
                parentLabel = "Parent",
                childLabel = "Child",
                recordsPerGroup = 4,
                parentIndexInGroup = 0,
                startNumber = 1,
                useArabicNumerals = false,
                restartNumberingPerGroup = true,
                dateMode = "INCREMENT_DAY",
                startDateStr = "30/11/2008",
                showDateOnEveryGroup = true,
                combineGroupsIntoOneList = false,
                sortMode = "ORIGINAL",
                removeEmptyLines = true
            ),
            ModelingTemplateEntity(
                id = 2L,
                name = "Sequential List",
                category = "Built-in",
                templateType = "SEQUENTIAL_LIST",
                isBuiltIn = true,
                isDefault = false,
                headerPattern = "-----------------------------------------------------",
                parentPattern = "",
                itemPattern = "{number}: {record}\n------------------------------------------------------",
                separatorLine = "-----------------------------------------------------",
                parentLabel = "Parent",
                childLabel = "Child",
                recordsPerGroup = 4,
                parentIndexInGroup = 0,
                startNumber = 1,
                useArabicNumerals = false,
                restartNumberingPerGroup = false,
                dateMode = "FIXED",
                startDateStr = "30/11/2008",
                showDateOnEveryGroup = false,
                combineGroupsIntoOneList = true,
                sortMode = "ORIGINAL",
                removeEmptyLines = true
            )
        )
    }
}
