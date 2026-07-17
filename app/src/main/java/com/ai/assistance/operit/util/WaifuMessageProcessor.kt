package com.ai.assistance.operit.util

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.streamnative.nativeMarkdownSplitByBlock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale

/**
 * Waifu模式消息处理器
 * 负责将AI回复按句号分割并模拟逐句发送
 */
object WaifuMessageProcessor {
    private const val URL_CHARS = "[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]"
    private const val ENTITY_PLACEHOLDER_PREFIX = "{WAIFUENTITY:"
    private const val ENTITY_PLACEHOLDER_SUFFIX = "}"
    private val FENCED_CODE_BLOCK_REGEX = Regex("```[^\\r\\n`]*[\\r\\n]?[\\s\\S]*?```")
    private val UNCLOSED_FENCED_CODE_BLOCK_REGEX = Regex("```[^\\r\\n`]*[\\r\\n]?[\\s\\S]*$")
    private val SENTENCE_SPLIT_REGEX =
        Regex("(?<=[。！？~～])(?![\"'”’」』])|(?<=[!?])(?![\"'”’」』])|(?<=\\.)(?![.\\d\"'”’」』])|(?<=\\.)$|(?<=\\.{3})|(?<=[…](?![…]))")
    private val SENTENCE_END_REGEX =
        Regex("(?:[。！？~～.!?…]|\\.{3})\\s*$")
    private val HORIZONTAL_RULE_REGEX = Regex("^[-_*]{3,}$")
    private val MARKDOWN_ENTITY_REGEX = Regex("""!?\[[^\]]*?\]\([^)]*?\)""")
    private val CUSTOM_EMOJI_MARKDOWN_REGEX =
        Regex(
            """!\[[^\]]*]\(file:[^)]*/custom_emoji/[^)]*\)""",
            RegexOption.IGNORE_CASE,
        )
    private val BARE_URL_REGEX = Regex("""https?://$URL_CHARS+""")
    private val EMAIL_ADDRESS_REGEX = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val DOMAIN_URL_REGEX =
        Regex("""(?<![@\w])(?:www\.)?(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}(?::\d+)?(?:[/?#]$URL_CHARS*)?""")
    private val ENTITY_PLACEHOLDER_REGEX =
        Regex("${Regex.escape(ENTITY_PLACEHOLDER_PREFIX)}\\d+${Regex.escape(ENTITY_PLACEHOLDER_SUFFIX)}")
    private val EMOTION_TAG_REGEX =
        Regex("""<emotion>\s*([a-z0-9_]+)\s*</emotion>""", RegexOption.IGNORE_CASE)
    private val EMOTION_CATEGORY_REGEX = Regex("^[a-z0-9_]+$")
    private const val MAX_EMOJIS_PER_REPLY = 1
    private const val MIN_SEGMENT_TYPING_DELAY_MS = 60L
    private const val MAX_SEGMENT_TYPING_DELAY_MS = 480L
    
    private var customEmojiRepository: CustomEmojiRepository? = null
    private var activePromptManager: ActivePromptManager? = null

    class StreamingSession(
        private val removePunctuation: Boolean = false
    ) {
        private val emittedSegments = mutableListOf<String>()
        private val resolvedEmotions = mutableMapOf<Int, ResolvedEmotion>()

        private fun resolveEmotion(emotion: String, occurrenceIndex: Int): String? {
            val normalizedEmotion = normalizeEmotion(emotion) ?: return null
            val cached = resolvedEmotions[occurrenceIndex]
            if (cached?.emotion == normalizedEmotion) {
                return cached.markdown
            }

            return resolveEmotionMarkdown(normalizedEmotion).also { markdown ->
                resolvedEmotions[occurrenceIndex] =
                    ResolvedEmotion(emotion = normalizedEmotion, markdown = markdown)
            }
        }

        fun collectStableSegments(content: String): List<String> {
            return collectSegments(
                splitMessageBySentencesInternal(
                    content = buildRenderableContentForWaifu(content),
                    removePunctuation = removePunctuation,
                    includeTrailingIncomplete = false,
                    emotionResolver = ::resolveEmotion,
                )
            )
        }

        fun collectFinalSegments(content: String): List<String> {
            return collectSegments(
                splitMessageBySentencesInternal(
                    content = buildRenderableContentForWaifu(content),
                    removePunctuation = removePunctuation,
                    includeTrailingIncomplete = true,
                    emotionResolver = ::resolveEmotion,
                )
            )
        }

        private fun collectSegments(segments: List<String>): List<String> {
            if (segments.isEmpty()) {
                return emptyList()
            }

            if (segments.size < emittedSegments.size) {
                AppLogger.w(
                    "WaifuMessageProcessor",
                    "流式分句结果短于已输出结果，忽略本次快照: emitted=${emittedSegments.size}, current=${segments.size}"
                )
                return emptyList()
            }

            val prefixMatches =
                emittedSegments.indices.all { index ->
                    emittedSegments[index] == segments[index]
                }
            if (!prefixMatches) {
                AppLogger.w(
                    "WaifuMessageProcessor",
                    "流式分句前缀发生变化，忽略不可回滚的增量输出"
                )
                return emptyList()
            }

            val newSegments = segments.drop(emittedSegments.size)
            emittedSegments.addAll(newSegments)
            return newSegments
        }
    }

    fun streamSegments(
        sourceStream: Stream<String>,
        removePunctuation: Boolean = false,
    ): Stream<String> = stream {
        val session = StreamingSession(removePunctuation = removePunctuation)
        val renderableBuffer = StringBuilder()

        suspend fun appendRenderableText(text: String) {
            if (text.isEmpty()) {
                return
            }
            renderableBuffer.append(text)
            session.collectStableSegments(renderableBuffer.toString()).forEach { emit(it) }
        }

        sourceStream.nativeMarkdownSplitByBlock().collect { blockGroup ->
            val blockType = blockGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT
            when (blockType) {
                MarkdownProcessorType.XML_BLOCK -> {
                    val blockBuilder = StringBuilder()
                    blockGroup.stream.collect { blockBuilder.append(it) }
                    appendRenderableText(buildRenderableContentForWaifu(blockBuilder.toString()))
                }

                MarkdownProcessorType.CODE_BLOCK,
                MarkdownProcessorType.TABLE,
                MarkdownProcessorType.BLOCK_LATEX,
                MarkdownProcessorType.IMAGE -> {
                    val blockBuilder = StringBuilder()
                    blockGroup.stream.collect { blockBuilder.append(it) }
                    appendRenderableText(blockBuilder.toString())
                }

                else -> {
                    blockGroup.stream.collect { piece ->
                        appendRenderableText(piece)
                    }
                }
            }
        }

        session.collectFinalSegments(renderableBuffer.toString()).forEach { emit(it) }
    }

    fun streamSegmentsWithTypingQueue(
        sourceStream: Stream<String>,
        removePunctuation: Boolean = false,
        charDelayMs: Int,
        synchronizeWithSpeech: Boolean = false,
    ): Stream<String> = stream {
        coroutineScope {
            val segmentQueue = Channel<String>(Channel.UNLIMITED)
            val producerJob = launch {
                try {
                    streamSegments(
                        sourceStream = sourceStream,
                        removePunctuation = false,
                    ).collect { segment ->
                        val queuedSegment =
                            if (removePunctuation) {
                                removeSentenceEndPunctuation(segment)
                            } else {
                                segment
                            }
                        if (queuedSegment.isNotBlank()) {
                            segmentQueue.send(queuedSegment)
                        }
                    }
                } finally {
                    segmentQueue.close()
                }
            }

            var nextSendAtMs = 0L
            for (segment in segmentQueue) {
                val waitMs = nextSendAtMs - SystemClock.elapsedRealtime()
                if (waitMs > 0L) {
                    delay(waitMs)
                }

                emit(segment)
                nextSendAtMs =
                    SystemClock.elapsedRealtime() +
                        calculateSegmentRevealDelayMs(
                            segmentLength = segment.length,
                            charDelayMs = charDelayMs,
                            synchronizeWithSpeech = synchronizeWithSpeech,
                        )
            }

            producerJob.join()
        }
    }

    internal fun calculateSegmentTypingDelayMs(segmentLength: Int, charDelayMs: Int): Long {
        if (segmentLength <= 0 || charDelayMs <= 0) return 0L
        return (segmentLength.toLong() * charDelayMs.toLong())
            .coerceIn(MIN_SEGMENT_TYPING_DELAY_MS, MAX_SEGMENT_TYPING_DELAY_MS)
    }

    internal fun calculateSegmentRevealDelayMs(
        segmentLength: Int,
        charDelayMs: Int,
        synchronizeWithSpeech: Boolean,
    ): Long {
        // The collector waits for the active TTS block to finish. Keep only a short
        // conversational pause here instead of estimating audio duration from text.
        return calculateSegmentTypingDelayMs(segmentLength, charDelayMs)
    }

    internal fun appendSegmentToReply(current: String, segment: String): String {
        val addition = segment.trim()
        if (addition.isEmpty()) return current
        if (current.isBlank()) return addition

        val previous = current.last()
        val next = addition.first()
        val separator =
            when {
                previous == '\n' || next == '\n' -> ""
                previous == '`' || next == '`' -> "\n\n"
                previous.isLetterOrDigit() && next.isLetterOrDigit() &&
                    previous.code < 128 && next.code < 128 -> " "
                else -> ""
            }
        return current + separator + addition
    }

    internal fun findMissingFinalSegments(
        finalContent: String,
        emittedSegments: List<String>,
        removePunctuation: Boolean = false,
    ): List<String> {
        val finalSegments = splitMessageBySentences(finalContent, removePunctuation)
        return findMissingFinalSegments(finalSegments, emittedSegments)
    }

    internal fun findMissingFinalSegments(
        finalSegments: List<String>,
        emittedSegments: List<String>,
    ): List<String> {
        if (finalSegments.isEmpty()) return emptyList()

        val finalCombined = finalSegments.joinToString(separator = "").comparisonKey()
        val emittedCombined = emittedSegments.joinToString(separator = "").comparisonKey()
        if (finalCombined == emittedCombined) return emptyList()

        val remainingEmitted = emittedSegments.map(::normalizeSegmentForComparison).toMutableList()
        return finalSegments.filter { segment ->
            val normalized = normalizeSegmentForComparison(segment)
            val matchedIndex = remainingEmitted.indexOf(normalized)
            if (matchedIndex >= 0) {
                remainingEmitted.removeAt(matchedIndex)
                false
            } else {
                true
            }
        }
    }

    private fun normalizeSegmentForComparison(segment: String): String =
        segment
            .replace(Regex("!\\[([^]]+)]\\([^)]+\\)"), "![$1]")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.comparisonKey(): String =
        normalizeSegmentForComparison(this).replace(" ", "")

    fun streamTtsText(sourceStream: Stream<String>): Stream<Char> = stream {
        var lastCharWasNewline = true

        suspend fun emitText(text: String) {
            text.forEach { char ->
                val isCurrentCharNewline = char == '\n'
                if (isCurrentCharNewline) {
                    if (!lastCharWasNewline) {
                        emit('\n')
                        lastCharWasNewline = true
                    }
                } else if (char.isWhitespace() && lastCharWasNewline) {
                    Unit
                } else {
                    emit(char)
                    lastCharWasNewline = false
                }
            }
        }

        sourceStream.nativeMarkdownSplitByBlock().collect { blockGroup ->
            val blockType = blockGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT
            when (blockType) {
                MarkdownProcessorType.XML_BLOCK,
                MarkdownProcessorType.CODE_BLOCK -> {
                    blockGroup.stream.collect { }
                    if (!lastCharWasNewline) {
                        emit('\n')
                        lastCharWasNewline = true
                    }
                }

                else -> {
                    blockGroup.stream.collect { piece ->
                        emitText(piece)
                    }
                }
            }
        }
    }

    private fun removeSentenceEndPunctuation(sentence: String): String {
        val trimmed = sentence.trim()
        return if (trimmed.endsWith("...")) {
            trimmed
        } else {
            trimmed.replace(Regex("[。！？.!?]+$"), "").trim()
        }
    }
    
    /**
     * 初始化处理器（需要在应用启动时调用）
     */
    fun initialize(appContext: Context) {
        customEmojiRepository = CustomEmojiRepository.getInstance(appContext)
        activePromptManager = ActivePromptManager.getInstance(appContext)
    }
    
    /**
     * 将完整的消息按句号分割成句子
     * @param content 完整的消息内容
     * @param removePunctuation 是否移除标点符号
     * @return 分割后的句子列表
     */
    fun splitMessageBySentences(content: String, removePunctuation: Boolean = false): List<String> {
        return splitMessageBySentencesInternal(
            content = buildRenderableContentForWaifu(content),
            removePunctuation = removePunctuation,
            includeTrailingIncomplete = true,
        )
    }

    fun splitStableMessageSegments(content: String, removePunctuation: Boolean = false): List<String> {
        return splitMessageBySentencesInternal(
            content = buildRenderableContentForWaifu(content),
            removePunctuation = removePunctuation,
            includeTrailingIncomplete = false,
        )
    }

    private fun splitMessageBySentencesInternal(
        content: String,
        removePunctuation: Boolean,
        includeTrailingIncomplete: Boolean,
        emotionResolver: (String, Int) -> String? = { emotion, _ ->
            resolveEmotionMarkdown(emotion)
        },
    ): List<String> {
        if (content.isBlank()) return emptyList()

        val entities = mutableListOf<String>()

        fun createPlaceholder(value: String): String {
            val placeholder = "$ENTITY_PLACEHOLDER_PREFIX${entities.size}$ENTITY_PLACEHOLDER_SUFFIX"
            entities.add(value)
            return placeholder
        }

        fun protectMatches(source: String, regex: Regex): String =
            regex.replace(source) { matchResult ->
                val (protectedValue, trailingPunctuation) =
                    splitTrailingProtectedText(matchResult.value)

                if (protectedValue.isBlank()) {
                    matchResult.value
                } else {
                    createPlaceholder(protectedValue) + trailingPunctuation
                }
            }

        // 1. 将Markdown实体、URL和邮箱替换为占位符，以保护它们不被错误分割
        var contentWithPlaceholders =
            MARKDOWN_ENTITY_REGEX.replace(content) { createPlaceholder(it.value) }
        contentWithPlaceholders = protectMatches(contentWithPlaceholders, BARE_URL_REGEX)
        contentWithPlaceholders = protectMatches(contentWithPlaceholders, EMAIL_ADDRESS_REGEX)
        contentWithPlaceholders = protectMatches(contentWithPlaceholders, DOMAIN_URL_REGEX)
        
        // 2. 首先分离表情包和文本内容（在处理占位符版本的内容上）
        val segments = splitIntoSegments(contentWithPlaceholders)
        val hasFollowingStableBoundarySegment =
            BooleanArray(segments.size).also { flags ->
                var seenStableBoundarySegment = false
                for (index in segments.indices.reversed()) {
                    flags[index] = seenStableBoundarySegment
                    if (
                        segmentProducesOutput(segments[index]) &&
                        segments[index].blockType.canCloseStableTextAtBlockBoundary()
                    ) {
                        seenStableBoundarySegment = true
                    }
                }
            }

        val resultWithPlaceholders = mutableListOf<String>()
        var emotionOccurrenceIndex = 0
        var renderedEmojiCount = 0

        for ((segmentIndex, segment) in segments.withIndex()) {
            if (segment.isProtected) {
                val block = segment.content.trim('\n', '\r')
                if (block.isNotBlank()) {
                    resultWithPlaceholders.add(block)
                }
                continue
            }

            val contentWithoutThinking = ChatUtils.removeThinkingContent(segment.content)
            if (contentWithoutThinking.isBlank()) continue

            val separatedContent =
                separateEmotionAndTextInternal(contentWithoutThinking) { emotion ->
                    val occurrenceIndex = emotionOccurrenceIndex++
                    if (renderedEmojiCount >= MAX_EMOJIS_PER_REPLY) {
                        null
                    } else {
                        emotionResolver(emotion, occurrenceIndex)?.also {
                            renderedEmojiCount++
                        }
                    }
                }

            for (item in separatedContent) {
                // 如果这个item是表情包（包含![开头的），直接添加
                if (item.startsWith("![")) {
                    resultWithPlaceholders.add(item)
                    continue
                }

                // 对于文本内容，进行正常的清理和分句处理
                val cleanedContent = cleanContentForWaifu(item)

                if (cleanedContent.isBlank()) continue

                var sentences = splitPlainTextIntoSentences(cleanedContent, removePunctuation = removePunctuation)

                if (shouldSplitStructuredMarkdownLines(item, sentences)) {
                    sentences = splitStructuredMarkdownLines(item, removePunctuation = removePunctuation)
                }

                if (!includeTrailingIncomplete && sentences.isNotEmpty()) {
                    val unclosedInlineMarkdownStart = findLastUnclosedInlineMarkdownStart(item)
                    if (unclosedInlineMarkdownStart != null) {
                        val stableRawContent = item.substring(0, unclosedInlineMarkdownStart)
                        val stableCleanedContent = cleanContentForWaifu(stableRawContent)
                        sentences =
                            splitStableSentencesForRawContent(
                                rawContent = stableRawContent,
                                cleanedContent = stableCleanedContent,
                                removePunctuation = removePunctuation,
                                segment = segment,
                                hasFollowingStableBoundarySegment =
                                    hasFollowingStableBoundarySegment[segmentIndex]
                            )
                    } else if (
                        shouldHoldLastStableSentence(
                            rawContent = item,
                            cleanedContent = cleanedContent,
                            segment = segment,
                            hasFollowingStableBoundarySegment =
                                hasFollowingStableBoundarySegment[segmentIndex]
                        )
                    ) {
                        sentences = sentences.dropLast(1)
                    }
                }

                resultWithPlaceholders.addAll(sentences)
            }
        }
        
        // 3.5. 合并仅包含标点符号的句子到前一句
        val mergedResultWithPlaceholders = mergePunctuationOnlySegments(resultWithPlaceholders)
        
        // 4. 将占位符恢复为原始的Markdown实体
        val finalResult = mergedResultWithPlaceholders.map { sentence ->
            var currentSentence = sentence
            val placeholderRegex =
                Regex(
                    "${Regex.escape(ENTITY_PLACEHOLDER_PREFIX)}(\\d+)${Regex.escape(ENTITY_PLACEHOLDER_SUFFIX)}"
                )
            
            // 循环替换，以处理一个句子中可能存在的多个占位符
            while (placeholderRegex.containsMatchIn(currentSentence)) {
                currentSentence = placeholderRegex.replace(currentSentence) { matchResult ->
                    val index = matchResult.groupValues[1].toInt()
                    
                    if (index < entities.size) {
                        entities[index]
                    } else {
                        matchResult.value // 理论上不会发生，作为安全回退
                    }
                }
            }
            currentSentence
        }

        return finalResult
    }

    private fun splitTrailingProtectedText(value: String): Pair<String, String> {
        if (value.isEmpty()) {
            return "" to ""
        }

        var splitIndex = value.length
        while (splitIndex > 0 && value[splitIndex - 1] in TRAILING_PROTECTED_TEXT_CHARS) {
            splitIndex--
        }

        return value.substring(0, splitIndex) to value.substring(splitIndex)
    }

    private fun splitPlainTextIntoSentences(
        cleanedContent: String,
        removePunctuation: Boolean,
    ): List<String> {
        if (cleanedContent.isBlank()) {
            return emptyList()
        }

        var sentences =
            cleanedContent.split(SENTENCE_SPLIT_REGEX)
                .filter { it.isNotBlank() }
                .map { it.trim() }

        if (removePunctuation) {
            sentences =
                sentences
                    .map { sentence ->
                        if (sentence.endsWith("...")) {
                            sentence.trim()
                        } else {
                            sentence.replace(Regex("[。！？.!?]+$"), "").trim()
                        }
                    }
                    .filter { it.isNotBlank() }
        }

        return sentences
    }

    private fun mergePunctuationOnlySegments(segments: List<String>): MutableList<String> {
        val mergedSegments = mutableListOf<String>()
        if (segments.isEmpty()) {
            return mergedSegments
        }

        mergedSegments.add(segments[0])
        for (i in 1 until segments.size) {
            val currentSentence = segments[i]
            val trimmedSentence = currentSentence.trim()
            if (trimmedSentence.isNotEmpty() && trimmedSentence.matches(Regex("^[。！？~～.!?…]+$"))) {
                val lastIndex = mergedSegments.size - 1
                val lastSentence = mergedSegments[lastIndex]
                if (!lastSentence.contains('\n') && !lastSentence.contains('\r')) {
                    mergedSegments[lastIndex] = lastSentence + currentSentence
                } else {
                    mergedSegments.add(currentSentence)
                }
            } else {
                mergedSegments.add(currentSentence)
            }
        }

        return mergedSegments
    }

    private fun shouldSplitStructuredMarkdownLines(
        content: String,
        sentences: List<String>,
    ): Boolean {
        if (sentences.size != 1) {
            return false
        }

        val nonEmptyLines =
            content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()

        if (nonEmptyLines.size < 2) {
            return false
        }

        return nonEmptyLines.any { line ->
            isUrlOrEmailLine(cleanStructuredMarkdownLine(line))
        }
    }

    private fun splitStructuredMarkdownLines(
        content: String,
        removePunctuation: Boolean,
    ): List<String> {
        val results = mutableListOf<String>()

        content.lineSequence().forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty() || HORIZONTAL_RULE_REGEX.matches(trimmedLine)) {
                return@forEach
            }

            val cleanedLine = cleanStructuredMarkdownLine(trimmedLine)
            if (cleanedLine.isBlank()) {
                return@forEach
            }

            val lineContent = cleanContentForWaifu(cleanedLine)
            results.addAll(splitPlainTextIntoSentences(lineContent, removePunctuation))
        }

        return mergePunctuationOnlySegments(results)
    }

    private fun cleanStructuredMarkdownLine(line: String): String =
        line
            .trim()
            .replace(Regex("^#+\\s*"), "")
            .replace(Regex("^>\\s*"), "")
            .replace(Regex("^(?:[\\-*+]\\s+|\\d+\\.\\s+)"), "")
            .replace(Regex("^\\*\\*(.+)\\*\\*$"), "$1")
            .replace(Regex("^__(.+)__$"), "$1")
            .replace(Regex("^~~(.+)~~$"), "$1")
            .trim()

    private fun getLastVisibleLine(content: String): String =
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .lastOrNull()
            ?: ""

    private fun lineAllowsStableWithoutSentenceEnding(line: String): Boolean {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) {
            return false
        }

        if (
            trimmedLine.startsWith("```") ||
            trimmedLine.startsWith("|") ||
            trimmedLine.startsWith("$$") ||
            Regex("^(?:#+\\s*|>\\s*|[-*+]\\s+|\\d+\\.\\s+)").containsMatchIn(trimmedLine)
        ) {
            return true
        }

        return isUrlOrEmailLine(cleanStructuredMarkdownLine(trimmedLine))
    }

    private fun isUrlOrEmailLine(line: String): Boolean {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) {
            return false
        }

        return BARE_URL_REGEX.containsMatchIn(trimmedLine) ||
            DOMAIN_URL_REGEX.containsMatchIn(trimmedLine) ||
            EMAIL_ADDRESS_REGEX.containsMatchIn(trimmedLine) ||
            ENTITY_PLACEHOLDER_REGEX.containsMatchIn(trimmedLine)
    }

    private fun hasStableSentenceEnding(content: String): Boolean {
        return SENTENCE_END_REGEX.containsMatchIn(content.trimEnd())
    }

    private fun splitStableSentencesForRawContent(
        rawContent: String,
        cleanedContent: String,
        removePunctuation: Boolean,
        segment: Segment,
        hasFollowingStableBoundarySegment: Boolean,
    ): List<String> {
        if (cleanedContent.isBlank()) {
            return emptyList()
        }

        var stableSentences =
            splitPlainTextIntoSentences(cleanedContent, removePunctuation = removePunctuation)

        if (shouldSplitStructuredMarkdownLines(rawContent, stableSentences)) {
            stableSentences =
                splitStructuredMarkdownLines(rawContent, removePunctuation = removePunctuation)
        }

        if (
            stableSentences.isNotEmpty() &&
            shouldHoldLastStableSentence(
                rawContent = rawContent,
                cleanedContent = cleanedContent,
                segment = segment,
                hasFollowingStableBoundarySegment = hasFollowingStableBoundarySegment
            )
        ) {
            stableSentences = stableSentences.dropLast(1)
        }

        return stableSentences
    }

    private fun shouldHoldLastStableSentence(
        rawContent: String,
        cleanedContent: String,
        segment: Segment,
        hasFollowingStableBoundarySegment: Boolean,
    ): Boolean {
        return !hasStableSentenceEnding(cleanedContent) &&
            !lineAllowsStableWithoutSentenceEnding(getLastVisibleLine(rawContent)) &&
            !segment.canUseBlockBoundaryAsStableEnding(
                hasFollowingStableBoundarySegment = hasFollowingStableBoundarySegment
            )
    }

    private fun findLastUnclosedInlineMarkdownStart(content: String): Int? {
        val trimmedContent = content.trimEnd()
        if (trimmedContent.isEmpty()) {
            return null
        }

        return listOf("**", "__", "~~", "`")
            .mapNotNull { delimiter -> findLastUnclosedDelimiterStart(trimmedContent, delimiter) }
            .maxOrNull()
    }

    private fun findLastUnclosedDelimiterStart(content: String, delimiter: String): Int? {
        val starts = mutableListOf<Int>()
        var index = 0
        while (index <= content.length - delimiter.length) {
            val foundIndex = content.indexOf(delimiter, startIndex = index)
            if (foundIndex < 0) {
                break
            }

            if (!isEscaped(content, foundIndex)) {
                starts.add(foundIndex)
            }
            index = foundIndex + delimiter.length
        }

        if (starts.size % 2 == 0) {
            return null
        }

        return starts.lastOrNull()
    }

    private fun isEscaped(content: String, index: Int): Boolean {
        var slashCount = 0
        var cursor = index - 1
        while (cursor >= 0 && content[cursor] == '\\') {
            slashCount += 1
            cursor -= 1
        }

        return slashCount % 2 == 1
    }

    private fun segmentProducesOutput(segment: Segment): Boolean {
        if (segment.isProtected) {
            return segment.content.trim('\n', '\r').isNotBlank()
        }

        val contentWithoutThinking = ChatUtils.removeThinkingContent(segment.content)
        if (contentWithoutThinking.isBlank()) {
            return false
        }

        return separateEmotionAndText(contentWithoutThinking).any { item ->
            item.startsWith("![") || cleanContentForWaifu(item).isNotBlank()
        }
    }

    fun buildRenderableContentForWaifu(content: String): String {
        if (content.isBlank()) {
            return ""
        }

        val blocks = StructuredAssistantContentParser.parse(content)
        if (blocks.isEmpty()) {
            return content
        }

        val builder = StringBuilder()
        blocks.forEach { block ->
            when (block.kind) {
                StructuredAssistantContentParser.BlockKind.TEXT -> {
                    builder.append(block.rawContent)
                }

                StructuredAssistantContentParser.BlockKind.XML -> {
                    if (block.closed && block.tagName.equals("emotion", ignoreCase = true)) {
                        builder.append(block.rawContent)
                    } else if (block.tagName.equals("speech", ignoreCase = true)) {
                        builder.append(block.content)
                    }
                }
            }
        }

        return builder.toString()
    }
    
    /**
     * 清理内容中的状态标签和XML标签，只保留纯文本
     */
    fun cleanContentForWaifu(content: String): String {
        val sanitizedContent =
            ChatUtils.removeThinkingContent(
                ChatUtils.stripGeminiThoughtSignatureMeta(
                    buildRenderableContentForWaifu(content)
                )
            )

        return sanitizedContent
            .replace(FENCED_CODE_BLOCK_REGEX, " ")
            .replace(UNCLOSED_FENCED_CODE_BLOCK_REGEX, " ")
            // 移除状态标签
            .replace(ChatMarkupRegex.statusTag, "")
            .replace(ChatMarkupRegex.statusSelfClosingTag, "")
            // 移除工具标签
            .replace(ChatMarkupRegex.toolTag, "")
            .replace(ChatMarkupRegex.toolSelfClosingTag, "")
            // 移除工具结果标签
            .replace(ChatMarkupRegex.toolResultTag, "")
            .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
            // 移除emotion标签（因为已经在processEmotionTags中处理过了）
            .replace(ChatMarkupRegex.emotionTag, "")
            // 表情包只负责视觉表达，不应让 TTS 念出情绪分类或本地文件路径。
            .replace(CUSTOM_EMOJI_MARKDOWN_REGEX, " ")
            
            // --- 新增：移除Markdown相关标记 ---
            // 1. 移除图片和链接，保留替代文本或链接文本
            .replace(Regex("!?\\[(.*?)\\]\\(.*?\\)"), "$1")
            // 2. 移除标题标记
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            // 3. 移除引用标记
            .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
            // 4. 移除列表标记
            .replace(Regex("^[\\*\\-\\+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
            // 5. 移除代码块标记
            .replace(Regex("```[a-zA-Z]*\\n?|\\n?```"), "")
            // 6. 移除加粗、斜体、删除线 (注意顺序和互斥)
            .replace(Regex("(\\*\\*\\*|___)(.+?)\\1"), "$2") // 加粗斜体
            .replace(Regex("(\\*\\*|__(?!MD_ENTITY__))(.+?)\\1"), "$2") // 加粗 (避免匹配占位符)
            .replace(Regex("(\\*|_)(.+?)\\1"), "$2")        // 斜体
            .replace(Regex("~~(.+?)~~"), "$1")              // 删除线
            // 7. 移除行内代码
            .replace(Regex("`(.+?)`"), "$1")
            // 8. 移除水平线
            .replace(Regex("^[-_*]{3,}\\s*$", RegexOption.MULTILINE), "")
            // --- Markdown移除结束 ---
            
            // 移除其他常见的XML标签
            .replace(ChatMarkupRegex.anyXmlTag, "")
            // 清理多余的空白
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    

    
    private data class Segment(
        val content: String,
        val isProtected: Boolean,
        val blockType: MarkdownProcessorType,
    ) {
        fun canUseBlockBoundaryAsStableEnding(hasFollowingStableBoundarySegment: Boolean): Boolean {
            return hasFollowingStableBoundarySegment || blockType.canCloseStableTextAtBlockBoundary()
        }
    }

    private fun MarkdownProcessorType.canCloseStableTextAtBlockBoundary(): Boolean =
        when (this) {
            MarkdownProcessorType.HEADER,
            MarkdownProcessorType.BLOCK_QUOTE,
            MarkdownProcessorType.CODE_BLOCK,
            MarkdownProcessorType.ORDERED_LIST,
            MarkdownProcessorType.UNORDERED_LIST,
            MarkdownProcessorType.BLOCK_LATEX,
            MarkdownProcessorType.TABLE,
            MarkdownProcessorType.IMAGE -> true

            MarkdownProcessorType.HORIZONTAL_RULE,
            MarkdownProcessorType.XML_BLOCK,
            MarkdownProcessorType.BOLD,
            MarkdownProcessorType.ITALIC,
            MarkdownProcessorType.INLINE_CODE,
            MarkdownProcessorType.LINK,
            MarkdownProcessorType.STRIKETHROUGH,
            MarkdownProcessorType.UNDERLINE,
            MarkdownProcessorType.INLINE_LATEX,
            MarkdownProcessorType.PLAIN_TEXT,
            MarkdownProcessorType.HTML_BREAK -> false
        }

    private val TRAILING_PROTECTED_TEXT_CHARS =
        setOf('。', '！', '？', '.', '!', '?', '…', '，', ',', '；', ';', '：', ':', ')', '）', ']', '】', '}', '」', '"', '\'')

    private fun splitIntoSegments(content: String): List<Segment> {
        if (content.isEmpty()) {
            return listOf(
                Segment(
                    content = "",
                    isProtected = false,
                    blockType = MarkdownProcessorType.PLAIN_TEXT,
                )
            )
        }

        val segments = mutableListOf<Segment>()

        runBlocking {
            content.stream()
                .nativeMarkdownSplitByBlock()
                .collect { blockGroup ->
                    val blockType = blockGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT
                    val sb = StringBuilder()
                    blockGroup.stream.collect { sb.append(it) }
                    val block = sb.toString()
                    if (block.isEmpty()) return@collect

                    val isProtected =
                        when (blockType) {
                            MarkdownProcessorType.CODE_BLOCK,
                            MarkdownProcessorType.TABLE -> true
                            else -> false
                        }

                    segments.add(
                        Segment(
                            content = block,
                            isProtected = isProtected,
                            blockType = blockType,
                        )
                    )
                }
        }

        return segments
    }
    
    /**
     * 处理表情包标签，将<emotion>标签替换为对应的表情图片
     * @param content 包含emotion标签的内容
     * @return 处理后的内容，emotion标签被替换为表情图片
     */
    fun processEmotionTags(content: String): String {
        if (content.isBlank()) return content
        var renderedEmojiCount = 0

        return EMOTION_TAG_REGEX.replace(content) { matchResult ->
            if (renderedEmojiCount >= MAX_EMOJIS_PER_REPLY) {
                return@replace ""
            }

            resolveEmotionMarkdown(matchResult.groupValues[1])
                ?.also { renderedEmojiCount++ }
                .orEmpty()
        }
    }
    
    /**
     * 分离表情包和文本内容
     * @param content 包含emotion标签的内容
     * @return 包含文本内容和表情包内容的列表，表情包会单独作为一个元素
     */
    fun separateEmotionAndText(content: String): List<String> {
        var renderedEmojiCount = 0
        return separateEmotionAndTextInternal(content) { emotion ->
            if (renderedEmojiCount >= MAX_EMOJIS_PER_REPLY) {
                null
            } else {
                resolveEmotionMarkdown(emotion)?.also { renderedEmojiCount++ }
            }
        }
    }

    private fun separateEmotionAndTextInternal(
        content: String,
        emotionResolver: (String) -> String?,
    ): List<String> {
        if (content.isBlank()) return listOf(content)

        val matches = EMOTION_TAG_REGEX.findAll(content).toList()
        if (matches.isEmpty()) return listOf(content)

        val result = mutableListOf<String>()
        var lastEnd = 0

        for (match in matches) {
            val beforeText = content.substring(lastEnd, match.range.first).trim()
            if (beforeText.isNotEmpty()) {
                result.add(beforeText)
            }

            emotionResolver(match.groupValues[1])?.let(result::add)
            lastEnd = match.range.last + 1
        }

        val afterText = content.substring(lastEnd).trim()
        if (afterText.isNotEmpty()) {
            result.add(afterText)
        }

        return result
    }

    private fun normalizeEmotion(emotion: String): String? {
        val normalized = emotion.trim().lowercase(Locale.ROOT)
        return normalized.takeIf(EMOTION_CATEGORY_REGEX::matches)
    }

    private fun resolveEmotionMarkdown(emotion: String): String? {
        val normalizedEmotion = normalizeEmotion(emotion) ?: return null
        val emojiPath = getRandomEmojiPath(normalizedEmotion) ?: return null
        val imageUrl =
            if (emojiPath.startsWith("/")) {
                Uri.fromFile(File(emojiPath)).toString()
            } else {
                "file:///android_asset/emoji/${Uri.encode(emojiPath, "/")}"
            }
        return "![$normalizedEmotion]($imageUrl)"
    }
    
    /**
     * 根据情绪名称获取随机的表情图片路径
     * @param emotion 情绪名称（如：happy、sad、miss_you等）
     * @return 表情图片的完整路径（file:// 格式），如果找不到则返回null
     */
    private fun getRandomEmojiPath(emotion: String): String? {
        try {
            // 只从自定义表情中查找
            val customEmoji = try {
                customEmojiRepository?.let { repo ->
                    runBlocking {
                        val activePrompt = activePromptManager?.getActivePrompt() ?: return@runBlocking null
                        repo.initializeBuiltinEmojis(activePrompt)
                        val emojis = repo.getEmojisForCategory(activePrompt, emotion).first()
                        if (emojis.isNotEmpty()) {
                            val randomEmoji = emojis.random()
                            val file = repo.getEmojiFile(activePrompt, randomEmoji)
                            if (file.exists()) {
                                return@runBlocking file.absolutePath
                            }
                        }
                        null
                    }
                }
            } catch (e: Exception) {
                com.ai.assistance.operit.util.AppLogger.e("WaifuMessageProcessor", "查询自定义表情失败", e)
                null
            }
            
            // 如果找到自定义表情，直接返回（已经是完整路径）
            if (customEmoji != null) {
                return customEmoji
            }
            
            // 如果自定义表情中没有找到，则直接返回null
            com.ai.assistance.operit.util.AppLogger.w("WaifuMessageProcessor", "在自定义表情中未找到对于情绪 '$emotion' 的表情")
            return null
            
        } catch (e: Exception) {
            com.ai.assistance.operit.util.AppLogger.e("WaifuMessageProcessor", "获取表情图片失败: $emotion", e)
            return null
        }
    }

    private data class ResolvedEmotion(
        val emotion: String,
        val markdown: String?,
    )
} 
