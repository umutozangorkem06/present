package com.umut.present

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun BlockListScreen(resume: Int, hasAcc: Boolean, hasUsage: Boolean) {
    val c = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    // Re-read on resume so changes made in the app picker show up.
    val selected = remember(resume) {
        Prefs.blocked(c).map { AppInfo(it, UsageRepo.label(c, it)) }.sortedBy { it.label.lowercase(EN) }
    }
    var schedules by remember { mutableStateOf(Prefs.schedules(c)) }
    var limit by remember { mutableStateOf(Prefs.limitLock(c)) }
    var goal by remember { mutableIntStateOf(Prefs.goalMinutes(c)) }
    var opensPerDay by remember { mutableIntStateOf(Prefs.opensPerDay(c)) }
    var minutesPerOpen by remember { mutableIntStateOf(Prefs.minutesPerOpen(c)) }
    val opensUsed = remember(version) { Prefs.opensUsedToday(c) }
    var status by remember { mutableStateOf<BlockState?>(null) }
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var editLimit by remember { mutableStateOf(false) }

    fun refreshSchedules() {
        schedules = Prefs.schedules(c)
        version++
    }

    LaunchedEffect(version, limit, goal, hasUsage) {
        while (true) {
            status = withContext(Dispatchers.Default) {
                Blocker.current(c) { if (hasUsage) UsageRepo.today(c).total else 0L }
            }
            delay(15_000)
        }
    }

    val now = System.currentTimeMillis()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding) {
        item {
            Label("Block")
            Spacer(Modifier.height(24.dp))
            Text("What do you want\nto keep away?", style = Type.headline)
            Spacer(Modifier.height(16.dp))
            StatusLine(status)
            if (!hasAcc) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Accessibility is off, so nothing will lock.  Turn on  →",
                    Modifier.quietClick { openSettings(c, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    style = Type.small.copy(color = Clay),
                )
            }

            // Apps
            Spacer(Modifier.height(40.dp))
            Row {
                Label("Apps to block", Modifier.weight(1f))
                Label("${selected.size} selected", color = if (selected.isEmpty()) Muted else Sage)
            }
            Spacer(Modifier.height(12.dp))
            Hairline()
        }
        items(selected, key = { "app:" + it.pkg }) { app ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.pkg)
                Spacer(Modifier.width(14.dp))
                Text(app.label, Modifier.weight(1f), style = Type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            if (selected.isEmpty()) {
                Text(
                    "Nothing selected yet. Pick the apps that pull you in.",
                    Modifier.padding(vertical = 14.dp),
                    style = Type.small,
                )
            }
            Spacer(Modifier.height(12.dp))
            GhostButton(if (selected.isEmpty()) "Choose apps" else "Edit apps") {
                c.startActivity(Intent(c, AppPickerActivity::class.java))
            }

            // Schedules
            Spacer(Modifier.height(40.dp))
            Label("Schedules")
            Spacer(Modifier.height(6.dp))
            Text("Blocked apps lock during these hours.", style = Type.small)
            Spacer(Modifier.height(12.dp))
            Hairline()
        }
        items(schedules, key = { it.id }) { s ->
            ScheduleRow(s, active = s.activeAt(now), onClick = { editing = s }) { on ->
                Prefs.upsertSchedule(c, s.copy(enabled = on))
                refreshSchedules()
            }
        }
        item {
            Text(
                "+  Add schedule",
                Modifier.fillMaxWidth().quietClick { editing = Schedule.blank() }.padding(vertical = 16.dp),
                style = Type.body.copy(color = Sage),
            )
            Hairline()

            // Daily limit
            Spacer(Modifier.height(40.dp))
            Label("Daily limit")
            Spacer(Modifier.height(12.dp))
            Hairline()
            ToggleRow(
                "Lock when I pass my limit",
                "Once your screen time for the day passes the limit, blocked apps stay locked until midnight.",
                limit,
            ) { limit = it; Prefs.setLimitLock(c, it); version++ }
            Hairline()
            Row(
                Modifier.fillMaxWidth().clickable { editLimit = true }.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Limit", Modifier.weight(1f), style = Type.body)
                Text(fmtDuration(goal * 60_000L) + "  ✎", style = Type.body.copy(color = Muted))
            }
            Hairline()

            // Opens
            Spacer(Modifier.height(40.dp))
            Row {
                Label("Opens", Modifier.weight(1f))
                Label("$opensUsed of $opensPerDay used today", color = if (opensUsed >= opensPerDay) Clay else Muted)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "When a schedule or your limit locks an app, you can still open it a few times a day. Focus sessions have no opens.",
                style = Type.small,
            )
            Spacer(Modifier.height(12.dp))
            Hairline()
            StepperRow("Opens per day", opensPerDay, 0..10) { opensPerDay = it; Prefs.setOpensPerDay(c, it) }
            Hairline()
            StepperRow("Minutes per open", minutesPerOpen, 1..30) { minutesPerOpen = it; Prefs.setMinutesPerOpen(c, it) }
            Hairline()
        }
    }

    editing?.let { s ->
        val isNew = schedules.none { it.id == s.id }
        ScheduleDialog(
            s, isNew,
            onDismiss = { editing = null },
            onSave = { Prefs.upsertSchedule(c, it); refreshSchedules(); editing = null },
            onDelete = { Prefs.deleteSchedule(c, s.id); refreshSchedules(); editing = null },
        )
    }

    if (editLimit) GoalDialog(goal, title = "Daily limit", onDismiss = { editLimit = false }) {
        goal = it
        Prefs.setGoalMinutes(c, it)
        version++
    }
}

@Composable
private fun StatusLine(s: BlockState?) {
    val clock = remember { SimpleDateFormat("HH:mm", EN) }
    val (text, dot) = when (s?.reason) {
        null -> "Nothing is locked right now." to Faint
        BlockReason.FOCUS -> "Focus session · until ${clock.format(Date(s.until))}" to Sage
        BlockReason.SCHEDULE -> "${s.name} is on · until ${clock.format(Date(s.until))}" to Sage
        BlockReason.LIMIT -> "Daily limit reached · locked until midnight" to Clay
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Type.small.copy(color = if (s == null) Muted else Ink))
    }
}

@Composable
private fun ScheduleRow(s: Schedule, active: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = Type.body.copy(color = if (s.enabled) Ink else Muted))
                    if (active) {
                        Spacer(Modifier.width(8.dp))
                        Text("on now", style = Type.small.copy(color = Sage, fontSize = 12.sp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("${s.timeRange()} · ${s.daysLabel()}", style = Type.small)
            }
            Spacer(Modifier.width(16.dp))
            Toggle(s.enabled, onChange = onToggle)
        }
        Hairline()
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().quietClick { onChange(!on) }.padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = Type.small)
        }
        Spacer(Modifier.width(16.dp))
        Toggle(on, onChange = onChange)
    }
}

@Composable
private fun StepperRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = Type.body)
        RoundButton("−", value > range.first) { onChange(value - 1) }
        Text("$value", Modifier.width(48.dp), style = Type.number.copy(fontSize = 22.sp, textAlign = TextAlign.Center))
        RoundButton("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun RoundButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).border(1.dp, Line, CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = Type.body.copy(fontSize = 18.sp, color = if (enabled) Ink else Faint))
    }
}

// ───────────────────────────── Schedule editor ─────────────────────────────

@Composable
private fun ScheduleDialog(
    initial: Schedule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableIntStateOf(initial.start) }
    var end by remember { mutableIntStateOf(initial.end) }
    var days by remember { mutableIntStateOf(initial.days) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Raised).padding(24.dp)) {
            val isStart = picking
            if (isStart == null) {
                Label(if (isNew) "New schedule" else "Edit schedule")
                Spacer(Modifier.height(12.dp))
                val nameStyle = Type.headline.copy(fontSize = 26.sp)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    textStyle = nameStyle,
                    cursorBrush = SolidColor(Sage),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Column {
                            Box {
                                if (name.isEmpty()) Text("Name it", style = nameStyle.copy(color = Faint))
                                inner()
                            }
                            Spacer(Modifier.height(8.dp))
                            Hairline()
                        }
                    },
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth()) {
                    TimeCell("Starts", start, Modifier.weight(1f)) { picking = true }
                    Spacer(Modifier.width(10.dp))
                    TimeCell("Ends", end, Modifier.weight(1f)) { picking = false }
                }
                Spacer(Modifier.height(22.dp))
                Label("Repeats on")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Schedule.DAY_LETTER.forEachIndexed { i, letter ->
                        val on = days and (1 shl i) != 0
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .then(if (on) Modifier.background(Ink) else Modifier.border(1.dp, Line, CircleShape))
                                .clickable { days = days xor (1 shl i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(letter, style = Type.small.copy(color = if (on) Bg else Muted))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        start == end -> "Locks all day."
                        end < start -> "Runs past midnight and ends the next morning."
                        else -> "Locks for ${fmtDuration((end - start) * 60_000L)} each time."
                    },
                    style = Type.small,
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Save", enabled = days != 0) {
                    onSave(initial.copy(name = name.trim().ifBlank { "Schedule" }, start = start, end = end, days = days))
                }
                if (!isNew) {
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Delete schedule", color = Clay, onClick = onDelete)
                }
            } else {
                val value = if (isStart) start else end
                fun set(v: Int) { if (isStart) start = v else end = v }
                Label(if (isStart) "Starts at" else "Ends at")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Wheel(24, value / 60, Modifier.weight(1f), { "%02d".format(it) }) { h ->
                        set(h * 60 + (if (isStart) start else end) % 60)
                    }
                    Text(":", style = Type.number.copy(fontSize = 30.sp))
                    Wheel(12, (value % 60) / 5, Modifier.weight(1f), { "%02d".format(it * 5) }) { m ->
                        set((if (isStart) start else end) / 60 * 60 + m * 5)
                    }
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Done") { picking = null }
            }
        }
    }
}

@Composable
private fun TimeCell(title: String, minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).border(1.dp, Line, shape).clickable(onClick = onClick).padding(14.dp)) {
        Label(title)
        Spacer(Modifier.height(6.dp))
        Text(Schedule.clock(minutes), style = Type.number.copy(fontSize = 30.sp))
    }
}

/** A looping, snapping number wheel; three rows tall with the middle one selected. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(count: Int, initial: Int, modifier: Modifier, text: (Int) -> String, onChange: (Int) -> Unit) {
    val itemH = 48.dp
    val loops = 400
    val state = rememberLazyListState(initialFirstVisibleItemIndex = count * (loops / 2) + initial - 1)
    val fling = rememberSnapFlingBehavior(state)
    val itemPx = with(LocalDensity.current) { itemH.toPx() }
    val center by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + (if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0) + 1
        }
    }
    val latest by rememberUpdatedState(onChange)
    LaunchedEffect(center) { latest(center % count) }

    Box(modifier.height(itemH * 3)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(itemH))
            Hairline()
            Spacer(Modifier.height(itemH - 2.dp))
            Hairline()
        }
        LazyColumn(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count * loops) { i ->
                val selected = i == center
                Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text(i % count),
                        style = Type.number.copy(
                            fontSize = if (selected) 30.sp else 22.sp,
                            color = if (selected) Ink else Faint,
                        ),
                    )
                }
            }
        }
    }
}
