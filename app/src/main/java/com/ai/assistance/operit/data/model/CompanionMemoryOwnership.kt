package com.ai.assistance.operit.data.model

/**
 * Product-facing ownership for a memory. The data stays on the existing ObjectBox Memory entity
 * and is stored through Memory.properties, so old memory databases remain readable.
 */
enum class CompanionMemoryScope(val storageValue: String) {
    USER("user"),
    RELATIONSHIP("relationship"),
    CHARACTER_WORLD("character_world");

    companion object {
        fun fromStorageValue(value: String?): CompanionMemoryScope? =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() }
    }
}

data class CompanionMemoryTarget(
    val characterId: String = "",
    val characterName: String = "",
    val characterGroupId: String = "",
    val characterGroupName: String = "",
) {
    val displayName: String
        get() = characterGroupName.ifBlank { characterName }

    fun matches(characterId: String, characterGroupId: String): Boolean =
        if (this.characterGroupId.isNotBlank()) {
            this.characterGroupId == characterGroupId
        } else {
            this.characterId.isNotBlank() && this.characterId == characterId
        }
}

fun CompanionMemoryTarget.structuredCompanionId(): String =
    characterGroupId.takeIf { it.isNotBlank() }?.let { "group:$it" }
        ?: characterId.takeIf { it.isNotBlank() }?.let { "character:$it" }
        ?: characterName.takeIf { it.isNotBlank() }?.let { "character_name:$it" }
        .orEmpty()

data class CompanionMemoryOwnership(
    val scope: CompanionMemoryScope,
    val profileId: String,
    val characterId: String = "",
    val characterName: String = "",
    val characterGroupId: String = "",
    val characterGroupName: String = "",
    val userConfirmed: Boolean = false,
    val recallEnabled: Boolean = true,
) {
    fun toPropertyValues(): Map<String, String> =
        buildMap {
            put(CompanionMemoryOwnershipKeys.SCOPE, scope.storageValue)
            put(CompanionMemoryOwnershipKeys.PROFILE_ID, profileId.trim())
            characterId.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CompanionMemoryOwnershipKeys.CHARACTER_ID, it) }
            characterName.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CompanionMemoryOwnershipKeys.CHARACTER_NAME, it) }
            characterGroupId.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CompanionMemoryOwnershipKeys.CHARACTER_GROUP_ID, it) }
            characterGroupName.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CompanionMemoryOwnershipKeys.CHARACTER_GROUP_NAME, it) }
            put(CompanionMemoryOwnershipKeys.USER_CONFIRMED, userConfirmed.toString())
            put(CompanionMemoryOwnershipKeys.RECALL_ENABLED, recallEnabled.toString())
        }

    fun belongsToProfile(profileId: String): Boolean =
        this.profileId.isBlank() || this.profileId == profileId

    fun belongsToTarget(target: CompanionMemoryTarget): Boolean =
        scope == CompanionMemoryScope.USER || target.matches(characterId, characterGroupId)

    fun canRecall(profileId: String, target: CompanionMemoryTarget): Boolean =
        recallEnabled && belongsToProfile(profileId) && belongsToTarget(target)

    companion object {
        fun fromPropertyValues(values: Map<String, String>): CompanionMemoryOwnership? {
            val scope =
                CompanionMemoryScope.fromStorageValue(values[CompanionMemoryOwnershipKeys.SCOPE])
                    ?: return null
            return CompanionMemoryOwnership(
                scope = scope,
                profileId = values[CompanionMemoryOwnershipKeys.PROFILE_ID].orEmpty(),
                characterId = values[CompanionMemoryOwnershipKeys.CHARACTER_ID].orEmpty(),
                characterName = values[CompanionMemoryOwnershipKeys.CHARACTER_NAME].orEmpty(),
                characterGroupId = values[CompanionMemoryOwnershipKeys.CHARACTER_GROUP_ID].orEmpty(),
                characterGroupName = values[CompanionMemoryOwnershipKeys.CHARACTER_GROUP_NAME].orEmpty(),
                userConfirmed =
                    values[CompanionMemoryOwnershipKeys.USER_CONFIRMED]?.toBooleanStrictOrNull() ?: false,
                recallEnabled =
                    values[CompanionMemoryOwnershipKeys.RECALL_ENABLED]?.toBooleanStrictOrNull() ?: true,
            )
        }

        fun manual(
            scope: CompanionMemoryScope,
            profileId: String,
            target: CompanionMemoryTarget,
            recallEnabled: Boolean = true,
        ): CompanionMemoryOwnership =
            if (scope == CompanionMemoryScope.USER) {
                CompanionMemoryOwnership(
                    scope = scope,
                    profileId = profileId,
                    userConfirmed = true,
                    recallEnabled = recallEnabled,
                )
            } else {
                CompanionMemoryOwnership(
                    scope = scope,
                    profileId = profileId,
                    characterId = target.characterId,
                    characterName = target.characterName,
                    characterGroupId = target.characterGroupId,
                    characterGroupName = target.characterGroupName,
                    userConfirmed = true,
                    recallEnabled = recallEnabled,
                )
            }
    }
}

object CompanionMemoryOwnershipKeys {
    const val SCOPE = "companion.scope"
    const val PROFILE_ID = "companion.profileId"
    const val CHARACTER_ID = CompanionMemoryKeys.CHARACTER_ID
    const val CHARACTER_NAME = CompanionMemoryKeys.CHARACTER_NAME
    const val CHARACTER_GROUP_ID = CompanionMemoryKeys.CHARACTER_GROUP_ID
    const val CHARACTER_GROUP_NAME = "companion.characterGroupName"
    const val USER_CONFIRMED = "companion.userConfirmed"
    const val RECALL_ENABLED = "companion.recallEnabled"

    val ALL: Set<String> =
        setOf(
            SCOPE,
            PROFILE_ID,
            CHARACTER_ID,
            CHARACTER_NAME,
            CHARACTER_GROUP_ID,
            CHARACTER_GROUP_NAME,
            USER_CONFIRMED,
            RECALL_ENABLED,
        )
}

fun Memory.companionOwnership(): CompanionMemoryOwnership? =
    CompanionMemoryOwnership.fromPropertyValues(properties.associate { it.key to it.value })

object CompanionMemoryRecallPolicy {
    /** Legacy memories remain recallable until the user assigns an explicit owner. */
    fun canRecall(
        memory: Memory,
        profileId: String,
        target: CompanionMemoryTarget,
    ): Boolean {
        memory.companionOwnership()?.let { return it.canRecall(profileId, target) }
        val eventMetadata = memory.companionMetadata() ?: return true
        val eventIsBound =
            eventMetadata.characterId.isNotBlank() || eventMetadata.characterGroupId.isNotBlank()
        return !eventIsBound || target.matches(eventMetadata.characterId, eventMetadata.characterGroupId)
    }
}
