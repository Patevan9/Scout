package com.example.scoutface

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class JournalDb(context: Context) :
    SQLiteOpenHelper(context, "scout_journal.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS journal (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "text TEXT, " +
                    "created_at INTEGER, " +
                    "entry_type TEXT DEFAULT 'note', " +
                    "subject TEXT, " +
                    "weight INTEGER DEFAULT 1, " +
                    "reel_id INTEGER" +
                    ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE journal ADD COLUMN entry_type TEXT DEFAULT 'note';")
            db.execSQL("ALTER TABLE journal ADD COLUMN subject TEXT;")
            db.execSQL("ALTER TABLE journal ADD COLUMN weight INTEGER DEFAULT 1;")
            db.execSQL("ALTER TABLE journal ADD COLUMN reel_id INTEGER;")
        }
    }

    // Existing diagnostic/system-event logging — unchanged, defaults to a low-weight 'note'.
    fun add(text: String) {
        add(text, entryType = "note", subject = null, weight = 1)
    }

    // Memory-reel capture. entryType: 'first_met' | 'teaching' | 'correction' | 'milestone' | 'freeform'.
    // subject is who the entry is about ("Elijah", "Nicolas"), null when it's about the primary user.
    fun add(text: String, entryType: String, subject: String?, weight: Int) {
        writableDatabase.execSQL(
            "INSERT INTO journal(text, created_at, entry_type, subject, weight) VALUES(?, ?, ?, ?, ?);",
            arrayOf(text, System.currentTimeMillis(), entryType, subject, weight)
        )
    }

    data class Entry(val text: String, val createdAt: Long, val entryType: String, val subject: String?)

    // First read method this class has ever had (previously write-only). Used by
    // ScoutCompanionMomentsEngine's wiring for novelty tracking: how long since a
    // given category/specific moment last fired, and how many moments have fired
    // today. [entryType] filters to just one kind of entry (e.g. "companion_moment")
    // so this doesn't pull in unrelated teaching/correction/system-note entries.
    fun getEntriesSince(entryType: String, sinceMs: Long): List<Entry> {
        val out = mutableListOf<Entry>()
        readableDatabase.rawQuery(
            "SELECT text, created_at, entry_type, subject FROM journal WHERE entry_type=? AND created_at>=? ORDER BY created_at ASC;",
            arrayOf(entryType, sinceMs.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Entry(c.getString(0), c.getLong(1), c.getString(2), c.getString(3)))
            }
        }
        return out
    }
}