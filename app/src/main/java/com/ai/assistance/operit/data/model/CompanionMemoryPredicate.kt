package com.ai.assistance.operit.data.model

import java.util.Locale

object CompanionMemoryPredicate {
    private val aliases =
        mapOf(
            "full_name" to "name",
            "user_name" to "name",
            "date_of_birth" to "birthday",
            "birth_date" to "birthday",
            "job" to "occupation",
            "career" to "occupation",
            "work_role" to "occupation",
            "profession" to "occupation",
            "employer" to "workplace",
            "company" to "workplace",
            "address" to "location",
            "city" to "location",
            "residence" to "location",
            "home_location" to "location",
            "nickname" to "preferred_address",
            "preferred_name" to "preferred_address",
            "preferred_call_name" to "preferred_address",
            "food.likes" to "likes",
            "preference.likes" to "likes",
            "food.dislikes" to "dislikes",
            "preference.dislikes" to "dislikes",
            "allergy" to "health.allergy",
            "allergies" to "health.allergy",
            "medical_condition" to "health.condition",
            "condition" to "health.condition",
            "medication" to "health.medication",
            "medicine" to "health.medication",
            "goal" to "long_term_goal",
            "longterm_goal" to "long_term_goal",
            "habit" to "routine",
            "habits" to "routine",
            "promise" to "commitment",
            "limits" to "boundary",
        )

    private val singletonPredicates =
        setOf(
            "name",
            "birthday",
            "age",
            "location",
            "occupation",
            "workplace",
            "school",
            "preferred_address",
        )

    fun canonicalize(predicate: String): String {
        val normalized =
            predicate
                .trim()
                .lowercase(Locale.ROOT)
                .replace('-', '_')
                .replace(Regex("\\s+"), "_")
        return aliases[normalized] ?: normalized
    }

    fun isSingleton(predicate: String): Boolean {
        val canonical = canonicalize(predicate)
        return canonical in singletonPredicates || canonical.startsWith("family.")
    }

    fun isPreference(predicate: String): Boolean {
        val canonical = canonicalize(predicate)
        return canonical == "likes" ||
            canonical == "dislikes" ||
            canonical.startsWith("likes.") ||
            canonical.startsWith("dislikes.") ||
            canonical in setOf("favorite_game", "hobby", "interest", "favorite_genre")
    }

    fun isOppositePreference(left: String, right: String): Boolean {
        val canonicalLeft = canonicalize(left)
        val canonicalRight = canonicalize(right)
        return (canonicalLeft == "likes" && canonicalRight == "dislikes") ||
            (canonicalLeft == "dislikes" && canonicalRight == "likes")
    }
}
