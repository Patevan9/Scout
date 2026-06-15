package com.example.scoutface

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.Calendar

class HabitLayer(private val context: Context) {

    companion object {
        private const val TAG = "HabitLayer"
        private const val FILE_NAME = "scout_habits.json"
        private const val HALF_LIFE_MS = 14L * 24L * 60L * 60L * 1000L
        private const val PRUNE_THRESHOLD = 0.05f
        private const val TOP_N = 8

        private val STOP_WORDS = setOf(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "i", "me", "my", "we", "our", "you", "your", "he", "she", "it", "they",
            "his", "her", "its", "their", "this", "that", "these", "those",
            "and", "or", "but", "so", "if", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "up", "about", "into", "through",
            "do", "did", "does", "have", "has", "had", "will", "would", "can",
            "could", "should", "may", "might", "shall", "not", "no", "yes",
            "what", "when", "where", "who", "why", "how", "just", "like",
            "okay", "ok", "hey", "hi", "hello", "scout", "please", "thank",
            "thanks", "get", "got", "go", "going", "come", "coming", "want",
            "know", "think", "tell", "say", "said", "make", "made", "need",
            "see", "look", "there", "here", "then", "than", "more", "some",
            "any", "all", "also", "back", "well", "now", "right", "good"
        )

        val TIME_SLOTS = listOf(
            TimeSlot("night", 0, 6, "quiet hours"),
            TimeSlot("morning", 6, 12, "morning"),
            TimeSlot("midday", 12, 14, "midday"),
            TimeSlot("afternoon", 14, 18, "afternoon"),
            TimeSlot("evening", 18, 22, "evening"),
            TimeSlot("late", 22, 24, "late night")
        )
    }

    data class TimeSlot(val key: String, val startHour: Int, val endHour: Int, val label: String)

    private data class TopicEntry(
        var rawScore: Float,
        var lastUpdatedMs: Long,
        var totalCount: Int
    )

    private data class SlotProfile(
        val topicCounts: MutableMap<String, Int> = mutableMapOf(),
        var totalInteractions: Int = 0
    )

    private data class PersonEntry(
        var name: String,
        var rawScore: Float,
        var lastSeenMs: Long,
        var totalSightings: Int,
        val slotCounts: MutableMap<String, Int> = mutableMapOf()
    )

    private val topics = mutableMapOf<String, TopicEntry>()
    private val slots = mutableMapOf<String, SlotProfile>()
    private val persons = mutableMapOf<String, PersonEntry>()

    private val file: File = File(context.filesDir, FILE_NAME)

    init {
        load()
    }

    fun logUtterance(text: String, faceHash: String? = null) {
        val nowMs = System.currentTimeMillis()
        val slotKey = currentTimeSlot().key
        val keywords = extractKeywords(text)

        val slotProfile = slots.getOrPut(slotKey) { SlotProfile() }
        slotProfile.totalInteractions++

        for (word in keywords) {
            val entry = topics.getOrPut(word) { TopicEntry(0f, nowMs, 0) }
            entry.rawScore += 1.0f
            entry.lastUpdatedMs = nowMs
            entry.totalCount++

            slotProfile.topicCounts[word] = (slotProfile.topicCounts[word] ?: 0) + 1
        }

        if (faceHash != null) {
            val person = persons.getOrPut(faceHash) {
                PersonEntry("", 0f, nowMs, 0)
            }
            person.rawScore += 0.5f
            person.lastSeenMs = nowMs
            person.slotCounts[slotKey] = (person.slotCounts[slotKey] ?: 0) + 1
        }

        saveDebounced()
    }

    fun logPersonSeen(faceHash: String, nameIfKnown: String = "") {
        val nowMs = System.currentTimeMillis()
        val slotKey = currentTimeSlot().key

        val entry = persons.getOrPut(faceHash) {
            PersonEntry(nameIfKnown, 0f, nowMs, 0)
        }

        if (nameIfKnown.isNotBlank() && entry.name.isBlank()) {
            entry.name = nameIfKnown
        }

        entry.rawScore += 1.0f
        entry.lastSeenMs = nowMs
        entry.totalSightings++
        entry.slotCounts[slotKey] = (entry.slotCounts[slotKey] ?: 0) + 1

        saveDebounced()
    }

    fun currentTimeSlot(): TimeSlot {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return TIME_SLOTS.firstOrNull { hour >= it.startHour && hour < it.endHour }
            ?: TIME_SLOTS.last()
    }

    fun getTopTopics(n: Int = TOP_N): List<Pair<String, Float>> {
        val nowMs = System.currentTimeMillis()
        return topics.entries
            .map { (key, entry) -> key to decayedScore(entry.rawScore, entry.lastUpdatedMs, nowMs) }
            .filter { it.second >= PRUNE_THRESHOLD }
            .sortedByDescending { it.second }
            .take(n)
    }

    fun getTopicsForSlot(slot: TimeSlot, n: Int = 5): List<Pair<String, Int>> {
        val profile = slots[slot.key] ?: return emptyList()
        return profile.topicCounts.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value }
    }

    fun getPersonProfile(faceHash: String): PersonProfile? {
        val entry = persons[faceHash] ?: return null
        val nowMs = System.currentTimeMillis()
        val score = decayedScore(entry.rawScore, entry.lastSeenMs, nowMs)
        if (score < PRUNE_THRESHOLD) return null

        val dominantSlot = entry.slotCounts.entries
            .maxByOrNull { it.value }?.key
            ?.let { key -> TIME_SLOTS.firstOrNull { it.key == key } }

        return PersonProfile(
            faceHash = faceHash,
            name = entry.name,
            decayedScore = score,
            totalSightings = entry.totalSightings,
            lastSeenMs = entry.lastSeenMs,
            dominantSlot = dominantSlot,
            slotCounts = entry.slotCounts.toMap()
        )
    }

    fun getKnownPersons(): List<PersonProfile> {
        return persons.entries
            .filter { it.value.name.isNotBlank() }
            .mapNotNull { (hash, _) -> getPersonProfile(hash) }
            .filter { it.decayedScore >= PRUNE_THRESHOLD }
            .sortedByDescending { it.decayedScore }
    }

    fun getSummaryForGemini(): String {
        val slot = currentTimeSlot()
        val slotTopics = getTopicsForSlot(slot, 4).map { it.first }
        val globalTopics = getTopTopics(5).map { it.first }
        val knownPersons = getKnownPersons()

        val sb = StringBuilder("Habit context: ")
        sb.append("It is currently ${slot.label}. ")

        if (slotTopics.isNotEmpty()) {
            sb.append("In the ${slot.label}, Scout typically discusses: ")
            sb.append(slotTopics.joinToString(", "))
            sb.append(". ")
        }

        if (globalTopics.isNotEmpty()) {
            sb.append("Top recurring topics overall: ")
            sb.append(globalTopics.joinToString(", "))
            sb.append(". ")
        }

        if (knownPersons.isNotEmpty()) {
            val presentNow = knownPersons.filter { p -> p.dominantSlot?.key == slot.key }
            if (presentNow.isNotEmpty()) {
                val names = presentNow.mapNotNull { p ->
                    p.name.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() }
                }
                if (names.isNotEmpty()) {
                    sb.append("${names.joinToString(" and ")} ")
                    sb.append(if (names.size == 1) "is" else "are")
                    sb.append(" often present at this time. ")
                }
            }
        }

        return sb.toString().trim()
    }

    fun getIdleObservation(): String? {
        val slot = currentTimeSlot()
        val topics = getTopicsForSlot(slot, 1)
        val top = topics.firstOrNull() ?: return null
        if (top.second < 3) return null
        return "You usually bring up ${top.first} around this time."
    }

    private fun decayedScore(rawScore: Float, lastUpdatedMs: Long, nowMs: Long): Float {
        val elapsedMs = (nowMs - lastUpdatedMs).coerceAtLeast(0L)
        val halfLives = elapsedMs.toDouble() / HALF_LIFE_MS.toDouble()
        return (rawScore * Math.pow(0.5, halfLives)).toFloat()
    }

    private fun extractKeywords(text: String): List<String> {
        return text
            .lowercase()
            .split(Regex("[^a-z']+"))
            .map { it.trim('\'') }
            .filter { word ->
                word.length >= 3 && word !in STOP_WORDS
            }
            .distinct()
    }

    private var pendingSave = false

    private fun saveDebounced() {
        if (!pendingSave) {
            pendingSave = true
            Handler(Looper.getMainLooper()).postDelayed({
                pendingSave = false
                save()
            }, 2000L)
        }
    }

    private fun save() {
        try {
            val nowMs = System.currentTimeMillis()

            val topicsJson = JSONObject()
            for ((key, entry) in topics) {
                val decayed = decayedScore(entry.rawScore, entry.lastUpdatedMs, nowMs)
                if (decayed >= PRUNE_THRESHOLD) {
                    topicsJson.put(key, JSONObject().apply {
                        put("raw", entry.rawScore)
                        put("ms", entry.lastUpdatedMs)
                        put("count", entry.totalCount)
                    })
                }
            }

            val slotsJson = JSONObject()
            for ((slotKey, profile) in slots) {
                val topicCountsJson = JSONObject()
                for ((t, c) in profile.topicCounts) topicCountsJson.put(t, c)
                slotsJson.put(slotKey, JSONObject().apply {
                    put("topics", topicCountsJson)
                    put("total", profile.totalInteractions)
                })
            }

            val personsJson = JSONObject()
            for ((hash, entry) in persons) {
                val decayed = decayedScore(entry.rawScore, entry.lastSeenMs, nowMs)
                if (decayed >= PRUNE_THRESHOLD) {
                    val slotCountsJson = JSONObject()
                    for ((s, c) in entry.slotCounts) slotCountsJson.put(s, c)
                    personsJson.put(hash, JSONObject().apply {
                        put("name", entry.name)
                        put("raw", entry.rawScore)
                        put("ms", entry.lastSeenMs)
                        put("sightings", entry.totalSightings)
                        put("slots", slotCountsJson)
                    })
                }
            }

            val root = JSONObject().apply {
                put("version", 1)
                put("topics", topicsJson)
                put("slots", slotsJson)
                put("persons", personsJson)
            }

            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save habit data", e)
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())

            val topicsJson = root.optJSONObject("topics") ?: JSONObject()
            val topicKeys = topicsJson.keys()
            while (topicKeys.hasNext()) {
                val key = topicKeys.next()
                val obj = topicsJson.getJSONObject(key)
                topics[key] = TopicEntry(
                    rawScore = obj.getDouble("raw").toFloat(),
                    lastUpdatedMs = obj.getLong("ms"),
                    totalCount = obj.optInt("count", 1)
                )
            }

            val slotsJson = root.optJSONObject("slots") ?: JSONObject()
            val slotKeys = slotsJson.keys()
            while (slotKeys.hasNext()) {
                val slotKey = slotKeys.next()
                val obj = slotsJson.getJSONObject(slotKey)
                val profile = SlotProfile(totalInteractions = obj.optInt("total", 0))
                val tc = obj.optJSONObject("topics") ?: JSONObject()
                val tcKeys = tc.keys()
                while (tcKeys.hasNext()) {
                    val t = tcKeys.next()
                    profile.topicCounts[t] = tc.getInt(t)
                }
                slots[slotKey] = profile
            }

            val personsJson = root.optJSONObject("persons") ?: JSONObject()
            val personKeys = personsJson.keys()
            while (personKeys.hasNext()) {
                val hash = personKeys.next()
                val obj = personsJson.getJSONObject(hash)
                val sc = obj.optJSONObject("slots") ?: JSONObject()
                val slotCounts = mutableMapOf<String, Int>()
                val scKeys = sc.keys()
                while (scKeys.hasNext()) {
                    val s = scKeys.next()
                    slotCounts[s] = sc.getInt(s)
                }
                persons[hash] = PersonEntry(
                    name = obj.optString("name", ""),
                    rawScore = obj.getDouble("raw").toFloat(),
                    lastSeenMs = obj.getLong("ms"),
                    totalSightings = obj.optInt("sightings", 1),
                    slotCounts = slotCounts
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load habit data — starting fresh", e)
            topics.clear()
            slots.clear()
            persons.clear()
        }
    }

    data class PersonProfile(
        val faceHash: String,
        val name: String,
        val decayedScore: Float,
        val totalSightings: Int,
        val lastSeenMs: Long,
        val dominantSlot: TimeSlot?,
        val slotCounts: Map<String, Int>
    )
}