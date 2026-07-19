package com.ai.assistance.operit.data.updates

import com.ai.assistance.operit.data.api.GitHubRelease
import java.util.Locale

internal fun GitHubRelease.apkBrowserDownloadUrlOrNull(): String? =
    assets
        .asSequence()
        .filter { asset -> asset.name.trim().endsWith(".apk", ignoreCase = true) }
        .sortedByDescending { asset ->
            val name = asset.name.lowercase(Locale.ROOT)
            when {
                "debug" in name || "unsigned" in name -> 0
                "universal" in name -> 3
                "release" in name -> 2
                else -> 1
            }
        }
        .map { asset -> asset.browser_download_url.trim() }
        .firstOrNull { url -> url.startsWith("https://") }
