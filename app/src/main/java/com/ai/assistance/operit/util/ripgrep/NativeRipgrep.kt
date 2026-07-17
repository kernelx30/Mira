package com.ai.assistance.operit.util.ripgrep

import com.ai.assistance.operit.util.AppLogger

internal object NativeRipgrep {
    val isAvailable: Boolean

    init {
        isAvailable =
            try {
                System.loadLibrary("mira_ripgrep")
                true
            } catch (error: LinkageError) {
                AppLogger.w(
                    "NativeRipgrep",
                    "libmira_ripgrep.so is unavailable; using the Kotlin search fallback",
                    error,
                )
                false
            }
    }

    @JvmStatic
    external fun searchJson(
        path: String,
        patterns: Array<String>,
        filePattern: String,
        caseInsensitive: Boolean,
        literal: Boolean,
        contextLines: Int,
        maxResults: Int
    ): String
}
