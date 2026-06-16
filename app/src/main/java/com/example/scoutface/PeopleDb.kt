package com.example.scoutface

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeopleDb(context: Context) :
    SQLiteOpenHelper(context, "scout_people.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS people (" +
                    "id INTEGER PRIMARY KEY, " +
                    "name TEXT, " +
                    "face_hash TEXT UNIQUE, " +
                    "first_met INTEGER, " +
                    "last_seen INTEGER, " +
                    "embedding BLOB" +
                    ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE people ADD COLUMN embedding BLOB;")
        }
    }

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

    fun storeEmbedding(faceHash: String, embedding: FloatArray) {
        try {
            val cv = ContentValues()
            cv.put("embedding", floatArrayToBytes(embedding))
            writableDatabase.update("people", cv, "face_hash=?", arrayOf(faceHash))
        } catch (_: Exception) {
        }
    }

    fun findBestMatch(embedding: FloatArray, threshold: Float = 0.65f): String? {
        return try {
            val cursor = readableDatabase.rawQuery(
                "SELECT face_hash, embedding FROM people WHERE embedding IS NOT NULL;",
                null
            )
            var bestHash: String? = null
            var bestScore = threshold
            while (cursor.moveToNext()) {
                val hash = cursor.getString(0)
                val blob = cursor.getBlob(1) ?: continue
                val stored = bytesToFloatArray(blob)
                if (stored.size != embedding.size) continue
                val score = cosineSimilarity(embedding, stored)
                if (score > bestScore) {
                    bestScore = score
                    bestHash = hash
                }
            }
            cursor.close()
            bestHash
        } catch (_: Exception) {
            null
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    private fun floatArrayToBytes(fa: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(fa.size * 4)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        fa.forEach { bb.putFloat(it) }
        return bb.array()
    }

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val bb = ByteBuffer.wrap(bytes)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { bb.getFloat() }
    }

}
