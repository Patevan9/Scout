package com.example.scoutface

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ConversationDb(context: Context) : SQLiteOpenHelper(context, "scout_convo.db", null, 2) {

    // Previous-session/long-idle leak fix: getLastTurns() previously returned
    // whatever the last N rows in conversation_log were, with no age check at
    // all -- so the very first question of a brand-new app launch could pull
    // rows logged hours or days earlier in a different sitting and hand them
    // to Qwen/Gemini as if they were the immediately preceding turns. This
    // does not delete or prune any row; conversation_log stays the full,
    // durable history. It only bounds what getLastTurns() treats as ACTIVE
    // context, using two independent boundaries (see activeContextCutoff()).
    private val sessionStartedAtMs = System.currentTimeMillis()

    companion object {
        // How long a turn stays eligible as active context after it was
        // logged, even within the current session (e.g. the app stays open
        // but sits idle for a long stretch).
        const val MAX_ACTIVE_CONTEXT_AGE_MS = 10 * 60 * 1000L

        // Pure boundary calculation, exposed here so it can be unit-tested
        // without a live Android SQLite instance -- ConversationDb itself,
        // like TruthDb/ScoutEntityResolver elsewhere in this codebase, isn't
        // constructable in the plain-JVM src/test/ source set. A row only
        // counts as active context when its created_at is at or after BOTH
        // this session's own start (rules out a previous session's rows
        // outright, regardless of how recent they are) AND the rolling
        // MAX_ACTIVE_CONTEXT_AGE_MS window (rules out a long-idle gap within
        // the same running session).
        fun activeContextCutoff(sessionStartedAtMs: Long, nowMs: Long, maxAgeMs: Long = MAX_ACTIVE_CONTEXT_AGE_MS): Long =
            maxOf(sessionStartedAtMs, nowMs - maxAgeMs)
    }

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
        val cutoff = activeContextCutoff(sessionStartedAtMs, System.currentTimeMillis())
        readableDatabase.rawQuery(
            "SELECT role, text FROM conversation_log WHERE created_at >= ? ORDER BY id DESC LIMIT ?;",
            arrayOf(cutoff.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(c.getString(0) to c.getString(1))
            }
        }
        return out.reversed()
    }
}