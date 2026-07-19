package com.ai.assistance.operit.ui.features.update.screens

import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.api.GitHubReleaseAsset
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateViewModelTest {
    @Test
    fun `selects universal release apk instead of release page or debug asset`() {
        val release =
            releaseWithAssets(
                asset("Mira-debug.apk", "https://example.test/debug.apk"),
                asset("Mira-release.apk", "https://example.test/release.apk"),
                asset("Mira-universal.apk", "https://example.test/universal.apk"),
                asset("checksums.txt", "https://example.test/checksums.txt")
            )

        assertEquals(
            "https://example.test/universal.apk",
            selectReleaseApkDownloadUrl(release)
        )
    }

    @Test
    fun `returns empty url when release has no apk`() {
        val release = releaseWithAssets(asset("source.zip", "https://example.test/source.zip"))

        assertEquals("", selectReleaseApkDownloadUrl(release))
    }

    @Test
    fun `rejects non https apk asset url`() {
        val release = releaseWithAssets(asset("Mira-release.apk", "http://example.test/Mira.apk"))

        assertEquals("", selectReleaseApkDownloadUrl(release))
    }

    private fun releaseWithAssets(vararg assets: GitHubReleaseAsset) =
        GitHubRelease(
            id = 1,
            tag_name = "v0.1.0",
            name = "Mira 0.1.0",
            body = null,
            html_url = "https://github.com/kernelx30/Mira/releases/tag/v0.1.0",
            published_at = "2026-07-18T00:00:00Z",
            created_at = "2026-07-18T00:00:00Z",
            assets = assets.toList()
        )

    private fun asset(name: String, url: String) =
        GitHubReleaseAsset(
            id = name.hashCode().toLong(),
            name = name,
            browser_download_url = url,
            size = 1,
            download_count = 0,
            content_type = "application/vnd.android.package-archive"
        )
}
