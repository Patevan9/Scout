package com.example.scoutface

import java.io.File

class ScoutDatasetStore(
    private val filesDir: File
) {
    private fun datasetsDir(): File = File(filesDir, "datasets")

    fun dictDir(): File = File(datasetsDir(), "dict")
    fun idiomsDir(): File = File(datasetsDir(), "idioms")
    fun wordnetDir(): File = File(datasetsDir(), "wordnet")
    fun sentimentDir(): File = File(datasetsDir(), "sentiment")
    fun slangDir(): File = File(datasetsDir(), "slang")

    fun ensureDir(dir: File) {
        if (!dir.exists()) dir.mkdirs()
    }

    fun dictDbFile(): File = File(dictDir(), "en.db")
    fun dictMarker(): File = File(dictDir(), "installed.marker")

    fun idiomsFile(): File = File(idiomsDir(), "idioms.json")
    fun idiomsMarker(): File = File(idiomsDir(), "installed.marker")

    fun wordnetMarker(): File = File(wordnetDir(), "installed.marker")

    fun posWordsFile(): File = File(sentimentDir(), "positive-words.txt")
    fun negWordsFile(): File = File(sentimentDir(), "negative-words.txt")
    fun sentimentMarker(): File = File(sentimentDir(), "installed.marker")

    fun slangFile(): File = File(slangDir(), "slang.json")
    fun slangMarker(): File = File(slangDir(), "installed.marker")

    fun dictInstalled(): Boolean = dictMarker().exists() || dictDbFile().exists()
    fun idiomsInstalled(): Boolean = idiomsMarker().exists() || idiomsFile().exists()

    fun wordnetInstalled(): Boolean =
        wordnetMarker().exists() || (wordnetDir().exists() && wordnetDir().listFiles()
            ?.any { it.name.endsWith(".json") } == true)

    fun sentimentInstalled(): Boolean =
        sentimentMarker().exists() || (posWordsFile().exists() && negWordsFile().exists())

    fun slangInstalled(): Boolean = slangMarker().exists() || slangFile().exists()

    fun deleteAllDatasets() {
        if (datasetsDir().exists()) datasetsDir().deleteRecursively()
    }
    fun buildStatusString(): String {
        val parts = ArrayList<String>()
        parts.add(if (dictInstalled()) "Dictionary: installed." else "Dictionary: not installed.")
        parts.add(if (idiomsInstalled()) "Idioms: installed." else "Idioms: not installed.")
        parts.add(if (wordnetInstalled()) "WordNet: installed." else "WordNet: not installed.")
        parts.add(if (sentimentInstalled()) "Sentiment: installed." else "Sentiment: not installed.")
        parts.add(if (slangInstalled()) "Slang: installed." else "Slang: not installed.")
        return parts.joinToString(" ")
    }
    fun assetExists(assetManager: android.content.res.AssetManager, name: String): Boolean {
        return try {
            assetManager.open(name).close()
            true
        } catch (_: Exception) {
            false
        }
    }
}