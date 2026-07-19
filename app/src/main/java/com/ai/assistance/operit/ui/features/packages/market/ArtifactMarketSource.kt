package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.MarketV2Entry

enum class ArtifactMarketOrigin(
    val wireValue: String,
    internal val defaultPriority: Int
) {
    MIRA("mira", 3),
    CUSTOM_GITHUB("custom_github", 2),
    OPERIT_LEGACY("operit_legacy", 1);

    companion object {
        fun fromWireValue(value: String?): ArtifactMarketOrigin? {
            return entries.firstOrNull { it.wireValue == value?.trim()?.lowercase() }
        }
    }
}

data class SourcedArtifactMarketEntry(
    val entry: MarketV2Entry,
    val origin: ArtifactMarketOrigin
)

/**
 * Collapses the same runtime package exposed by multiple compatible catalogs.
 * An already-installed entry wins so updates stay on the source chosen by the user;
 * otherwise Mira wins over custom GitHub and the legacy Operit catalog.
 */
fun mergeCompatibleMarketEntries(
    entries: List<SourcedArtifactMarketEntry>,
    installedEntryIds: Set<String> = emptySet()
): List<SourcedArtifactMarketEntry> {
    val selectedByKey = linkedMapOf<String, SourcedArtifactMarketEntry>()
    entries.forEach { candidate ->
        val key = candidate.entry.compatibilityKey()
        val current = selectedByKey[key]
        if (current == null || candidate.isPreferredOver(current, installedEntryIds)) {
            selectedByKey[key] = candidate
        }
    }
    return selectedByKey.values.toList()
}

fun inferArtifactMarketOrigin(entry: MarketV2Entry): ArtifactMarketOrigin {
    val urls = buildList {
        entry.source?.url?.let(::add)
        entry.assets.mapTo(this) { it.url }
    }
    return if (urls.any { url ->
            url.contains("/MiraForge/", ignoreCase = true) ||
                url.contains("github.com/$MIRA_MARKET_OWNER/", ignoreCase = true)
        }
    ) {
        ArtifactMarketOrigin.MIRA
    } else {
        ArtifactMarketOrigin.OPERIT_LEGACY
    }
}

fun MarketV2Entry.inferArtifactForgeRepoName(): String? {
    val urls = buildList {
        entrySourceUrls().forEach(::add)
    }
    val repoPattern = Regex("github\\.com/[^/]+/([^/]+)/", RegexOption.IGNORE_CASE)
    return urls.asSequence()
        .mapNotNull { url -> repoPattern.find(url)?.groupValues?.getOrNull(1) }
        .map { repo -> repo.removeSuffix(".git") }
        .firstOrNull { it.isNotBlank() }
}

private fun MarketV2Entry.compatibilityKey(): String {
    val runtimeId =
        latestVersion?.runtimePackageId
            ?.trim()
            .orEmpty()
            .ifBlank { artifact?.runtimePkg?.trim().orEmpty() }
            .ifBlank { artifact?.projectId?.trim().orEmpty() }
            .ifBlank { id.trim() }
    return "${type.trim().canonicalMarketType()}:${normalizeMarketArtifactId(runtimeId)}"
}

private fun String.canonicalMarketType(): String {
    return when (trim().lowercase()) {
        "toolpkg", "tool_package", "tool-package" -> "package"
        "js", "javascript" -> "script"
        else -> trim().lowercase()
    }
}

private fun MarketV2Entry.entrySourceUrls(): List<String> {
    return buildList {
        source?.url?.let(::add)
        assets.mapTo(this) { it.url }
    }
}

private fun SourcedArtifactMarketEntry.isPreferredOver(
    current: SourcedArtifactMarketEntry,
    installedEntryIds: Set<String>
): Boolean {
    val candidateInstalled = entry.id in installedEntryIds
    val currentInstalled = current.entry.id in installedEntryIds
    if (candidateInstalled != currentInstalled) return candidateInstalled
    return origin.defaultPriority > current.origin.defaultPriority
}
