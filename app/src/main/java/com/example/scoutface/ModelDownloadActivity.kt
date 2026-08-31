package com.example.scoutface

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

// The one gate MainActivity waits on before it appears, asks permissions, or does
// anything else -- covers three phases in order: Downloading (only if the model file
// isn't present anywhere locally), Loading (the offline brain into memory, triggered
// from here since that's the only reliable way to avoid a deadlock with MainActivity's
// own boot), and a brief Preparing beat before handing control back. Never finishes
// with RESULT_OK until LlamaEngine.isReady is actually true.
class ModelDownloadActivity : AppCompatActivity() {

    companion object {
        // Qwen migration Step 3 -- active local model. Was
        // "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"; MainActivity.MODEL_FILENAME
        // references this constant directly (see MainActivity.kt) rather than
        // carrying its own copy, so there is exactly one source of truth for
        // this filename across the app.
        const val MODEL_FILENAME    = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        // Hosted as a GitHub Release asset on this repo. Re-upload and update this URL
        // if the model file ever changes or the release is deleted.
        const val MODEL_DOWNLOAD_URL =
            "https://github.com/Patevan9/Scout/releases/download/model-v1/$MODEL_FILENAME"
        private const val PREF_DOWNLOAD_ID = "model_download_id"
        // Minimum byte size for a valid Q4_K_M download — anything below this is
        // truncated. Qwen migration Step 3: the real release asset is
        // 1,117,320,736 bytes (independently verified). Was 500_000_000L, sized
        // for TinyLlama's ~669MB file; raised to a conservative floor still well
        // below Qwen's true size but well above what a truncated/partial
        // download (e.g. ~800MB) would reach, so an incomplete Qwen download
        // can no longer be mistaken for a complete one.
        const val MIN_MODEL_BYTES = 1_000_000_000L
        // True only when a real network download actually happened this run -- lets
        // MainActivity distinguish "just downloaded" (speak the first-time/again line)
        // from an ordinary launch that only needed to load an already-present file.
        const val EXTRA_DID_DOWNLOAD = "did_download"
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
        "Rummaging through the junk drawer...",
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
    private lateinit var rootScroll   : View
    private lateinit var messageView  : TextView
    private lateinit var progressBar  : ProgressBar
    private lateinit var percentText  : TextView
    private lateinit var sizeText     : TextView
    private lateinit var tipText      : TextView
    private lateinit var tipContainer : View

    private var messageIndex  = 0
    private var screenWidth   = 0
    private var animating     = false
    private var downloadId    = -1L
    private var downloadDone  = false
    private var lastNoRowLogMs = 0L
    private var lastStallLogMs = 0L
    private var tipIndex      = 0
    private var didDownload   = false

    private val SLIDE_IN_MS = 320L
    private val HOLD_MS     = 3800L
    private val SLIDE_OUT_MS = 280L
    private val TIP_HOLD_MS = 6000L
    private val TIP_FADE_MS = 200L
    private val PREPARING_MS = 1400L

    // Real, already-shipped feature tips only -- deliberately not shuffled, fixed
    // order. Interruption handling is intentionally excluded until that feature is
    // reliable; tips should only ever describe what Scout can actually do today.
    private val tips = listOf(
        "To enter Scout Settings, simply swipe to the right.",
        "Scout remembers what you teach him, stored locally on this device.",
        "Your privacy stays on your device whenever possible.",
        "You can ask Scout about today's weather when connected to the internet.",
        "Scout gets to know you over time."
    )

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

        rootScroll   = findViewById(R.id.rootScroll)
        messageView  = findViewById(R.id.downloadMessage)
        progressBar  = findViewById(R.id.downloadProgress)
        percentText  = findViewById(R.id.downloadPercent)
        sizeText     = findViewById(R.id.downloadSizeText)
        tipText      = findViewById(R.id.tipText)
        tipContainer = findViewById(R.id.tipContainer)

        screenWidth = resources.displayMetrics.widthPixels
        messages.shuffle()
        messageView.text = messages[messageIndex]
        handler.postDelayed({ cycleMessage() }, HOLD_MS)

        registerReceiver(
            completionReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            RECEIVER_NOT_EXPORTED
        )

        if (LlamaEngine.isReady) {
            finishReady()
            return
        }

        val internalDest = File(filesDir, MODEL_FILENAME)
        if (internalDest.exists() && internalDest.length() >= MIN_MODEL_BYTES) {
            enterLoadingPhase()
            return
        }

        val externalDest = File(getExternalFilesDir(null) ?: filesDir, MODEL_FILENAME)
        if (externalDest.exists() && externalDest.length() >= MIN_MODEL_BYTES) {
            // Downloaded in an earlier session but never copied into filesDir
            // (e.g. the app was closed before that step completed).
            copyIntoFilesDirThenLoad(externalDest, internalDest)
            return
        }

        // Check for any other pre-existing local copy before falling back to a real
        // download -- same search MainActivity.bootstrapModelFile() used to do.
        Thread {
            val sources = mutableListOf<File>()
            getExternalFilesDir(null)?.let { sources.add(File(it, MODEL_FILENAME)) }
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                @Suppress("DEPRECATION")
                sources.add(File(android.os.Environment.getExternalStorageDirectory(), MODEL_FILENAME))
            }
            val src = sources.firstOrNull { it.exists() && it.canRead() && it.length() >= MIN_MODEL_BYTES }

            runOnUiThread {
                if (src != null) {
                    copyIntoFilesDirThenLoad(src, internalDest)
                } else {
                    // Deliberately never resumes a saved download ID -- DownloadManager's
                    // entries live in a system-wide database that isn't necessarily cleared
                    // by "clear app data," so a stale saved ID can point at an old, silently
                    // dead entry that reports as active forever. Always starting fresh trades
                    // a small convenience (resuming after briefly backgrounding mid-download)
                    // for eliminating a real, repeat "stuck at 0%" failure mode.
                    startDownload()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(completionReceiver) }
    }

    // ── Phase 1: Downloading ──────────────────────────────────────

    private fun startDownload() {
        android.util.Log.i("ScoutBrain", "startDownload() called")
        didDownload = true
        val dir = getExternalFilesDir(null) ?: filesDir
        if (getExternalFilesDir(null) == null) {
            android.util.Log.w("ScoutBrain", "getExternalFilesDir(null) returned null — falling back to filesDir")
        }
        val stat = android.os.StatFs(dir.absolutePath)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        // Qwen migration Step 3: the real download is 1,117,320,736 bytes, not
        // TinyLlama's ~669MB -- was MIN_MODEL_BYTES + 50_000_000L (~550MB),
        // sized for the old file. Headroom is kept generous (300MB) rather than
        // the bare minimum: the download destination alone needs the full
        // ~1.12GB, and this is only a pre-flight sanity check, not a guarantee
        // -- DownloadManager's own STATUS_FAILED handling (see pollProgress())
        // remains the real backstop if a device runs out of space mid-download.
        if (freeBytes < MIN_MODEL_BYTES + 300_000_000L) {
            android.util.Log.e("ScoutBrain", "Not enough storage: dir=${dir.absolutePath} freeBytes=$freeBytes")
            sizeText.text = "Not enough storage — free up at least 1.3 GB and reopen Scout."
            percentText.text = ""
            return
        }

        // Shown immediately -- DownloadManager doesn't report real byte progress for a
        // few seconds after enqueueing, and the screen shouldn't sit blank until then.
        // updateProgress() overwrites this once real numbers start coming in.
        sizeText.text = "Downloading Scout's offline AI brain — this is a one-time setup and may take a few minutes."

        val destFile = File(dir, MODEL_FILENAME)
        if (destFile.exists()) destFile.delete()

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Explicitly cancel any old entry from a previous attempt rather than leaving it
        // orphaned in DownloadManager's own system-wide database -- that database isn't
        // necessarily cleared by "clear app data," so leftover dead entries can otherwise
        // accumulate indefinitely across repeated fresh installs.
        val oldId = getSharedPreferences("scout_prefs", MODE_PRIVATE).getLong(PREF_DOWNLOAD_ID, -1L)
        if (oldId != -1L) {
            try { dm.remove(oldId) } catch (_: Exception) {}
        }

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
                showRetry("Download failed — tap here to try again.") { startDownload() }
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
            showRetry("Download incomplete — tap here to try again.") { startDownload() }
            return
        }
        downloadDone = true
        getSharedPreferences("scout_prefs", MODE_PRIVATE).edit()
            .remove(PREF_DOWNLOAD_ID).apply()
        updateProgress(100, "", "", "")
        copyIntoFilesDirThenLoad(dest, File(filesDir, MODEL_FILENAME))
    }

    private fun copyIntoFilesDirThenLoad(src: File, dest: File) {
        Thread {
            try {
                if (src.absolutePath != dest.absolutePath) {
                    src.inputStream().use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                android.util.Log.i("ScoutBrain", "Model copy complete (${dest.length() / 1_048_576}MB)")
            } catch (e: Exception) {
                android.util.Log.e("ScoutBrain", "Model copy failed", e)
                dest.delete()
            }
            runOnUiThread { enterLoadingPhase() }
        }.start()
    }

    // ── Phase 2: Loading into memory ──────────────────────────────

    private fun enterLoadingPhase() {
        val dest = File(filesDir, MODEL_FILENAME)
        if (!dest.exists() || dest.length() < MIN_MODEL_BYTES) {
            showRetry("Something went wrong preparing the offline brain — tap here to try again.") {
                startDownload()
            }
            return
        }

        // Loading has no real progress metric to report (llama.cpp gives no partial-load
        // percentage), so this phase is deliberately styled differently from Downloading
        // rather than faking a bar/number: solid black background matching Scout's own
        // face screen, with just a large, prominent status line.
        rootScroll.setBackgroundColor(android.graphics.Color.BLACK)
        progressBar.visibility = View.GONE
        percentText.visibility = View.GONE
        sizeText.textSize = 20f
        sizeText.setTypeface(sizeText.typeface, android.graphics.Typeface.BOLD)
        sizeText.text = "Waking Scout up…"

        tipContainer.visibility = View.VISIBLE
        tipText.text = tips[tipIndex]
        handler.postDelayed({ cycleTip() }, TIP_HOLD_MS)

        LlamaEngine.loadAsync(modelFile = dest, nCtx = 512, nThreads = 2) { success ->
            runOnUiThread {
                if (success) {
                    enterPreparingPhase()
                } else {
                    showRetry("The offline brain failed to load — tap here to try again.") {
                        enterLoadingPhase()
                    }
                }
            }
        }
    }

    // ── Phase 3: Preparing (brief beat before handing back to MainActivity) ──────

    private fun enterPreparingPhase() {
        sizeText.text = "Preparing Scout…"
        handler.postDelayed({ finishReady() }, PREPARING_MS)
    }

    private fun finishReady() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_DID_DOWNLOAD, didDownload))
        finish()
    }

    private fun showRetry(message: String, retry: () -> Unit) {
        percentText.text = ""
        sizeText.text = message
        sizeText.setOnClickListener {
            sizeText.setOnClickListener(null)
            sizeText.text = "Retrying…"
            retry()
        }
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
                timeLeft.isNotBlank() -> "Downloading… $downloaded of $total  •  $timeLeft"
                downloaded.isNotBlank() -> "Downloading… $downloaded of $total"
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

    // Simple crossfade — independent of cycleMessage()'s slide animation above.
    // Fixed order (no shuffle): real feature tips only, in the order declared in `tips`.
    private fun cycleTip() {
        val fadeOut = ObjectAnimator.ofFloat(tipText, "alpha", 1f, 0f)
        fadeOut.duration = TIP_FADE_MS
        fadeOut.start()

        handler.postDelayed({
            tipIndex = (tipIndex + 1) % tips.size
            tipText.text = tips[tipIndex]

            val fadeIn = ObjectAnimator.ofFloat(tipText, "alpha", 0f, 1f)
            fadeIn.duration = TIP_FADE_MS
            fadeIn.start()

            handler.postDelayed({ cycleTip() }, TIP_HOLD_MS)
        }, TIP_FADE_MS)
    }
}
