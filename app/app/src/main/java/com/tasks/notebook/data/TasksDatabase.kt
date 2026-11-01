package com.tasks.notebook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskGroup::class, TaskItem::class], version = 1, exportSchema = false)
abstract class TasksDatabase : RoomDatabase() {
    abstract fun dao(): TasksDao

    companion object {
        fun create(context: Context): TasksDatabase =
            Room.databaseBuilder(context, TasksDatabase::class.java, "tasks.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "INSERT INTO groups (name, kind, sortOrder, builtIn) VALUES " +
                                "('Сегодня', 'LIST', 0, 1)," +
                                "('Неделя', 'LIST', 1, 1)," +
                                "('Месяц', 'LIST', 2, 1)," +
                                "('Запланированные', 'SCHEDULED', 3, 1)",
                        )
                    }
                })
                .build()
    }
}
