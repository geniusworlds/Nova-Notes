package com.example.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.database.ModelingTemplateEntity
import com.example.database.NoteEntity
import com.example.modeling.ModelingProcessResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportImportManager {

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)

    fun exportNotesToTxt(notes: List<NoteEntity>): String {
        if (notes.size == 1) {
            val n = notes.first()
            return buildString {
                if (n.title.isNotBlank()) {
                    append(n.title).append("\n")
                    append("=====================================================\n")
                }
                append(n.content)
            }
        }
        return buildString {
            notes.forEachIndexed { idx, note ->
                if (idx > 0) append("\n\n-----------------------------------------------------\n\n")
                append("Title: ").append(note.title.ifBlank { "Untitled" }).append("\n")
                append("Updated: ").append(dateFormatter.format(Date(note.updatedAt))).append("\n")
                append("-----------------------------------------------------\n")
                append(note.content)
            }
        }
    }

    fun exportNotesToJson(notes: List<NoteEntity>): String {
        val array = JSONArray()
        for (n in notes) {
            array.put(
                JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("content", n.content)
                    put("folderId", n.folderId ?: JSONObject.NULL)
                    put("isPinned", n.isPinned)
                    put("isFavorite", n.isFavorite)
                    put("textDirection", n.textDirection)
                    put("createdAt", n.createdAt)
                    put("updatedAt", n.updatedAt)
                }
            )
        }
        return JSONObject().apply {
            put("type", "NOVA_NOTES_EXPORT")
            put("count", notes.size)
            put("notes", array)
        }.toString(2)
    }

    fun exportNotesToCsv(notes: List<NoteEntity>): String {
        return buildString {
            append("ID,Title,Content,Pinned,Favorite,Direction,UpdatedAt\n")
            for (n in notes) {
                append(n.id).append(",")
                append(escapeCsv(n.title)).append(",")
                append(escapeCsv(n.content)).append(",")
                append(n.isPinned).append(",")
                append(n.isFavorite).append(",")
                append(escapeCsv(n.textDirection)).append(",")
                append(n.updatedAt).append("\n")
            }
        }
    }

    fun exportModelingResultToCsv(result: ModelingProcessResult): String {
        return buildString {
            append("LineNumber,NormalizedRecord,Valid,Duplicate,InvalidReason\n")
            for (rec in result.records) {
                if (rec.isEmpty) continue
                append(rec.originalIndex).append(",")
                append(escapeCsv(rec.normalizedLine)).append(",")
                append(!rec.isInvalid).append(",")
                append(rec.isDuplicate).append(",")
                append(escapeCsv(rec.invalidReason ?: "")).append("\n")
            }
        }
    }

    fun exportModelingResultToJson(result: ModelingProcessResult): String {
        return JSONObject().apply {
            put("type", "NOVA_MODELING_RESULT")
            put("totalInputLines", result.totalInputLines)
            put("validCount", result.validCount)
            put("invalidCount", result.invalidCount)
            put("duplicateCount", result.duplicateCount)
            put("fullOutput", result.fullOutput)
            put("groups", JSONArray().apply {
                result.groups.forEach { g ->
                    put(JSONObject().apply {
                        put("groupNumber", g.groupNumber)
                        put("date", g.dateString)
                        put("formattedText", g.formattedText)
                    })
                }
            })
        }.toString(2)
    }

    fun generatePdfBytes(title: String, bodyText: String): ByteArray {
        return try {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 40f
            val usableWidth = pageWidth - (margin * 2)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 18f
                isFakeBoldText = true
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 11.5f
            }

            val wrappedLines = ArrayList<String>()
            for (paragraph in bodyText.split("\n")) {
                if (paragraph.isEmpty()) {
                    wrappedLines.add("")
                    continue
                }
                var start = 0
                while (start < paragraph.length) {
                    val count = bodyPaint.breakText(paragraph, start, paragraph.length, true, usableWidth, null)
                        .let { if (it <= 0) (paragraph.length - start).coerceAtMost(80) else it }
                    if (count <= 0) break
                    wrappedLines.add(paragraph.substring(start, start + count))
                    start += count
                }
            }

            var lineIdx = 0
            var pageNumber = 1

            do {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = document.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                var y = margin + 16f

                if (pageNumber == 1 && title.isNotBlank()) {
                    canvas.drawText(title.take(65), margin, y, titlePaint)
                    y += 28f
                }

                val lineHeight = 16f
                while (lineIdx < wrappedLines.size && y < pageHeight - margin) {
                    canvas.drawText(wrappedLines[lineIdx], margin, y, bodyPaint)
                    y += lineHeight
                    lineIdx++
                }

                document.finishPage(page)
                pageNumber++
            } while (lineIdx < wrappedLines.size)

            val out = ByteArrayOutputStream()
            document.writeTo(out)
            document.close()
            val bytes = out.toByteArray()
            if (bytes.isNotEmpty()) bytes else buildPortablePdf14Bytes(title, bodyText)
        } catch (_: Throwable) {
            buildPortablePdf14Bytes(title, bodyText)
        }
    }

    private fun buildPortablePdf14Bytes(title: String, bodyText: String): ByteArray {
        val escapedLines = (listOf(title) + bodyText.lines()).map {
            it.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        }
        val streamContent = buildString {
            append("BT\n/F1 12 Tf\n50 780 Td\n15 TL\n")
            escapedLines.forEach { line ->
                append("(").append(line).append(") Tj T*\n")
            }
            append("ET\n")
        }
        val pdfText = """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj
            4 0 obj << /Length ${streamContent.length} >>
            stream
            $streamContent
            endstream
            endobj
            5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
            xref
            0 6
            0000000000 65535 f 
            trailer << /Size 6 /Root 1 0 R >>
            %%EOF
        """.trimIndent()
        return pdfText.toByteArray(Charsets.UTF_8)
    }

    fun parseImportedNotes(rawContent: String, formatHint: String = "AUTO"): List<NoteEntity> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Imported file is empty.")

        val detectedFormat = when {
            formatHint.uppercase() in listOf("JSON", "CSV", "TXT") -> formatHint.uppercase()
            trimmed.startsWith("{") || trimmed.startsWith("[") -> "JSON"
            trimmed.startsWith("ID,Title,Content") -> "CSV"
            else -> "TXT"
        }

        return when (detectedFormat) {
            "JSON" -> {
                val root = JSONObject(trimmed)
                val notesArr = root.optJSONArray("notes")
                    ?: root.optJSONObject("data")?.optJSONArray("notes")
                    ?: throw IllegalArgumentException("JSON does not contain a 'notes' array.")
                val list = ArrayList<NoteEntity>(notesArr.length())
                for (i in 0 until notesArr.length()) {
                    val obj = notesArr.getJSONObject(i)
                    list.add(
                        NoteEntity(
                            id = 0L,
                            title = obj.optString("title", "Imported Note"),
                            content = obj.optString("content", ""),
                            isPinned = obj.optBoolean("isPinned", false),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            textDirection = obj.optString("textDirection", "AUTO")
                        )
                    )
                }
                list
            }

            "CSV" -> {
                val rows = parseCsvRecords(trimmed)
                if (rows.size <= 1) throw IllegalArgumentException("CSV file contains no data rows.")
                rows.drop(1).map { cols ->
                    val title = cols.getOrNull(1)?.ifBlank { "Imported CSV Note" } ?: "Imported CSV Note"
                    val content = cols.getOrNull(2) ?: cols.joinToString(",")
                    NoteEntity(id = 0L, title = title, content = content)
                }
            }

            else -> {
                val lines = rawContent.lines()
                val firstLine = lines.firstOrNull()?.trim().orEmpty()
                val title = firstLine.take(60).ifBlank { "Imported TXT Note" }
                listOf(NoteEntity(id = 0L, title = title, content = rawContent))
            }
        }
    }

    fun exportTemplatesToJson(
        templates: List<ModelingTemplateEntity>,
        serializeOne: (ModelingTemplateEntity) -> JSONObject
    ): String {
        return JSONObject().apply {
            put("type", "NOVA_MODELING_TEMPLATES")
            put("count", templates.size)
            put("templates", JSONArray().apply {
                templates.forEach { put(serializeOne(it)) }
            })
        }.toString(2)
    }

    fun parseImportedTemplates(
        rawJson: String,
        parseOne: (JSONObject) -> ModelingTemplateEntity
    ): List<ModelingTemplateEntity> {
        val root = try {
            JSONObject(rawJson.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON template configuration: ${e.message}")
        }

        val arr = root.optJSONArray("templates")
            ?: if (root.has("name") && root.has("itemPattern")) JSONArray().put(root) else null
            ?: throw IllegalArgumentException("JSON does not contain valid modeling templates.")

        val result = ArrayList<ModelingTemplateEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val tmpl = parseOne(arr.getJSONObject(i)).copy(id = 0L, isBuiltIn = false, isDefault = false)
            result.add(tmpl)
        }
        return result
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun parseCsvRecords(csvText: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        var currentRow = ArrayList<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < csvText.length) {
            val c = csvText[i]
            when {
                c == '"' && inQuotes && i + 1 < csvText.length && csvText[i + 1] == '"' -> {
                    currentField.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < csvText.length && csvText[i + 1] == '\n') {
                        i++
                    }
                    currentRow.add(currentField.toString())
                    currentField.clear()
                    if (currentRow.any { it.isNotEmpty() }) {
                        records.add(currentRow)
                    }
                    currentRow = ArrayList()
                }
                else -> currentField.append(c)
            }
            i++
        }
        currentRow.add(currentField.toString())
        if (currentRow.any { it.isNotEmpty() }) {
            records.add(currentRow)
        }
        return records
    }
}
