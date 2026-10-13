package com.umut.present

import org.json.JSONObject
import java.util.Calendar

/**
 * A recurring block window. [start]/[end] are minutes after midnight; when end <= start
 * the window runs past midnight (e.g. 22:00–07:00). start == end means all day.
 * [days] is a bitmask, bit 0 = Monday … bit 6 = Sunday, of the days the window *starts* on.
 */
data class Schedule(
    val id: Long,
    val name: String,
    val start: Int,
    val end: Int,
    val days: Int,
    val enabled: Boolean,
) {
    fun isOn(day: Int) = days and (1 shl day) != 0

    fun activeAt(t: Long): Boolean {
        if (!enabled || days == 0) return false
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val today = mondayIndex(cal)
        val yesterday = (today + 6) % 7
        return when {
            start == end -> isOn(today)
            start < end -> isOn(today) && minute >= start && minute < end
            else -> (isOn(today) && minute >= start) || (isOn(yesterday) && minute < end)
        }
    }

    /** When the current window closes, assuming it's active at [t]. */
    fun endsAfter(t: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val target = if (start == end) 24 * 60 else end
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (target <= minute) cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.add(Calendar.MINUTE, target)
        return cal.timeInMillis
    }

    fun timeRange() = if (start == end) "All day" else "${clock(start)} – ${clock(end)}"

    fun daysLabel(): String = when (days) {
        0b1111111 -> "Every day"
        0b0011111 -> "Weekdays"
        0b1100000 -> "Weekends"
        0 -> "No days"
        else -> DAY_SHORT.filterIndexed { i, _ -> isOn(i) }.joinToString(", ")
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("start", start).put("end", end)
        .put("days", days).put("enabled", enabled)

    companion object {
        val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val DAY_LETTER = listOf("M", "T", "W", "T", "F", "S", "S")

        fun clock(min: Int) = "%02d:%02d".format(min / 60 % 24, min % 60)

        fun mondayIndex(cal: Calendar) = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        fun fromJson(o: JSONObject) = Schedule(
            o.getLong("id"), o.getString("name"), o.getInt("start"), o.getInt("end"),
            o.getInt("days"), o.getBoolean("enabled"),
        )

        fun defaults() = listOf(
            Schedule(1, "Work", 9 * 60, 17 * 60, 0b0011111, false),
            Schedule(2, "Wind down", 22 * 60, 7 * 60, 0b1111111, false),
        )

        fun blank() = Schedule(System.currentTimeMillis(), "", 9 * 60, 17 * 60, 0b0011111, true)
    }
}
