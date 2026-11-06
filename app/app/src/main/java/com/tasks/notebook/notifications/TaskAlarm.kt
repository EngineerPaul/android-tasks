package com.tasks.notebook.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.thread

object TaskAlarm {
    const val EXTRA_TASK_ID = "task_id"
    private const val CHANNEL = "task_due_2"
    private const val PREFS = "tasks_prefs"
    private const val KEY_VIBRATE = "vibrate"
    private val VIBRATION = longArrayOf(0, 400)
    private val gate = Mutex()

    fun vibrationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VIBRATE, true)

    fun setVibration(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_VIBRATE, enabled).apply()
        ensureChannel(context)
    }

    fun ensureChannel(context: Context) {
        val vibrate = vibrationEnabled(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL)
        val patternOk = existing?.vibrationPattern?.contentEquals(VIBRATION) == true
        if (existing != null && existing.shouldVibrate() == vibrate && (!vibrate || patternOk)) return
        if (existing != null) manager.deleteNotificationChannel(CHANNEL)
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.channel_due),
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.enableVibration(vibrate)
        if (vibrate) channel.vibrationPattern = VIBRATION
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

    fun ignoresBattery(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java)
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun schedule(context: Context, task: TaskItem) {
        val at = task.nextFireAt ?: return
        val alarm = context.getSystemService(AlarmManager::class.java)
        val fire = pending(context, task.id)
        alarm.cancel(legacyPending(context, task.id))
        alarm.cancel(clockPending(context, task.id))
        try {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context, taskId))
        alarm.cancel(legacyPending(context, taskId))
        alarm.cancel(clockPending(context, taskId))
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
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(task.title)
            .setAutoCancel(true)
            .setContentIntent(content)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply { if (task.description.isNotBlank()) setContentText(task.description) }
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

    fun batterySettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    suspend fun deliver(context: Context, taskId: Long) {
        gate.withLock {
            val app = context.applicationContext as TasksApp
            val task = app.repository.task(taskId) ?: return
            val now = System.currentTimeMillis()
            val at = task.nextFireAt ?: return
            if (at > now + 5_000L) {
                schedule(context, task)
                return
            }
            if (!task.notifiedOnce) show(context, task)
            val repeat = task.repeatEveryDays
            if (repeat == null) {
                app.repository.markFired(task.id, nextFireAt = at, unseen = true, notifiedOnce = true)
                cancel(context, task.id)
            } else {
                val next = DueRules.nextFuture(at, repeat, now)
                app.repository.markFired(task.id, nextFireAt = next, unseen = true, notifiedOnce = false)
                schedule(context, task.copy(nextFireAt = next, dueUnseen = true))
            }
        }
    }

    suspend fun catchUp(context: Context): Long? {
        val app = context.applicationContext as TasksApp
        val now = System.currentTimeMillis()
        var nextAt: Long? = null
        app.repository.tasksNow().filter { it.nextFireAt != null }.forEach { task ->
            val at = task.nextFireAt ?: return@forEach
            if (at > now + 5_000L) {
                schedule(context, task)
                val soonest = nextAt
                if (soonest == null || at < soonest) nextAt = at
            } else {
                deliver(context, task.id)
            }
        }
        return nextAt
    }

    fun idOf(intent: Intent): Long {
        intent.data?.lastPathSegment?.toLongOrNull()?.let { return it }
        return intent.getLongExtra(EXTRA_TASK_ID, -1L)
    }

    private fun pending(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.tasks.notebook.REMINDER"
            data = Uri.parse("tasks://reminder/$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun legacyPending(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun clockPending(context: Context, taskId: Long): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getActivity(
            context,
            taskId.toInt() xor 0x5A5A,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != null && intent.action != "com.tasks.notebook.REMINDER") return
        val id = TaskAlarm.idOf(intent)
        if (id < 0) return
        val pending = goAsync()
        val appContext = context.applicationContext
        val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tasks:reminder")
        wake.setReferenceCounted(false)
        wake.acquire(20_000L)
        thread(name = "reminder") {
            try {
                runBlocking { TaskAlarm.deliver(appContext, id) }
            } catch (_: Exception) {
            } finally {
                if (wake.isHeld) wake.release()
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
