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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs

@Composable
fun ListScreen(vm: AppViewModel, navScroll: ScrollState, onOpenTask: (Long?, Long) -> Unit) {
    val palette = LocalPalette.current
    val groups by vm.groups.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val scrollable = groups.filter { !it.isPinnedScheduled() }.sortedBy { it.sortOrder }
    val pinned = groups.firstOrNull { it.isPinnedScheduled() }
    val pages = scrollable + listOfNotNull(pinned)
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragGroup by remember { mutableStateOf<Long?>(null) }
    var dragIds by remember { mutableStateOf<List<Long>?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val spots = remember { mutableStateMapOf<Long, Rect>() }
    val latestPages = rememberUpdatedState(pages)
    val latestTasks = rememberUpdatedState(tasks)
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
    LaunchedEffect(tasks, dragId, dragGroup, dragIds) {
        val gid = dragGroup
        val ids = dragIds
        if (dragId != null || gid == null || ids == null) return@LaunchedEffect
        val page = pages.firstOrNull { it.id == gid } ?: return@LaunchedEffect
        val current = orderedTasks(tasks, page).map { it.id }
        if (current.toSet() == ids.toSet()) {
            dragGroup = null
            dragIds = null
        }
    }
    LaunchedEffect(pager, pages) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            val group = pages.getOrNull(page) ?: return@collect
            if ((vm.screen as? com.tasks.notebook.ui.Screen.Lists)?.groupId != group.id) vm.openGroup(group.id)
        }
    }
    fun visibleTasks(group: TaskGroup): List<TaskItem> {
        val ids = dragIds
        val gid = dragGroup
        if (ids == null || gid == null) return orderedTasks(tasks, group)
        val byId = tasks.associateBy { it.id }
        val held = ids.toSet()
        return if (group.id == gid) ids.mapNotNull { byId[it] } else orderedTasks(tasks, group).filter { it.id !in held }
    }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    val originState = rememberUpdatedState(boxOrigin)
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxOrigin = it.positionInRoot() }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val timeout = viewConfiguration.longPressTimeoutMillis.toLong()
                val jump = 72.dp.toPx()
                val row = 64.dp.toPx()
                while (true) {
                    val down = awaitPointerEventScope {
                        awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    }
                    val origin = down.position
                    var released = false
                    var slipped = false
                    kotlinx.coroutines.withTimeoutOrNull(timeout) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                                    released = true
                                    return@awaitPointerEventScope
                                }
                                if (!change.pressed) {
                                    released = true
                                    return@awaitPointerEventScope
                                }
                                if ((change.position - origin).getDistance() > slop) {
                                    slipped = true
                                    return@awaitPointerEventScope
                                }
                            }
                        }
                    }
                    if (released || slipped) continue
                    val root = originState.value + origin
                    val hit = spots.entries.firstOrNull { (_, rect) ->
                        root.x in rect.left..rect.right && root.y in rect.top..rect.bottom
                    } ?: continue
                    val taskId = hit.key
                    val startGroup = latestTasks.value.firstOrNull { it.id == taskId }?.groupId ?: continue
                    val startPage = latestPages.value.firstOrNull { it.id == startGroup } ?: continue
                    var groupId = startGroup
                    var ids = orderedTasks(latestTasks.value, startPage).map { it.id }.toMutableList()
                    var index = ids.indexOf(taskId)
                    if (index < 0) continue
                    val startIds = ids.toList()
                    dragId = taskId
                    dragGroup = groupId
                    dragIds = startIds
                    dragY = 0f
                    var accX = 0f
                    var accY = 0f
                    var axis = 0
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val delta = change.position - change.previousPosition
                            if (axis != 1) accX += delta.x
                            if (axis != 2) accY += delta.y
                            change.consume()
                            val pagesNow = latestPages.value
                            val group = pagesNow.firstOrNull { it.id == groupId }
                            if (axis != 1 && abs(accX) > jump && abs(accX) > abs(accY) && group != null) {
                                val dir = if (accX > 0f) 1 else -1
                                val same = pagesNow.filter { it.kindEnum() == group.kindEnum() }
                                val from = same.indexOfFirst { it.id == group.id }
                                val target = same.getOrNull(from + dir)
                                accX = 0f
                                if (target != null) {
                                    val landing = orderedTasks(latestTasks.value, target).map { it.id }
                                        .filter { it != taskId }
                                        .toMutableList()
                                        .also { it.add(0, taskId) }
                                    ids = landing
                                    index = 0
                                    groupId = target.id
                                    accY = 0f
                                    dragY = 0f
                                    axis = 2
                                    dragGroup = groupId
                                    dragIds = ids.toList()
                                    vm.openGroup(target.id)
                                }
                            } else if (axis != 2 && group != null && !group.isPinnedScheduled()) {
                                var guard = 0
                                while (guard++ < 64) {
                                    val shift = when {
                                        accY > row && index < ids.lastIndex -> 1
                                        accY < -row && index > 0 -> -1
                                        else -> 0
                                    }
                                    if (shift == 0) break
                                    val moved = ids.removeAt(index)
                                    index += shift
                                    ids.add(index, moved)
                                    accY -= shift * row
                                    axis = 1
                                }
                                dragIds = ids.toList()
                                dragY = when {
                                    index == 0 && accY < 0f -> 0f
                                    index == ids.lastIndex && accY > 0f -> 0f
                                    else -> accY
                                }
                            } else {
                                dragY = 0f
                            }
                        }
                    }
                    val changed = groupId != startGroup || ids.toList() != startIds
                    dragY = 0f
                    dragId = null
                    if (changed) {
                        dragGroup = groupId
                        dragIds = ids.toList()
                        vm.place(taskId, groupId, index)
                    } else {
                        dragGroup = null
                        dragIds = null
                    }
                }
            },
    ) {
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding()) {
        NavBar(
            scrollable = scrollable,
            pinned = pinned,
            selectedId = selected,
            settings = false,
            scroll = navScroll,
            centerGroupId = if (vm.highlightId >= 0) selected else null,
            onGroup = vm::openGroup,
            onSettings = vm::openSettings,
        )
        val current = pages.getOrNull(pager.currentPage) ?: pages.first()
        AccentButton(stringResource(R.string.add), palette.accent, palette.onAccent, { onOpenTask(null, current.id) })
        PermissionBanner(current.kindEnum() == GroupKind.SCHEDULED, vm)
        HorizontalPager(
            state = pager,
            userScrollEnabled = dragId == null,
            modifier = Modifier.weight(1f),
            key = { pages[it].id },
        ) { page ->
            val group = pages[page]
            TaskPage(
                vm,
                group,
                visibleTasks(group),
                vm.highlightId,
                dragId,
                dragY,
                spots,
                dragIds != null,
                onOpenTask,
            )
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
    scroll: ScrollState,
    centerGroupId: Long? = null,
    onGroup: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalPalette.current
    val places = remember { mutableStateMapOf<Long, Pair<Int, Int>>() }
    LaunchedEffect(centerGroupId) {
        val id = centerGroupId ?: return@LaunchedEffect
        val place = snapshotFlow {
            val item = places[id] ?: return@snapshotFlow null
            val viewport = scroll.viewportSize
            if (viewport <= 0) null else item to viewport
        }.filterNotNull().first()
        val (item, viewport) = place
        val target = (item.first + item.second / 2 - viewport / 2).coerceIn(0, scroll.maxValue.coerceAtLeast(0))
        scroll.animateScrollTo(target)
    }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            scrollable.forEach { group ->
                val active = group.id == selectedId && !settings
                Column(
                    Modifier
                        .onGloballyPositioned { coords ->
                            places[group.id] = coords.positionInParent().x.toInt() to coords.size.width
                        }
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
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (settings) palette.accent else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                tint = if (settings) palette.onAccent else palette.accent,
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
    dragId: Long?,
    dragY: Float,
    spots: MutableMap<Long, Rect>,
    suppressEmpty: Boolean,
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
        if (!suppressEmpty) {
            Text(
                stringResource(R.string.empty_tasks),
                color = TasksColors.secondary,
                fontFamily = Manrope,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
        return
    }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize().padding(top = 12.dp, bottom = 8.dp)) {
        itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
            val highlighted = task.id == highlightId
            val lifting = task.id == dragId
            val base = when {
                task.dueUnseen -> TasksColors.dueFill
                highlighted -> palette.newRow
                else -> palette.cloud
            }
            val fill by animateColorAsState(
                if (lifting) lerp(base, androidx.compose.ui.graphics.Color.White, 0.18f) else base,
                animationSpec = tween(if (highlighted || lifting) 0 else 300),
                label = "card",
            )
            DisposableEffect(task.id) {
                onDispose { spots.remove(task.id) }
            }
            CloudCard(
                fill,
                palette.cloudBorder,
                Modifier
                    .graphicsLayer { translationY = if (lifting) dragY else 0f }
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        spots[task.id] = Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpenTask(task.id, group.id) },
                    ),
                ) {
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


