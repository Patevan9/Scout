package com.example.scoutface

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class JournalDb(context: Context) :
    SQLiteOpenHelper(context, "scout_journal.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS journal (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "text TEXT, " +
                    "created_at INTEGER" +
                    ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    fun add(text: String) {
        writableDatabase.execSQL(
            "INSERT INTO journal(text, created_at) VALUES(?, ?);",
            arrayOf(text, System.currentTimeMillis())
        )
    }
}