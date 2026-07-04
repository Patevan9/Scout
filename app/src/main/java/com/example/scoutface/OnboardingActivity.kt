package com.example.scoutface

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OnboardingActivity : AppCompatActivity() {

    companion object {
        const val PREF_ONBOARDING_DONE = "onboarding_complete"
        private const val TOTAL_PAGES = 5
    }

    private data class OnboardPage(
        val title: String,
        val body: String,
        val bullets: List<String> = emptyList(),
        val showIcon: Boolean = false
    )

    private val pages = listOf(
        OnboardPage(
            title = "Welcome to Scout",
            body = "I'm Scout. Just say my name and I'll be listening.",
            bullets = listOf(
                "See & Recognize — I see faces, scenes, and more.",
                "Remember & Learn — Tell me anything. I'll keep it.",
                "Speak & Listen — Works completely offline.",
                "No account required. No subscriptions."
            ),
            showIcon = true
        ),
        OnboardPage(
            title = "Try Scout Free for 7 Days",
            body = "\$9.99 one time.\nNever a subscription.\nPay once. Own Scout forever."
        ),
        OnboardPage(
            title = "This Is Just The Beginning",
            body = "Scout is actively growing. More is coming.",
            bullets = listOf(
                "Calendar integration",
                "News & updates",
                "More expressions",
                "Additional languages"
            )
        ),
        OnboardPage(
            title = "Your Privacy Matters",
            body = "No account needed. You are in control.",
            bullets = listOf(
                "Your conversations stay on your phone.",
                "Face recognition runs locally — never uploaded.",
                "Gemini AI is optional and clearly labeled.",
                "You decide what Scout remembers."
            )
        ),
        OnboardPage(
            title = "Ready To Begin?",
            body = "This is just the beginning.",
            showIcon = true
        )
    )

    // Single source of truth for the current page — drives dots, counter, and content.
    private var currentPage = 0

    private lateinit var onboardIcon: ImageView
    private lateinit var onboardTitle: TextView
    private lateinit var onboardBody: TextView
    private lateinit var bulletContainer: LinearLayout
    private lateinit var dotsContainer: LinearLayout
    private lateinit var pageCounter: TextView
    private lateinit var btnNext: Button
    private lateinit var dotViews: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        onboardIcon = findViewById(R.id.onboardIcon)
        onboardTitle = findViewById(R.id.onboardTitle)
        onboardBody = findViewById(R.id.onboardBody)
        bulletContainer = findViewById(R.id.bulletContainer)
        dotsContainer = findViewById(R.id.dotsContainer)
        pageCounter = findViewById(R.id.pageCounter)
        btnNext = findViewById(R.id.btnNext)

        buildDots()
        showPage(currentPage)

        btnNext.setOnClickListener {
            if (currentPage < TOTAL_PAGES - 1) {
                currentPage++
                showPage(currentPage)
            } else {
                finishOnboarding()
            }
        }
    }

    private fun buildDots() {
        val dotSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 4
        val dotMargin = (6 * resources.displayMetrics.density).toInt()
        val views = mutableListOf<View>()
        repeat(TOTAL_PAGES) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(dotSize, dotSize)
            params.setMargins(0, 0, dotMargin, 0)
            dot.layoutParams = params
            dot.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#2A3A5C"))
            }
            dotsContainer.addView(dot)
            views.add(dot)
        }
        dotViews = views
    }

    private fun showPage(index: Int) {
        val page = pages[index]

        // Icon
        onboardIcon.visibility = if (page.showIcon) View.VISIBLE else View.GONE

        // Text content
        onboardTitle.text = page.title
        onboardBody.text = page.body
        onboardBody.visibility = if (page.body.isNotBlank()) View.VISIBLE else View.GONE

        // Bullet points
        bulletContainer.removeAllViews()
        page.bullets.forEach { bullet ->
            val tv = TextView(this)
            tv.text = "•  $bullet"
            tv.setTextColor(android.graphics.Color.parseColor("#B0C4E8"))
            tv.textSize = 14f
            tv.setPadding(0, 4, 0, 4)
            bulletContainer.addView(tv)
        }
        bulletContainer.visibility = if (page.bullets.isNotEmpty()) View.VISIBLE else View.GONE

        // Dots — currentPage is the single variable driving both dots and counter
        dotViews.forEachIndexed { i, dot ->
            val color = if (i == index) "#9BBEFF" else "#2A3A5C"
            (dot.background as android.graphics.drawable.GradientDrawable)
                .setColor(android.graphics.Color.parseColor(color))
        }

        // Counter driven by the same index
        pageCounter.text = "${index + 1} / $TOTAL_PAGES"

        // Button label
        btnNext.text = if (index == TOTAL_PAGES - 1) "Start Using Scout" else "Next"
    }

    private fun finishOnboarding() {
        getSharedPreferences("scout_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ONBOARDING_DONE, true)
            .apply()
        // New installs start in offline mode. The user can enable online
        // mode in Settings once they have a Gemini API key.
        getSharedPreferences("scout_memory", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("gemini_enabled", false)
            .apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
