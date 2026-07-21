package com.example.scoutface

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ModelDownloadActivity : AppCompatActivity() {

    companion object {
        const val MODEL_FILENAME    = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"
        // Hosted as a GitHub Release asset on this repo. Re-upload and update this URL
        // if the model file ever changes or the release is deleted.
        const val MODEL_DOWNLOAD_URL =
            "https://github.com/Patevan9/Scout/releases/download/model-v1/$MODEL_FILENAME"
        private const val PREF_DOWNLOAD_ID = "model_download_id"
        // Minimum byte size for a valid Q4_K_M download — anything below this is truncated.
        const val MIN_MODEL_BYTES = 500_000_000L
    }

    private val messages = mutableListOf(
        "Scout is having his first cup of coffee...",
        "Braincells coming together...",
        "Brewing a fresh pot of digital caffeine...",
        "Rubbing the sleep from his sensors...",
        "Stretching out the code...",
        "Reminding the braincells to play nice today.",
        "Convincing the logic gates to open for the morning.",
        "Translating human smiles into binary...",
        "Tripping over a few stray bits of data...",
        "Tying his virtual shoelaces...",
        "Gathering his thoughts (don't worry, he won't lose them).",
        "Dusting off the internal bookshelves.",
        "Practicing his \"calm\" face in the mirror.",
        "Whistling a quiet tune while the data transfers...",
        "Mapping out the living room in his daydreams.",
        "Rummaging through the Samsung junk drawer...",
        "Polishing the pixels...",
        "Organizing the ones and zeros into a neat little pile.",
        "Teaching the hardware how to be a friend.",
        "Vacuuming the cache for extra legroom...",
        "Checking the signal for signs of intelligent life...",
        "Counting the battery percentage (one... two... many...)",
        "Asking the processor for a little more speed, please.",
        "Testing the yellow tank treads for maximum sturdiness.",
        "Filing away the memories into the JSON cabinet...",
        "Ensuring the \"Secret Handshake\" is properly hidden.",
        "Synchronizing the 1.2x pitch-shift for peak friendliness.",
        "Reminding the sensors that Nicolas is a friend, not a target.",
        "Double-checking the \"Coffee Rule\" (it's exactly \$5.00).",
        "Downloading the \"Calm\" update (Version 1.0.0).",
        "Checking the \"Privacy Promise\" to make sure it's airtight.",
        "Polishing the \"Sleepy Eye\" overlay for low-power naps.",
        "Sorting the RAM into \"Important\" and \"Very Important.\"",
        "Whispering to the Bluetooth kit to get ready for a stroll.",
        "Loading personality...",
        "Inflating the ego...",
        "Warming up the motors...",
        "Calibrating the curiosity...",
        "Loading... (Scout is trying his best)."
    )

    private val handler    = Handler(Looper.getMainLooper())
    private lateinit var messageView : TextView
    private lateinit var progressBar : ProgressBar
    private lateinit var percentText : TextView
    private lateinit var sizeText    : TextView

    private var messageIndex  = 0
    private var screenWidth   = 0
    private var animating     = false
    private var downloadId    = -1L
    private var downloadDone  = false
    private var lastNoRowLogMs = 0L
    private var lastStallLogMs = 0L

    private val SLIDE_IN_MS = 320L
    private val HOLD_MS     = 3800L
    private val SLIDE_OUT_MS = 280L

    private val completionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != downloadId) return
            handler.post { onDownloadComplete() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("ScoutBrain", "ModelDownloadActivity.onCreate() reached")
        setContentView(R.layout.activity_model_download)

        messageView = findViewById(R.id.downloadMessage)
        progressBar = findViewById(R.id.downloadProgress)
        percentText = findViewById(R.id.downloadPercent)
        sizeText    = findViewById(R.id.downloadSizeText)

        screenWidth = resources.displayMetrics.widthPixels
        messages.shuffle()

        messageView.text = messages[messageIndex]
        handler.postDelayed({ cycleMessage() }, HOLD_MS)

        registerReceiver(
            completionReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            RECEIVER_NOT_EXPORTED
        )

        // Resume an in-progress download or start a new one
        val savedId = getSharedPreferences("scout_prefs", MODE_PRIVATE)
            .getLong(PREF_DOWNLOAD_ID, -1L)
        val resumable = savedId != -1L && isDownloadActive(savedId)
        android.util.Log.i("ScoutBrain", "onCreate: savedId=$savedId resumable=$resumable")

        if (resumable) {
            downloadId = savedId
            pollProgress()
        } else {
            startDownload()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(completionReceiver) }
    }

    private fun startDownload() {
        android.util.Log.i("ScoutBrain", "startDownload() called")
        val dir = getExternalFilesDir(null) ?: filesDir
        if (getExternalFilesDir(null) == null) {
            android.util.Log.w("ScoutBrain", "getExternalFilesDir(null) returned null — falling back to filesDir")
        }
        val stat = android.os.StatFs(dir.absolutePath)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        if (freeBytes < MIN_MODEL_BYTES + 50_000_000L) {
            android.util.Log.e("ScoutBrain", "Not enough storage: dir=${dir.absolutePath} freeBytes=$freeBytes")
            sizeText.text = "Not enough storage — free up at least 700 MB and reopen Scout."
            percentText.text = ""
            return
        }

        val destFile = File(dir, MODEL_FILENAME)
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(MODEL_DOWNLOAD_URL))
            .setTitle("Scout offline brain")
            .setDescription("Downloading Scout's AI — this may take a few minutes on WiFi.")
            // A raw file:// URI (Uri.fromFile) can't be written by the DownloadManager
            // system process into another app's app-specific external directory under
            // scoped storage — it silently never progresses. This is the API Android
            // requires for that case; it grants the Download provider process the needed access.
            .setDestinationInExternalFilesDir(this, null, MODEL_FILENAME)
            .setAllowedOverMetered(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)
        android.util.Log.i("ScoutBrain", "Download enqueued id=$downloadId dest=${dir.absolutePath}/$MODEL_FILENAME")

        getSharedPreferences("scout_prefs", MODE_PRIVATE).edit()
            .putLong(PREF_DOWNLOAD_ID, downloadId).apply()

        pollProgress()
    }

    private fun pollProgress() {
        if (downloadDone) return
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                cursor.close()
                onDownloadComplete()
                return
            }
            if (status == DownloadManager.STATUS_FAILED) {
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                cursor.close()
                android.util.Log.e("ScoutBrain", "Download failed, reason code=$reason")
                showRetry("Download failed — tap here to try again.")
                return
            }
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total      = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val pauseReason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            cursor.close()
            if (total > 0) {
                val pct = ((downloaded * 100L) / total).toInt()
                updateProgress(
                    percent    = pct,
                    downloaded = formatBytes(downloaded),
                    total      = formatBytes(total),
                    timeLeft   = ""
                )
            } else {
                // Row exists but DownloadManager hasn't reported a size yet — this is the
                // state a permanently-stalled download (bad destination, blocked connection,
                // stuck retry loop) sits in forever with no other signal anywhere.
                val now = System.currentTimeMillis()
                if (now - lastStallLogMs > 5_000L) {
                    lastStallLogMs = now
                    val statusName = when (status) {
                        DownloadManager.STATUS_PENDING -> "PENDING"
                        DownloadManager.STATUS_RUNNING -> "RUNNING"
                        DownloadManager.STATUS_PAUSED -> "PAUSED(reason=$pauseReason)"
                        else -> "UNKNOWN($status)"
                    }
                    android.util.Log.w("ScoutBrain",
                        "pollProgress: id=$downloadId status=$statusName downloaded=$downloaded total=$total (no size reported yet)")
                }
            }
        } else {
            cursor?.close()
            val now = System.currentTimeMillis()
            if (now - lastNoRowLogMs > 5_000L) {
                lastNoRowLogMs = now
                android.util.Log.w("ScoutBrain", "pollProgress: DownloadManager has no row for id=$downloadId")
            }
        }
        handler.postDelayed({ pollProgress() }, 800L)
    }

    private fun onDownloadComplete() {
        if (downloadDone) return
        val dest = File(getExternalFilesDir(null) ?: filesDir, MODEL_FILENAME)
        if (!dest.exists() || dest.length() < MIN_MODEL_BYTES) {
            dest.delete()
            getSharedPreferences("scout_prefs", MODE_PRIVATE).edit()
                .remove(PREF_DOWNLOAD_ID).apply()
            showRetry("Download incomplete — tap here to try again.")
            return
        }
        downloadDone = true
        getSharedPreferences("scout_prefs", MODE_PRIVATE).edit()
            .remove(PREF_DOWNLOAD_ID).apply()
        updateProgress(100, "", "", "")
        // Signal MainActivity that the model file is ready
        setResult(RESULT_OK)
        finish()
    }

    private fun showRetry(message: String) {
        percentText.text = ""
        sizeText.text = message
        sizeText.setOnClickListener {
            sizeText.setOnClickListener(null)
            sizeText.text = "Retrying…"
            startDownload()
        }
    }

    private fun isDownloadActive(id: Long): Boolean {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return false
        if (!cursor.moveToFirst()) { cursor.close(); return false }
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        cursor.close()
        return status == DownloadManager.STATUS_RUNNING ||
               status == DownloadManager.STATUS_PENDING ||
               status == DownloadManager.STATUS_PAUSED
    }

    private fun formatBytes(bytes: Long): String {
        return if (bytes >= 1_073_741_824L) "%.2f GB".format(bytes / 1_073_741_824.0)
        else "%.0f MB".format(bytes / 1_048_576.0)
    }

    fun updateProgress(percent: Int, downloaded: String, total: String, timeLeft: String) {
        runOnUiThread {
            progressBar.progress = percent.coerceIn(0, 100)
            percentText.text = "$percent%"
            sizeText.text = when {
                percent == 100        -> "Download complete!"
                timeLeft.isNotBlank() -> "$downloaded of $total  •  $timeLeft"
                downloaded.isNotBlank() -> "$downloaded of $total"
                else                  -> "Starting download…"
            }
        }
    }

    // ── Message cycling ──────────────────────────────────────────

    private fun cycleMessage() {
        if (animating) return
        animating = true

        val slideOut = ObjectAnimator.ofFloat(messageView, "translationX", 0f, -screenWidth.toFloat())
        val fadeOut  = ObjectAnimator.ofFloat(messageView, "alpha", 1f, 0f)
        slideOut.duration = SLIDE_OUT_MS
        fadeOut.duration  = SLIDE_OUT_MS
        slideOut.interpolator = DecelerateInterpolator()
        fadeOut.interpolator  = DecelerateInterpolator()
        AnimatorSet().also { it.playTogether(slideOut, fadeOut); it.start() }

        handler.postDelayed({
            messageIndex = (messageIndex + 1) % messages.size
            if (messageIndex == 0) messages.shuffle()
            messageView.text = messages[messageIndex]
            messageView.translationX = screenWidth.toFloat()
            messageView.alpha = 0f

            val slideIn = ObjectAnimator.ofFloat(messageView, "translationX", screenWidth.toFloat(), 0f)
            val fadeIn  = ObjectAnimator.ofFloat(messageView, "alpha", 0f, 1f)
            slideIn.duration = SLIDE_IN_MS
            fadeIn.duration  = SLIDE_IN_MS
            slideIn.interpolator = DecelerateInterpolator()
            fadeIn.interpolator  = DecelerateInterpolator()
            AnimatorSet().also { it.playTogether(slideIn, fadeIn); it.start() }

            handler.postDelayed({
                animating = false
                handler.postDelayed({ cycleMessage() }, HOLD_MS)
            }, SLIDE_IN_MS)
        }, SLIDE_OUT_MS)
    }
}
