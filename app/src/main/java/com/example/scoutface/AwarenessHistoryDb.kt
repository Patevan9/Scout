package com.example.scoutface

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Rolling Awareness history store — Scout_Awareness_Layer_Spec.md §4.
 *
 * Physically separate from both JournalDb (durable narrative store, no
 * retention policy) and DiagnosticDb (exportable via Share Diagnostic
 * Report). This store may contain resolved entity names; it is never
 * referenced by DiagReportActivity/DiagnosticDb and is never exportable —
 * that separation is structural (a different .db file, touched from no
 * shared code path), not a runtime filter that a future edit could bypass.
 *
 * Phase 1 writes only the two required edge-triggered categories (§3):
 * charging started/stopped and connectivity lost/restored. Nothing reads
 * from this store yet — Phase 1 has zero consumers (§5). It exists purely
 * to prove the write path and to size its own retention cap from real
 * on-device volume (see MAX_ENTRIES note below).
 *
 * Retention: 7-day cutoff, reused directly from DiagnosticDb.RETENTION_MS
 * (§4/§8), plus a row-count ceiling. The time-based purge runs once per app
 * session on the first call to add(), same pattern DiagnosticDb already
 * proves works. The count ceiling is a true hard cap: it's enforced after
 * every insert, not just once per session, so MAX_ENTRIES can never actually
 * be exceeded mid-session.
 */
class AwarenessHistoryDb(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private var purgedThisSession = false

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS awareness_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "category TEXT NOT NULL, " +
                "detail TEXT NOT NULL, " +
                "entity TEXT, " +
                "created_at INTEGER NOT NULL" +
            ");"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /**
     * Record one edge-triggered Awareness event.
     * entity — resolved entity name, only when genuinely known (§7); null otherwise.
     */
    fun add(category: AwarenessCategory, detail: String, entity: String? = null) {
        if (!purgedThisSession) {
            purgeOldByTime()
            purgedThisSession = true
        }
        val cv = ContentValues().apply {
            put("category", category.name)
            put("detail", detail)
            put("entity", entity)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("awareness_events", null, cv)
        enforceCountCeiling()
    }

    /**
     * Returns events from the last seven days, newest-first — for the on-device
     * A32 logging trial (§9) to measure real daily event volume. Not wired into
     * any UI or export flow.
     */
    fun getAll(): List<Triple<Long, String, String>> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val out = mutableListOf<Triple<Long, String, String>>()
        readableDatabase.rawQuery(
            "SELECT created_at, category, detail FROM awareness_events " +
            "WHERE created_at >= ? " +
            "ORDER BY created_at DESC;",
            arrayOf(cutoff.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2)))
            }
        }
        return out
    }

    private fun purgeOldByTime() {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        writableDatabase.execSQL(
            "DELETE FROM awareness_events WHERE created_at < ?;",
            arrayOf(cutoff.toString())
        )
    }

    /**
     * Keeps only the newest MAX_ENTRIES rows regardless of age. Runs after
     * every insert (not just once per session) so the documented cap is a
     * real hard ceiling instead of one that can be exceeded mid-session.
     * MAX_ENTRIES itself is provisional — the spec explicitly defers picking
     * a final number until the A32 logging trial shows real daily event
     * volume (§4, §9). This reuses DiagnosticDb's proven value as a safe
     * interim ceiling only, not a considered answer for Awareness history
     * specifically.
     */
    private fun enforceCountCeiling() {
        writableDatabase.execSQL(
            "DELETE FROM awareness_events WHERE id NOT IN " +
            "(SELECT id FROM awareness_events ORDER BY created_at DESC LIMIT $MAX_ENTRIES);"
        )
    }

    companion object {
        private const val DB_NAME     = "scout_awareness.db"
        private const val DB_VERSION  = 1
        const val RETENTION_MS        = DiagnosticDb.RETENTION_MS  // 7 days — reused precedent, §4
        private const val MAX_ENTRIES = 1_000  // provisional — see enforceCountCeiling()
    }
}

/**
 * Phase 1 Awareness event categories — Scout_Awareness_Layer_Spec.md §3.
 * Deliberately closed: only the two required signals exist here. Brightness
 * (optional) and every deferred category (presence, pickup, greetings, etc.)
 * are intentionally absent until their own phase.
 */
enum class AwarenessCategory {
    CHARGING_STARTED,
    CHARGING_STOPPED,
    CONNECTIVITY_LOST,
    CONNECTIVITY_RESTORED
}
