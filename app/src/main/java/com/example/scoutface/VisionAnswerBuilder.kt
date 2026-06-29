package com.example.scoutface

import com.example.scoutface.brain.FactKey
import java.util.Locale

private val OBJECT_WHITELIST = setOf(
    // seating
    "sofa", "couch", "loveseat", "recliner", "chair", "armchair", "rocker",
    "ottoman", "bench", "stool", "daybed", "futon", "beanbag", "deckchair",
    // tables, desks & storage
    "table", "desk", "nightstand", "sideboard", "buffet", "hutch",
    "cabinet", "dresser", "drawer", "bookshelf", "bookcase", "shelf",
    "wardrobe", "armoire", "closet", "trunk", "chest", "vanity", "footstool",
    // bedroom
    "bed", "mattress", "headboard", "footboard", "bunk bed", "bunkbed", "crib",
    "hamper", "laundry",
    // textiles & soft furnishings
    "mirror", "rug", "carpet", "curtain", "blind", "cushion", "pillow",
    "blanket", "quilt", "comforter", "sheet", "towel", "washcloth",
    "tablecloth", "coaster", "bathmat", "doormat", "mat",
    // lighting
    "lamp", "light", "lantern", "candle", "chandelier", "nightlight", "spotlight",
    // climate & air
    "fan", "heater", "fireplace", "humidifier", "dehumidifier", "purifier",
    "diffuser", "air conditioner", "airconditioner", "thermostat",
    // TV & audio
    "television", "tv", "monitor", "speaker", "soundbar", "subwoofer",
    "receiver", "radio", "projector", "remote",
    // computers & peripherals
    "phone", "tablet", "laptop", "computer", "keyboard", "mouse", "printer",
    "router", "modem", "charger", "cable", "cord", "plug", "adapter",
    "extension cord", "extensioncord", "power strip", "powerstrip",
    "headphones", "earbuds", "microphone", "webcam", "scanner", "tripod",
    "camera", "clock", "alarm", "doorbell", "console",
    // security & safety
    "detector", "extinguisher", "safe", "lockbox", "lock box",
    // kitchen appliances
    "microwave", "oven", "stove", "cooktop", "refrigerator", "freezer",
    "dishwasher", "toaster", "blender", "kettle", "coffee maker", "coffeemaker",
    "mixer", "grinder", "juicer", "crockpot", "air fryer", "airfryer",
    "vacuum", "robot",
    // cookware & utensils
    "pan", "pot", "skillet", "wok", "lid", "spatula", "ladle", "whisk", "tongs",
    "knife", "fork", "spoon", "cutting board",
    // tableware & containers
    "plate", "bowl", "cup", "mug", "glass", "pitcher", "bottle", "thermos",
    "napkin", "tray", "basket", "container", "jar", "can", "box",
    "sink", "faucet",
    // fruits
    "apple", "banana", "orange", "grape", "pear", "peach", "plum",
    "melon", "watermelon", "pineapple", "mango",
    "berry", "strawberry", "blueberry", "raspberry",
    // vegetables
    "carrot", "potato", "onion", "pepper", "broccoli", "lettuce",
    "cucumber", "tomato", "corn", "bean", "pea",
    // grains & baked goods
    "rice", "pasta", "bread", "bagel", "biscuit", "cracker",
    "cookie", "cake", "pie", "donut", "muffin",
    // meals & proteins
    "pizza", "burger", "sandwich", "sausage", "bacon", "ham",
    "chicken", "turkey", "beef", "steak", "fish", "shrimp", "egg",
    // dairy
    "cheese", "butter", "yogurt", "milk",
    // drinks & snacks
    "juice", "water", "coffee", "tea", "soda", "cola", "lemonade",
    "beer", "wine", "snack", "candy", "chocolate",
    // general food labels
    "food", "fruit", "vegetable",
    // bags & carry
    "bag", "backpack", "handbag", "purse", "suitcase", "briefcase",
    "wallet", "lanyard", "keychain", "key", "keys",
    // clothing — tops
    "shirt", "tshirt", "t-shirt", "blouse", "sweater", "hoodie", "jacket", "coat", "vest",
    // clothing — bottoms & full
    "dress", "skirt", "jeans", "pants", "shorts", "leggings",
    // underwear & sleep
    "socks", "underwear", "bra", "pajamas", "robe",
    // footwear
    "shoe", "shoes", "sneakers", "boot", "boots", "sandals", "slippers", "cleat",
    // clothing accessories
    "hat", "cap", "scarf", "glove", "gloves", "belt", "tie", "umbrella", "hanger",
    // jewelry
    "watch", "bracelet", "necklace", "ring", "earring", "brooch", "locket", "pendant",
    // cosmetics & grooming
    "lipstick", "mascara", "eyeliner", "foundation", "powder", "blush",
    "concealer", "palette",
    "hair dryer", "hairdryer", "straightener", "curler", "clippers",
    "comb", "brush", "razor",
    // eyewear & mobility aids
    "glasses", "sunglasses", "cane", "walker", "wheelchair", "crutches",
    // bathroom fixtures
    "toilet", "bathtub", "shower", "plunger", "bidet",
    "soap dish", "soapdish", "toilet paper", "toiletpaper", "tissue",
    "medicine cabinet", "medicinecabinet", "scale", "washbasin",
    "loofah", "squeegee",
    // toiletries
    "soap", "shampoo", "conditioner", "toothbrush", "toothpaste",
    "mouthwash", "floss", "deodorant", "perfume", "cologne", "toiletry",
    // medical
    "medicine", "bandage", "thermometer",
    // books & paper
    "book", "notebook", "magazine", "newspaper", "notepad", "journal",
    "sketchbook", "calendar", "bookmark", "album", "paper",
    // office supplies
    "binder", "folder", "envelope", "stapler", "staples", "paperclip", "pushpin",
    "whiteboard", "clipboard", "calculator", "document", "receipt",
    "pen", "pencil", "marker", "eraser", "crayon", "highlighter",
    // home decor
    "plant", "flower", "vase", "wreath", "statue", "figurine",
    "painting", "poster", "picture", "frame",
    "window", "door", "switch", "outlet", "pipe",
    // bins & storage
    "toy box", "toybox", "bin",
    // pets
    "dog", "puppy", "cat", "kitten", "bird", "parrot", "hamster",
    "rabbit", "guinea pig", "guineapig", "ferret", "lizard", "turtle",
    "goldfish", "fish",
    "tank", "aquarium", "leash", "collar", "harness",
    "feeder", "crate", "kennel", "carrier",
    "bone", "treat", "scratcher", "perch", "litter box", "litterbox",
    // toys & games
    "toy", "ball", "doll", "action figure", "actionfigure",
    "block", "blocks", "puzzle", "board game", "boardgame", "card game", "cardgame",
    "paint", "playdough", "play dough",
    "train", "airplane", "helicopter", "rocket",
    "plushie", "teddy bear", "teddybear",
    // baby & nursery
    "baby", "bassinet", "playpen", "diaper", "wipes", "bib", "pacifier",
    "rattle", "teether", "onesie", "car seat", "carseat",
    "high chair", "highchair", "changing table", "changingtable",
    "baby gate", "babygate", "mobile",
    // musical instruments
    "guitar", "violin", "cello", "trumpet", "trombone", "clarinet",
    "flute", "saxophone", "ukulele", "banjo", "mandolin", "accordion",
    "harmonica", "drum", "xylophone", "amplifier",
    // sports & fitness
    "basketball", "football", "baseball", "soccer ball", "soccerball",
    "volleyball", "tennis ball", "tennisball",
    "frisbee", "kite", "helmet", "bat", "puck", "skate", "snowboard", "skis",
    "dumbbell", "barbell", "treadmill", "elliptical", "bench press", "benchpress",
    "yoga mat", "yogamat", "medicine ball", "medicineball", "goal", "net",
    // ride-on & outdoor toys
    "scooter", "bicycle", "bike", "tricycle", "wagon", "stroller",
    // vehicles
    "car", "truck", "bus", "motorcycle", "suv", "van", "pickup",
    "tractor", "atv", "rv", "camper", "trailer",
    "boat", "canoe", "kayak", "jet ski", "jetski",
    // outdoor & yard
    "mailbox", "grill", "smoker", "cooler", "wheelbarrow", "planter",
    "birdhouse", "bird feeder", "birdfeeder", "fountain", "swing",
    "hammock", "patio", "shovel", "bucket", "hose", "sprinkler", "rake",
    "mower", "lawn mower", "blower",
    // tools & workshop
    "ladder", "toolbox", "tool box", "hammer", "screwdriver", "wrench",
    "drill", "saw", "mallet", "chisel", "level", "sander", "clamp",
    "workbench", "vise", "compressor", "generator", "welder", "anvil",
    "chain saw", "chainsaw", "hedge trimmer", "hedgetrimmer", "edger", "ax",
    "screw", "nail", "bolt", "nut", "tool belt", "toolbelt",
    // cleaning
    "broom", "mop", "dustpan", "sponge", "detergent", "scissors",
    // office equipment
    "filing cabinet", "filingcabinet",
    // electrical
    "light bulb", "lightbulb",
    // navigation & reference
    "globe", "map", "telescope", "binoculars", "compass",
    "clock radio", "clockradio", "weather station", "weatherstation",
    // holiday & seasonal
    "stocking", "ornament", "garland", "menorah", "dreidel",
    "pumpkin", "jack-o-lantern", "jackolantern", "cornstalk",
    "firework", "gift", "present", "ribbon", "bow", "wrapping paper", "wrappingpaper",
    // misc household
    "flashlight", "battery"
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
        pendingIntroName: String? = null,
        secondaryFaceName: String? = null
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
                    !knownFaceName.isNullOrBlank() && !secondaryFaceName.isNullOrBlank() ->
                        "I can see you, $knownFaceName and $secondaryFaceName.$dogLine"
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
