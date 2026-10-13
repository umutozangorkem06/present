package com.umut.present

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FocusRecord(val start: Long, val minutes: Int, val completed: Boolean)

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("present", Context.MODE_PRIVATE)

    fun blocked(c: Context): Set<String> = sp(c).getStringSet("blocked", emptySet())!!.toSet()
    fun setBlocked(c: Context, s: Set<String>) =
        sp(c).edit().putStringSet("blocked", HashSet(s)).apply()

    fun limitLock(c: Context) = sp(c).getBoolean("limitLock", false)
    fun setLimitLock(c: Context, v: Boolean) = sp(c).edit().putBoolean("limitLock", v).apply()

    fun goalMinutes(c: Context) = sp(c).getInt("goal", 180)
    fun setGoalMinutes(c: Context, v: Int) = sp(c).edit().putInt("goal", v).apply()

    // Opens: how many times a day a blocked app may be opened, and for how long.
    fun opensPerDay(c: Context) = sp(c).getInt("opensPerDay", 5)
    fun setOpensPerDay(c: Context, v: Int) = sp(c).edit().putInt("opensPerDay", v).apply()

    fun minutesPerOpen(c: Context) = sp(c).getInt("minutesPerOpen", 5)
    fun setMinutesPerOpen(c: Context, v: Int) = sp(c).edit().putInt("minutesPerOpen", v).apply()

    fun opensUsedToday(c: Context) = sp(c).getInt("opens_" + dayKey(System.currentTimeMillis()), 0)
    fun opensLeftToday(c: Context) = (opensPerDay(c) - opensUsedToday(c)).coerceAtLeast(0)

    /** Spends one open on [pkg]. Returns false if none are left. */
    fun useOpen(c: Context, pkg: String): Boolean {
        if (opensLeftToday(c) <= 0) return false
        val o = JSONObject(sp(c).getString("unlock", "{}")!!)
        o.put(pkg, System.currentTimeMillis() + minutesPerOpen(c) * 60_000L)
        sp(c).edit().putString("unlock", o.toString())
            .putInt("opens_" + dayKey(System.currentTimeMillis()), opensUsedToday(c) + 1)
            .apply()
        return true
    }

    fun unlockedUntil(c: Context, pkg: String): Long =
        JSONObject(sp(c).getString("unlock", "{}")!!).optLong(pkg, 0L)

    // Schedules
    fun schedules(c: Context): List<Schedule> {
        val raw = sp(c).getString("schedules", null)
            ?: return Schedule.defaults().also { saveSchedules(c, it) }
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { Schedule.fromJson(arr.getJSONObject(it)) }
    }

    fun saveSchedules(c: Context, list: List<Schedule>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp(c).edit().putString("schedules", arr.toString()).apply()
    }

    fun upsertSchedule(c: Context, s: Schedule) {
        val list = schedules(c).toMutableList()
        val i = list.indexOfFirst { it.id == s.id }
        if (i >= 0) list[i] = s else list.add(s)
        saveSchedules(c, list)
    }

    fun deleteSchedule(c: Context, id: Long) = saveSchedules(c, schedules(c).filter { it.id != id })

    // Focus sessions
    fun focusStart(c: Context) = sp(c).getLong("focusStart", 0L)
    fun focusEnd(c: Context) = sp(c).getLong("focusEnd", 0L)
    fun focusMinutes(c: Context) = sp(c).getInt("focusMin", 0)
    fun focusActive(c: Context) = focusEnd(c) > System.currentTimeMillis()

    fun startFocus(c: Context, minutes: Int) {
        val now = System.currentTimeMillis()
        sp(c).edit()
            .putLong("focusStart", now)
            .putLong("focusEnd", now + minutes * 60_000L)
            .putInt("focusMin", minutes)
            .apply()
    }

    fun cancelFocus(c: Context) {
        if (focusEnd(c) == 0L) return
        val elapsed = ((System.currentTimeMillis() - focusStart(c)) / 60_000).toInt()
        addRecord(c, FocusRecord(focusStart(c), elapsed, false))
        clearFocus(c)
    }

    fun finalizeFocusIfDone(c: Context) {
        val end = focusEnd(c)
        if (end != 0L && end <= System.currentTimeMillis()) {
            addRecord(c, FocusRecord(focusStart(c), focusMinutes(c), true))
            clearFocus(c)
        }
    }

    private fun clearFocus(c: Context) =
        sp(c).edit().remove("focusStart").remove("focusEnd").remove("focusMin").apply()

    fun focusHistory(c: Context): List<FocusRecord> {
        val arr = JSONArray(sp(c).getString("focusHistory", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FocusRecord(o.getLong("s"), o.getInt("m"), o.getBoolean("ok"))
        }.sortedByDescending { it.start }
    }

    private fun addRecord(c: Context, r: FocusRecord) {
        val arr = JSONArray(sp(c).getString("focusHistory", "[]"))
        arr.put(JSONObject().put("s", r.start).put("m", r.minutes).put("ok", r.completed))
        sp(c).edit().putString("focusHistory", arr.toString()).apply()
    }

    // Daily screen-time totals, kept so streaks survive the system pruning old usage events
    fun dailyTotals(c: Context): Map<String, Long> {
        val o = JSONObject(sp(c).getString("daily", "{}")!!)
        return o.keys().asSequence().associateWith { o.getLong(it) }
    }

    fun saveDailyTotals(c: Context, values: Map<String, Long>) {
        val o = JSONObject(sp(c).getString("daily", "{}")!!)
        values.forEach { (k, v) -> o.put(k, v) }
        sp(c).edit().putString("daily", o.toString()).apply()
    }

    fun dayKey(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))
}
