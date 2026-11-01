package com.tasks.notebook.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TasksDao {
    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    fun observeGroups(): Flow<List<TaskGroup>>

    @Query("SELECT * FROM tasks")
    fun observeTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM groups ORDER BY sortOrder, id")
    suspend fun groups(): List<TaskGroup>

    @Query("SELECT * FROM tasks")
    suspend fun tasks(): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: Long): TaskItem?

    @Insert
    suspend fun insertGroup(group: TaskGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskItem): Long

    @Query("UPDATE groups SET name = :name WHERE id = :id")
    suspend fun renameGroup(id: Long, name: String)

    @Query("UPDATE groups SET sortOrder = :position WHERE id = :id")
    suspend fun setGroupOrder(id: Long, position: Int)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("UPDATE tasks SET dueUnseen = :unseen WHERE id = :id")
    suspend fun setUnseen(id: Long, unseen: Boolean)

    @Query("UPDATE tasks SET rating = :rating WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int)

    @Query(
        "UPDATE tasks SET groupId = :groupId, sortOrder = :sortOrder, title = :title, description = :description, " +
            "rating = :rating, nextFireAt = :nextFireAt, repeatEveryDays = :repeatEveryDays, " +
            "notifiedOnce = :notifiedOnce, dueUnseen = :dueUnseen WHERE id = :id",
    )
    suspend fun updateTask(
        id: Long,
        groupId: Long,
        sortOrder: Int,
        title: String,
        description: String,
        rating: Int,
        nextFireAt: Long?,
        repeatEveryDays: Int?,
        notifiedOnce: Boolean,
        dueUnseen: Boolean,
    )

    @Query("UPDATE tasks SET sortOrder = :position WHERE id = :id")
    suspend fun setTaskOrder(id: Long, position: Int)

    @Query("UPDATE tasks SET groupId = :groupId, sortOrder = :position WHERE id = :id")
    suspend fun moveTask(id: Long, groupId: Long, position: Int)

    @Query("UPDATE tasks SET nextFireAt = :nextFireAt, dueUnseen = :unseen, notifiedOnce = :notifiedOnce WHERE id = :id")
    suspend fun setFire(id: Long, nextFireAt: Long?, unseen: Boolean, notifiedOnce: Boolean)
}
