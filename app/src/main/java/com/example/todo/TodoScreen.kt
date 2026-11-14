package com.example.todo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.database.TodoEntity
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

@Composable
fun TodoScreen(
    todos: List<TodoEntity>,
    searchQuery: String,
    onAddOrUpdateTodo: (Long, String, String, String) -> Unit,
    onToggleTodo: (TodoEntity) -> Unit,
    onDeleteTodo: (TodoEntity) -> Unit,
    onMoveOrder: (TodoEntity, Boolean) -> Unit,
    onClearCompleted: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoEntity?>(null) }

    val categories = remember(todos) {
        listOf("All") + todos.map { it.category }.distinct().sorted()
    }

    val filteredTodos = remember(todos, selectedCategory, searchQuery) {
        todos.filter { item ->
            val catMatch = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val queryMatch = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.description.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true)
            catMatch && queryMatch
        }
    }

    val completedCount = remember(todos) { todos.count { it.isCompleted } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .testTag("todo_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TO-DO & TASKS",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${todos.size - completedCount} active • $completedCount completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaCyan
                    )
                }

                if (completedCount > 0) {
                    TextButton(
                        onClick = onClearCompleted,
                        modifier = Modifier.testTag("clear_completed_todos_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Completed", tint = StatusError, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Done", color = StatusError)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardDarkGray,
                            labelColor = TextSecondary,
                            selectedContainerColor = NovaDeepTeal,
                            selectedLabelColor = NovaCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredTodos.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No Tasks in This View", style = MaterialTheme.typography.titleLarge, color = TextWhite)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Add structured tasks, descriptions, and categories.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("todo_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTodos, key = { it.id }) { todo ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("todo_item_${todo.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (todo.isCompleted) CardDarkGray.copy(alpha = 0.6f) else CardDarkGray
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (todo.isCompleted) SurfaceBorder.copy(alpha = 0.5f) else NovaCyan.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onToggleTodo(todo) },
                                    modifier = Modifier.testTag("toggle_todo_${todo.id}")
                                ) {
                                    Icon(
                                        imageVector = if (todo.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = "Complete Task",
                                        tint = if (todo.isCompleted) StatusSuccess else NovaCyan
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            editingTodo = todo
                                            showEditorDialog = true
                                        }
                                        .padding(horizontal = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = todo.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            color = if (todo.isCompleted) TextMuted else TextWhite
                                        )
                                        Surface(color = NovaDeepTeal.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                text = todo.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NovaCyan,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (todo.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = todo.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onMoveOrder(todo, true) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { onMoveOrder(todo, false) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            editingTodo = todo
                                            showEditorDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Task", tint = NovaCyan, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteTodo(todo) },
                                        modifier = Modifier.size(32.dp).testTag("delete_todo_${todo.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Task", tint = StatusError, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingTodo = null
                showEditorDialog = true
            },
            containerColor = NovaCyan,
            contentColor = ObsidianBlack,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_todo_fab")
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Task", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showEditorDialog) {
        var title by remember(editingTodo) { mutableStateOf(editingTodo?.title ?: "") }
        var description by remember(editingTodo) { mutableStateOf(editingTodo?.description ?: "") }
        var category by remember(editingTodo) { mutableStateOf(editingTodo?.category ?: "General") }

        AlertDialog(
            onDismissRequest = { showEditorDialog = false },
            containerColor = CardElevatedGray,
            title = { Text(if (editingTodo == null) "New Task" else "Edit Task", color = TextWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("todo_title_input")
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (e.g., Work, Personal, Urgent)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("todo_category_input")
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("todo_description_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onAddOrUpdateTodo(editingTodo?.id ?: 0L, title, description, category)
                            showEditorDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaCyan, contentColor = ObsidianBlack),
                    modifier = Modifier.testTag("save_todo_button")
                ) {
                    Text("Save Task", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditorDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
