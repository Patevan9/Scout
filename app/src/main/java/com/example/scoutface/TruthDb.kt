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

    fun upsertFact(
        entity: String,
        factKey: String,
        value: String,
        confidence: Float,
        source: String
    ) {
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
}