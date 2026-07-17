package com.ai.assistance.operit.data.preferences

enum class AutoReadOverride(val storageValue: String) {
    INHERIT("inherit"),
    ENABLED("enabled"),
    DISABLED("disabled");

    companion object {
        fun fromStorageValue(value: String?): AutoReadOverride {
            return entries.firstOrNull { it.storageValue == value } ?: INHERIT
        }
    }
}

fun resolveAutoReadEnabled(
    globalEnabled: Boolean,
    override: AutoReadOverride,
): Boolean {
    return when (override) {
        AutoReadOverride.INHERIT -> globalEnabled
        AutoReadOverride.ENABLED -> true
        AutoReadOverride.DISABLED -> false
    }
}
