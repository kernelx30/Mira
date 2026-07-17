package com.ai.assistance.operit.util.ripgrep

import java.io.File
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import org.json.JSONArray
import org.json.JSONObject

internal object KotlinRipgrepFallback {
    suspend fun searchJson(
        path: String,
        patterns: List<String>,
        filePattern: String,
        caseInsensitive: Boolean,
        literal: Boolean,
        contextLines: Int,
        maxResults: Int,
    ): String {
        val root = File(path)
        require(root.exists()) { "search path does not exist: $path" }
        val matchers =
            patterns.filter { it.isNotBlank() }.map { pattern ->
                val source = if (literal) Regex.escape(pattern) else pattern
                Regex(source, if (caseInsensitive) setOf(RegexOption.IGNORE_CASE) else emptySet())
            }
        require(matchers.isNotEmpty()) { "pattern is required" }

        val includeMatcher = buildGlobMatcher(filePattern)
        var filesSearched = 0
        val blocks = JSONArray()
        if (maxResults <= 0) {
            return JSONObject()
                .put("success", true)
                .put("error", "")
                .put("filesSearched", filesSearched)
                .put("blocks", blocks)
                .toString()
        }
        val files =
            if (root.isFile) {
                sequenceOf(root)
            } else {
                root.walkTopDown()
                    .onEnter { directory ->
                        directory == root || directory.name !in EXCLUDED_DIRECTORY_NAMES
                    }
                    .asSequence()
            }

        for (file in files) {
            currentCoroutineContext().ensureActive()
            if (!file.isFile || isExcluded(root, file)) continue
            val relativePath = file.relativeToOrSelf(root).invariantSeparatorsPath
            if (!includeMatcher(relativePath, file.name) || isProbablyBinary(file)) continue
            filesSearched++

            val lines = runCatching { file.bufferedReader().use { it.readLines() } }.getOrNull() ?: continue
            val matchingIndexes =
                lines.indices.filter { index -> matchers.any { matcher -> matcher.containsMatchIn(lines[index]) } }
            if (matchingIndexes.isEmpty()) continue

            val firstMatchIndex = matchingIndexes.first()
            val contextIndexes = sortedSetOf<Int>()
            matchingIndexes.forEach { index ->
                val start = (index - contextLines).coerceAtLeast(0)
                val end = (index + contextLines).coerceAtMost(lines.lastIndex)
                for (contextIndex in start..end) contextIndexes += contextIndex
            }
            val lineContent =
                if (matchingIndexes.size == 1) {
                    clip(lines[firstMatchIndex], 300)
                } else {
                    val digest =
                        matchingIndexes.take(5).joinToString(" | ") { index -> clip(lines[index], 80) }
                    "${matchingIndexes.size} matches: ${clip(digest, 200)}..."
                }
            val matchContext =
                contextIndexes.joinToString("\n") { index -> clip(lines[index], 400) }

            blocks.put(
                JSONObject()
                    .put("filePath", file.absolutePath)
                    .put("firstMatchLine", firstMatchIndex + 1)
                    .put("lineContent", lineContent)
                    .put("matchContext", clip(matchContext, 4_000))
                    .put("matchCount", matchingIndexes.size)
            )
            if (blocks.length() >= maxResults) break
        }

        return JSONObject()
            .put("success", true)
            .put("error", "")
            .put("filesSearched", filesSearched)
            .put("blocks", blocks)
            .toString()
    }

    private fun buildGlobMatcher(pattern: String): (String, String) -> Boolean {
        val normalized = pattern.trim().replace('\\', '/')
        if (normalized.isEmpty() || normalized == "*") return { _, _ -> true }
        val regex = Regex(globToRegex(normalized), RegexOption.IGNORE_CASE)
        val matchesFileName = '/' !in normalized
        return { relativePath, fileName ->
            regex.matches(relativePath) || (matchesFileName && regex.matches(fileName))
        }
    }

    private fun globToRegex(glob: String): String {
        val result = StringBuilder("^")
        var index = 0
        while (index < glob.length) {
            when (val char = glob[index]) {
                '*' -> {
                    if (index + 1 < glob.length && glob[index + 1] == '*') {
                        result.append(".*")
                        index++
                    } else {
                        result.append("[^/]*")
                    }
                }
                '?' -> result.append("[^/]")
                else -> result.append(Regex.escape(char.toString()))
            }
            index++
        }
        return result.append('$').toString()
    }

    private fun isExcluded(root: File, file: File): Boolean {
        val relative = file.relativeToOrSelf(root).invariantSeparatorsPath
        return relative.split('/').any { it in EXCLUDED_DIRECTORY_NAMES }
    }

    private fun isProbablyBinary(file: File): Boolean =
        runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(8_192)
                val size = input.read(buffer)
                size > 0 && buffer.take(size).any { it == 0.toByte() }
            }
        }.getOrDefault(true)

    private fun clip(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars) + "..."

    private val EXCLUDED_DIRECTORY_NAMES =
        setOf(".backup", ".git", ".gradle", ".operit", "backup", "build", "node_modules")
}
