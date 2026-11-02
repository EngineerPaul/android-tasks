package com.tasks.notebook.data

import android.content.Context
import com.tasks.notebook.DueRules
import com.tasks.notebook.notifications.TaskAlarm
import kotlinx.coroutines.flow.Flow

class TasksRepository(
    private val dao: TasksDao,
    private val context: Context,
) {
    fun observeGroups(): Flow<List<TaskGroup>> = dao.observeGroups()

    fun observeTasks(): Flow<List<TaskItem>> = dao.observeTasks()

    suspend fun groups(): List<TaskGroup> = dao.groups()

    suspend fun tasksNow(): List<TaskItem> = dao.tasks()

    suspend fun task(id: Long): TaskItem? = dao.task(id)

    suspend fun addGroup(name: String, kind: GroupKind): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || nameTaken(trimmed, null)) return null
        val order = dao.groups().filter { !it.isPinnedScheduled() }.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        return dao.insertGroup(
            TaskGroup(name = trimmed, kind = kind.name, sortOrder = order, builtIn = false),
        )
    }

    suspend fun renameGroup(id: Long, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || nameTaken(trimmed, id)) return false
        dao.renameGroup(id, trimmed)
        return true
    }

    suspend fun reorderScrollable(ids: List<Long>) {
        ids.forEachIndexed { index, id -> dao.setGroupOrder(id, index) }
    }

    suspend fun deleteGroup(id: Long) {
        val group = dao.groups().firstOrNull { it.id == id } ?: return
        if (group.isPinnedScheduled()) return
        dao.tasks().filter { it.groupId == id }.forEach { TaskAlarm.cancel(context, it.id) }
        dao.deleteGroup(id)
    }

    suspend fun saveTask(existingId: Long?, draft: TaskDraft): Long {
        val groups = dao.groups()
        val group = groups.first { it.id == draft.groupId }
        val tasks = dao.tasks()
        val inGroup = tasks.filter { it.groupId == group.id && it.id != (existingId ?: -1L) }
        val order = if (group.isPinnedScheduled()) 0 else (inGroup.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val previous = existingId?.let { id -> tasks.first { it.id == id } }
        val moved = previous != null && previous.groupId != group.id
        val sort = when {
            moved && group.isPinnedScheduled() -> 0
            moved -> order
            previous != null -> previous.sortOrder
            else -> order
        }
        val item = TaskItem(
            id = existingId ?: 0,
            groupId = group.id,
            title = draft.title.trim(),
            description = draft.description,
            rating = draft.rating,
            sortOrder = sort,
            nextFireAt = if (group.kindEnum() == GroupKind.SCHEDULED) draft.nextFireAt else null,
            repeatEveryDays = if (group.kindEnum() == GroupKind.SCHEDULED) draft.repeatEveryDays else null,
            notifiedOnce = false,
            dueUnseen = false,
        )
        val id = if (existingId == null) {
            dao.insertTask(item)
        } else {
            dao.updateTask(
                id = existingId,
                groupId = item.groupId,
                sortOrder = item.sortOrder,
                title = item.title,
                description = item.description,
                rating = item.rating,
                nextFireAt = item.nextFireAt,
                repeatEveryDays = item.repeatEveryDays,
                notifiedOnce = false,
                dueUnseen = previous?.dueUnseen == true && item.nextFireAt == previous.nextFireAt,
            )
            existingId
        }
        val stored = dao.task(id) ?: item.copy(id = id)
        if (stored.nextFireAt != null && stored.nextFireAt > System.currentTimeMillis()) {
            TaskAlarm.schedule(context, stored)
        } else {
            TaskAlarm.cancel(context, id)
        }
        return id
    }

    suspend fun setRating(id: Long, rating: Int) {
        dao.setRating(id, rating)
    }

    suspend fun deleteTask(id: Long) {
        TaskAlarm.cancel(context, id)
        dao.deleteTask(id)
    }

    suspend fun restore(task: TaskItem) {
        dao.insertTask(task)
        val stored = dao.task(task.id) ?: return
        if (stored.nextFireAt != null) TaskAlarm.schedule(context, stored)
    }

    suspend fun moveWithin(groupId: Long, orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> dao.moveTask(id, groupId, index) }
    }

    suspend fun place(taskId: Long, groupId: Long, index: Int) {
        val task = dao.task(taskId) ?: return
        val group = dao.groups().firstOrNull { it.id == groupId } ?: return
        val oldGroup = task.groupId
        if (group.isPinnedScheduled()) {
            dao.moveTask(taskId, groupId, 0)
        } else {
            val others = dao.tasks()
                .filter { it.groupId == groupId && it.id != taskId }
                .sortedBy { it.sortOrder }
                .toMutableList()
            others.add(index.coerceIn(0, others.size), task)
            others.forEachIndexed { position, item -> dao.moveTask(item.id, groupId, position) }
        }
        if (oldGroup != groupId) {
            val left = dao.tasks().filter { it.groupId == oldGroup }.sortedBy { it.sortOrder }
            left.forEachIndexed { position, item -> dao.setTaskOrder(item.id, position) }
        }
    }

    suspend fun clearUnseen(id: Long) {
        dao.setUnseen(id, false)
    }

    suspend fun markFired(id: Long, nextFireAt: Long?, unseen: Boolean, notifiedOnce: Boolean) {
        dao.setFire(id, nextFireAt, unseen, notifiedOnce)
    }

    suspend fun markArrived(now: Long) {
        dao.tasks().filter { task ->
            val at = task.nextFireAt
            at != null && at <= now && !task.dueUnseen
        }.forEach { dao.setUnseen(it.id, true) }
    }

    private suspend fun nameTaken(name: String, exceptId: Long?): Boolean =
        dao.groups().any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }
}

data class TaskDraft(
    val groupId: Long,
    val title: String,
    val description: String,
    val rating: Int,
    val nextFireAt: Long?,
    val repeatEveryDays: Int?,
)
