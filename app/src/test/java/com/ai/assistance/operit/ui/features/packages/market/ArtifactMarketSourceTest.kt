package com.ai.assistance.operit.ui.features.packages.market

import com.ai.assistance.operit.data.api.MarketV2Asset
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2Version
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtifactMarketSourceTest {
    @Test
    fun `Mira source wins duplicate runtime package by default`() {
        val legacy = sourcedEntry("legacy-id", ArtifactMarketOrigin.OPERIT_LEGACY)
        val mira = sourcedEntry("mira-id", ArtifactMarketOrigin.MIRA)

        val merged = mergeCompatibleMarketEntries(listOf(legacy, mira))

        assertEquals(listOf("mira-id"), merged.map { it.entry.id })
    }

    @Test
    fun `installed legacy source remains selected when Mira duplicate exists`() {
        val legacy = sourcedEntry("legacy-id", ArtifactMarketOrigin.OPERIT_LEGACY)
        val mira = sourcedEntry("mira-id", ArtifactMarketOrigin.MIRA)

        val merged =
            mergeCompatibleMarketEntries(
                entries = listOf(legacy, mira),
                installedEntryIds = setOf("legacy-id")
            )

        assertEquals(listOf("legacy-id"), merged.map { it.entry.id })
    }

    @Test
    fun `legacy toolpkg type deduplicates with package type`() {
        val legacy =
            SourcedArtifactMarketEntry(
                marketEntry("legacy-id").copy(type = "toolpkg"),
                ArtifactMarketOrigin.OPERIT_LEGACY
            )
        val mira = sourcedEntry("mira-id", ArtifactMarketOrigin.MIRA)

        assertEquals(1, mergeCompatibleMarketEntries(listOf(legacy, mira)).size)
    }

    @Test
    fun `MiraForge asset is recognized as Mira origin`() {
        val entry =
            marketEntry("mira-id").copy(
                assets =
                    listOf(
                        MarketV2Asset(
                            id = "asset",
                            url = "https://github.com/example/MiraForge/releases/download/v1/pkg.toolpkg"
                        )
                    )
            )

        assertEquals(ArtifactMarketOrigin.MIRA, inferArtifactMarketOrigin(entry))
        assertEquals("MiraForge", entry.inferArtifactForgeRepoName())
    }

    private fun sourcedEntry(
        id: String,
        origin: ArtifactMarketOrigin
    ) = SourcedArtifactMarketEntry(marketEntry(id), origin)

    private fun marketEntry(id: String) =
        MarketV2Entry(
            type = "package",
            id = id,
            title = id,
            latestVersion =
                MarketV2Version(
                    id = "$id-version",
                    version = "1.0.0",
                    runtimePackageId = "shared.package"
                )
        )
}
