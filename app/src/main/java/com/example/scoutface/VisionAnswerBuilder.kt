package com.example.scoutface

import com.example.scoutface.brain.FactKey
import java.util.Locale

class VisionAnswerBuilder(
    private val truthDb: TruthDb,
    private val peopleDb: PeopleDb,
    private val voice: VoiceBank,
    private val entityUserPrimary: String
) {
    fun build(
        now: Long,
        lastFaceUpdatedMs: Long,
        lastSceneUpdatedMs: Long,
        lastFaceCount: Int,
        lastFaceHashes: List<String>,
        lastSceneLabels: List<Pair<String, Float>>,
        knownFaceName: String? = null,
        pendingIntroName: String? = null
    ): String {
        val faceAgeMs = now - lastFaceUpdatedMs
        val labelAgeMs = now - lastSceneUpdatedMs

        val faceFresh = lastFaceUpdatedMs != 0L && faceAgeMs <= 1800L
        val labelFresh = lastSceneUpdatedMs != 0L && labelAgeMs <= 1800L

        if (!faceFresh && !labelFresh) return voice.say("VISION_STALE")

        val labels = if (labelFresh) lastSceneLabels else emptyList()
        val faceCount = if (faceFresh) lastFaceCount else 0
        val faceHashes = if (faceFresh) lastFaceHashes else emptyList()

        val dogKnownName = truthDb.getFactValue(entityUserPrimary, FactKey.DOG_NAME)

        val filteredObjects = labels
            .map { it.first }
            .filterNot {
    it == "person" ||
    it == "human face" ||
    it == "face" ||
    it == "head" ||
    it == "selfie" ||
    it == "fun" ||
    it == "tableware" ||
    it == "photograph" ||
    it == "photo" ||
    it == "smile" ||
    it == "gesture" ||
    it == "hair" ||
    it == "sunglasses" ||
    it == "eyewear" ||
    it == "glasses" ||
    it == "room" ||
    it == "indoor" ||
    it == "interior"
}
            .distinct()
            .take(3)

        val seesDog = labels.any { it.first == "dog" || it.first == "puppy" }
        val seesCat = labels.any { it.first == "cat" || it.first == "kitten" }

        return when {
            faceCount >= 3 -> {
                val objects = if (filteredObjects.isNotEmpty()) {
                    " I also see ${formatLabelList(filteredObjects)}."
                } else ""
                "I see several people.$objects"
            }

            faceCount == 2 -> {
                val dogLine = when {
                    seesDog && !dogKnownName.isNullOrBlank() -> " ${dogKnownName} is nearby too."
                    seesDog -> " I also see a dog nearby."
                    else -> ""
                }
                when {
                    !knownFaceName.isNullOrBlank() && !pendingIntroName.isNullOrBlank() ->
                        "I can see you, $knownFaceName and $pendingIntroName.$dogLine"
                    !knownFaceName.isNullOrBlank() ->
                        "I can see you, $knownFaceName and someone else.$dogLine"
                    else -> "I see two people.$dogLine"
                }
            }

            faceCount == 1 -> {
                val knownName = (knownFaceName?.trim()?.takeIf { it.isNotBlank() }
                    ?: faceHashes.firstOrNull()?.let { peopleDb.getName(it) }?.trim()?.takeIf { it.isNotBlank() })

                val objectLine = when {
                    seesDog && !dogKnownName.isNullOrBlank() -> " ${dogKnownName} is nearby too."
                    seesDog -> " I also see a dog nearby."
                    seesCat -> " I also see a cat nearby."
                    filteredObjects.isNotEmpty() -> " I also see ${formatLabelList(filteredObjects)}."
                    else -> ""
                }

                if (knownName != null) {
                    "I can see you, $knownName.$objectLine"
                } else {
                    "I see one person in front of me.$objectLine"
                }
            }

            seesDog && !dogKnownName.isNullOrBlank() -> "I see ${dogKnownName} nearby."
            seesDog -> "I see a dog nearby."
            seesCat -> "I see a cat nearby."
            filteredObjects.isNotEmpty() -> "Right now I see ${formatLabelList(filteredObjects)}."
            else -> voice.say("VISION_UNCLEAR")
        }
    }

    private fun formatLabelList(items: List<String>): String {
        if (items.isEmpty()) return "something"
        if (items.size == 1) return articleize(items[0])
        if (items.size == 2) return "${articleize(items[0])} and ${articleize(items[1])}"

        val head = items.dropLast(1).joinToString(", ") { articleize(it) }
        return "$head, and ${articleize(items.last())}"
    }

    private fun articleize(label: String): String {
        val clean = label.trim().lowercase(Locale.US)
        if (clean.isBlank()) return "something"
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        val article = if (vowels.contains(clean.first())) "an" else "a"
        return "$article $clean"
    }
}