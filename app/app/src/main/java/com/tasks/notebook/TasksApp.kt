package com.tasks.notebook

import android.app.Application
import com.tasks.notebook.data.TasksDatabase
import com.tasks.notebook.data.TasksRepository
import com.tasks.notebook.notifications.TaskAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TasksApp : Application() {
    lateinit var repository: TasksRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = TasksDatabase.create(this)
        repository = TasksRepository(database.dao(), this)
        TaskAlarm.ensureChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            TaskAlarm.catchUp(this@TasksApp)
        }
    }
}
