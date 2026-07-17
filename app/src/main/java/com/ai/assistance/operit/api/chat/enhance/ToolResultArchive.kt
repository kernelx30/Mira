package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Keeps the complete tool output on-device while exposing only a bounded provider view.
 * The archive path is deliberately readable through Operit's existing file tools.
 */
internal object ToolResultArchive {
    private const val TAG = "ToolResultArchive"
    private const val ARCHIVE_DIRECTORY = "tool_results"
    private const val MAX_ARCHIVE_FILES = 128
    private const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L

    const val MODEL_INLINE_LIMIT_CHARS = 12_000
    private const val PREVIEW_HEAD_CHARS = 7_000
    private const val PREVIEW_TAIL_CHARS = 3_000

    private val archiveLock = Any()
    private val referenceIdRegex = Regex("^tr_[0-9a-f]{24}$")
    private val resultRefRegex = Regex("""\bresult_ref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val resultPathRegex = Regex("""\bresult_path\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val sourceCharsRegex = Regex("""\bsource_chars\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
    private val sourceShaRegex = Regex("""\bsource_sha256\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    data class Reference(
        val id: String,
        val absolutePath: String,
        val sourceChars: Int,
        val sha256: String
    )

    fun archiveIfLarge(context: Context, toolName: String, payload: String): Reference? =
        archiveIfLarge(archiveDirectory(context), toolName, payload, logResult = true)

    internal fun archiveIfLarge(directory: File, toolName: String, payload: String): Reference? =
        archiveIfLarge(directory, toolName, payload, logResult = false)

    private fun archiveIfLarge(
        directory: File,
        toolName: String,
        payload: String,
        logResult: Boolean,
    ): Reference? {
        if (payload.length <= MODEL_INLINE_LIMIT_CHARS) return null
        return archiveSafely(directory, toolName, payload, logResult)
    }

    fun resolveOrArchive(
        context: Context,
        toolName: String,
        payload: String,
        attributes: String
    ): Reference? =
        resolveOrArchive(
            directory = archiveDirectory(context),
            toolName = toolName,
            payload = payload,
            attributes = attributes,
            logResult = true,
        )

    internal fun resolveOrArchive(
        directory: File,
        toolName: String,
        payload: String,
        attributes: String,
    ): Reference? =
        resolveOrArchive(directory, toolName, payload, attributes, logResult = false)

    private fun resolveOrArchive(
        directory: File,
        toolName: String,
        payload: String,
        attributes: String,
        logResult: Boolean,
    ): Reference? {
        if (payload.length <= MODEL_INLINE_LIMIT_CHARS) return null

        resolveExistingReference(directory, attributes)?.let { return it }

        return archiveSafely(directory, toolName, payload, logResult)
    }

    fun appendReferenceAttributes(attributes: String, reference: Reference?): String {
        if (reference == null) return attributes
        val sanitizedAttributes =
            attributes
                .replace(resultRefRegex, "")
                .replace(resultPathRegex, "")
                .replace(sourceCharsRegex, "")
                .replace(sourceShaRegex, "")
                .trimEnd()
        return buildString {
            append(sanitizedAttributes)
            append(" result_ref=\"")
            append(escapeXmlAttribute(reference.id))
            append("\" result_path=\"")
            append(escapeXmlAttribute(reference.absolutePath))
            append("\" source_chars=\"")
            append(reference.sourceChars)
            append("\" source_sha256=\"")
            append(reference.sha256)
            append('"')
        }
    }

    fun projectForModel(payload: String, reference: Reference): String {
        if (payload.length <= MODEL_INLINE_LIMIT_CHARS) return payload

        val projectionSource =
            if (reference.sourceChars > payload.length) {
                runCatching {
                    File(reference.absolutePath)
                        .takeIf { it.isFile }
                        ?.readText(Charsets.UTF_8)
                }.getOrNull() ?: payload
            } else {
                payload
            }
        val head = projectionSource.take(PREVIEW_HEAD_CHARS).trimEnd()
        val tail = projectionSource.takeLast(PREVIEW_TAIL_CHARS).trimStart()
        val omitted = (projectionSource.length - head.length - tail.length).coerceAtLeast(0)
        val projected = buildString {
            appendLine("[Tool result compacted for context]")
            appendLine("tool_result_handle: ${reference.id}")
            appendLine("full_result_path: ${reference.absolutePath}")
            appendLine("original_chars: ${reference.sourceChars}")
            appendLine("sha256: ${reference.sha256}")
            appendLine("Use read_file_part with full_result_path and a narrow range when exact omitted content is needed.")
            appendLine("--- head ---")
            appendLine(head)
            appendLine("--- omitted $omitted chars ---")
            appendLine("--- tail ---")
            append(tail)
        }
        val savedPercent = ((1.0 - projected.length.toDouble() / projectionSource.length) * 100.0)
            .coerceIn(0.0, 100.0)
            .roundToInt()
        AppLogger.d(
            TAG,
            "Provider projection: handle=${reference.id}, before=${projectionSource.length}, after=${projected.length}, saved=${savedPercent}%"
        )
        return projected
    }

    fun projectMarkupForModel(context: Context, content: String): String {
        return ChatMarkupRegex.toolResultTagWithAttrs.replace(content) { matchResult ->
            val tagName = matchResult.groupValues[1]
            val attributes = matchResult.groupValues[2]
            val body = matchResult.groupValues[3]
            val toolName =
                ChatMarkupRegex.nameAttr.find(attributes)?.groupValues?.getOrNull(1).orEmpty()
            projectXmlForModel(
                context = context,
                tagName = tagName,
                attributes = attributes,
                body = body,
                toolName = toolName
            ) ?: matchResult.value
        }
    }

    fun projectXmlForModel(
        context: Context,
        tagName: String,
        attributes: String,
        body: String,
        toolName: String
    ): String? {
        val contentMatch = ChatMarkupRegex.contentTag.find(body)
        val payload = contentMatch?.groupValues?.getOrNull(1) ?: body
        val archiveReference =
            resolveOrArchive(
                context = context,
                toolName = toolName.ifBlank { "tool" },
                payload = payload,
                attributes = attributes
            ) ?: return null
        val projectedPayload = projectForModel(payload, archiveReference)
        val projectedBody =
            if (contentMatch == null) {
                projectedPayload
            } else {
                body.replaceRange(
                    contentMatch.range,
                    "<content>$projectedPayload</content>"
                )
            }
        val projectedAttributes = appendReferenceAttributes(attributes, archiveReference)
        return "<$tagName$projectedAttributes>$projectedBody</$tagName>"
    }

    private fun archiveSafely(
        directory: File,
        toolName: String,
        payload: String,
        logResult: Boolean,
    ): Reference? =
        try {
            archive(directory, payload).also { reference ->
                if (logResult) {
                    AppLogger.d(
                        TAG,
                        "Archived tool result: tool=$toolName, handle=${reference.id}, " +
                            "chars=${payload.length}, path=${reference.absolutePath}"
                    )
                }
            }
        } catch (error: Exception) {
            if (logResult) {
                AppLogger.e(TAG, "Failed to archive tool result for $toolName", error)
            }
            null
        }

    private fun archive(directory: File, payload: String): Reference {
        val digest = sha256(payload)
        val referenceId = referenceId(digest)
        val target = File(directory, "$referenceId.txt")

        synchronized(archiveLock) {
            if (!directory.exists() && !directory.mkdirs()) {
                error("Unable to create ${directory.absolutePath}")
            }
            val targetMatches =
                target.isFile &&
                    target.length() > 0L &&
                    runCatching { sha256(target.readText(Charsets.UTF_8)) == digest }
                        .getOrDefault(false)
            if (!targetMatches) {
                val temporary = File(directory, ".$referenceId.tmp")
                temporary.writeText(payload, Charsets.UTF_8)
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
            }
            target.setLastModified(System.currentTimeMillis())
            pruneArchive(directory, target)
        }
        return Reference(
            id = referenceId,
            absolutePath = target.absolutePath,
            sourceChars = payload.length,
            sha256 = digest
        )
    }

    private fun resolveExistingReference(directory: File, attributes: String): Reference? {
        val existingPath = resultPathRegex.find(attributes)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val existingId = resultRefRegex.find(attributes)?.groupValues?.getOrNull(1)
            ?.takeIf(referenceIdRegex::matches)
            ?: return null
        val declaredSha = sourceShaRegex.find(attributes)?.groupValues?.getOrNull(1)
        return runCatching {
            val canonicalDirectory = directory.canonicalFile
            val file = File(existingPath).canonicalFile
            require(
                file.parentFile == canonicalDirectory &&
                    file.name == "$existingId.txt" &&
                    file.isFile
            )

            val source = file.readText(Charsets.UTF_8)
            val digest = sha256(source)
            require(existingId == referenceId(digest))
            require(declaredSha.isNullOrBlank() || declaredSha.equals(digest, ignoreCase = true))
            Reference(
                id = existingId,
                absolutePath = file.absolutePath,
                sourceChars = source.length,
                sha256 = digest,
            )
        }.getOrNull()
    }

    private fun archiveDirectory(context: Context): File = File(context.filesDir, ARCHIVE_DIRECTORY)

    private fun referenceId(sha256: String): String = "tr_${sha256.take(24)}"

    private fun pruneArchive(directory: File, current: File) {
        val files = directory.listFiles { file -> file.isFile && file.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        var retainedBytes = 0L
        var retainedFiles = 0
        files.forEach { file ->
            val shouldRetain = file == current ||
                (retainedFiles < MAX_ARCHIVE_FILES && retainedBytes + file.length() <= MAX_ARCHIVE_BYTES)
            if (shouldRetain) {
                retainedFiles += 1
                retainedBytes += file.length()
            } else {
                file.delete()
            }
        }
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun escapeXmlAttribute(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
