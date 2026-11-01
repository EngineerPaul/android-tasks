package com.tasks.notebook.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class GroupKind { LIST, SCHEDULED }

@Entity(tableName = "groups")
data class TaskGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val sortOrder: Int,
    val builtIn: Boolean,
) {
    fun kindEnum(): GroupKind = if (kind == GroupKind.SCHEDULED.name) GroupKind.SCHEDULED else GroupKind.LIST

    fun isPinnedScheduled(): Boolean = builtIn && kindEnum() == GroupKind.SCHEDULED
}

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val title: String,
    val description: String,
    val rating: Int,
    val sortOrder: Int,
    val nextFireAt: Long?,
    val repeatEveryDays: Int?,
    val notifiedOnce: Boolean,
    val dueUnseen: Boolean,
)
