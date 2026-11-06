package com.tasks.notebook.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tasks.notebook.TasksApp
import com.tasks.notebook.data.GroupKind
import com.tasks.notebook.data.TaskDraft
import com.tasks.notebook.data.TaskGroup
import com.tasks.notebook.data.TaskItem
import com.tasks.notebook.notifications.TaskAlarm
import com.tasks.notebook.ui.theme.Palettes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Screen {
    data class Lists(val groupId: Long) : Screen
    data object Settings : Screen
    data class Editor(val taskId: Long?, val originGroupId: Long) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as TasksApp).repository
    private val prefs = app.getSharedPreferences("tasks_prefs", Context.MODE_PRIVATE)

    val groups = repo.observeGroups().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tasks = repo.observeTasks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var paletteId by mutableStateOf(prefs.getString(KEY_PALETTE, Palettes.amber.id) ?: Palettes.amber.id)
        private set
    var screen by mutableStateOf<Screen>(Screen.Lists(-1L))
        private set
    var undo by mutableStateOf<List<TaskItem>>(emptyList())
        private set
    var undoToken by mutableIntStateOf(0)
        private set
    var highlightId by mutableLongStateOf(-1L)
        private set

    init {
        viewModelScope.launch {
            repo.observeGroups().collect { list ->
                val current = (screen as? Screen.Lists)?.groupId
                if (current == null || current < 0 || list.none { it.id == current }) {
                    val first = list.firstOrNull { !it.isPinnedScheduled() } ?: list.firstOrNull()
                    if (first != null && screen is Screen.Lists) screen = Screen.Lists(first.id)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                repo.markArrived(System.currentTimeMillis())
                delay(15_000)
            }
        }
    }

    fun palette() = Palettes.byId(paletteId)

    fun selectPalette(id: String) {
        paletteId = id
        prefs.edit().putString(KEY_PALETTE, id).apply()
    }

    fun openGroup(id: Long) {
        screen = Screen.Lists(id)
    }

    fun openSettings() {
        screen = Screen.Settings
    }

    fun openEditor(taskId: Long?, originGroupId: Long) {
        screen = Screen.Editor(taskId, originGroupId)
    }

    fun openFromNotification(taskId: Long) {
        viewModelScope.launch {
            val task = repo.task(taskId) ?: return@launch
            screen = Screen.Editor(task.id, task.groupId)
        }
    }

    fun closeEditor() {
        val editor = screen as? Screen.Editor ?: return
        viewModelScope.launch {
            if (editor.taskId != null) repo.clearUnseen(editor.taskId)
            screen = Screen.Lists(editor.originGroupId)
        }
    }

    fun leaveToGroup(id: Long) {
        val editor = screen as? Screen.Editor
        viewModelScope.launch {
            if (editor?.taskId != null) repo.clearUnseen(editor.taskId)
            screen = Screen.Lists(id)
        }
    }

    fun leaveToSettings() {
        val editor = screen as? Screen.Editor
        viewModelScope.launch {
            if (editor?.taskId != null) repo.clearUnseen(editor.taskId)
            screen = Screen.Settings
        }
    }

    fun setRating(id: Long, rating: Int) {
        viewModelScope.launch { repo.setRating(id, rating) }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            repo.deleteTask(task.id)
            undo = undo + task
            undoToken += 1
            val editor = screen as? Screen.Editor
            if (editor?.taskId == task.id) screen = Screen.Lists(task.groupId)
        }
    }

    fun undoAll() {
        val items = undo
        undo = emptyList()
        viewModelScope.launch { items.forEach { repo.restore(it) } }
    }

    fun discardUndo() {
        undo = emptyList()
    }

    var vibrationOn by mutableStateOf(TaskAlarm.vibrationEnabled(app))
        private set

    fun updateVibration(enabled: Boolean) {
        vibrationOn = enabled
        TaskAlarm.setVibration(getApplication(), enabled)
    }

    fun save(existingId: Long?, draft: TaskDraft, close: Boolean = true) {
        viewModelScope.launch {
            val id = repo.saveTask(existingId, draft)
            if (existingId == null) highlightId = id
            if (close) screen = Screen.Lists(draft.groupId)
        }
    }

    fun clearHighlight() {
        highlightId = -1L
    }

    fun place(taskId: Long, groupId: Long, index: Int) {
        viewModelScope.launch { repo.place(taskId, groupId, index) }
    }

    fun commitOrder(groupId: Long, ids: List<Long>) {
        viewModelScope.launch { repo.moveWithin(groupId, ids) }
    }

    fun reorderWindows(ids: List<Long>) {
        viewModelScope.launch { repo.reorderScrollable(ids) }
    }

    fun addWindow(name: String, kind: GroupKind) {
        viewModelScope.launch { repo.addGroup(name, kind) }
    }

    fun renameWindow(id: Long, name: String) {
        viewModelScope.launch { repo.renameGroup(id, name) }
    }

    fun deleteWindow(id: Long) {
        viewModelScope.launch {
            repo.deleteGroup(id)
            val left = repo.groups()
            if ((screen as? Screen.Lists)?.groupId == id) {
                val next = left.firstOrNull { !it.isPinnedScheduled() } ?: left.firstOrNull()
                if (next != null) screen = Screen.Lists(next.id)
            }
        }
    }

    fun refreshAlarms() {
        viewModelScope.launch { TaskAlarm.catchUp(getApplication()) }
    }

    private companion object {
        const val KEY_PALETTE = "palette"
    }
}

fun orderedTasks(tasks: List<TaskItem>, group: TaskGroup): List<TaskItem> {
    val mine = tasks.filter { it.groupId == group.id }
    return if (group.isPinnedScheduled()) {
        mine.sortedWith(compareBy({ it.nextFireAt ?: Long.MAX_VALUE }, { it.id }))
    } else {
        mine.sortedBy { it.sortOrder }
    }
}

fun listTitle(title: String): String = if (title.length > 50) title.take(50) + "…" else title
