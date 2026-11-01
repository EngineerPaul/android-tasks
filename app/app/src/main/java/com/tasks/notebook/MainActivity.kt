package com.tasks.notebook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tasks.notebook.notifications.TaskAlarm
import com.tasks.notebook.ui.AppViewModel
import com.tasks.notebook.ui.Screen
import com.tasks.notebook.ui.edit.EditScreen
import com.tasks.notebook.ui.list.ListScreen
import com.tasks.notebook.ui.settings.SettingsScreen
import com.tasks.notebook.ui.theme.TasksTheme

class MainActivity : ComponentActivity() {
    private val openToken = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparent = Color.Transparent.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(transparent),
            navigationBarStyle = SystemBarStyle.dark(transparent),
        )
        setContent {
            val vm: AppViewModel = viewModel()
            val nav = rememberNavController()
            val token = openToken.intValue
            LaunchedEffect(token) { openTask(vm, intent) }
            TasksTheme(vm.palette()) {
                Box(Modifier.fillMaxSize()) {
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            when (val screen = vm.screen) {
                                is Screen.Lists -> ListScreen(vm) { taskId, groupId -> vm.openEditor(taskId, groupId) }
                                Screen.Settings -> SettingsScreen(vm)
                                is Screen.Editor -> EditScreen(vm, screen.taskId, screen.originGroupId)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openToken.intValue += 1
    }

    private fun openTask(vm: AppViewModel, intent: Intent?) {
        val id = intent?.getLongExtra(TaskAlarm.EXTRA_TASK_ID, -1L) ?: -1L
        if (id >= 0) vm.openFromNotification(id)
    }
}
