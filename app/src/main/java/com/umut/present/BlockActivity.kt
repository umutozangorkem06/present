package com.umut.present

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockActivity : ComponentActivity() {
    private var pkg by mutableStateOf("")
    private var state by mutableStateOf(BlockState(BlockReason.SCHEDULE, "", 0L))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        read(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent { PresentTheme { BlockScreen(pkg, state, ::goHome, ::useOpen) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(i: Intent) {
        pkg = i.getStringExtra("pkg") ?: ""
        state = BlockState(
            runCatching { BlockReason.valueOf(i.getStringExtra("reason")!!) }.getOrDefault(BlockReason.SCHEDULE),
            i.getStringExtra("name") ?: "",
            i.getLongExtra("until", 0L),
        )
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun useOpen() {
        if (!Prefs.useOpen(this, pkg)) return
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }
}

@Composable
private fun BlockScreen(pkg: String, state: BlockState, onLeave: () -> Unit, onOpen: () -> Unit) {
    val c = LocalContext.current
    val label = remember(pkg) { UsageRepo.label(c, pkg) }
    var wait by remember(pkg) { mutableIntStateOf(10) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val opensLeft = remember(pkg) { Prefs.opensLeftToday(c) }
    val opensPerDay = remember { Prefs.opensPerDay(c) }
    val minutes = remember { Prefs.minutesPerOpen(c) }

    LaunchedEffect(pkg) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
            if (wait > 0) wait--
        }
    }

    val breathe = rememberInfiniteTransition(label = "breathe")
    val scale by breathe.animateFloat(
        0.78f, 1f,
        infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val clock = remember { SimpleDateFormat("HH:mm", Locale.ENGLISH) }

    val (title, body) = when (state.reason) {
        BlockReason.FOCUS -> "You're in focus." to
            "$label stays locked until your session ends.\n${fmtClock(state.until - now)} to go."
        BlockReason.SCHEDULE -> "${state.name} is on." to
            "$label is blocked until ${clock.format(Date(state.until))}. Is this worth one of your opens?"
        BlockReason.LIMIT -> "That's enough for today." to
            "You've passed your daily limit. $label rests until tomorrow."
    }

    Column(
        Modifier.fillMaxSize().background(Bg).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(220.dp).scale(scale).clip(CircleShape).background(Sage.copy(alpha = 0.07f)))
            Box(Modifier.size(150.dp).scale(scale).clip(CircleShape).background(Sage.copy(alpha = 0.12f)))
            AppIcon(pkg, 56.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text("breathe in · breathe out", style = Type.small.copy(color = Faint))
        Spacer(Modifier.height(36.dp))
        Text(title, style = Type.headline.copy(textAlign = TextAlign.Center))
        Spacer(Modifier.height(12.dp))
        Text(body, style = Type.small.copy(textAlign = TextAlign.Center))
        Spacer(Modifier.weight(1f))
        PrimaryButton("Never mind, go back", onClick = onLeave)
        if (state.reason != BlockReason.FOCUS) {
            Spacer(Modifier.height(10.dp))
            when {
                opensLeft <= 0 -> GhostButton("No opens left today", enabled = false) {}
                wait > 0 -> GhostButton("Open for $minutes min · $wait", enabled = false) {}
                else -> GhostButton("Open for $minutes min", onClick = onOpen)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "$opensLeft of $opensPerDay opens left today",
                style = Type.small.copy(color = if (opensLeft == 0) Clay else Faint),
            )
        }
    }
}
