package com.tasks.notebook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasks.notebook.R
import com.tasks.notebook.data.GroupKind
import com.tasks.notebook.data.TaskGroup
import com.tasks.notebook.ui.AppViewModel
import com.tasks.notebook.ui.ChecklistMark
import com.tasks.notebook.ui.list.NavBar
import com.tasks.notebook.ui.theme.LocalPalette
import com.tasks.notebook.ui.theme.Manrope
import com.tasks.notebook.ui.theme.Palettes
import com.tasks.notebook.ui.theme.TasksColors

@Composable
fun SettingsScreen(vm: AppViewModel) {
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
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding()) {
        NavBar(scrollable, pinned, null, settings = true, onGroup = vm::openGroup, onSettings = {})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChecklistMark(palette.accent, palette.background, 40.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.app_name), color = TasksColors.text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                    Text(stringResource(R.string.app_caption), color = TasksColors.secondary, fontFamily = Manrope)
                }
            }
            scrollable.forEachIndexed { index, group ->
                WindowRow(group, draggable = true, onRename = {
                    rename = group
                    renameText = group.name
                }, onDelete = { pendingDelete = group }, onUp = {
                    if (index == 0) return@WindowRow
                    val ids = scrollable.map { it.id }.toMutableList()
                    val swap = ids[index - 1]
                    ids[index - 1] = ids[index]
                    ids[index] = swap
                    vm.reorderWindows(ids)
                })
            }
            pinned?.let { group ->
                WindowRow(group, draggable = false, onRename = {
                    rename = group
                    renameText = group.name
                }, onDelete = {}, onUp = {})
            }
            Text(stringResource(R.string.window_name), color = TasksColors.secondary, fontFamily = Manrope, fontSize = 13.sp)
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = TextStyle(color = TasksColors.text, fontFamily = Manrope, fontSize = 16.sp),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth().background(palette.cloud, RoundedCornerShape(12.dp)).padding(12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindChip(stringResource(R.string.kind_notes), kind == GroupKind.LIST) { kind = GroupKind.LIST }
                KindChip(stringResource(R.string.kind_reminders), kind == GroupKind.SCHEDULED) { kind = GroupKind.SCHEDULED }
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
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.language), color = TasksColors.text, fontFamily = Manrope, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.language_value), color = TasksColors.secondary, fontFamily = Manrope)
            }
            Row(
                Modifier.fillMaxWidth().clickable { themeOpen = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.theme), color = TasksColors.text, fontFamily = Manrope, modifier = Modifier.weight(1f))
                Text(paletteName(vm.paletteId), color = palette.accent, fontFamily = Manrope)
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
    if (themeOpen) {
        AlertDialog(
            onDismissRequest = { themeOpen = false },
            title = { Text(stringResource(R.string.theme)) },
            text = {
                Column {
                    Palettes.all.forEach { item ->
                        Text(
                            paletteName(item.id),
                            color = if (item.id == vm.paletteId) palette.accent else TasksColors.text,
                            fontFamily = Manrope,
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
private fun WindowRow(
    group: TaskGroup,
    draggable: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onUp: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().background(palette.cloud, RoundedCornerShape(12.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (draggable) {
            androidx.compose.material3.Icon(
                Icons.Outlined.DragHandle,
                null,
                tint = TasksColors.muted,
                modifier = Modifier.size(28.dp).clickable(onClick = onUp),
            )
        }
        Column(Modifier.weight(1f).clickable(onClick = onRename).padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (group.kindEnum() == GroupKind.SCHEDULED) {
                    androidx.compose.material3.Icon(Icons.Outlined.Schedule, null, tint = TasksColors.muted, modifier = Modifier.padding(end = 4.dp).size(16.dp))
                }
                Text(group.name, color = TasksColors.text, fontFamily = Manrope)
            }
            Text(
                stringResource(if (group.kindEnum() == GroupKind.SCHEDULED) R.string.kind_reminders else R.string.kind_notes),
                color = TasksColors.muted,
                fontFamily = Manrope,
                fontSize = 12.sp,
            )
        }
        if (!group.builtIn) {
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
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Text(
        label,
        color = if (selected) palette.onAccent else TasksColors.text,
        fontFamily = Manrope,
        modifier = Modifier
            .background(if (selected) palette.accent else palette.cloud, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun paletteName(id: String): String = when (id) {
    Palettes.teal.id -> stringResource(R.string.palette_teal)
    Palettes.violet.id -> stringResource(R.string.palette_violet)
    Palettes.coral.id -> stringResource(R.string.palette_coral)
    else -> stringResource(R.string.palette_amber)
}
