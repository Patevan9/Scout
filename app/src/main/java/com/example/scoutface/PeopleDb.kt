package com.example.scoutface

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PeopleDb(context: Context) :
    SQLiteOpenHelper(context, "scout_people.db", null, 4) {

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
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS person_embeddings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "embedding BLOB NOT NULL" +
                    ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE people ADD COLUMN embedding BLOB;")
        }
        if (oldVersion < 3) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS person_embeddings (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL, " +
                        "embedding BLOB NOT NULL" +
                        ");"
            )
        }
        if (oldVersion < 4) {
            // Face model upgraded from 192-dim to 512-dim (ArcFace). Old embeddings
            // are incompatible — clear them so Scout re-learns faces fresh.
            // Names are preserved; only the stored face vectors are removed.
            db.execSQL("DELETE FROM person_embeddings;")
            db.execSQL("UPDATE people SET embedding = NULL;")
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

    // Accumulates embeddings per named person across varied lighting/angles.
    // More coverage = better match rate in real-world conditions.
    private val MAX_EMBEDDINGS_PER_PERSON = 12

    fun addNamedEmbedding(name: String, embedding: FloatArray) {
        try {
            val db = writableDatabase
            val cursor = db.rawQuery(
                "SELECT id, embedding FROM person_embeddings WHERE name=?;", arrayOf(name)
            )
            val existing = mutableListOf<Pair<Long, FloatArray>>()
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val blob = it.getBlob(1) ?: continue
                    val stored = bytesToFloatArray(blob)
                    if (stored.size == embedding.size) existing.add(id to stored)
                }
            }
            if (existing.size >= MAX_EMBEDDINGS_PER_PERSON) {
                // Replace the most redundant embedding (most similar to the incoming one)
                // so the stored set stays diverse as conditions change over time.
                val mostRedundant = existing.maxByOrNull { (_, stored) ->
                    cosineSimilarity(embedding, stored)
                }
                if (mostRedundant != null) {
                    val cv = ContentValues()
                    cv.put("embedding", floatArrayToBytes(embedding))
                    db.update("person_embeddings", cv, "id=?", arrayOf(mostRedundant.first.toString()))
                }
                return
            }
            val cv = ContentValues()
            cv.put("name", name)
            cv.put("embedding", floatArrayToBytes(embedding))
            db.insert("person_embeddings", null, cv)
        } catch (_: Exception) {
        }
    }

    // Computes the best cosine similarity score per distinct person against the given embedding.
    private fun scoreByPerson(embedding: FloatArray): Map<String, Float> {
        val cursor = readableDatabase.rawQuery("SELECT name, embedding FROM person_embeddings;", null)
        val best = mutableMapOf<String, Float>()
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                val blob = it.getBlob(1) ?: continue
                val stored = bytesToFloatArray(blob)
                if (stored.size != embedding.size) continue
                val score = cosineSimilarity(embedding, stored)
                val prev = best[name]
                if (prev == null || score > prev) best[name] = score
            }
        }
        return best
    }

    // Returns the matched name, or null when no candidate passes the threshold OR when the
    // top two candidates are too close to call (margin < minMargin). The margin check prevents
    // Scout from guessing when two people score similarly — it says nothing rather than picking wrong.
    fun findBestMatchName(embedding: FloatArray, threshold: Float = 0.65f, minMargin: Float = 0.08f): String? {
        return try {
            val sorted = scoreByPerson(embedding).entries.sortedByDescending { it.value }
            val best = sorted.firstOrNull() ?: return null
            if (best.value < threshold) return null
            val second = sorted.getOrNull(1)
            if (second != null && best.value - second.value < minMargin) return null
            best.key
        } catch (_: Exception) {
            null
        }
    }

    // Same as findBestMatchName but also returns the winning score so callers can gate on confidence.
    fun findBestMatchNameWithScore(embedding: FloatArray, threshold: Float = 0.65f, minMargin: Float = 0.08f): Pair<String, Float>? {
        return try {
            val sorted = scoreByPerson(embedding).entries.sortedByDescending { it.value }
            val best = sorted.firstOrNull() ?: return null
            if (best.value < threshold) return null
            val second = sorted.getOrNull(1)
            if (second != null && best.value - second.value < minMargin) return null
            best.key to best.value
        } catch (_: Exception) {
            null
        }
    }

    fun forgetPerson(name: String) {
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.execSQL(
                    "UPDATE people SET name='', embedding=NULL WHERE LOWER(name)=LOWER(?);",
                    arrayOf(name)
                )
                db.execSQL(
                    "DELETE FROM person_embeddings WHERE LOWER(name)=LOWER(?);",
                    arrayOf(name)
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
        }
    }

    fun findBestMatch(embedding: FloatArray, threshold: Float = 0.65f): String? {
        return try {
            val cursor = readableDatabase.rawQuery(
                "SELECT face_hash, embedding FROM people WHERE embedding IS NOT NULL AND name IS NOT NULL AND name != '';",
                null
            )
            var bestHash: String? = null
            var bestScore = threshold
            cursor.use {
                while (it.moveToNext()) {
                    val hash = it.getString(0)
                    val blob = it.getBlob(1) ?: continue
                    val stored = bytesToFloatArray(blob)
                    if (stored.size != embedding.size) continue
                    val score = cosineSimilarity(embedding, stored)
                    if (score > bestScore) {
                        bestScore = score
                        bestHash = hash
                    }
                }
            }
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
