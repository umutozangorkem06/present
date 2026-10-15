package com.umut.present

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

data class AppInfo(val pkg: String, val label: String)
data class AppUsage(val pkg: String, val label: String, val millis: Long)
data class DayStats(val total: Long, val apps: List<AppUsage>, val pickups: Int)
data class DayTotal(val dayStart: Long, val millis: Long)

object UsageRepo {
    // Shared between the blocker service (main thread) and UI coroutines (background).
    private val labels = ConcurrentHashMap<String, String>()
    private val eligible = ConcurrentHashMap<String, Boolean>()
    @Volatile private var homes: Set<String>? = null

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasAccessibility(c: Context): Boolean {
        val enabled = Settings.Secure.getString(
            c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(c, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun startOfDay(t: Long, offsetDays: Int = 0): Long = Calendar.getInstance().run {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, offsetDays)
        timeInMillis
    }

    fun label(c: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = c.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    fun homePackages(c: Context): Set<String> = homes ?: run {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        c.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
            .also { homes = it }
    }

    /** Apps a person actually opens: has a launcher entry, isn't the home screen or us. */
    fun isUserApp(c: Context, pkg: String): Boolean = eligible.getOrPut(pkg) {
        pkg != c.packageName && pkg !in homePackages(c) &&
            c.packageManager.getLaunchIntentForPackage(pkg) != null
    }

    fun launchableApps(c: Context): List<AppInfo> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return c.packageManager.queryIntentActivities(i, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != c.packageName }
            .map { AppInfo(it, label(c, it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun stats(c: Context, start: Long, end: Long): DayStats {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        val open = HashMap<String, Pair<String, Long>>()
        val totals = HashMap<String, Long>()
        var keyguard = 0
        var screenOn = 0

        fun close(key: String, ts: Long) {
            open.remove(key)?.let { (pkg, t0) ->
                totals[pkg] = (totals[pkg] ?: 0L) + (ts - t0).coerceAtLeast(0L)
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val key = pkg + "/" + e.className
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (key !in open) open[key] = pkg to e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> close(key, e.timeStamp)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE ->
                    open.keys.toList().forEach { close(it, e.timeStamp) }
                UsageEvents.Event.SCREEN_INTERACTIVE -> screenOn++
                UsageEvents.Event.KEYGUARD_HIDDEN -> keyguard++
            }
        }
        val now = minOf(end, System.currentTimeMillis())
        open.keys.toList().forEach { close(it, now) }

        val apps = totals
            .filter { it.value >= 1000 && isUserApp(c, it.key) }
            .map { AppUsage(it.key, label(c, it.key), it.value) }
            .sortedByDescending { it.millis }
        return DayStats(apps.sumOf { it.millis }, apps, if (keyguard > 0) keyguard else screenOn)
    }

    fun today(c: Context): DayStats {
        val now = System.currentTimeMillis()
        return stats(c, startOfDay(now), now)
    }

    /** Last [days] days including today, oldest first. Persists totals for streaks. */
    fun recentDays(c: Context, days: Int = 7): List<DayTotal> {
        val now = System.currentTimeMillis()
        val saved = Prefs.dailyTotals(c)
        val result = (days - 1 downTo 0).map { d ->
            val s = startOfDay(now, -d)
            val e = if (d == 0) now else startOfDay(now, -d + 1)
            val v = maxOf(stats(c, s, e).total, saved[Prefs.dayKey(s)] ?: 0L)
            DayTotal(s, v)
        }
        Prefs.saveDailyTotals(c, result.associate { Prefs.dayKey(it.dayStart) to it.millis })
        return result
    }

    /** Consecutive finished days (before today) that stayed under the goal. */
    fun streak(c: Context, goalMs: Long): Int {
        val totals = Prefs.dailyTotals(c)
        val now = System.currentTimeMillis()
        var n = 0
        for (d in 1..365) {
            val v = totals[Prefs.dayKey(startOfDay(now, -d))] ?: break
            if (v > goalMs) break
            n++
        }
        return n
    }
}
