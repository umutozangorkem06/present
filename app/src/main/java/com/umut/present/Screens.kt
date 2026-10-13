package com.umut.present

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import java.util.Locale
import kotlin.math.roundToInt

val EN: Locale = Locale.ENGLISH
val screenPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp)

fun Modifier.quietClick(onClick: () -> Unit) =
    clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)

fun openSettings(c: Context, action: String) =
    c.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun focusMinutesToday(c: Context): Int {
    val today = UsageRepo.startOfDay(System.currentTimeMillis())
    val done = Prefs.focusHistory(c).filter { it.start >= today }.sumOf { it.minutes }
    val running = if (Prefs.focusActive(c)) ((System.currentTimeMillis() - Prefs.focusStart(c)) / 60_000).toInt() else 0
    return done + running
}

@Composable
fun BigDuration(ms: Long, size: Float = 64f) {
    Row(verticalAlignment = Alignment.Bottom) {
        durationParts(ms).forEachIndexed { i, (n, unit) ->
            if (i > 0) Spacer(Modifier.width(14.dp))
            Text(n, style = Type.display.copy(fontSize = size.sp, lineHeight = size.sp))
            Spacer(Modifier.width(5.dp))
            Text(
                unit,
                Modifier.padding(bottom = (size * 0.12f).dp),
                style = Type.headline.copy(fontSize = (size * 0.34f).sp, color = Muted),
            )
        }
    }
}

// ───────────────────────────── Today ─────────────────────────────

@Composable
fun TodayScreen(resume: Int, hasUsage: Boolean, hasAcc: Boolean) {
    val c = LocalContext.current
    var stats by remember { mutableStateOf<DayStats?>(null) }
    var streak by remember { mutableIntStateOf(0) }
    var goal by remember { mutableIntStateOf(Prefs.goalMinutes(c)) }
    var editGoal by remember { mutableStateOf(false) }
    val focusToday = remember(resume) { focusMinutesToday(c) }

    LaunchedEffect(resume, hasUsage, goal) {
        if (!hasUsage) return@LaunchedEffect
        while (true) {
            val (s, st) = withContext(Dispatchers.Default) {
                UsageRepo.recentDays(c)
                UsageRepo.today(c) to UsageRepo.streak(c, goal * 60_000L)
            }
            stats = s
            streak = st
            delay(60_000)
        }
    }

    val date = remember { SimpleDateFormat("EEEE, MMMM d", EN).format(Date()) }
    val total = stats?.total ?: 0L
    val apps = stats?.apps.orEmpty().take(12)
    val top = apps.firstOrNull()?.millis ?: 1L

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding) {
        item {
            Label(date)
            Spacer(Modifier.height(24.dp))
        }
        if (!hasUsage || !hasAcc) item {
            SetupBlock(hasUsage, hasAcc)
            Spacer(Modifier.height(36.dp))
        }
        item {
            BigDuration(total)
            Spacer(Modifier.height(4.dp))
            Text("on your screen today", style = Type.small)
            Spacer(Modifier.height(28.dp))
            GoalBar(total, goal) { editGoal = true }
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                StatCell("Streak", "$streak", if (streak == 1) "day" else "days", Modifier.weight(1f))
                VLine()
                StatCell("Pickups", "${stats?.pickups ?: 0}", "", Modifier.weight(1f))
                VLine()
                StatCell("Focus", "$focusToday", "min", Modifier.weight(1f))
            }
            Spacer(Modifier.height(40.dp))
            Label("Most used")
            Spacer(Modifier.height(10.dp))
        }
        items(apps, key = { it.pkg }) { a -> UsageRow(a, a.millis.toFloat() / top) }
        if (hasUsage && stats != null && apps.isEmpty()) item {
            Text("You haven't opened anything yet today.", style = Type.small)
        }
    }

    if (editGoal) GoalDialog(goal, onDismiss = { editGoal = false }) {
        goal = it
        Prefs.setGoalMinutes(c, it)
    }
}

@Composable
private fun SetupBlock(hasUsage: Boolean, hasAcc: Boolean) {
    val c = LocalContext.current
    Column {
        Text("Before you start", style = Type.headline)
        Spacer(Modifier.height(8.dp))
        Text(
            "Present needs two Android permissions. Everything stays on your phone.",
            style = Type.small,
        )
        Spacer(Modifier.height(16.dp))
        Hairline()
        SetupRow("01", "Usage access", "To read your screen time", hasUsage) {
            openSettings(c, Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
        Hairline()
        SetupRow("02", "Accessibility", "Turn on Present Blocker in the list", hasAcc) {
            openSettings(c, Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        Hairline()
    }
}

@Composable
private fun SetupRow(n: String, title: String, desc: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !done, onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(n, style = Type.number.copy(fontSize = 17.sp, color = Faint))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body)
            Text(desc, style = Type.small)
        }
        if (done) Text("On", style = Type.small.copy(color = Sage))
        else Text("Allow  →", style = Type.body)
    }
}

@Composable
private fun GoalBar(total: Long, goalMin: Int, onClick: () -> Unit) {
    val goalMs = goalMin * 60_000L
    val over = total > goalMs
    val frac by animateFloatAsState((total.toFloat() / goalMs).coerceIn(0f, 1f), tween(700), label = "goal")
    Column(Modifier.fillMaxWidth().quietClick(onClick)) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Line)) {
            Box(
                Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                    .background(if (over) Clay else Sage)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Text(
                if (over) "${fmtDuration(total - goalMs)} over your goal" else "${fmtDuration(goalMs - total)} left",
                Modifier.weight(1f),
                style = Type.small.copy(color = if (over) Clay else Ink),
            )
            Text("Goal ${fmtDuration(goalMs)}  ✎", style = Type.small)
        }
    }
}

@Composable
private fun StatCell(title: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 4.dp)) {
        Label(title)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Type.number)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, Modifier.padding(bottom = 3.dp), style = Type.small)
            }
        }
    }
}

@Composable
private fun VLine() =
    Box(Modifier.padding(horizontal = 12.dp).width(1.dp).fillMaxHeight().background(Line))

@Composable
private fun UsageRow(a: AppUsage, frac: Float) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(a.pkg)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, Modifier.weight(1f), style = Type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(12.dp))
                Text(fmtDuration(a.millis), style = Type.small)
            }
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Line)) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).fillMaxHeight().background(Ink.copy(alpha = 0.5f)))
            }
        }
    }
}

@Composable
fun GoalDialog(current: Int, title: String = "Daily goal", onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var v by remember { mutableIntStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Raised).padding(24.dp)) {
            Label(title)
            Spacer(Modifier.height(14.dp))
            BigDuration(v * 60_000L, 44f)
            Spacer(Modifier.height(18.dp))
            Slider(
                value = v.toFloat(),
                onValueChange = { v = ((it / 15f).roundToInt() * 15).coerceIn(30, 480) },
                valueRange = 30f..480f,
                colors = SliderDefaults.colors(
                    thumbColor = Ink, activeTrackColor = Sage, inactiveTrackColor = Line,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                ),
            )
            Text("Every day you stay under this adds to your streak.", style = Type.small)
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Save") { onSave(v); onDismiss() }
        }
    }
}

// ───────────────────────────── Focus ─────────────────────────────

@Composable
fun FocusScreen(hasAcc: Boolean, goToBlock: () -> Unit) {
    val c = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var version by remember { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            Prefs.finalizeFocusIfDone(c)
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val end = remember(now, version) { Prefs.focusEnd(c) }
    val start = remember(now, version) { Prefs.focusStart(c) }
    val active = end > now
    val history = remember(version, active) { Prefs.focusHistory(c) }
    val blockedCount = remember(version) { Prefs.blocked(c).size }
    val apps = if (blockedCount == 1) "app" else "apps"

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding) {
        item {
            Label(if (active) "In focus" else "Focus")
            Spacer(Modifier.height(24.dp))
        }
        if (active) item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                val progress = ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = 3.dp.toPx()
                        val inset = Offset(sw, sw)
                        val s = Size(size.width - 2 * sw, size.height - 2 * sw)
                        drawArc(Line, 0f, 360f, false, inset, s, style = Stroke(1.dp.toPx()))
                        drawArc(Sage, -90f, 360f * progress, false, inset, s, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtClock(end - now), style = Type.display.copy(fontSize = 54.sp))
                        Text("to go", style = Type.small)
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    "$blockedCount $apps locked.\nThey unlock on their own when time's up.",
                    style = Type.small.copy(textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(32.dp))
                GhostButton("End session", color = Clay) { confirm = true }
            }
        } else item {
            Text("How long do you\nwant to focus?", style = Type.headline)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$minutes", style = Type.display.copy(fontSize = 104.sp, lineHeight = 104.sp))
                Spacer(Modifier.width(10.dp))
                Text("minutes", Modifier.padding(bottom = 16.dp), style = Type.headline.copy(fontSize = 22.sp, color = Muted))
            }
            Spacer(Modifier.height(20.dp))
            listOf(listOf(15, 25, 45), listOf(60, 90, 120)).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m -> Pill("$m", minutes == m, Modifier.weight(1f)) { minutes = m } }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(20.dp))
            if (blockedCount == 0) {
                Text("You haven't picked any apps to block yet.", style = Type.small)
                Spacer(Modifier.height(6.dp))
                Text("Pick apps  →", Modifier.quietClick(goToBlock), style = Type.body.copy(color = Sage))
            } else {
                Text("$blockedCount $apps will lock, with no opens until time's up.", style = Type.small)
            }
            if (!hasAcc) {
                Spacer(Modifier.height(8.dp))
                Text("Accessibility is off, so blocking won't work.", style = Type.small.copy(color = Clay))
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Start", enabled = blockedCount > 0) {
                Prefs.startFocus(c, minutes)
                version++
                now = System.currentTimeMillis()
            }
        }
        item {
            val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
            val week = history.filter { it.start >= weekAgo }
            val done = week.count { it.completed }
            Spacer(Modifier.height(48.dp))
            Label("History")
            Spacer(Modifier.height(6.dp))
            if (week.isNotEmpty()) {
                Text(
                    "$done ${if (done == 1) "session" else "sessions"} completed in the last 7 days · " +
                        fmtDuration(week.sumOf { it.minutes } * 60_000L),
                    style = Type.small,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        items(history.take(30)) { r -> HistoryRow(r) }
        if (history.isEmpty()) item { Text("Your first session will show up here.", style = Type.small) }
    }

    if (confirm) Dialog(onDismissRequest = { confirm = false }) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Raised).padding(24.dp)) {
            Text("End this\nsession?", style = Type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp))
            Spacer(Modifier.height(10.dp))
            Text("${fmtClock(end - now)} to go. Sessions you end early still show in your history.", style = Type.small)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Keep going") { confirm = false }
            Spacer(Modifier.height(10.dp))
            GhostButton("End it", color = Clay) {
                Prefs.cancelFocus(c)
                confirm = false
                version++
            }
        }
    }
}

@Composable
private fun HistoryRow(r: FocusRecord) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", EN) }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmt.format(Date(r.start)), style = Type.body)
                Text(
                    if (r.completed) "Completed" else "Ended early",
                    style = Type.small.copy(color = if (r.completed) Sage else Muted),
                )
            }
            Text("${r.minutes} min", style = Type.number.copy(fontSize = 19.sp))
        }
        Hairline()
    }
}

// ───────────────────────────── Week ─────────────────────────────

@Composable
fun WeekScreen(resume: Int, hasUsage: Boolean) {
    val c = LocalContext.current
    var days by remember { mutableStateOf<List<DayTotal>>(emptyList()) }
    val goal = remember(resume) { Prefs.goalMinutes(c) }
    val goalMs = goal * 60_000L

    LaunchedEffect(resume, hasUsage) {
        if (hasUsage) days = withContext(Dispatchers.Default) { UsageRepo.recentDays(c) }
    }

    val withData = days.filter { it.millis > 0 }
    val avg = if (withData.isEmpty()) 0L else withData.sumOf { it.millis } / withData.size
    val under = days.dropLast(1).count { it.millis in 1..goalMs }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding) {
        item {
            Label("Last 7 days")
            Spacer(Modifier.height(24.dp))
            if (!hasUsage) {
                Text("The weekly view needs usage access.", style = Type.small)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Allow  →",
                    Modifier.quietClick { openSettings(c, Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                    style = Type.body.copy(color = Sage),
                )
                return@item
            }
            Text("daily average", style = Type.small)
            Spacer(Modifier.height(4.dp))
            BigDuration(avg)
            Spacer(Modifier.height(10.dp))
            Text("You stayed under your goal on $under of the last 6 days.", style = Type.small)
            Spacer(Modifier.height(36.dp))
            if (days.isNotEmpty()) WeekChart(days, goalMs)
            Spacer(Modifier.height(40.dp))
            Label("Days")
            Spacer(Modifier.height(4.dp))
        }
        items(days.reversed()) { d -> DayRow(d, goalMs) }
    }
}

@Composable
private fun WeekChart(days: List<DayTotal>, goalMs: Long) {
    val maxV = maxOf(days.maxOf { it.millis }, goalMs) * 1.15f
    val dayFmt = remember { SimpleDateFormat("EEE", EN) }
    Column {
        Row {
            Spacer(Modifier.weight(1f))
            Text("- - goal ${fmtDuration(goalMs)}", style = Type.small.copy(fontSize = 11.sp))
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val slot = size.width / days.size
            val bw = slot * 0.38f
            days.forEachIndexed { i, d ->
                val h = (d.millis / maxV * size.height).coerceAtLeast(3f)
                val color = when {
                    i == days.lastIndex -> Ink
                    d.millis > goalMs -> Clay
                    else -> Sage
                }
                val r = minOf(bw / 2, h / 2)
                drawRoundRect(
                    color,
                    topLeft = Offset(slot * i + (slot - bw) / 2, size.height - h),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(r, r),
                )
            }
            val gy = size.height - goalMs / maxV * size.height
            drawLine(
                Muted, Offset(0f, gy), Offset(size.width, gy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            days.forEachIndexed { i, d ->
                Text(
                    if (i == days.lastIndex) "today" else dayFmt.format(Date(d.dayStart)).lowercase(EN),
                    Modifier.weight(1f),
                    style = Type.small.copy(fontSize = 12.sp, textAlign = TextAlign.Center, color = if (i == days.lastIndex) Ink else Muted),
                )
            }
        }
    }
}

@Composable
private fun DayRow(d: DayTotal, goalMs: Long) {
    val today = UsageRepo.startOfDay(System.currentTimeMillis())
    val name = when (d.dayStart) {
        today -> "Today"
        UsageRepo.startOfDay(today, -1) -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d", EN).format(Date(d.dayStart))
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = Type.body)
            Text(fmtDuration(d.millis), style = Type.small)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (d.millis > goalMs) Clay else Sage))
        }
        Hairline()
    }
}
