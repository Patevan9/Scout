package com.example.scoutface

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScoutExportManager(
    private val context: Context,
    private val truthDb: TruthDb,
    private val peopleDb: PeopleDb
) {

    fun exportBrainToJson(): String? {
        return try {
            val root = JSONObject()
            root.put("exported_at", System.currentTimeMillis())

            val truthArr = JSONArray()
            truthDb.readableDatabase.rawQuery(
                "SELECT entity, fact_key, value FROM entity_memory;",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    o.put("entity", c.getString(0))
                    o.put("fact", c.getString(1))
                    o.put("val", c.getString(2))
                    truthArr.put(o)
                }
            }
            root.put("truth", truthArr)

            // Named faces from the hash-keyed people table
            val peopleArr = JSONArray()
            peopleDb.readableDatabase.rawQuery(
                "SELECT face_hash, name, first_met, last_seen FROM people WHERE name IS NOT NULL AND name != '' ORDER BY last_seen DESC;",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    o.put("face_hash", c.getString(0))
                    o.put("name", c.getString(1))
                    o.put("first_met", c.getLong(2))
                    o.put("last_seen", c.getLong(3))
                    peopleArr.put(o)
                }
            }
            root.put("people", peopleArr)

            // Named embedding counts from the multi-embedding table
            val embArr = JSONArray()
            peopleDb.readableDatabase.rawQuery(
                "SELECT name, COUNT(*) FROM person_embeddings GROUP BY name ORDER BY name;",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    o.put("name", c.getString(0))
                    o.put("embedding_count", c.getInt(1))
                    embArr.put(o)
                }
            }
            root.put("face_embeddings", embArr)

            val outFile = File(context.filesDir, "scout_export_${System.currentTimeMillis()}.json")
            outFile.writeText(root.toString(2))
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun shareJsonFileSafely(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) return false

            val uri = FileProvider.getUriForFile(
                context,
                context.applicationContext.packageName + ".provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Scout Memory Export")
                putExtra(Intent.EXTRA_TEXT, "Scout memory export JSON file.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val resolved = context.packageManager.queryIntentActivities(
                shareIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            if (resolved.isNullOrEmpty()) return false

            for (ri in resolved) {
                val pkg = ri.activityInfo?.packageName ?: continue
                try {
                    context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
            }

            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(Intent.createChooser(shareIntent, "Share Scout Memory").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}