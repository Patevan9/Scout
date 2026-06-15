package com.example.scoutface

import kotlin.math.max
import kotlin.math.min
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Locale

/**
 * OfflineLexicon
 * - Uses downloaded WordNet JSON (OEWN) in a "best effort" way.
 * - Uses downloaded sentiment word lists.
 *
 * Design goals:
 * - Stability > perfect NLP
 * - Lazy + cached (don’t parse all of WordNet at boot)
 * - Works even if WordNet JSON schema differs slightly (we scan for likely fields)
 */
class OfflineLexicon(private val context: Context) {

    private val db = LexiconDb(context)

    // Sentiment caches (lazy)
    @Volatile private var posSet: HashSet<String>? = null
    @Volatile private var negSet: HashSet<String>? = null

    fun sentimentReady(posFile: File, negFile: File): Boolean {
        return posFile.exists() && posFile.length() > 20 && negFile.exists() && negFile.length() > 20
    }

    fun wordnetReady(wordnetDir: File): Boolean {
        if (!wordnetDir.exists()) return false
        val hasJson = wordnetDir.listFiles()?.any { it.isFile && it.name.endsWith(".json", true) && it.length() > 200 } == true
        return hasJson
    }

    /**
     * Define a word using cached definitions, otherwise scan WordNet JSON files and cache the first hit.
     */
    fun defineWord(wordnetDir: File, rawWord: String): String? {
        val w = normalizeWord(rawWord)
        if (w.isBlank()) return null

        // 1) cache
        db.readableDatabase.rawQuery(
            "SELECT def FROM word_defs WHERE word=? LIMIT 1;",
            arrayOf(w)
        ).use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }

        // 2) scan WordNet JSON (best-effort)
        val files = wordnetDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json", true) && it.length() > 200 }
            ?.sortedBy { it.name.lowercase(Locale.US) }
            ?: emptyList()

        for (f in files) {
            try {
                val hit = scanJsonFileForDefinition(f, w)
                if (!hit.isNullOrBlank()) {
                    db.writableDatabase.execSQL(
                        "INSERT OR REPLACE INTO word_defs(word, def, updated_at) VALUES(?, ?, ?);",
                        arrayOf(w, hit, System.currentTimeMillis())
                    )
                    return hit
                }
            } catch (e: Exception) {
                Log.w("OfflineLexicon", "scan failed: ${f.name}", e)
            }
        }
        return null
    }

    /**
     * Very simple sentiment analysis:
     * - counts positive/negative words
     * - returns "positive / negative / mixed / neutral"
     */
    fun sentimentOfText(posFile: File, negFile: File, text: String): String? {
        ensureSentimentLoaded(posFile, negFile)
        val pos = posSet ?: return null
        val neg = negSet ?: return null

        val tokens = text
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9'\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        var p = 0
        var n = 0
        for (t in tokens) {
            val w = normalizeWord(t)
            if (w.isBlank()) continue
            if (pos.contains(w)) p++
            if (neg.contains(w)) n++
        }

        return when {
            p == 0 && n == 0 -> "neutral"
            p > n + 1 -> "positive"
            n > p + 1 -> "negative"
            else -> "mixed"
        }
    }

    fun isPositiveWord(posFile: File, negFile: File, rawWord: String): Boolean? {
        ensureSentimentLoaded(posFile, negFile)
        val w = normalizeWord(rawWord)
        val pos = posSet ?: return null
        val neg = negSet ?: return null
        return when {
            pos.contains(w) && !neg.contains(w) -> true
            neg.contains(w) && !pos.contains(w) -> false
            else -> null
        }
    }

    // ---------------- internals ----------------

    private fun ensureSentimentLoaded(posFile: File, negFile: File) {
        if (posSet != null && negSet != null) return
        synchronized(this) {
            if (posSet != null && negSet != null) return
            try {
                posSet = loadWordList(posFile)
                negSet = loadWordList(negFile)
            } catch (e: Exception) {
                Log.e("OfflineLexicon", "sentiment load failed", e)
                posSet = null
                negSet = null
            }
        }
    }

    private fun loadWordList(file: File): HashSet<String> {
        val set = HashSet<String>(50_000)
        if (!file.exists()) return set
        BufferedReader(InputStreamReader(FileInputStream(file))).use { br ->
            while (true) {
                val line = br.readLine() ?: break
                val t = line.trim()
                if (t.isEmpty()) continue
                if (t.startsWith(";") || t.startsWith("#")) continue
                val w = normalizeWord(t)
                if (w.isNotBlank()) set.add(w)
            }
        }
        return set
    }

    private fun normalizeWord(raw: String): String {
        return raw
            .lowercase(Locale.US)
            .trim()
            .replace(Regex("^[^a-z0-9]+|[^a-z0-9]+$"), "")
            .take(48)
    }

    /**
     * Best-effort scan:
     * - tries to parse JSON if it looks like JSON objects/arrays
     * - otherwise does a cheap text-window search for "lemma/writtenForm" + "definition/gloss"
     *
     * This is intentionally tolerant because OEWN JSON packaging can vary.
     */
    private fun scanJsonFileForDefinition(file: File, word: String): String? {
        // Fast path: try JSON parse (only if not gigantic)
        if (file.length() < 8_000_000) { // ~8MB threshold to stay safe on A32
            try {
                val text = file.readText()
                val trimmed = text.trim()
                if (trimmed.startsWith("{")) {
                    val obj = JSONObject(trimmed)
                    val hit = walkJsonForDef(obj, word)
                    if (!hit.isNullOrBlank()) return hit
                } else if (trimmed.startsWith("[")) {
                    val arr = JSONArray(trimmed)
                    val hit = walkJsonForDef(arr, word)
                    if (!hit.isNullOrBlank()) return hit
                }
            } catch (_: Exception) {
                // fall through to text scan
            }
        }

        // Tolerant text scan (streaming)
        return streamingWindowScan(file, word)
    }

    private fun walkJsonForDef(any: Any, word: String): String? {
        // Try common OEWN-ish field names
        // We search for lemma fields (lemma/writtenForm) and nearby definition fields (definition/gloss/def).
        val stack = ArrayDeque<Any>()
        stack.add(any)

        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            when (cur) {
                is JSONObject -> {
                    // direct match: if object contains a lemma and definition/gloss
                    val lemma = cur.optString("lemma", "")
                        .ifBlank { cur.optString("writtenForm", "") }
                        .ifBlank { cur.optString("word", "") }
                        .lowercase(Locale.US)

                    if (lemma == word) {
                        val def = cur.optString("definition", "")
                            .ifBlank { cur.optString("gloss", "") }
                            .ifBlank { cur.optString("def", "") }
                        val cleaned = cleanupDef(def)
                        if (cleaned.isNotBlank()) return cleaned
                    }

                    val keys = cur.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = cur.opt(k)
                        if (v is JSONObject || v is JSONArray) stack.add(v)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until cur.length()) {
                        val v = cur.opt(i)
                        if (v is JSONObject || v is JSONArray) stack.add(v)
                    }
                }
            }
        }
        return null
    }

    private fun streamingWindowScan(file: File, word: String): String? {
        // We scan text for the word and then look around it for definition-like strings.
        val target1 = "\"lemma\":\"$word\""
        val target2 = "\"writtenForm\":\"$word\""
        val target3 = "\"word\":\"$word\""

        val buf = CharArray(64 * 1024)
        val window = StringBuilder(200_000)

        InputStreamReader(FileInputStream(file)).use { r ->
            while (true) {
                val n = r.read(buf)
                if (n <= 0) break
                window.append(buf, 0, n)

                // keep window bounded
                if (window.length > 220_000) {
                    window.delete(0, window.length - 180_000)
                }

                val w = window.toString()
                val idx = findAny(w, target1, target2, target3)
                if (idx >= 0) {
                    // take a chunk around the hit
                    val start = max(0, idx - 2000)
                    val end = min(w.length, idx + 8000)
                    val chunk = w.substring(start, end)

                    // pull definition/gloss nearby
                    val def = extractNearbyDef(chunk)
                    val cleaned = cleanupDef(def)
                    if (cleaned.isNotBlank()) return cleaned
                }
            }
        }
        return null
    }

    private fun findAny(hay: String, a: String, b: String, c: String): Int {
        val ia = hay.indexOf(a)
        if (ia >= 0) return ia
        val ib = hay.indexOf(b)
        if (ib >= 0) return ib
        val ic = hay.indexOf(c)
        if (ic >= 0) return ic
        return -1
    }

    private fun extractNearbyDef(chunk: String): String {
        // Try a few patterns; tolerant, not perfect.
        val keys = listOf("\"definition\":\"", "\"gloss\":\"", "\"def\":\"")
        for (k in keys) {
            val i = chunk.indexOf(k)
            if (i >= 0) {
                val j = i + k.length
                val end = chunk.indexOf('"', j)
                if (end > j) return chunk.substring(j, end)
            }
        }
        return ""
    }

    private fun cleanupDef(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace("\\n", " ")
            .replace("\\t", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(220)
    }

    private class LexiconDb(context: Context) : SQLiteOpenHelper(context, "scout_lexicon.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS word_defs (" +
                        "word TEXT PRIMARY KEY, " +
                        "def TEXT, " +
                        "updated_at INTEGER" +
                        ");"
            )
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
}