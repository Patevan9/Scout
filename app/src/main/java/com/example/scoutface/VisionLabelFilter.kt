package com.example.scoutface.vision

object VisionLabelFilter {

    private val bad = setOf(
        "beard", "facial hair", "moustache", "mustache", "jaw", "forehead", "chin",
        "eyebrow", "eyelash", "nose", "lip", "neck", "sleeve", "jacket", "coat",
        "shirt", "hat", "cap", "glasses", "goggles", "smile"
    )

    fun isUseful(label: String): Boolean {
        return label.isNotBlank() && !bad.contains(label)
    }
}