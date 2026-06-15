package com.example.scoutface.brain

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fetches current weather or forecast from Open-Meteo.
 * Open-Meteo is completely free — no API key required.
 * API reference: https://open-meteo.com/
 *
 * Handles five question types:
 * CURRENT      — "what's the weather", "is it raining"
 * TONIGHT      — "what's the weather tonight"
 * TOMORROW     — "what's the weather tomorrow"
 * SPECIFIC_DAY — "what's the weather Saturday" / "what's the weather going to be today"
 * WEEK         — "weather this week", "forecast for next week"
 *
 * Each type has its own cache so they never overwrite each other.
 * If Scout can't see that far ahead he says so honestly.
 *
 * Does NOT touch speech, camera, downloads, or any memory layer.
 */
class ScoutWeatherManager(
    private val context: Context,
    private val hasValidatedInternet: () -> Boolean,
    private val runOnMain: (() -> Unit) -> Unit,
    private val respond: (String) -> Unit,
    private val finishThinking: () -> Unit
) {

    // =======================
    // QUERY TYPE
    // =======================

    private enum class QueryType {
        CURRENT, TONIGHT, TOMORROW, SPECIFIC_DAY, WEEK
    }


    // =======================
    // CONSTANTS
    // =======================

    companion object {
        private const val TAG = "ScoutWeather"
        private const val PREFS_NAME = "scout_weather_cache"

        // Separate cache key per query type so they never overwrite each other
        private const val KEY_CURRENT_TEXT    = "current_text"
        private const val KEY_CURRENT_AGE_MS  = "current_age_ms"
        private const val KEY_TONIGHT_TEXT    = "tonight_text"
        private const val KEY_TONIGHT_AGE_MS  = "tonight_age_ms"
        private const val KEY_TOMORROW_TEXT   = "tomorrow_text"
        private const val KEY_TOMORROW_AGE_MS = "tomorrow_age_ms"
        private const val KEY_WEEK_TEXT       = "week_text"
        private const val KEY_WEEK_AGE_MS     = "week_age_ms"

        private const val CURRENT_CACHE_MS  = 30L * 60L * 1_000L      // 30 minutes
        private const val FORECAST_CACHE_MS = 3L * 60L * 60L * 1_000L // 3 hours
    }


    // =======================
    // STATE
    // =======================

    private val requestInFlight = AtomicBoolean(false)

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)


    // =======================
    // PUBLIC ENTRY POINT
    // =======================

    fun fetchWeather(query: String = "") {

        if (!requestInFlight.compareAndSet(false, true)) {
            Log.e(TAG, "Blocked: request already in flight")
            finishThinking()
            return
        }

        val q = query.trim().lowercase()

        // Detect a specific day name first (highest priority)
        val specificDay = listOf(
            "monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday"
        ).firstOrNull { q.contains(it) }
            ?.replaceFirstChar { it.uppercase() }

        val type = when {
            specificDay != null  -> QueryType.SPECIFIC_DAY
            q.contains("tomorrow") -> QueryType.TOMORROW
            q.contains("tonight")  -> QueryType.TONIGHT
            q.contains("week") || q.contains("days") ||
                    q.contains("forecast") -> QueryType.WEEK
            else                   -> QueryType.CURRENT
        }

        // If user asks about today's forecast (future tense), use SPECIFIC_DAY with today's name
        val todayName = if (specificDay == null && q.contains("today") &&
            (q.contains("going to") || q.contains("will") ||
                    q.contains("later") || q.contains("rest of")))
            SimpleDateFormat("EEEE", Locale.US).format(java.util.Date()) else null

        dispatch(if (todayName != null) QueryType.SPECIFIC_DAY else type, todayName ?: specificDay)
    }


    // =======================
    // DISPATCH
    // =======================

    private fun dispatch(type: QueryType, specificDay: String?) {

        // Pick the right cache keys and duration for this type
        val (textKey, ageKey, cacheDuration) = when (type) {
            QueryType.CURRENT      -> Triple(KEY_CURRENT_TEXT,  KEY_CURRENT_AGE_MS,  CURRENT_CACHE_MS)
            QueryType.TONIGHT      -> Triple(KEY_TONIGHT_TEXT,  KEY_TONIGHT_AGE_MS,  FORECAST_CACHE_MS)
            QueryType.TOMORROW     -> Triple(KEY_TOMORROW_TEXT, KEY_TOMORROW_AGE_MS, FORECAST_CACHE_MS)
            QueryType.SPECIFIC_DAY -> Triple(KEY_WEEK_TEXT,     KEY_WEEK_AGE_MS,     FORECAST_CACHE_MS)
            QueryType.WEEK         -> Triple(KEY_WEEK_TEXT,     KEY_WEEK_AGE_MS,     FORECAST_CACHE_MS)
        }

        // No internet — try cache, then apologize
        if (!hasValidatedInternet()) {
            requestInFlight.set(false)
            val cached = prefs.getString(textKey, null)
            if (cached != null) {
                runOnMain { respond("I can't check the weather right now — I'm not connected to the internet.") }
            } else {
                runOnMain { respond("I can't check the weather right now — I'm not connected to the internet.") }
            }
            return
        }

        // Cache still fresh — use it (skipped for specific day queries — always fetch fresh)
        val cacheAge = System.currentTimeMillis() - prefs.getLong(ageKey, 0L)
        if (cacheAge < cacheDuration && type != QueryType.SPECIFIC_DAY) {
            val cached = prefs.getString(textKey, null)
            if (cached != null) {
                requestInFlight.set(false)
                runOnMain { respond(cached) }
                return
            }
        }

        if (!checkPermission()) return

        Thread {
            try {
                val (lat, lon) = getDeviceLocation() ?: run {
                    runOnMain {
                        requestInFlight.set(false)
                        respond("I couldn't get your location right now. I need that to check the weather.")
                    }
                    return@Thread
                }

                val result: String? = when (type) {
                    QueryType.CURRENT      -> callCurrentApi(lat, lon)
                    QueryType.TONIGHT      -> callForecastApi(lat, lon)?.let { parseTonightResponse(it) }
                    QueryType.TOMORROW     -> callForecastApi(lat, lon)?.let { parseTomorrowResponse(it) }
                    QueryType.WEEK         -> callForecastApi(lat, lon)?.let { parseWeekResponse(it) }
                    QueryType.SPECIFIC_DAY -> {
                        val raw = callForecastApi(lat, lon)
                        if (raw != null) {
                            // Save full week text to cache for SPECIFIC_DAY reuse
                            val weekText = parseWeekResponse(raw)
                            if (weekText != null) {
                                prefs.edit()
                                    .putString(KEY_WEEK_TEXT, weekText)
                                    .putLong(KEY_WEEK_AGE_MS, System.currentTimeMillis())
                                    .apply()
                            }
                            parseSpecificDayResponse(raw, specificDay ?: "")
                        } else null
                    }
                }

                runOnMain {
                    requestInFlight.set(false)
                    if (result != null) {
                        // Cache the result (skip SPECIFIC_DAY — already cached above as week)
                        if (type != QueryType.SPECIFIC_DAY) {
                            prefs.edit()
                                .putString(textKey, result)
                                .putLong(ageKey, System.currentTimeMillis())
                                .apply()
                        }
                        respond(result)
                    } else {
                        val cached = prefs.getString(textKey, null)
                        if (cached != null) respond("I couldn't get a fresh reading. My last one: $cached")
                        else respond("I wasn't able to reach the weather service right now.")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Weather fetch crashed", e)
                runOnMain {
                    requestInFlight.set(false)
                    respond("Something went wrong when I tried to check the weather.")
                }
            }
        }.start()
    }


    // =======================
    // LOCATION
    // =======================

    private fun checkPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestInFlight.set(false)
            runOnMain {
                respond("I need location access to check the weather. I will ask for it next time Scout starts up.")
            }
        }
        return granted
    }

    private fun getDeviceLocation(): Pair<Double, Double>? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var bestAccuracy = Float.MAX_VALUE
        var bestLat = Double.NaN
        var bestLon = Double.NaN
        for (provider in providers) {
            try {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (loc.accuracy < bestAccuracy) {
                    bestAccuracy = loc.accuracy
                    bestLat = loc.latitude
                    bestLon = loc.longitude
                }
            } catch (_: SecurityException) {}
        }
        return if (!bestLat.isNaN()) Pair(bestLat, bestLon) else null
    }


    // =======================
    // API CALLS
    // =======================

    private fun callCurrentApi(lat: Double, lon: Double): String? {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current_weather=true" +
                    "&temperature_unit=fahrenheit" +
                    "&wind_speed_unit=mph"
        )
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout    = 15_000
            val code = conn.responseCode
            if (code !in 200..299) { conn.disconnect(); return null }
            val raw = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            parseCurrentResponse(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Current API failed: $e")
            null
        }
    }

    /** Returns the raw JSON string from the daily forecast endpoint, or null on failure. */
    private fun callForecastApi(lat: Double, lon: Double): String? {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&temperature_unit=fahrenheit" +
                    "&timezone=auto"
        )
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout    = 15_000
            val code = conn.responseCode
            if (code !in 200..299) { conn.disconnect(); return null }
            val raw = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            raw
        } catch (e: Exception) {
            Log.e(TAG, "Forecast API failed: $e")
            null
        }
    }


    // =======================
    // PARSERS
    // =======================

    private fun parseCurrentResponse(raw: String): String? {
        return try {
            val cw   = JSONObject(raw).optJSONObject("current_weather") ?: return null
            val tempF = cw.optDouble("temperature", Double.NaN)
            val code  = cw.optInt("weathercode", -1)
            val wind  = cw.optDouble("windspeed", Double.NaN)
            if (tempF.isNaN() || code < 0) return null
            val windPart = if (!wind.isNaN() && wind >= 10.0)
                " Winds around ${wind.toInt()} miles per hour." else ""
            "It is ${tempF.toInt()} degrees outside. ${wmoCodeToText(code)}.$windPart"
        } catch (e: Exception) { null }
    }

    private fun parseTonightResponse(raw: String): String? {
        return try {
            val daily = JSONObject(raw).optJSONObject("daily") ?: return null
            val codes = daily.optJSONArray("weathercode") ?: return null
            val lows  = daily.optJSONArray("temperature_2m_min") ?: return null
            val probs = daily.optJSONArray("precipitation_probability_max")
            // Index 0 = today. The daily low IS tonight's expected low.
            val low  = lows.optDouble(0, Double.NaN)
            val code = codes.optInt(0, -1)
            if (low.isNaN() || code < 0) return null
            val probPct  = probs?.optInt(0, -1) ?: -1
            val probPart = if (probPct in 5..100) " $probPct percent chance of precipitation." else ""
            "Tonight should be around ${low.toInt()} degrees. ${wmoCodeToText(code)}.$probPart"
        } catch (e: Exception) { null }
    }

    private fun parseTomorrowResponse(raw: String): String? {
        return try {
            val daily = JSONObject(raw).optJSONObject("daily") ?: return null
            val codes = daily.optJSONArray("weathercode") ?: return null
            val highs = daily.optJSONArray("temperature_2m_max") ?: return null
            val lows  = daily.optJSONArray("temperature_2m_min") ?: return null
            val probs = daily.optJSONArray("precipitation_probability_max")
            if (highs.length() < 2) return null
            // Index 0 = today, index 1 = tomorrow
            val high = highs.optDouble(1, Double.NaN)
            val low  = lows.optDouble(1, Double.NaN)
            val code = codes.optInt(1, -1)
            if (high.isNaN() || low.isNaN() || code < 0) return null
            val probPct  = probs?.optInt(1, -1) ?: -1
            val probPart = if (probPct in 5..100) " $probPct percent chance of precipitation." else ""
            "Tomorrow: high of ${high.toInt()}, low of ${low.toInt()}. ${wmoCodeToText(code)}.$probPart"
        } catch (e: Exception) { null }
    }

    private fun parseWeekResponse(raw: String): String? {
        return try {
            val daily = JSONObject(raw).optJSONObject("daily") ?: return null
            val times = daily.optJSONArray("time") ?: return null
            val codes = daily.optJSONArray("weathercode") ?: return null
            val highs = daily.optJSONArray("temperature_2m_max") ?: return null
            val lows  = daily.optJSONArray("temperature_2m_min") ?: return null
            val probs = daily.optJSONArray("precipitation_probability_max")
            val days  = minOf(times.length(), 7)
            if (days < 2) return null

            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dayFmt  = SimpleDateFormat("EEEE", Locale.US)
            val sb      = StringBuilder("Here is the week ahead. ")

            for (i in 1 until days) { // skip index 0 (today)
                val high = highs.optDouble(i, Double.NaN)
                val low  = lows.optDouble(i, Double.NaN)
                val code = codes.optInt(i, -1)
                if (high.isNaN() || low.isNaN() || code < 0) continue

                val dayLabel = run {
                    val dateStr = times.optString(i, "")
                    val date    = runCatching { dateFmt.parse(dateStr) }.getOrNull()
                    if (date != null) dayFmt.format(date) else "Day $i"
                }

                val probPct  = probs?.optInt(i, -1) ?: -1
                val probPart = if (probPct in 5..100) ", $probPct percent chance of precipitation" else ""
                sb.append("$dayLabel: high of ${high.toInt()}, low of ${low.toInt()}, ${wmoCodeToText(code)}$probPart. ")
            }
            sb.toString().trimEnd()
        } catch (e: Exception) { null }
    }

    /**
     * Finds a specific day name in the 7-day forecast.
     * Returns an honest out-of-range message if the day is not in the next 7 days.
     */
    private fun parseSpecificDayResponse(raw: String, dayName: String): String? {
        return try {
            val daily = JSONObject(raw).optJSONObject("daily") ?: return null
            val times = daily.optJSONArray("time") ?: return null
            val codes = daily.optJSONArray("weathercode") ?: return null
            val highs = daily.optJSONArray("temperature_2m_max") ?: return null
            val lows  = daily.optJSONArray("temperature_2m_min") ?: return null
            val probs = daily.optJSONArray("precipitation_probability_max")

            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dayFmt  = SimpleDateFormat("EEEE", Locale.US)

            for (i in 0 until times.length()) {
                val dateStr = times.optString(i, "") ?: continue
                val date    = runCatching { dateFmt.parse(dateStr) }.getOrNull() ?: continue
                val name    = dayFmt.format(date)
                if (name.equals(dayName, ignoreCase = true)) {
                    val high = highs.optDouble(i, Double.NaN)
                    val low  = lows.optDouble(i, Double.NaN)
                    val code = codes.optInt(i, -1)
                    if (high.isNaN() || low.isNaN() || code < 0) return null
                    val label    = if (i == 0) "Today" else dayName
                    val probPct  = probs?.optInt(i, -1) ?: -1
                    val probPart = if (probPct in 5..100) " $probPct percent chance of precipitation." else ""
                    return "$label: high of ${high.toInt()}, low of ${low.toInt()}. ${wmoCodeToText(code)}.$probPart"
                }
            }

            // Day was not found in the 7-day window
            "I can only see about a week ahead for weather. I'm sorry, I can't tell you about $dayName from here."
        } catch (e: Exception) { null }
    }

    /**
     * Used when the week forecast is cached and the user asks for a specific day.
     * Scans the cached week text for the day name rather than calling the API again.
     */
    private fun extractDayFromWeekText(weekText: String, dayName: String): String {
        // The week text is a sentence like:
        // "Here is the week ahead. Tomorrow: high of 72... Saturday: high of 68..."
        // Try to find and return just the sentence for the requested day.
        val sentences = weekText.split(". ").map { it.trim() }
        val match = sentences.firstOrNull { it.contains(dayName, ignoreCase = true) }
        return if (match != null) "$match." else
            "I can only see about a week ahead for weather. I'm sorry, I can't tell you about $dayName from here."
    }


    // =======================
    // WMO CODE TABLE
    // =======================

    private fun wmoCodeToText(code: Int): String = when (code) {
        0         -> "Clear skies"
        1         -> "Mostly clear"
        2         -> "Partly cloudy"
        3         -> "Overcast"
        45, 48    -> "Foggy"
        51        -> "Light drizzle"
        53        -> "Drizzle"
        55        -> "Heavy drizzle"
        61        -> "Light rain"
        63        -> "Rainy"
        65        -> "Heavy rain"
        71        -> "Light snow"
        73        -> "Snowing"
        75        -> "Heavy snow"
        77        -> "Snow flurries"
        80        -> "Light showers"
        81        -> "Rain showers"
        82        -> "Heavy rain showers"
        85, 86    -> "Snow showers"
        95        -> "Thunderstorms"
        96, 99    -> "Thunderstorms with hail"
        else      -> "Conditions unclear"
    }
}
