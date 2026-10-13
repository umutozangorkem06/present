package com.umut.present

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PresentTheme { App(resumeTick.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

private val tabs = listOf("Today", "Focus", "Block", "Week")

@Composable
fun App(resume: Int) {
    val c = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var hasUsage by remember { mutableStateOf(UsageRepo.hasUsageAccess(c)) }
    var hasAcc by remember { mutableStateOf(UsageRepo.hasAccessibility(c)) }
    LaunchedEffect(resume) {
        hasUsage = UsageRepo.hasUsageAccess(c)
        hasAcc = UsageRepo.hasAccessibility(c)
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> TodayScreen(resume, hasUsage, hasAcc)
                    1 -> FocusScreen(hasAcc) { tab = 2 }
                    2 -> BlockListScreen(hasAcc, hasUsage)
                    else -> WeekScreen(resume, hasUsage)
                }
            }
        }
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            tabs.forEachIndexed { i, name ->
                val selected = i == tab
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tab = i }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(name, style = Type.small.copy(color = if (selected) Ink else Muted))
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.size(4.dp).clip(CircleShape)
                            .background(if (selected) Sage else Color.Transparent)
                    )
                }
            }
        }
    }
}
