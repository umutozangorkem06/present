package com.umut.present

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

// Palette: warm paper-on-ink, two quiet accents.
val Bg = Color(0xFF12110F)
val Raised = Color(0xFF1B1A17)
val Line = Color(0xFF2A2824)
val Ink = Color(0xFFEFE9DD)
val Muted = Color(0xFF8A8478)
val Faint = Color(0xFF4A4740)
val Sage = Color(0xFFA9C49A)
val Clay = Color(0xFFE08A6D)

val Serif = FontFamily.Serif

object Type {
    val display = TextStyle(fontFamily = Serif, fontSize = 64.sp, lineHeight = 66.sp, color = Ink, letterSpacing = (-1.5).sp)
    val headline = TextStyle(fontFamily = Serif, fontSize = 30.sp, lineHeight = 36.sp, color = Ink, letterSpacing = (-0.5).sp)
    val number = TextStyle(fontFamily = Serif, fontSize = 26.sp, lineHeight = 30.sp, color = Ink)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = Ink)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Muted)
    val label = TextStyle(fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium, color = Muted)
}

@Composable
fun PresentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink, onPrimary = Bg, background = Bg, onBackground = Ink,
            surface = Raised, onSurface = Ink, surfaceVariant = Raised, onSurfaceVariant = Muted,
            outline = Line, secondary = Sage, error = Clay,
        ),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(text.uppercase(java.util.Locale.ENGLISH), modifier, style = Type.label.copy(color = color))

@Composable
fun Hairline(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(1.dp).background(Line))

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (enabled) Ink else Line)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.body.copy(color = if (enabled) Bg else Muted, fontWeight = FontWeight.Medium))
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Ink, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, Line, RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.body.copy(color = if (enabled) color else Muted))
    }
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .then(if (selected) Modifier.background(Ink) else Modifier.border(1.dp, Line, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.body.copy(color = if (selected) Bg else Ink, textAlign = TextAlign.Center))
    }
}

@Composable
fun Check(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(if (checked) Modifier.background(Sage) else Modifier.border(1.5.dp, Faint, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Box(Modifier.size(8.dp).clip(CircleShape).background(Bg))
    }
}

@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (on) Sage else Line)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) Bg else Muted))
    }
}

object Icons {
    private val cache = HashMap<String, ImageBitmap?>()
    fun get(c: Context, pkg: String): ImageBitmap? = cache.getOrPut(pkg) {
        runCatching { c.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp = 34.dp) {
    val c = LocalContext.current
    val bmp = remember(pkg) { Icons.get(c, pkg) }
    if (bmp != null) {
        Image(bmp, null, Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Line))
    }
}

fun fmtDuration(ms: Long): String {
    val m = ms / 60_000
    val h = m / 60
    val mm = m % 60
    return when {
        m < 1 -> "0m"
        h == 0L -> "${mm}m"
        mm == 0L -> "${h}h"
        else -> "${h}h ${mm}m"
    }
}

/** Splits a duration into big-number/small-unit parts for display type. */
fun durationParts(ms: Long): List<Pair<String, String>> {
    val m = ms / 60_000
    val h = m / 60
    val mm = m % 60
    return if (h == 0L) listOf("$mm" to "min") else listOf("$h" to "hr", "$mm" to "min")
}

fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
