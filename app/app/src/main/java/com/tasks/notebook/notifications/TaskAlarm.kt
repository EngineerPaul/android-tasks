package com.tasks.notebook.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tasks.notebook.DueRules
import com.tasks.notebook.MainActivity
import com.tasks.notebook.R
import com.tasks.notebook.TasksApp
import com.tasks.notebook.data.TaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TaskAlarm {
    const val EXTRA_TASK_ID = "task_id"
    private const val CHANNEL = "task_due"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.channel_due),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canScheduleExact(context: Context): Boolean {
        val alarm = context.getSystemService(AlarmManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarm.canScheduleExactAlarms() else true
    }

    fun schedule(context: Context, task: TaskItem) {
        val at = task.nextFireAt ?: return
        if (!canScheduleExact(context) || !canNotify(context)) {
            cancel(context, task.id)
            return
        }
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = pending(context, task.id)
        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    fun cancel(context: Context, taskId: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context, taskId))
    }

    fun show(context: Context, task: TaskItem) {
        if (!canNotify(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val content = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = task.description.ifBlank { context.getString(R.string.notification_body) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(task.title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(task.id.toInt(), notification)
    }

    fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    fun exactAlarmSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else {
            notificationSettings(context)
        }

    suspend fun deliver(context: Context, taskId: Long) {
        val app = context.applicationContext as TasksApp
        val task = app.repository.task(taskId) ?: return
        val now = System.currentTimeMillis()
        show(context, task)
        val repeat = task.repeatEveryDays
        if (repeat == null) {
            app.repository.markFired(task.id, nextFireAt = task.nextFireAt, unseen = true, notifiedOnce = true)
            cancel(context, task.id)
        } else {
            val base = task.nextFireAt ?: now
            val next = DueRules.nextFuture(base, repeat, now)
            app.repository.markFired(task.id, nextFireAt = next, unseen = true, notifiedOnce = false)
            schedule(context, task.copy(nextFireAt = next, dueUnseen = true))
        }
    }

    suspend fun catchUp(context: Context) {
        val app = context.applicationContext as TasksApp
        val now = System.currentTimeMillis()
        app.repository.tasksNow().filter { it.nextFireAt != null }.forEach { task ->
            val at = task.nextFireAt ?: return@forEach
            if (at > now) {
                schedule(context, task)
                return@forEach
            }
            val repeat = task.repeatEveryDays
            if (repeat == null) {
                app.repository.markFired(task.id, nextFireAt = at, unseen = true, notifiedOnce = task.notifiedOnce)
                cancel(context, task.id)
            } else {
                val next = DueRules.nextFuture(at, repeat, now)
                app.repository.markFired(task.id, nextFireAt = next, unseen = true, notifiedOnce = false)
                schedule(context, task.copy(nextFireAt = next))
            }
        }
    }

    private fun pending(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(TaskAlarm.EXTRA_TASK_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TaskAlarm.deliver(context, id)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TaskAlarm.ensureChannel(context)
                TaskAlarm.catchUp(context)
            } finally {
                pending.finish()
            }
        }
    }
}
