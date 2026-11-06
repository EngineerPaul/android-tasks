package com.tasks.notebook.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasks.notebook.R
import com.tasks.notebook.data.GroupKind
import com.tasks.notebook.notifications.TaskAlarm
import com.tasks.notebook.data.TaskGroup
import com.tasks.notebook.ui.AppViewModel
import com.tasks.notebook.ui.ChecklistMark
import com.tasks.notebook.ui.list.NavBar
import com.tasks.notebook.ui.theme.LocalPalette
import com.tasks.notebook.ui.theme.Manrope
import com.tasks.notebook.ui.theme.Palettes
import com.tasks.notebook.ui.theme.TasksColors

@Composable
fun SettingsScreen(vm: AppViewModel, navScroll: ScrollState) {
    val palette = LocalPalette.current
    val groups by vm.groups.collectAsState()
    val scrollable = groups.filter { !it.isPinnedScheduled() }.sortedBy { it.sortOrder }
    val pinned = groups.firstOrNull { it.isPinnedScheduled() }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(GroupKind.LIST) }
    var rename by remember { mutableStateOf<TaskGroup?>(null) }
    var renameText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TaskGroup?>(null) }
    var themeOpen by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }
    var autostartOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var draft by remember { mutableStateOf<List<Long>?>(null) }
    var lifted by remember { mutableStateOf<Long?>(null) }
    var liftY by remember { mutableFloatStateOf(0f) }
    val rowBounds = remember { androidx.compose.runtime.mutableStateMapOf<Long, Pair<Float, Float>>() }
    val shownIds = draft ?: scrollable.map { it.id }
    val shown = shownIds.mapNotNull { id -> scrollable.find { it.id == id } }
    val idsState = rememberUpdatedState(shownIds)
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding()) {
        NavBar(scrollable, pinned, null, settings = true, scroll = navScroll, onGroup = vm::openGroup, onSettings = {})
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChecklistMark(palette.accent, palette.background, 40.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.app_name), color = TasksColors.text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                    Text(stringResource(R.string.app_caption), color = TasksColors.secondary, fontFamily = Manrope)
                }
            }
            Column(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    val timeout = viewConfiguration.longPressTimeoutMillis.toLong()
                    val gap = 10.dp.toPx()
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
                        val hit = rowBounds.entries.firstOrNull { (_, span) ->
                            origin.y >= span.first && origin.y <= span.second
                        }?.key ?: continue
                        val order = idsState.value.toMutableList()
                        var index = order.indexOf(hit)
                        if (index < 0) continue
                        val span = rowBounds[hit] ?: continue
                        val row = (span.second - span.first) + gap
                        lifted = hit
                        liftY = 0f
                        var accY = 0f
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                accY += (change.position - change.previousPosition).y
                                change.consume()
                                var guard = 0
                                while (guard++ < 64) {
                                    val last = order.lastIndex
                                    if (accY > row && index < last) {
                                        val next = order[index + 1]
                                        order[index + 1] = order[index]
                                        order[index] = next
                                        index += 1
                                        accY -= row
                                    } else if (accY < -row && index > 0) {
                                        val prev = order[index - 1]
                                        order[index - 1] = order[index]
                                        order[index] = prev
                                        index -= 1
                                        accY += row
                                    } else {
                                        break
                                    }
                                }
                                draft = order.toList()
                                liftY = when {
                                    index == 0 && accY < 0f -> 0f
                                    index == order.lastIndex && accY > 0f -> 0f
                                    else -> accY
                                }
                            }
                        }
                        vm.reorderWindows(order)
                        draft = null
                        lifted = null
                        liftY = 0f
                    }
                },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                shown.forEach { group ->
                    key(group.id) {
                    WindowRow(
                        group = group,
                        lifted = lifted == group.id,
                        offsetY = if (lifted == group.id) liftY else 0f,
                        onRename = {
                            rename = group
                            renameText = group.name
                        },
                        onDelete = { pendingDelete = group },
                        onSpan = { top, bottom -> rowBounds[group.id] = top to bottom },
                    )
                    }
                }
            }
            pinned?.let { group ->
                WindowRow(
                    group = group,
                    lifted = false,
                    offsetY = 0f,
                    onRename = {
                        rename = group
                        renameText = group.name
                    },
                    onDelete = {},
                    onSpan = { _, _ -> },
                )
            }
            SectionLine()
            Text(
                stringResource(R.string.new_window),
                color = TasksColors.text,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            val datesOn = kind == GroupKind.SCHEDULED
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = TextStyle(color = TasksColors.text, fontFamily = Manrope, fontSize = 16.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.weight(1f).background(palette.cloud, RoundedCornerShape(12.dp)).padding(12.dp),
                )
                Box(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (datesOn) palette.accent else Color.Transparent)
                        .border(1.dp, if (datesOn) palette.accent else palette.cloudBorder, CircleShape)
                        .clickable { kind = if (datesOn) GroupKind.LIST else GroupKind.SCHEDULED },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = stringResource(if (datesOn) R.string.kind_reminders else R.string.kind_notes),
                        tint = if (datesOn) palette.onAccent else palette.accent,
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().height(48.dp).background(palette.accent, RoundedCornerShape(16.dp)).clickable {
                    vm.addWindow(name, kind)
                    name = ""
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.add_window), color = palette.onAccent, fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
            }
            SectionLine()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.cloud)
                    .clickable { languageOpen = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.language), color = TasksColors.text, fontFamily = Manrope, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.language_value), color = TasksColors.text, fontFamily = Manrope)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.cloud)
                    .clickable { themeOpen = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.theme), color = TasksColors.text, fontFamily = Manrope, modifier = Modifier.weight(1f))
                Text(paletteName(vm.paletteId), color = TasksColors.text, fontFamily = Manrope)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.cloud)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.vibration), color = TasksColors.text, fontFamily = Manrope, modifier = Modifier.weight(1f))
                SlideSwitch(vm.vibrationOn) { vm.updateVibration(!vm.vibrationOn) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.cloud)
                    .clickable { autostartOpen = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.autostart_title), color = TasksColors.text, fontFamily = Manrope, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.open_settings), color = TasksColors.text, fontFamily = Manrope)
            }
        }
    }
    rename?.let { group ->
        AlertDialog(
            onDismissRequest = { rename = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                BasicTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    textStyle = TextStyle(color = TasksColors.text, fontFamily = Manrope),
                    cursorBrush = SolidColor(palette.accent),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameWindow(group.id, renameText)
                    rename = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { rename = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    pendingDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            text = { Text(stringResource(R.string.delete_window, group.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteWindow(group.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (languageOpen) {
        AlertDialog(
            onDismissRequest = { languageOpen = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Text(
                    stringResource(R.string.language_value),
                    color = TasksColors.text,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clickable { languageOpen = false }.padding(vertical = 10.dp),
                )
            },
            confirmButton = {},
        )
    }
    if (autostartOpen) {
        AlertDialog(
            onDismissRequest = { autostartOpen = false },
            title = {
                Text(stringResource(R.string.autostart_title), color = TasksColors.text, fontFamily = Manrope)
            },
            text = {
                Text(stringResource(R.string.autostart_why), color = TasksColors.text, fontFamily = Manrope)
            },
            confirmButton = {
                TextButton(onClick = {
                    autostartOpen = false
                    TaskAlarm.markAutostartAsked(context)
                    try {
                        TaskAlarm.openAutostart(context)
                    } catch (_: Exception) {
                    }
                }) {
                    Text(stringResource(R.string.open_settings), color = TasksColors.text, fontFamily = Manrope)
                }
            },
            dismissButton = {
                TextButton(onClick = { autostartOpen = false }) {
                    Text(stringResource(R.string.cancel), color = TasksColors.text, fontFamily = Manrope)
                }
            },
        )
    }
    if (themeOpen) {
        AlertDialog(
            onDismissRequest = { themeOpen = false },
            title = { Text(stringResource(R.string.theme)) },
            text = {
                Column {
                    Palettes.all.forEach { item ->
                        Text(
                            paletteName(item.id),
                            color = TasksColors.text,
                            fontFamily = Manrope,
                            fontWeight = if (item.id == vm.paletteId) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable {
                                vm.selectPalette(item.id)
                                themeOpen = false
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun SectionLine() {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth(0.92f).height(1.dp).background(TasksColors.muted))
    }
}

@Composable
private fun WindowRow(
    group: TaskGroup,
    lifted: Boolean,
    offsetY: Float,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSpan: (Float, Float) -> Unit,
) {
    val palette = LocalPalette.current
    val fill = if (lifted) lerp(palette.cloud, Color.White, 0.18f) else palette.cloud
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val top = coords.positionInParent().y
                onSpan(top, top + coords.size.height)
            }
            .graphicsLayer { translationY = offsetY }
            .background(fill, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!group.isPinnedScheduled()) {
            androidx.compose.material3.Icon(
                Icons.Outlined.DragHandle,
                null,
                tint = TasksColors.muted,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRename,
                )
                .padding(horizontal = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (group.kindEnum() == GroupKind.SCHEDULED) {
                    androidx.compose.material3.Icon(Icons.Outlined.Schedule, null, tint = TasksColors.muted, modifier = Modifier.padding(end = 4.dp).size(16.dp))
                }
                Text(group.name, color = TasksColors.text, fontFamily = Manrope)
            }
        }
        if (!group.isPinnedScheduled()) {
            androidx.compose.material3.Icon(
                Icons.Outlined.Delete,
                stringResource(R.string.delete),
                tint = TasksColors.delete,
                modifier = Modifier.size(28.dp).clickable(onClick = onDelete),
            )
        }
    }
}

@Composable
private fun SlideSwitch(on: Boolean, onToggle: () -> Unit) {
    val palette = LocalPalette.current
    val travel by animateDpAsState(if (on) 24.dp else 0.dp, tween(180), label = "vibrate")
    Box(
        Modifier
            .width(52.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) palette.accent else palette.background)
            .border(1.dp, if (on) palette.accent else palette.cloudBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle),
    ) {
        Box(
            Modifier
                .offset(x = travel + 2.dp, y = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (on) palette.onAccent else TasksColors.secondary),
        )
    }
}

@Composable
private fun paletteName(id: String): String = when (id) {
    Palettes.teal.id -> stringResource(R.string.palette_teal)
    Palettes.violet.id -> stringResource(R.string.palette_violet)
    Palettes.coral.id -> stringResource(R.string.palette_coral)
    else -> stringResource(R.string.palette_amber)
}
