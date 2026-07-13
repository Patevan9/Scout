package com.example.scoutface

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Displays the Scout diagnostic report and lets the user share it.
 *
 * Reads only from DiagnosticDb (scout_diagnostic.db) and the bounded crash
 * file (scout_crash.txt). Nothing is transmitted automatically — the share
 * sheet is opened and the user chooses the destination.
 *
 * Privacy: the report never contains speech text, saved memories, family
 * names, photos, face data, precise location, API keys, exception messages,
 * stack traces, URLs, or file paths. It contains only device model, Android
 * version, Scout version, timestamps, and controlled event codes.
 *
 * Not yet registered in the manifest (Step 5).
 */
class DiagReportActivity : AppCompatActivity() {

    private lateinit var tvReport: TextView
    private lateinit var etNotes: EditText
    private var diagDb: DiagnosticDb? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diag_report)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        tvReport = findViewById(R.id.tvReport)
        etNotes  = findViewById(R.id.etNotes)

        try {
            diagDb = DiagnosticDb(this)
        } catch (_: Exception) {
            tvReport.text = "Could not open diagnostic database.\nThe report cannot be displayed."
        }

        diagDb?.let { tvReport.text = buildReport(it) }

        findViewById<Button>(R.id.btnShare).setOnClickListener { shareReport() }
    }

    // ── Report builder ────────────────────────────────────────────────────────

    private fun buildReport(db: DiagnosticDb): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }

        return buildString {
            appendLine("=== Privacy Notice ===")
            appendLine(PRIVACY_NOTICE)
            appendLine()
            appendLine("=== Scout Diagnostic Report ===")
            appendLine("Generated:     ${fmt.format(Date())}")
            appendLine("Scout version: $appVersion")
            appendLine("Android API:   ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Device:        ${android.os.Build.MODEL}")
            appendLine()

            appendLine("=== Event Log (last 7 days, newest first) ===")
            try {
                val events = db.getAll()
                if (events.isEmpty()) {
                    appendLine("none")
                } else {
                    for ((ts, tag, detail) in events) {
                        appendLine("${fmt.format(Date(ts))}  $tag  $detail")
                    }
                }
            } catch (_: Exception) {
                appendLine("[error reading event log]")
            }

            appendLine()
            appendLine("=== Crash Log ===")
            try {
                val crashFile = db.crashFile
                if (crashFile.exists() && crashFile.length() > 0L) {
                    val crashContent = crashFile.readText(Charsets.UTF_8)
                    append(crashContent)
                    if (!crashContent.endsWith("\n")) appendLine()
                } else {
                    appendLine("none")
                }
            } catch (_: Exception) {
                appendLine("[error reading crash log]")
            }
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    private fun shareReport() {
        val db = diagDb
        if (db == null) {
            Toast.makeText(this, "Diagnostic database is not available.", Toast.LENGTH_LONG).show()
            return
        }

        val reportText = buildReport(db)
        val userNotes = etNotes.text?.toString()?.trim() ?: ""

        // User notes are clearly separated from the app-generated report.
        val fullContent = buildString {
            if (userNotes.isNotEmpty()) {
                appendLine("=== User Notes ===")
                appendLine(userNotes)
                appendLine()
            }
            append(reportText)
        }

        // Write to filesDir/diag/diag_report.txt.
        // filesDir is covered by the existing FileProvider:
        //   <files-path name="files" path="." />
        // No change to file_paths.xml is needed.
        val diagDir = File(filesDir, "diag")
        val reportFile: File
        val uri: Uri

        try {
            diagDir.mkdirs()
            reportFile = File(diagDir, "diag_report.txt")
            reportFile.writeText(fullContent, Charsets.UTF_8)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Could not write report file. Free some storage space and try again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            uri = FileProvider.getUriForFile(this, "$packageName.provider", reportFile)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Could not prepare report for sharing.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // ACTION_SEND with EXTRA_STREAM attaches the file.
        // EXTRA_EMAIL pre-fills the recipient in compatible email apps only.
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL,   arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "Scout Diagnostic Report")
            putExtra(Intent.EXTRA_STREAM,  uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(shareIntent, "Share diagnostic report via…"))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "No app found to share the report. Please try a different app.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val SUPPORT_EMAIL = "lippyroboticslabs@gmail.com"

        // Verbatim Privacy Policy disclosure wording — do not edit without updating
        // the app's Privacy Policy page.
        private const val PRIVACY_NOTICE =
            "If you contact us for support, you may choose to share a diagnostic report " +
            "from within the Scout app. The report may contain technical information such " +
            "as your device model, Android version, Scout version, timestamps, and technical " +
            "event codes. It does not include full conversation transcripts, saved memories, " +
            "photos, face recognition data, precise location, passwords, or API keys. " +
            "Sharing is always voluntary and initiated by you."
    }
}
