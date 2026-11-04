package com.tasks.notebook.ui.edit

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasks.notebook.DueRules
import com.tasks.notebook.R
import com.tasks.notebook.RatingScale
import com.tasks.notebook.RepeatDays
import com.tasks.notebook.data.GroupKind
import com.tasks.notebook.data.TaskDraft
import com.tasks.notebook.ui.AppViewModel
import com.tasks.notebook.ui.list.NavBar
import com.tasks.notebook.ui.RatingStars
import com.tasks.notebook.ui.orderedTasks
import com.tasks.notebook.ui.theme.LocalPalette
import com.tasks.notebook.ui.theme.Manrope
import com.tasks.notebook.ui.theme.TasksColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: AppViewModel, taskId: Long?, originGroupId: Long, navScroll: ScrollState) {
    val palette = LocalPalette.current
    val groups by vm.groups.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val origin = groups.firstOrNull { it.id == originGroupId }
    val existing = tasks.firstOrNull { it.id == taskId }
    val kind = (existing?.let { task -> groups.firstOrNull { it.id == task.groupId } } ?: origin)?.kindEnum() ?: GroupKind.LIST
    val sameKind = groups.filter { it.kindEnum() == kind }
    var title by remember(taskId) { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember(taskId) { mutableStateOf(existing?.description.orEmpty()) }
    var rating by remember(taskId) { mutableStateOf(existing?.rating ?: RatingScale.MIN) }
    var groupId by remember(taskId, originGroupId) { mutableLongStateOf(existing?.groupId ?: originGroupId) }
    val initialDue = existing?.nextFireAt ?: DueRules.defaultDue(System.currentTimeMillis())
    var due by remember(taskId) { mutableLongStateOf(initialDue) }
    var repeatText by remember(taskId) { mutableStateOf(existing?.repeatEveryDays?.toString().orEmpty()) }
    var titleErrorText by remember { mutableStateOf<String?>(null) }
    var dueErrorText by remember { mutableStateOf<String?>(null) }
    var repeatErrorText by remember { mutableStateOf<String?>(null) }
    var linkError by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var editingDescription by remember { mutableStateOf(existing == null) }
    val lastLegal = remember(taskId) { mutableLongStateOf(existing?.nextFireAt ?: initialDue) }
    fun applyDue(next: Long) {
        due = next
        if (next >= DueRules.earliest(System.currentTimeMillis())) lastLegal.longValue = next
    }
    val context = LocalContext.current
    val group = groups.firstOrNull { it.id == groupId }
    val scheduled = kind == GroupKind.SCHEDULED
    val titleMissing = stringResource(R.string.error_title)
    val gapPattern = stringResource(R.string.error_gap)
    val repeatInvalid = stringResource(R.string.error_repeat)
    fun confirm() {
        val trimmed = title.trim()
        titleErrorText = null
        dueErrorText = null
        repeatErrorText = null
        if (trimmed.isEmpty()) titleErrorText = titleMissing
        var fire: Long? = null
        var repeat: Int? = null
        if (scheduled) {
            val parsed = due
            val now = System.currentTimeMillis()
            val floor = DueRules.earliest(now)
            val unchangedPast = existing?.nextFireAt == parsed && parsed < floor
            when {
                parsed >= floor || unchangedPast -> fire = parsed
                parsed == lastLegal.longValue -> fire = floor
                else -> dueErrorText = gapPattern.format(DueRules.formatDate(floor), DueRules.formatTime(floor))
            }
            if (repeatText.isNotEmpty()) {
                val days = repeatText.toIntOrNull()
                if (days == null || days < RepeatDays.MIN || days > RepeatDays.MAX) {
                    repeatErrorText = repeatInvalid
                } else {
                    repeat = days
                }
            }
        }
        if (titleErrorText != null || dueErrorText != null || repeatErrorText != null) return
        vm.save(existing?.id, TaskDraft(groupId, trimmed, description, rating, fire, repeat))
    }
    fun shiftRepeat(delta: Int) {
        val current = repeatText.toIntOrNull()
        repeatText = when {
            delta > 0 && current == null -> "1"
            delta < 0 && (current == null || current <= RepeatDays.MIN) -> ""
            current == null -> ""
            else -> (current + delta).coerceIn(RepeatDays.MIN, RepeatDays.MAX).toString()
        }
    }

    BackHandler { vm.closeEditor() }
    LaunchedEffect(linkError) {
        if (linkError) {
            delay(3_000)
            linkError = false
        }
    }

    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding()) {
        val scrollable = groups.filter { !it.isPinnedScheduled() }.sortedBy { it.sortOrder }
        val pinned = groups.firstOrNull { it.isPinnedScheduled() }
        NavBar(
            scrollable = scrollable,
            pinned = pinned,
            selectedId = null,
            settings = false,
            scroll = navScroll,
            onGroup = { id -> vm.leaveToGroup(id) },
            onSettings = vm::leaveToSettings,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.cloud)
                    .border(1.dp, palette.cloudBorder, CircleShape)
                    .clickable { vm.closeEditor() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.back), tint = TasksColors.text)
            }
            Text(
                header(scheduled, tasks, groupId, existing?.id, if (scheduled) due else null, descriptionReady(scheduled)),
                color = TasksColors.text,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .clickable { confirm() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, stringResource(R.string.save), tint = palette.onAccent)
            }
            Box(Modifier.padding(start = 8.dp).size(40.dp).clickable {
                if (existing != null) vm.deleteTask(existing) else vm.closeEditor()
            }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = TasksColors.delete)
            }
        }
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FieldLabel(stringResource(R.string.field_title))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = title,
                    onValueChange = {
                        if (it.length <= 500) title = it
                        titleErrorText = null
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(color = TasksColors.text, fontFamily = Manrope, fontSize = 16.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.weight(1f).background(palette.cloud, RoundedCornerShape(12.dp)).padding(12.dp),
                )
                Box(Modifier.padding(start = 8.dp)) {
                    RatingStars(rating, palette.accent, TasksColors.muted) { rating = it }
                }
            }
            titleErrorText?.let { FieldError(it) }
            FieldLabel(stringResource(R.string.field_group))
            Box {
                Text(
                    group?.name.orEmpty(),
                    color = TasksColors.text,
                    fontFamily = Manrope,
                    modifier = Modifier.fillMaxWidth().background(palette.cloud, RoundedCornerShape(12.dp)).clickable { menu = true }.padding(12.dp),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    sameKind.forEach { item ->
                        DropdownMenuItem(text = { Text(item.name) }, onClick = {
                            groupId = item.id
                            menu = false
                        })
                    }
                }
            }
            FieldLabel(stringResource(R.string.field_description))
            if (editingDescription) {
                BasicTextField(
                    value = description,
                    onValueChange = { if (it.length <= 5000) description = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = TasksColors.text, fontFamily = Manrope, fontSize = 16.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxWidth().height(120.dp).background(palette.cloud, RoundedCornerShape(12.dp)).padding(12.dp),
                )
            } else {
                Text(
                    descriptionLinks(description) { url ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {
                            linkError = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().background(palette.cloud, RoundedCornerShape(12.dp)).clickable { editingDescription = true }.padding(12.dp),
                )
            }
            if (linkError) Text(stringResource(R.string.link_failed), color = TasksColors.error, fontFamily = Manrope, fontSize = 13.sp)
            if (scheduled) {
                val now = System.currentTimeMillis()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StepButton(enabled = DueRules.shiftDay(due, -1, now) != null, right = false) {
                        DueRules.shiftDay(due, -1, now)?.let { applyDue(it) }
                        dueErrorText = null
                    }
                    Box(
                        Modifier
                            .weight(1.25f)
                            .padding(horizontal = 4.dp)
                            .background(palette.cloud, RoundedCornerShape(12.dp))
                            .clickable {
                                dueErrorText = null
                                showDate = true
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            DueRules.formatDate(due),
                            color = TasksColors.text,
                            fontFamily = Manrope,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    StepButton(enabled = true, right = true) {
                        DueRules.shiftDay(due, 1, now)?.let { applyDue(it) }
                        dueErrorText = null
                    }
                    Box(Modifier.width(16.dp))
                    StepButton(enabled = DueRules.canShiftHourLeft(due, now), right = false) {
                        applyDue(DueRules.shiftHour(due, -1, now))
                        dueErrorText = null
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .background(palette.cloud, RoundedCornerShape(12.dp))
                            .clickable {
                                dueErrorText = null
                                showTime = true
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            DueRules.formatTime(due),
                            color = TasksColors.text,
                            fontFamily = Manrope,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    StepButton(enabled = true, right = true) {
                        applyDue(DueRules.shiftHour(due, 1, now))
                        dueErrorText = null
                    }
                }
                dueErrorText?.let { FieldError(it) }
                FieldLabel(stringResource(R.string.field_repeat))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        null,
                        tint = if (repeatText.isNotEmpty()) TasksColors.text else TasksColors.muted,
                        modifier = Modifier.size(40.dp).clickable(enabled = repeatText.isNotEmpty()) {
                            shiftRepeat(-1)
                            repeatErrorText = null
                        },
                    )
                    BasicTextField(
                        value = repeatText,
                        onValueChange = { value ->
                            repeatText = value.filter { it.isDigit() }.take(3)
                            repeatErrorText = null
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TasksColors.text, fontFamily = Manrope, fontSize = 16.sp),
                        cursorBrush = SolidColor(palette.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(72.dp).background(palette.cloud, RoundedCornerShape(12.dp)).padding(12.dp),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = if ((repeatText.toIntOrNull() ?: 0) < RepeatDays.MAX) TasksColors.text else TasksColors.muted,
                        modifier = Modifier.size(40.dp).clickable(enabled = (repeatText.toIntOrNull() ?: 0) < RepeatDays.MAX) {
                            shiftRepeat(1)
                            repeatErrorText = null
                        },
                    )
                    Text(stringResource(R.string.repeat_unit), color = TasksColors.secondary, fontFamily = Manrope, modifier = Modifier.padding(start = 8.dp))
                }
                repeatErrorText?.let { FieldError(it) }
                Text(stringResource(R.string.repeat_help), color = TasksColors.muted, fontFamily = Manrope, fontSize = 12.sp)
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = DueRules.pickerMillis(due),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= DueRules.todayPickerMillis(System.currentTimeMillis())
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { applyDue(DueRules.fromPicker(it, due)) }
                    showDate = false
                }) { Text(stringResource(R.string.save)) }
            },
        ) { DatePicker(state) }
    }
    if (showTime) {
        val local = java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault())
        val state = rememberTimePickerState(local.hour, local.minute, true)
        DatePickerDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val updated = local.toLocalDate().atTime(state.hour, state.minute)
                    applyDue(updated.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                    showTime = false
                }) { Text(stringResource(R.string.save)) }
            },
        ) { TimePicker(state) }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = TasksColors.secondary, fontFamily = Manrope, fontSize = 13.sp)
}

@Composable
private fun FieldError(text: String) {
    Text(text, color = TasksColors.error, fontFamily = Manrope, fontSize = 13.sp)
}

@Composable
private fun StepButton(enabled: Boolean, right: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (enabled) palette.cloud else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, palette.cloudBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (right) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = if (enabled) TasksColors.text else TasksColors.muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun descriptionReady(scheduled: Boolean): Boolean = scheduled

private fun header(
    scheduled: Boolean,
    tasks: List<com.tasks.notebook.data.TaskItem>,
    groupId: Long,
    existingId: Long?,
    due: Long?,
    ready: Boolean,
): String {
    if (!scheduled) {
        val mine = tasks.filter { it.groupId == groupId }.sortedBy { it.sortOrder }
        val index = if (existingId == null) 0 else mine.indexOfFirst { it.id == existingId }.coerceAtLeast(0)
        return "Задача ${index + 1}"
    }
    if (!ready || due == null) return "Напоминание"
    val others = tasks.filter { it.groupId == groupId && it.id != existingId && it.nextFireAt != null }
    val before = others.count { other ->
        val at = other.nextFireAt ?: return@count false
        at < due || (at == due && existingId != null && other.id < existingId)
    }
    return "Напоминание ${before + 1}"
}

@Composable
private fun descriptionLinks(text: String, onLink: (String) -> Unit): androidx.compose.ui.text.AnnotatedString {
    val regex = Regex("https?://\\S+")
    return buildAnnotatedString {
        var cursor = 0
        for (match in regex.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            withLink(
                LinkAnnotation.Clickable(
                    tag = match.value,
                    styles = TextLinkStyles(SpanStyle(color = TasksColors.link, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { onLink(match.value) },
                ),
            ) { append(match.value) }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
}
