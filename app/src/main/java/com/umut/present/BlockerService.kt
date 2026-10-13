package com.umut.present

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

enum class BlockReason { FOCUS, SCHEDULE, LIMIT }

/** Why blocked apps are locked right now; [until] is 0 when it lasts until midnight. */
data class BlockState(val reason: BlockReason, val name: String, val until: Long)

object Blocker {
    /** The rule currently locking the block list, ignoring per-app opens. */
    fun current(c: Context, todayUsage: () -> Long): BlockState? {
        val now = System.currentTimeMillis()
        Prefs.finalizeFocusIfDone(c)
        if (Prefs.focusActive(c)) return BlockState(BlockReason.FOCUS, "Focus session", Prefs.focusEnd(c))
        Prefs.schedules(c).firstOrNull { it.activeAt(now) }?.let {
            return BlockState(BlockReason.SCHEDULE, it.name.ifBlank { "Schedule" }, it.endsAfter(now))
        }
        if (Prefs.limitLock(c) && todayUsage() > Prefs.goalMinutes(c) * 60_000L) {
            return BlockState(BlockReason.LIMIT, "Daily limit", 0L)
        }
        return null
    }

    fun forApp(c: Context, pkg: String, todayUsage: () -> Long): BlockState? {
        if (pkg !in Prefs.blocked(c)) return null
        val state = current(c, todayUsage) ?: return null
        // Opens don't apply to focus sessions.
        if (state.reason != BlockReason.FOCUS && Prefs.unlockedUntil(c, pkg) > System.currentTimeMillis()) return null
        return state
    }
}

class BlockerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var usageCache = 0L
    private var usageCacheAt = 0L

    // Re-check periodically so a schedule that starts, or an open that runs out,
    // while you're already inside a blocked app still takes effect.
    private val tick = object : Runnable {
        override fun run() {
            currentPkg?.let { check(it) }
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onServiceConnected() {
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in UsageRepo.homePackages(this)) {
            currentPkg = pkg
            return
        }
        // Ignore keyboards, system overlays and other non-app windows.
        if (!UsageRepo.isUserApp(this, pkg)) return
        currentPkg = pkg
        check(pkg)
    }

    private fun todayUsage(): Long {
        val now = System.currentTimeMillis()
        if (now - usageCacheAt > 60_000 && UsageRepo.hasUsageAccess(this)) {
            usageCache = UsageRepo.today(this).total
            usageCacheAt = now
        }
        return usageCache
    }

    private fun check(pkg: String) {
        if (pkg == packageName || pkg in UsageRepo.homePackages(this)) return
        val state = Blocker.forApp(this, pkg, ::todayUsage) ?: return
        currentPkg = packageName
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("pkg", pkg)
                .putExtra("reason", state.reason.name)
                .putExtra("name", state.name)
                .putExtra("until", state.until)
        )
    }
}
