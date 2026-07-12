package com.example.scoutface

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/**
 * Isolated diagnostic event store for Scout support reports.
 *
 * Completely separate from JournalDb — uses scout_diagnostic.db so that
 * no existing JournalDb entries can ever appear in a diagnostic report,
 * and no diagnostic events can pollute the journal.
 *
 * Also owns the path of scout_crash.txt, which is written by the uncaught-
 * exception handler without touching SQLite.
 *
 * Retention: 7 days OR 1,000 entries, whichever is reached first.
 * Purge runs once per app session on the first call to add().
 */
class DiagnosticDb(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    // Captured at construction so the crash handler can reference this path
    // without going back through Android context during an active crash.
    val crashFile: File = File(context.filesDir, CRASH_FILE_NAME)

    private var purgedThisSession = false

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS diagnostic_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "tag TEXT NOT NULL, " +
                "detail TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL" +
            ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /**
     * Record one diagnostic event.
     * tag   — short category, e.g. "LISTEN", "SPEECH", "ROUTE", "LLAMA", "BOOT"
     * detail — key=value pairs with no speech text, names, or database values
     */
    fun add(tag: String, detail: String) {
        if (!purgedThisSession) {
            purgeOld()
            purgedThisSession = true
        }
        val cv = ContentValues().apply {
            put("tag", tag)
            put("detail", detail)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("diagnostic_events", null, cv)
    }

    /**
     * Returns events from the last seven days, newest-first, for report generation.
     * The cutoff is applied here independently of whether add() has run this session,
     * so stale records are never visible in a report even if purgeOld() has not yet fired.
     * Each entry is (timestamp_ms, tag, detail).
     */
    fun getAll(): List<Triple<Long, String, String>> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val out = mutableListOf<Triple<Long, String, String>>()
        readableDatabase.rawQuery(
            "SELECT created_at, tag, detail FROM diagnostic_events " +
            "WHERE created_at >= ? " +
            "ORDER BY created_at DESC;",
            arrayOf(cutoff.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2)))
            }
        }
        return out
    }

    /**
     * Trims the crash file if it has grown past MAX_CRASH_BYTES.
     * Call once at startup. Never call from inside the crash handler.
     */
    fun trimCrashFileIfNeeded() {
        if (!crashFile.exists() || crashFile.length() <= MAX_CRASH_BYTES) return
        try {
            val kept = crashFile.readLines().takeLast(MAX_CRASH_LINES)
            crashFile.writeText(kept.joinToString("\n") + "\n")
        } catch (_: Exception) {}
    }

    /**
     * Deletes all diagnostic events AND the crash file.
     * Called by "Delete Diagnostic Logs" in Settings.
     */
    fun deleteAll() {
        writableDatabase.execSQL("DELETE FROM diagnostic_events;")
        try { crashFile.delete() } catch (_: Exception) {}
    }

    private fun purgeOld() {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        writableDatabase.execSQL(
            "DELETE FROM diagnostic_events WHERE created_at < ?;",
            arrayOf(cutoff.toString())
        )
        // Secondary cap: keep only the newest MAX_ENTRIES regardless of age.
        writableDatabase.execSQL(
            "DELETE FROM diagnostic_events WHERE id NOT IN " +
            "(SELECT id FROM diagnostic_events ORDER BY created_at DESC LIMIT $MAX_ENTRIES);"
        )
    }

    companion object {
        private const val DB_NAME       = "scout_diagnostic.db"
        const val CRASH_FILE_NAME       = "scout_crash.txt"
        private const val DB_VERSION    = 1
        const val RETENTION_MS          = 7L * 24L * 60L * 60L * 1000L  // 7 days
        private const val MAX_ENTRIES   = 1_000
        const val MAX_CRASH_BYTES       = 50_000L   // 50 KB — prevents unbounded growth
        private const val MAX_CRASH_LINES = 100
    }
}
