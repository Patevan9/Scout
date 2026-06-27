package com.example.scoutface

import com.example.scoutface.brain.FactKey
import java.util.Locale

private val OBJECT_WHITELIST = setOf(
    // furniture
    "chair", "sofa", "couch", "table", "desk", "bed", "bench", "shelf", "cabinet",
    "drawer", "bookcase", "wardrobe", "dresser", "stool",
    // lighting
    "lamp", "light", "candle", "lantern",
    // kitchen
    "cup", "mug", "glass", "bottle", "plate", "bowl", "pot", "pan", "knife", "fork",
    "spoon", "kettle", "blender", "microwave", "oven", "refrigerator", "toaster",
    "cutting board", "coffee maker", "sink", "faucet",
    // electronics
    "phone", "laptop", "computer", "tablet", "television", "tv", "monitor",
    "keyboard", "mouse", "remote", "speaker", "headphones", "camera", "clock",
    "charger", "cable",
    // food & drink
    "apple", "banana", "orange", "food", "fruit", "vegetable", "bread", "sandwich",
    "pizza", "burger", "coffee", "tea", "water", "juice", "beer", "wine", "can",
    "cookie", "cake", "egg",
    // clothing & accessories
    "hat", "jacket", "shirt", "bag", "backpack", "handbag", "purse", "wallet",
    "umbrella", "shoe", "boot", "glasses", "watch",
    // books & stationery
    "book", "magazine", "newspaper", "notebook", "pen", "pencil", "paper",
    // home decor & misc
    "plant", "flower", "vase", "mirror", "window", "door", "key", "toy",
    "ball", "box", "basket", "pillow", "blanket", "rug", "mat", "towel",
    "soap", "frame", "picture",
    // vehicles (seen outdoors)
    "car", "truck", "bus", "bicycle", "bike", "motorcycle",
    // pets (included here but filtered out of speech output — handled separately)
    "dog", "cat", "puppy", "kitten", "bird", "fish",
    // tools & household
    "scissors", "hammer", "screwdriver", "drill", "broom", "mop", "vacuum",
    "hanger", "switch", "outlet", "pipe"
)

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

        val seesDog = labels.any { it.first.lowercase() == "dog" || it.first.lowercase() == "puppy" }
        val seesCat = labels.any { it.first.lowercase() == "cat" || it.first.lowercase() == "kitten" }

        val filteredObjects = labels
            .map { it.first.lowercase() }
            .filter { it in OBJECT_WHITELIST }
            .filterNot { it == "dog" || it == "puppy" || it == "cat" || it == "kitten" }
            .distinct()
            .take(3)

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
