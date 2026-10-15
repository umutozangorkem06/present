package com.umut.present

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen picker for the block list. Lives in its own activity (adjustResize) so the
 * search field stays pinned above the keyboard and results are never hidden behind it.
 */
class AppPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PresentTheme { AppPicker(onDone = ::finish) } }
    }
}

@Composable
private fun AppPicker(onDone: () -> Unit) {
    val c = LocalContext.current
    val initial = remember { Prefs.blocked(c) }
    var blocked by remember { mutableStateOf(initial) }
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val focusActive = remember { Prefs.focusActive(c) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            val launchable = UsageRepo.launchableApps(c)
            // Always show what's selected, even if it isn't in the launcher list.
            val missing = initial - launchable.map { it.pkg }.toSet()
            launchable + missing.map { AppInfo(it, UsageRepo.label(c, it)) }
        }
    }

    // Selected-at-open apps stay on top; sorting on the live set would make rows jump as you tap.
    val shown = remember(apps, query) {
        val q = query.trim()
        apps.orEmpty()
            .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true) }
            .sortedWith(compareByDescending<AppInfo> { it.pkg in initial }.thenBy { it.label.lowercase(EN) })
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Apps to block", Modifier.weight(1f))
                Text("Done", Modifier.quietClick(onDone).padding(4.dp), style = Type.body.copy(color = Sage))
            }
            Spacer(Modifier.height(20.dp))
            Text("Pick what\npulls you in.", style = Type.headline)
            Spacer(Modifier.height(8.dp))
            Text(
                "${blocked.size} selected",
                style = Type.small.copy(color = if (blocked.isEmpty()) Muted else Sage),
            )
            Spacer(Modifier.height(20.dp))
            SearchField(query) { query = it }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        ) {
            when {
                apps == null -> item { Text("Loading…", Modifier.padding(top = 12.dp), style = Type.small) }
                shown.isEmpty() -> item {
                    Text("No apps match \"${query.trim()}\".", Modifier.padding(top = 12.dp), style = Type.small)
                }
            }
            items(shown, key = { it.pkg }) { app ->
                val on = app.pkg in blocked
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            if (on && focusActive) {
                                Toast.makeText(c, "You can't remove apps during a focus session", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            blocked = if (on) blocked - app.pkg else blocked + app.pkg
                            Prefs.setBlocked(c, blocked)
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.pkg)
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, Modifier.weight(1f), style = Type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Check(on)
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Type.body,
        cursorBrush = SolidColor(Sage),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Column {
                Box(Modifier.padding(vertical = 6.dp)) {
                    if (value.isEmpty()) Text("Search apps", style = Type.body.copy(color = Faint))
                    inner()
                }
                Spacer(Modifier.height(6.dp))
                Hairline()
            }
        },
    )
}
