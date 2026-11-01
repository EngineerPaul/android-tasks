package com.tasks.notebook.ui.list

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasks.notebook.DueRules
import com.tasks.notebook.R
import com.tasks.notebook.data.GroupKind
import com.tasks.notebook.data.TaskGroup
import com.tasks.notebook.data.TaskItem
import com.tasks.notebook.notifications.TaskAlarm
import com.tasks.notebook.ui.AccentButton
import com.tasks.notebook.ui.AppViewModel
import com.tasks.notebook.ui.CloudCard
import com.tasks.notebook.ui.DeleteDot
import com.tasks.notebook.ui.RatingStars
import com.tasks.notebook.ui.listTitle
import com.tasks.notebook.ui.orderedTasks
import com.tasks.notebook.ui.theme.LocalPalette
import com.tasks.notebook.ui.theme.Manrope
import com.tasks.notebook.ui.theme.TasksColors
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ListScreen(vm: AppViewModel, onOpenTask: (Long?, Long) -> Unit) {
    val palette = LocalPalette.current
    val groups by vm.groups.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val scrollable = groups.filter { !it.isPinnedScheduled() }.sortedBy { it.sortOrder }
    val pinned = groups.firstOrNull { it.isPinnedScheduled() }
    val pages = scrollable + listOfNotNull(pinned)
    if (pages.isEmpty()) return
    val selected = (vm.screen as? com.tasks.notebook.ui.Screen.Lists)?.groupId ?: pages.first().id
    val pager = rememberPagerState(initialPage = pages.indexOfFirst { it.id == selected }.coerceAtLeast(0)) {
        pages.size
    }
    LaunchedEffect(selected, pages) {
        val index = pages.indexOfFirst { it.id == selected }
        if (index >= 0 && pager.currentPage != index && !pager.isScrollInProgress) {
            pager.scrollToPage(index)
        }
    }
    LaunchedEffect(pager, pages) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            val group = pages.getOrNull(page) ?: return@collect
            if ((vm.screen as? com.tasks.notebook.ui.Screen.Lists)?.groupId != group.id) vm.openGroup(group.id)
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding()) {
        NavBar(
            scrollable = scrollable,
            pinned = pinned,
            selectedId = selected,
            settings = false,
            onGroup = vm::openGroup,
            onSettings = vm::openSettings,
        )
        val current = pages.getOrNull(pager.currentPage) ?: pages.first()
        AccentButton(stringResource(R.string.add), palette.accent, palette.onAccent, { onOpenTask(null, current.id) })
        PermissionBanner(current.kindEnum() == GroupKind.SCHEDULED, vm)
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val group = pages[page]
            TaskPage(vm, group, orderedTasks(tasks, group), vm.highlightId, onOpenTask)
        }
    }
    UndoLayer(vm)
    }
}

@Composable
fun NavBar(
    scrollable: List<TaskGroup>,
    pinned: TaskGroup?,
    selectedId: Long?,
    settings: Boolean,
    onGroup: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalPalette.current
    val scroll = rememberScrollState()
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            scrollable.forEach { group ->
                val active = group.id == selectedId && !settings
                Column(
                    Modifier
                        .clickable { onGroup(group.id) }
                        .padding(horizontal = 12.dp)
                        .height(48.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (group.kindEnum() == GroupKind.SCHEDULED) {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = if (active) TasksColors.text else TasksColors.muted,
                                modifier = Modifier.padding(end = 4.dp).size(16.dp),
                            )
                        }
                        Text(
                            group.name,
                            color = if (active) TasksColors.text else TasksColors.muted,
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .height(2.dp)
                            .fillMaxWidth()
                            .background(if (active) palette.accent else androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }
        if (pinned != null) {
            val active = pinned.id == selectedId && !settings
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (active) palette.accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onGroup(pinned.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = pinned.name,
                    tint = if (active) palette.onAccent else palette.accent,
                )
            }
        }
        Box(
            Modifier.size(48.dp).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                tint = if (settings) palette.accent else TasksColors.text,
            )
        }
    }
}

@Composable
private fun PermissionBanner(scheduled: Boolean, vm: AppViewModel) {
    if (!scheduled) return
    val context = LocalContext.current
    val notify = TaskAlarm.canNotify(context)
    val exact = TaskAlarm.canScheduleExact(context)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshAlarms()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notify) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (notify && exact) return
    val text = stringResource(if (!notify) R.string.notifications_off else R.string.alarms_off)
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = TasksColors.secondary, fontFamily = Manrope, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.open_settings),
            color = palette.accent,
            fontFamily = Manrope,
            fontSize = 13.sp,
            modifier = Modifier.clickable {
                val intent = if (!notify) TaskAlarm.notificationSettings(context) else TaskAlarm.exactAlarmSettings(context)
                context.startActivity(intent)
            },
        )
    }
}

@Composable
private fun TaskPage(
    vm: AppViewModel,
    group: TaskGroup,
    tasks: List<TaskItem>,
    highlightId: Long,
    onOpenTask: (Long?, Long) -> Unit,
) {
    val palette = LocalPalette.current
    val listState = rememberLazyListState()
    LaunchedEffect(highlightId, tasks) {
        if (highlightId < 0) return@LaunchedEffect
        val index = tasks.indexOfFirst { it.id == highlightId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            kotlinx.coroutines.delay(3_000)
            vm.clearHighlight()
        }
    }
    if (tasks.isEmpty()) {
        Text(
            stringResource(R.string.empty_tasks),
            color = TasksColors.secondary,
            fontFamily = Manrope,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize().padding(top = 12.dp, bottom = 8.dp)) {
        itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
            val highlighted = task.id == highlightId
            val fill by animateColorAsState(
                when {
                    task.dueUnseen -> TasksColors.dueFill
                    highlighted -> palette.newRow
                    else -> palette.cloud
                },
                animationSpec = tween(if (highlighted) 0 else 300),
                label = "card",
            )
            CloudCard(fill, palette.cloudBorder) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                    onOpenTask(task.id, group.id)
                }) {
                    Text(
                        "${index + 1}",
                        color = TasksColors.text,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(listTitle(task.title), color = TasksColors.text, fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        if (group.kindEnum() == GroupKind.SCHEDULED && task.nextFireAt != null) {
                            val at = task.nextFireAt
                            Text(
                                DueRules.formatDate(at),
                                color = if (task.dueUnseen) TasksColors.error else TasksColors.secondary,
                                fontFamily = Manrope,
                                fontSize = 13.sp,
                            )
                            Text(DueRules.formatTime(at), color = TasksColors.secondary, fontFamily = Manrope, fontSize = 13.sp)
                            if (task.repeatEveryDays != null) {
                                Text(
                                    stringResource(R.string.repeat_line, task.repeatEveryDays),
                                    color = TasksColors.secondary,
                                    fontFamily = Manrope,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    RatingStars(task.rating, palette.accent, TasksColors.muted) { vm.setRating(task.id, it) }
                    Box(Modifier.padding(start = 8.dp)) {
                        DeleteDot { vm.deleteTask(task) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UndoLayer(vm: AppViewModel) {
    val items = vm.undo
    if (items.isEmpty()) return
    val token = vm.undoToken
    val fraction = remember(token) { Animatable(1f) }
    LaunchedEffect(token) {
        fraction.snapTo(1f)
        fraction.animateTo(0f, tween(5_000, easing = LinearEasing))
        vm.discardUndo()
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TasksColors.undoBottom)
                .clickable { vm.undoAll() },
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.value)
                    .background(TasksColors.undoTop),
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (items.size == 1) stringResource(R.string.deleted_one) else stringResource(R.string.deleted_many, items.size),
                    color = TasksColors.text,
                    fontFamily = Manrope,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.undo), color = TasksColors.text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

