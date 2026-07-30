package com.example.scoutface.brain

import com.example.scoutface.TruthDb

/**
 * Resolves who a taught or mentioned fact is actually about, so new facts attach
 * to that person or pet's own entity (e.g. "diana", "nicolas") instead of piling
 * up as more relation-specific keys (wife_birthday, dog_nickname) under the
 * user's own entity. wife_name/son_name/dog_name under the user's own entity
 * still work exactly as before -- they're the pointer that tells us WHICH entity
 * "my wife" currently resolves to.
 */
object ScoutEntityResolver {

    private val RELATION_TO_NAME_KEY = mapOf(
        "wife" to FactKey.WIFE_NAME, "husband" to FactKey.WIFE_NAME, "spouse" to FactKey.WIFE_NAME,
        "son" to FactKey.SON_NAME, "daughter" to FactKey.SON_NAME,
        "kid" to FactKey.SON_NAME, "child" to FactKey.SON_NAME,
        "dog" to FactKey.DOG_NAME, "cat" to FactKey.DOG_NAME, "pet" to FactKey.DOG_NAME
    )

    // subject examples: "my", "my wife", "wife", "diana"
    fun resolveEntity(subject: String, truthDb: TruthDb, selfEntity: String): String {
        val s = subject.trim().lowercase().removePrefix("my").trim()
        if (s.isBlank() || s == "i" || s == "myself") return selfEntity

        RELATION_TO_NAME_KEY[s]?.let { nameKey ->
            val name = truthDb.getFactValue(selfEntity, nameKey)
            return if (!name.isNullOrBlank()) name.trim().lowercase() else s
        }

        return s
    }

    // Every name/alias Scout can currently recognize, mapped to the entity slug
    // its facts live under -- built fresh from TruthDb each call (not cached), so
    // a name or alias taught moments ago is recognized immediately. Includes the
    // relation pointers under the user's own entity (wife_name/son_name/dog_name),
    // each named entity's own slug, and each named entity's stored aliases --
    // "Nicolas," "Nick," and "Nicky" all map to the same "nicolas" entity.
    fun buildAliasMap(truthDb: TruthDb, selfEntity: String): Map<String, String> {
        val map = mutableMapOf<String, String>()

        for (nameKey in setOf(FactKey.WIFE_NAME, FactKey.SON_NAME, FactKey.DOG_NAME)) {
            truthDb.getFactValue(selfEntity, nameKey)?.let { name ->
                val slug = name.trim().lowercase()
                if (slug.isNotBlank()) map[slug] = slug
            }
        }

        for (entity in truthDb.getAllEntities()) {
            if (entity == selfEntity || entity == "scout") continue
            map[entity] = entity
            for (alias in truthDb.getAliases(entity)) {
                val slug = alias.trim().lowercase()
                if (slug.isNotBlank()) map[slug] = entity
            }
        }

        return map
    }

    // Capitalized display form of an entity slug -- "diana" -> "Diana" -- used
    // when formatting facts about someone other than the user, for spoken
    // confirmations and for TinyLlama's grounding prompt.
    fun displayName(entity: String): String =
        entity.trim().split(" ", "_").filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
