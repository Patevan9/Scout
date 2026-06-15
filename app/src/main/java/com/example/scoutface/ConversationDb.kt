package com.example.scoutface

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ConversationDb(context: Context) : SQLiteOpenHelper(context, "scout_convo.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS conversation_log (" +
                    "id INTEGER PRIMARY KEY, " +
                    "role TEXT, " +
                    "text TEXT, " +
                    "created_at INTEGER" +
                    ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS conversation_log (" +
                        "id INTEGER PRIMARY KEY, " +
                        "role TEXT, " +
                        "text TEXT, " +
                        "created_at INTEGER" +
                        ");"
            )
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS conversation_log;")
        onCreate(db)
    }

    fun logTurn(role: String, text: String) {
        writableDatabase.execSQL(
            "INSERT INTO conversation_log(role, text, created_at) VALUES(?, ?, ?);",
            arrayOf(role, text, System.currentTimeMillis())
        )
    }

    fun getLastTurns(limit: Int): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        readableDatabase.rawQuery(
            "SELECT role, text FROM conversation_log ORDER BY id DESC LIMIT ?;",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(c.getString(0) to c.getString(1))
            }
        }
        return out.reversed()
    }
}