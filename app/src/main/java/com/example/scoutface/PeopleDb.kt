package com.example.scoutface

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PeopleDb(context: Context) :
    SQLiteOpenHelper(context, "scout_people.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS people (" +
                    "id INTEGER PRIMARY KEY, " +
                    "name TEXT, " +
                    "face_hash TEXT UNIQUE, " +
                    "first_met INTEGER, " +
                    "last_seen INTEGER" +
                    ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun touchSeen(faceHash: String) {
        try {
            writableDatabase.execSQL(
                "INSERT OR IGNORE INTO people(name, face_hash, first_met, last_seen) VALUES(?, ?, ?, ?);",
                arrayOf(
                    "",
                    faceHash,
                    System.currentTimeMillis(),
                    System.currentTimeMillis()
                )
            )
            writableDatabase.execSQL(
                "UPDATE people SET last_seen=? WHERE face_hash=?;",
                arrayOf(System.currentTimeMillis(), faceHash)
            )
        } catch (_: Exception) {
        }
    }

    fun getName(faceHash: String): String? {
        return try {
            val cursor = readableDatabase.rawQuery(
                "SELECT name FROM people WHERE face_hash=? AND name != '' LIMIT 1;",
                arrayOf(faceHash)
            )
            val result = if (cursor.moveToFirst()) cursor.getString(0) else null
            cursor.close()
            result
        } catch (_: Exception) {
            null
        }
    }

    fun setName(faceHash: String, name: String) {
        try {
            val cv = ContentValues()
            cv.put("name", name)
            writableDatabase.update("people", cv, "face_hash=?", arrayOf(faceHash))
        } catch (_: Exception) {
        }
    }

    fun isKnown(faceHash: String): Boolean {
        return getName(faceHash) != null
    }

}