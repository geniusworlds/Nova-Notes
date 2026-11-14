package com.example.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["isPinned"]),
        Index(value = ["isFavorite"]),
        Index(value = ["isTrashed"]),
        Index(value = ["updatedAt"]),
        Index(value = ["title"])
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val folderId: Long? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val textDirection: String = "AUTO", // AUTO, LTR, RTL
    val textAlignment: String = "START", // START, CENTER, END
    val lastCursorPosition: Int = 0,
    val lastSelectionEnd: Int = 0,
    val lastScrollOffset: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "folders",
    indices = [Index(value = ["name"], unique = false)]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorHex: String = "#00E5FF",
    val iconName: String = "folder",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "todos",
    indices = [
        Index(value = ["isCompleted"]),
        Index(value = ["sortOrder"]),
        Index(value = ["category"])
    ]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val category: String = "General",
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "modeling_templates",
    indices = [Index(value = ["category"]), Index(value = ["isBuiltIn"])]
)
data class ModelingTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val category: String = "Custom",
    val templateType: String = "CUSTOM", // PARENT_CHILDREN, SEQUENTIAL_LIST, CUSTOM
    val isBuiltIn: Boolean = false,
    val isDefault: Boolean = false,
    val headerPattern: String = "-----------------------------------------------------\n[ {date} ]\n-----------------------------------------------------",
    val parentPattern: String = "{parent_label}: {parent}\n------------------------------------------------------",
    val itemPattern: String = "{number} {child_label}: {child}\n------------------------------------------------------",
    val footerPattern: String = "",
    val separatorLine: String = "-----------------------------------------------------",
    val parentLabel: String = "Parent",
    val childLabel: String = "Child",
    val recordsPerGroup: Int = 4, // 1 parent + 3 children by default
    val parentIndexInGroup: Int = 0, // 0 = first item in group is parent
    val startNumber: Int = 1,
    val endNumber: Int? = null,
    val useArabicNumerals: Boolean = false,
    val restartNumberingPerGroup: Boolean = true,
    val dateMode: String = "INCREMENT_DAY", // FIXED, INCREMENT_DAY
    val startDateStr: String = "30/11/2008",
    val sortMode: String = "ORIGINAL", // ORIGINAL, ASC, DESC, REVERSE
    val removeEmptyLines: Boolean = true,
    val prefixText: String = "",
    val suffixText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
