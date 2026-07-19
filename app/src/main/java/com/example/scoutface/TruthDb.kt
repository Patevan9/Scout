package com.example.scoutface

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TruthDb(context: Context) : SQLiteOpenHelper(context, "scout_truth.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS entity_memory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "entity TEXT, fact_key TEXT, value TEXT, confidence REAL, source TEXT, " +
                    "last_confirmed INTEGER, created_at INTEGER, updated_at INTEGER, " +
                    "UNIQUE(entity, fact_key) ON CONFLICT REPLACE" +
                    ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    // Returns true if this call actually changed something (a brand-new fact, or an
    // existing one whose value changed) — false if it's a duplicate re-teach of the
    // same value. Callers use this to decide whether it's worth journaling.
    fun upsertFact(
        entity: String,
        factKey: String,
        value: String,
        confidence: Float,
        source: String
    ): Boolean {
        val existing = getFactValue(entity, factKey)
        val now = System.currentTimeMillis()
        writableDatabase.execSQL(
            "INSERT INTO entity_memory(entity, fact_key, value, confidence, source, last_confirmed, created_at, updated_at) " +
                    "VALUES(?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(entity, fact_key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at;",
            arrayOf(
                entity.lowercase(),
                factKey.lowercase(),
                value,
                confidence,
                source,
                now,
                now,
                now
            )
        )
        return existing == null || !existing.equals(value, ignoreCase = true)
    }

    fun getFactValue(entity: String, factKey: String): String? {
        readableDatabase.rawQuery(
            "SELECT value FROM entity_memory WHERE entity=? AND fact_key=? LIMIT 1;",
            arrayOf(entity.lowercase(), factKey.lowercase())
        ).use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    fun getAllFacts(entity: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery(
            "SELECT fact_key, value FROM entity_memory WHERE entity=? ORDER BY updated_at ASC;",
            arrayOf(entity.lowercase())
        ).use { c ->
            while (c.moveToNext()) out.add(c.getString(0) to c.getString(1))
        }
        return out
    }

    fun deleteFact(entity: String, factKey: String) {
        writableDatabase.execSQL(
            "DELETE FROM entity_memory WHERE entity=? AND fact_key=?;",
            arrayOf(entity.lowercase(), factKey.lowercase())
        )
    }

    fun deleteFactsWithKeyLike(entity: String, pattern: String) {
        writableDatabase.execSQL(
            "DELETE FROM entity_memory WHERE entity=? AND fact_key LIKE ?;",
            arrayOf(entity.lowercase(), pattern)
        )
    }

    fun getFactsUpdatedToday(entity: String): List<Pair<String, String>> {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val out = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery(
            "SELECT fact_key, value FROM entity_memory WHERE entity=? AND updated_at >= ? ORDER BY updated_at ASC;",
            arrayOf(entity.lowercase(), startOfDay.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(c.getString(0) to c.getString(1))
        }
        return out
    }
}