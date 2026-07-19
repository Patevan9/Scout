package com.example.scoutface

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar

data class CalendarEvent(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean
)

// Read-only calendar access for Calendar Awareness. Every method here does a single
// ContentResolver.query() against CalendarContract.Instances — never insert/update/delete.
// The projection below is the entire set of fields ever read: title, start, end, all-day.
// No description, attendees, location, or reminders are queried or stored anywhere.
class CalendarReader(private val context: Context) {

    private val projection = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY
    )

    private fun hasPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun eventsBetween(startMs: Long, endMs: Long): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val out = mutableListOf<CalendarEvent>()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, startMs)
            ContentUris.appendId(this, endMs)
        }.build()
        try {
            context.contentResolver.query(
                uri, projection, null, null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    out.add(
                        CalendarEvent(
                            title = cursor.getString(0) ?: "Untitled event",
                            startMs = cursor.getLong(1),
                            endMs = cursor.getLong(2),
                            allDay = cursor.getInt(3) != 0
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Provider missing, malformed, or access revoked mid-query — treat as no events.
        }
        return out
    }

    fun eventsToday(): List<CalendarEvent> = dayRange(0).let { eventsBetween(it.first, it.second) }

    fun eventsTomorrow(): List<CalendarEvent> = dayRange(1).let { eventsBetween(it.first, it.second) }

    fun eventsThisWeek(): List<CalendarEvent> {
        val start = startOfToday()
        val cal = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 7) }
        return eventsBetween(start, cal.timeInMillis)
    }

    // Soonest upcoming event from right now, searching up to [windowDays] ahead.
    fun nextEvent(windowDays: Int = 60): CalendarEvent? {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, windowDays) }
        return eventsBetween(now, cal.timeInMillis).firstOrNull()
    }

    // Title-substring search across an upcoming window — e.g. "Nick's vet appointment".
    fun findByTitle(keyword: String, windowDays: Int = 120): CalendarEvent? {
        val term = keyword.trim()
        if (term.isEmpty()) return null
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, windowDays) }
        return eventsBetween(now, cal.timeInMillis)
            .firstOrNull { it.title.contains(term, ignoreCase = true) }
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun dayRange(offsetDays: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfToday(); add(Calendar.DAY_OF_YEAR, offsetDays) }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return start to cal.timeInMillis
    }
}
