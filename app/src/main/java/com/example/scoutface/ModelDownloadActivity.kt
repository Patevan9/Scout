package com.example.scoutface

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ModelDownloadActivity : AppCompatActivity() {

    companion object {
        // Extra keys for callers
        const val EXTRA_TOTAL_BYTES = "total_bytes"
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

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var messageView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var percentText: TextView
    private lateinit var sizeText: TextView

    private var messageIndex = 0
    private var screenWidth = 0
    private var animating = false

    // Duration constants (ms)
    private val SLIDE_IN_MS = 320L
    private val HOLD_MS = 3800L
    private val SLIDE_OUT_MS = 280L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_download)

        messageView = findViewById(R.id.downloadMessage)
        progressBar = findViewById(R.id.downloadProgress)
        percentText = findViewById(R.id.downloadPercent)
        sizeText = findViewById(R.id.downloadSizeText)

        screenWidth = resources.displayMetrics.widthPixels
        messages.shuffle()

        // Show first message immediately, then start cycling
        messageView.text = messages[messageIndex]
        handler.postDelayed({ cycleMessage() }, HOLD_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun cycleMessage() {
        if (animating) return
        animating = true

        // Slide out to left + fade
        val slideOut = ObjectAnimator.ofFloat(messageView, "translationX", 0f, -screenWidth.toFloat())
        val fadeOut = ObjectAnimator.ofFloat(messageView, "alpha", 1f, 0f)
        slideOut.duration = SLIDE_OUT_MS
        fadeOut.duration = SLIDE_OUT_MS
        slideOut.interpolator = DecelerateInterpolator()
        fadeOut.interpolator = DecelerateInterpolator()

        val outSet = AnimatorSet()
        outSet.playTogether(slideOut, fadeOut)
        outSet.start()

        handler.postDelayed({
            // Advance to next message (wrap around)
            messageIndex = (messageIndex + 1) % messages.size

            // Re-shuffle the deck when we complete a full cycle
            if (messageIndex == 0) messages.shuffle()

            messageView.text = messages[messageIndex]
            messageView.translationX = screenWidth.toFloat()
            messageView.alpha = 0f

            // Slide in from right + fade in
            val slideIn = ObjectAnimator.ofFloat(messageView, "translationX", screenWidth.toFloat(), 0f)
            val fadeIn = ObjectAnimator.ofFloat(messageView, "alpha", 0f, 1f)
            slideIn.duration = SLIDE_IN_MS
            fadeIn.duration = SLIDE_IN_MS
            slideIn.interpolator = DecelerateInterpolator()
            fadeIn.interpolator = DecelerateInterpolator()

            val inSet = AnimatorSet()
            inSet.playTogether(slideIn, fadeIn)
            inSet.start()

            handler.postDelayed({
                animating = false
                handler.postDelayed({ cycleMessage() }, HOLD_MS)
            }, SLIDE_IN_MS)

        }, SLIDE_OUT_MS)
    }

    /**
     * Call this from the download manager to update the progress UI.
     * All params are display strings ready to show to the user.
     *
     * @param percent      0–100
     * @param downloaded   e.g. "1.24 GB"
     * @param total        e.g. "2.80 GB"
     * @param timeLeft     e.g. "About 3 min left"  (pass "" to omit)
     */
    fun updateProgress(percent: Int, downloaded: String, total: String, timeLeft: String) {
        runOnUiThread {
            progressBar.progress = percent.coerceIn(0, 100)
            percentText.text = "$percent%"
            sizeText.text = if (timeLeft.isBlank()) {
                "$downloaded of $total"
            } else {
                "$downloaded of $total  •  $timeLeft"
            }
        }
    }
}
