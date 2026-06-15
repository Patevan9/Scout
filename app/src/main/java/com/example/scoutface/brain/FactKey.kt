package com.example.scoutface.brain

object FactKey {

    // Original fixed keys — kept so nothing breaks
    const val NAME       = "name"
    const val WIFE_NAME  = "wife_name"
    const val SON_NAME   = "son_name"
    const val DOG_NAME   = "dog_name"

    // Flexible key builder — any label Scout learns
    // Example: FactKey.custom("favorite_color") → "favorite_color"
    fun custom(label: String): String {
        return label.trim().lowercase().replace(" ", "_")
    }
}