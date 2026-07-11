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
 * Fetches weather from the U.S. National Weather Service (api.weather.gov).
 * Completely free for commercial use — no API key required.
 * U.S. locations only.
 * API reference: https://www.weather.gov/documentation/services-web-api
 *
 * Handles five question types:
 * CURRENT      — "what's the weather", "is it raining"
 * TONIGHT      — "what's the weather tonight"
 * TOMORROW     — "what's the weather tomorrow"
 * SPECIFIC_DAY — "what's the weather Saturday"
 * WEEK         — "weather this week", "forecast for next week"
 *
 * Each type has its own cache so they never overwrite each other.
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
        private const val USER_AGENT = "ScoutApp/1.0"

        private const val KEY_CURRENT_TEXT     = "current_text"
        private const val KEY_CURRENT_AGE_MS   = "current_age_ms"
        private const val KEY_CURRENT_DATE     = "current_date"
        private const val KEY_TONIGHT_TEXT     = "tonight_text"
        private const val KEY_TONIGHT_AGE_MS   = "tonight_age_ms"
        private const val KEY_TONIGHT_DATE     = "tonight_date"
        private const val KEY_TOMORROW_TEXT    = "tomorrow_text"
        private const val KEY_TOMORROW_AGE_MS  = "tomorrow_age_ms"
        private const val KEY_TOMORROW_DATE    = "tomorrow_date"
        private const val KEY_WEEK_TEXT        = "week_text"
        private const val KEY_WEEK_AGE_MS      = "week_age_ms"
        private const val KEY_WEEK_DATE        = "week_date"

        // Gridpoint URL cache — avoids a /points call on every weather request
        private const val KEY_FORECAST_URL     = "nws_forecast_url"
        private const val KEY_FORECAST_URL_LAT = "nws_lat"
        private const val KEY_FORECAST_URL_LON = "nws_lon"

        private const val CURRENT_CACHE_MS  = 30L * 60L * 1_000L
        private const val FORECAST_CACHE_MS = 3L * 60L * 60L * 1_000L

        // Re-resolve gridpoint if the device has moved more than ~1.4 miles
        private const val GRIDPOINT_TOLERANCE = 0.02
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

        val specificDay = listOf(
            "monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday"
        ).firstOrNull { q.contains(it) }
            ?.replaceFirstChar { it.uppercase() }

        val type = when {
            specificDay != null -> QueryType.SPECIFIC_DAY
            q.contains("tomorrow") -> QueryType.TOMORROW
            q.contains("tonight") -> QueryType.TONIGHT
            q.contains("week") || q.contains("days") ||
                    q.contains("forecast") -> QueryType.WEEK
            else -> QueryType.CURRENT
        }

        val todayName = if (specificDay == null && q.contains("today") &&
            (q.contains("going to") || q.contains("will") ||
                    q.contains("later") || q.contains("rest of")))
            SimpleDateFormat("EEEE", Locale.US).format(java.util.Date()) else null

        dispatch(if (todayName != null) QueryType.SPECIFIC_DAY else type, todayName ?: specificDay)
    }

    // =======================
    // DISPATCH
    // =======================

    private fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())

    private fun dispatch(type: QueryType, specificDay: String?) {

        data class CacheKeys(val text: String, val age: String, val date: String, val duration: Long)

        val keys = when (type) {
            QueryType.CURRENT      -> CacheKeys(KEY_CURRENT_TEXT,  KEY_CURRENT_AGE_MS,  KEY_CURRENT_DATE,  CURRENT_CACHE_MS)
            QueryType.TONIGHT      -> CacheKeys(KEY_TONIGHT_TEXT,  KEY_TONIGHT_AGE_MS,  KEY_TONIGHT_DATE,  FORECAST_CACHE_MS)
            QueryType.TOMORROW     -> CacheKeys(KEY_TOMORROW_TEXT, KEY_TOMORROW_AGE_MS, KEY_TOMORROW_DATE, FORECAST_CACHE_MS)
            QueryType.SPECIFIC_DAY -> CacheKeys(KEY_WEEK_TEXT,     KEY_WEEK_AGE_MS,     KEY_WEEK_DATE,     FORECAST_CACHE_MS)
            QueryType.WEEK         -> CacheKeys(KEY_WEEK_TEXT,     KEY_WEEK_AGE_MS,     KEY_WEEK_DATE,     FORECAST_CACHE_MS)
        }

        val today = todayDateString()

        if (!hasValidatedInternet()) {
            requestInFlight.set(false)
            val cached = prefs.getString(keys.text, null)
            val cachedTimeMs = prefs.getLong(keys.age, 0L)
            val cachedDate = prefs.getString(keys.date, null)
            runOnMain {
                if (cached != null) {
                    val prefix = if (cachedDate != today) "The last weather I have is from an earlier day. " else "I'm offline right now, but as of ${formatCacheTime(cachedTimeMs)}: "
                    respond("$prefix$cached")
                } else {
                    respond("I can't check the weather right now — I'm not connected to the internet.")
                }
            }
            return
        }

        val cacheAge = System.currentTimeMillis() - prefs.getLong(keys.age, 0L)
        val cachedDate = prefs.getString(keys.date, null)
        val cacheIsToday = cachedDate == today
        if (cacheIsToday && cacheAge < keys.duration && type != QueryType.SPECIFIC_DAY) {
            val cached = prefs.getString(keys.text, null)
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

                val forecastUrl = resolveForecastUrl(lat, lon) ?: run {
                    runOnMain {
                        requestInFlight.set(false)
                        respond("I couldn't reach the weather service. The National Weather Service only covers the United States.")
                    }
                    return@Thread
                }

                val periods = fetchForecastPeriods(forecastUrl) ?: run {
                    runOnMain {
                        requestInFlight.set(false)
                        val cached = prefs.getString(keys.text, null)
                        val cachedTimeMs = prefs.getLong(keys.age, 0L)
                        val cDate = prefs.getString(keys.date, null)
                        if (cached != null) {
                            val prefix = if (cDate != today) "I couldn't get a fresh reading. The last weather I have is from an earlier day. " else "I couldn't get a fresh reading. As of ${formatCacheTime(cachedTimeMs)}: "
                            respond("$prefix$cached")
                        } else respond("I wasn't able to reach the weather service right now.")
                    }
                    return@Thread
                }

                val result: String? = when (type) {
                    QueryType.CURRENT  -> parseCurrentPeriods(periods)
                    QueryType.TONIGHT  -> parseTonightPeriods(periods)
                    QueryType.TOMORROW -> parseTomorrowPeriods(periods)
                    QueryType.WEEK     -> parseWeekPeriods(periods)
                    QueryType.SPECIFIC_DAY -> {
                        val weekText = parseWeekPeriods(periods)
                        if (weekText != null) {
                            prefs.edit()
                                .putString(KEY_WEEK_TEXT, weekText)
                                .putLong(KEY_WEEK_AGE_MS, System.currentTimeMillis())
                                .putString(KEY_WEEK_DATE, today)
                                .apply()
                        }
                        parseSpecificDayPeriods(periods, specificDay ?: "")
                    }
                }

                runOnMain {
                    requestInFlight.set(false)
                    if (result != null) {
                        if (type != QueryType.SPECIFIC_DAY) {
                            prefs.edit()
                                .putString(keys.text, result)
                                .putLong(keys.age, System.currentTimeMillis())
                                .putString(keys.date, today)
                                .apply()
                        }
                        respond(result)
                    } else {
                        val cached = prefs.getString(keys.text, null)
                        val cachedTimeMs = prefs.getLong(keys.age, 0L)
                        val cDate = prefs.getString(keys.date, null)
                        if (cached != null) {
                            val prefix = if (cDate != today) "I couldn't get a fresh reading. The last weather I have is from an earlier day. " else "I couldn't get a fresh reading. As of ${formatCacheTime(cachedTimeMs)}: "
                            respond("$prefix$cached")
                        } else respond("I wasn't able to reach the weather service right now.")
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
    // NWS API — TWO-STEP FLOW
    // =======================

    /**
     * Step 1: resolve lat/lon to a NWS gridpoint forecast URL.
     * The result is cached to avoid a /points round-trip on every weather query.
     */
    private fun resolveForecastUrl(lat: Double, lon: Double): String? {
        val cachedLat = prefs.getFloat(KEY_FORECAST_URL_LAT, Float.NaN).toDouble()
        val cachedLon = prefs.getFloat(KEY_FORECAST_URL_LON, Float.NaN).toDouble()
        val cachedUrl = prefs.getString(KEY_FORECAST_URL, null)
        if (cachedUrl != null &&
            !cachedLat.isNaN() && !cachedLon.isNaN() &&
            Math.abs(lat - cachedLat) < GRIDPOINT_TOLERANCE &&
            Math.abs(lon - cachedLon) < GRIDPOINT_TOLERANCE) {
            return cachedUrl
        }

        return try {
            val conn = URL("https://api.weather.gov/points/${"%.4f".format(lat)},${"%.4f".format(lon)}")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout    = 15_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/geo+json")
            if (conn.responseCode !in 200..299) { conn.disconnect(); return null }
            val raw = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            val url = JSONObject(raw)
                .optJSONObject("properties")
                ?.optString("forecast")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            prefs.edit()
                .putString(KEY_FORECAST_URL, url)
                .putFloat(KEY_FORECAST_URL_LAT, lat.toFloat())
                .putFloat(KEY_FORECAST_URL_LON, lon.toFloat())
                .apply()
            url
        } catch (e: Exception) {
            Log.e(TAG, "NWS /points failed: $e")
            null
        }
    }

    /** Step 2: fetch the gridpoint forecast URL and return the list of periods. */
    private fun fetchForecastPeriods(forecastUrl: String): List<NwsPeriod>? {
        return try {
            val conn = URL(forecastUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout    = 15_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/geo+json")
            if (conn.responseCode !in 200..299) { conn.disconnect(); return null }
            val raw = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            conn.disconnect()
            val periodsJson = JSONObject(raw)
                .optJSONObject("properties")
                ?.optJSONArray("periods")
                ?: return null
            val list = mutableListOf<NwsPeriod>()
            for (i in 0 until periodsJson.length()) {
                val p = periodsJson.optJSONObject(i) ?: continue
                val precipPct = p.optJSONObject("probabilityOfPrecipitation")
                    ?.let { if (it.isNull("value")) null else it.optInt("value", -1) }
                    ?.takeIf { it >= 0 }
                val humidity = p.optJSONObject("relativeHumidity")
                    ?.let { if (it.isNull("value")) null else it.optInt("value", -1) }
                    ?.takeIf { it >= 0 }
                // dewpoint in °C — used to calculate RH when relativeHumidity is absent
                val dewpointC = p.optJSONObject("dewpoint")
                    ?.let { if (it.isNull("value")) null else it.optDouble("value", Double.NaN) }
                    ?.takeIf { !it.isNaN() }
                list.add(NwsPeriod(
                    name          = p.optString("name", ""),
                    temperature   = p.optInt("temperature", Int.MIN_VALUE),
                    isDaytime     = p.optBoolean("isDaytime", true),
                    shortForecast = p.optString("shortForecast", ""),
                    windSpeed     = p.optString("windSpeed", ""),
                    precipPct     = precipPct,
                    humidity      = humidity,
                    dewpointC     = dewpointC
                ))
            }
            list.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "NWS forecast failed: $e")
            null
        }
    }

    private data class NwsPeriod(
        val name: String,
        val temperature: Int,
        val isDaytime: Boolean,
        val shortForecast: String,
        val windSpeed: String,
        val precipPct: Int?,
        val humidity: Int?,
        val dewpointC: Double?
    )

    // =======================
    // FEELS-LIKE CALCULATIONS
    // =======================

    // NWS Rothfusz regression — only valid at T ≥ 80°F and RH ≥ 40%
    private fun heatIndex(tempF: Int, humidity: Int): Int? {
        if (tempF < 80 || humidity < 40) return null
        val t = tempF.toDouble()
        val r = humidity.toDouble()
        val hi = -42.379 + 2.04901523 * t + 10.14333127 * r - 0.22475541 * t * r -
                 0.00683783 * t * t - 0.05481717 * r * r + 0.00122874 * t * t * r +
                 0.00085282 * t * r * r - 0.00000199 * t * t * r * r
        return hi.toInt()
    }

    // NWS 2001 wind chill formula — only valid at T ≤ 50°F and V ≥ 3 mph
    private fun windChill(tempF: Int, windMph: Int): Int? {
        if (tempF > 50 || windMph < 3) return null
        val t = tempF.toDouble()
        val v = windMph.toDouble()
        val wc = 35.74 + 0.6215 * t - 35.75 * Math.pow(v, 0.16) + 0.4275 * t * Math.pow(v, 0.16)
        return wc.toInt()
    }

    // Handles "6 mph", "6 to 10 mph", "Calm" — returns average of the range
    private fun parseWindMph(windSpeed: String): Int? {
        val nums = Regex("""\d+""").findAll(windSpeed).mapNotNull { it.value.toIntOrNull() }.toList()
        if (nums.isEmpty()) return null
        return nums.sum() / nums.size
    }

    // Magnus formula: relative humidity (%) from temperature (°F) and dewpoint (°C).
    private fun rhFromDewpoint(tempF: Int, dewpointC: Double): Int {
        val tempC = (tempF - 32.0) * 5.0 / 9.0
        val rh = 100.0 * Math.exp(17.625 * dewpointC / (243.04 + dewpointC)) /
                         Math.exp(17.625 * tempC    / (243.04 + tempC))
        return rh.toInt().coerceIn(0, 100)
    }

    // Returns a natural spoken "feels like" clause, or empty string if not meaningful.
    private fun feelsLikePart(p: NwsPeriod): String {
        val windMph = parseWindMph(p.windSpeed)
        if (windMph != null) {
            val wc = windChill(p.temperature, windMph)
            if (wc != null && (p.temperature - wc) >= 3) {
                return " With the wind chill it feels like $wc degrees."
            }
        }
        // Use reported humidity, or derive it from dewpoint if humidity is absent.
        val rh = p.humidity ?: p.dewpointC?.let { rhFromDewpoint(p.temperature, it) }
        if (rh != null) {
            val hi = heatIndex(p.temperature, rh)
            if (hi != null && (hi - p.temperature) >= 3) {
                return " The heat index makes it feel like $hi degrees."
            }
        }
        return ""
    }

    // =======================
    // PARSERS
    // =======================

    private fun parseCurrentPeriods(periods: List<NwsPeriod>): String? {
        val p = periods.firstOrNull() ?: return null
        if (p.temperature == Int.MIN_VALUE) return null
        val precipPart   = p.precipPct?.let { if (it >= 5) " $it percent chance of precipitation." else "" } ?: ""
        val windPart     = if (p.windSpeed.isNotBlank() && p.windSpeed != "0 mph") " Winds at ${p.windSpeed}." else ""
        val feelsLike    = feelsLikePart(p)
        return "Right now it is ${p.temperature} degrees and ${p.shortForecast.lowercase(Locale.US)}.$precipPart$windPart$feelsLike"
    }

    private fun parseTonightPeriods(periods: List<NwsPeriod>): String? {
        val p = periods.firstOrNull { !it.isDaytime } ?: return null
        if (p.temperature == Int.MIN_VALUE) return null
        val precipPart = p.precipPct?.let { if (it >= 5) " $it percent chance of precipitation." else "" } ?: ""
        val feelsLike  = feelsLikePart(p)
        return "Tonight should be around ${p.temperature} degrees. ${p.shortForecast}.$precipPart$feelsLike"
    }

    private fun parseTomorrowPeriods(periods: List<NwsPeriod>): String? {
        val todayKeywords = setOf("today", "this morning", "this afternoon")
        val p = periods
            .filter { it.isDaytime }
            .firstOrNull { period -> todayKeywords.none { period.name.lowercase(Locale.US).contains(it) } }
            ?: return null
        if (p.temperature == Int.MIN_VALUE) return null
        val precipPart = p.precipPct?.let { if (it >= 5) " $it percent chance of precipitation." else "" } ?: ""
        val feelsLike  = feelsLikePart(p)
        return "Tomorrow: ${p.temperature} degrees during the day. ${p.shortForecast}.$precipPart$feelsLike"
    }

    private fun parseWeekPeriods(periods: List<NwsPeriod>): String? {
        val todayKeywords = setOf("today", "this morning", "this afternoon")
        val dayPeriods = periods
            .filter { it.isDaytime }
            .filterNot { p -> todayKeywords.any { p.name.lowercase(Locale.US).contains(it) } }
            .take(6)
        if (dayPeriods.isEmpty()) return null
        val sb = StringBuilder("Here is the week ahead. ")
        for (p in dayPeriods) {
            val precipPart = p.precipPct?.let { if (it >= 5) ", $it percent chance of precipitation" else "" } ?: ""
            sb.append("${p.name}: ${p.temperature} degrees, ${p.shortForecast.lowercase(Locale.US)}$precipPart. ")
        }
        return sb.toString().trimEnd()
    }

    private fun parseSpecificDayPeriods(periods: List<NwsPeriod>, dayName: String): String? {
        if (dayName.isBlank()) return null
        val p = periods
            .filter { it.isDaytime }
            .firstOrNull { it.name.contains(dayName, ignoreCase = true) }
            ?: return "I can only see about a week ahead for weather. I'm sorry, I can't tell you about $dayName from here."
        if (p.temperature == Int.MIN_VALUE) return null
        val precipPart = p.precipPct?.let { if (it >= 5) " $it percent chance of precipitation." else "" } ?: ""
        val feelsLike  = feelsLikePart(p)
        return "${p.name}: ${p.temperature} degrees during the day. ${p.shortForecast}.$precipPart$feelsLike"
    }

    private fun formatCacheTime(timeMs: Long): String {
        if (timeMs == 0L) return "an earlier check"
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timeMs
        val hourOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute   = cal.get(java.util.Calendar.MINUTE)
        val hour12   = when {
            hourOfDay == 0 -> 12
            hourOfDay > 12 -> hourOfDay - 12
            else           -> hourOfDay
        }
        val amPm      = if (hourOfDay < 12) "am" else "pm"
        val minuteStr = if (minute < 10) "0$minute" else "$minute"
        return "$hour12:$minuteStr$amPm"
    }
}
