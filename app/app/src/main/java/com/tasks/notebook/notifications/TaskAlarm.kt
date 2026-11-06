package com.tasks.notebook.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
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
    private const val KEY_AUTOSTART = "autostart_asked_3"
    private const val KEY_CHAIN = "chain_at"
    private const val CHAIN_GAP = 2_000L
    private val VIBRATION = longArrayOf(0, 400)
    private val gate = Mutex()

    fun vibrationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_VIBRATE, true)

    fun setVibration(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_VIBRATE, enabled).apply()
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL)
        if (existing != null && existing.shouldVibrate() == enabled) return
        if (existing != null) manager.deleteNotificationChannel(CHANNEL)
        createChannel(context, manager, enabled)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        createChannel(context, manager, vibrationEnabled(context))
    }

    private fun createChannel(context: Context, manager: NotificationManager, vibrate: Boolean) {
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
        alarm.cancel(legacyPending(context, task.id))
        scheduleAt(context, task.id, at)
    }

    private fun scheduleAt(context: Context, taskId: Long, at: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val fire = pending(context, taskId)
        try {
            alarm.setAlarmClock(AlarmManager.AlarmClockInfo(at, openTask(context, taskId)), fire)
        } catch (_: SecurityException) {
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
            } catch (_: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
            }
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context, taskId))
        alarm.cancel(legacyPending(context, taskId))
    }

    fun show(context: Context, task: TaskItem) {
        if (!canNotify(context)) return
        val content = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            openIntent(context, task.id),
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
        NotificationManagerCompat.from(context).notify(task.id.toString(), task.id.toInt(), notification)
    }

    fun dismiss(context: Context, taskId: Long) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(taskId.toString(), taskId.toInt())
        manager.cancel(taskId.toInt())
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

    fun autostartAsked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTOSTART, false)

    fun markAutostartAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTOSTART, true).apply()
    }

    fun openAutostart(context: Context) {
        val brand = Build.MANUFACTURER.lowercase()
        val ordered = vendorScreens.sortedByDescending { screen -> screen.brands.any { brand.contains(it) } }
        for (screen in ordered) {
            if (launch(context, screen.component)) return
        }
        val fallbacks = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
        )
        for (intent in fallbacks) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
    }

    private fun launch(context: Context, component: ComponentName): Boolean =
        try {
            context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }

    fun openId(intent: Intent?): Long {
        val data = intent?.data
        if (data != null && data.scheme == "tasks" && data.host == "open") {
            data.lastPathSegment?.toLongOrNull()?.let { return it }
        }
        return intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L
    }

    suspend fun deliver(context: Context, taskId: Long) {
        catchUp(context, taskId)
    }

    suspend fun catchUp(context: Context, preferId: Long = -1L): Long? {
        gate.withLock { postDue(context, preferId) }
        return null
    }

    private suspend fun postDue(context: Context, preferId: Long) {
        val app = context.applicationContext as TasksApp
        val now = System.currentTimeMillis()
        val due = ArrayList<TaskItem>()
        val future = ArrayList<TaskItem>()
        for (task in app.repository.tasksNow()) {
            val at = task.nextFireAt ?: continue
            if (at <= now + 30_000L) due.add(task) else future.add(task)
        }
        for (task in future) schedule(context, task)
        for (task in due) {
            if (task.notifiedOnce && task.repeatEveryDays == null) cancel(context, task.id)
        }
        val waiting = due.filter { !it.notifiedOnce }.sortedWith(
            compareByDescending<TaskItem> { it.id == preferId }.thenBy { it.id },
        )
        if (waiting.isEmpty()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_CHAIN).commit()
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val chainAt = prefs.getLong(KEY_CHAIN, 0L)
        if (now + 1_500L < chainAt) {
            waiting.forEachIndexed { index, task -> scheduleAt(context, task.id, chainAt + CHAIN_GAP * index) }
            return
        }
        val head = waiting.first()
        show(context, head)
        val repeat = head.repeatEveryDays
        val at = head.nextFireAt ?: return
        if (repeat == null) {
            app.repository.markFired(head.id, nextFireAt = at, unseen = true, notifiedOnce = true)
            cancel(context, head.id)
        } else {
            val next = DueRules.nextFuture(at, repeat, now)
            app.repository.markFired(head.id, nextFireAt = next, unseen = true, notifiedOnce = false)
            schedule(context, head.copy(nextFireAt = next, dueUnseen = true))
        }
        val rest = waiting.drop(1)
        if (rest.isEmpty()) {
            prefs.edit().remove(KEY_CHAIN).commit()
        } else {
            val start = now + CHAIN_GAP
            rest.forEachIndexed { index, task -> scheduleAt(context, task.id, start + CHAIN_GAP * index) }
            prefs.edit().putLong(KEY_CHAIN, start).commit()
        }
    }

    fun idOf(intent: Intent): Long {
        intent.data?.lastPathSegment?.toLongOrNull()?.let { return it }
        return intent.getLongExtra(EXTRA_TASK_ID, -1L)
    }

    private fun openIntent(context: Context, taskId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("tasks://open/$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
        }

    private fun openTask(context: Context, taskId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            taskId.toInt() xor 0x5A5A,
            openIntent(context, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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

    private data class VendorScreen(val brands: Set<String>, val component: ComponentName)

    private val vendorScreens = listOf(
        VendorScreen(setOf("tecno", "infinix", "itel"), ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")),
        VendorScreen(setOf("tecno", "infinix", "itel"), ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.autostart.AutoStartActivity")),
        VendorScreen(setOf("tecno", "infinix", "itel"), ComponentName("com.transsion.security", "com.transsion.security.autostart.AutoStartActivity")),
        VendorScreen(setOf("xiaomi", "redmi", "poco"), ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        VendorScreen(setOf("oppo", "realme"), ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        VendorScreen(setOf("oppo", "realme"), ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
        VendorScreen(setOf("oppo", "realme"), ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        VendorScreen(setOf("oneplus"), ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
        VendorScreen(setOf("vivo", "iqoo"), ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        VendorScreen(setOf("vivo", "iqoo"), ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        VendorScreen(setOf("vivo", "iqoo"), ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        VendorScreen(setOf("huawei"), ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        VendorScreen(setOf("huawei"), ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        VendorScreen(setOf("honor"), ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        VendorScreen(setOf("samsung"), ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        VendorScreen(setOf("samsung"), ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        VendorScreen(setOf("asus"), ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),
        VendorScreen(setOf("asus"), ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")),
        VendorScreen(setOf("nokia", "hmd"), ComponentName("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity")),
        VendorScreen(setOf("meizu"), ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")),
        VendorScreen(setOf("letv", "leeco"), ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
    )

    private fun legacyPending(context: Context, taskId: Long): PendingIntent {
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
                runBlocking { TaskAlarm.catchUp(appContext, id) }
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
