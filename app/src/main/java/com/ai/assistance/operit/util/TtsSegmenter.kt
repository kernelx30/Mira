package com.ai.assistance.operit.util

object TtsSegmenter {
    const val MAX_SEGMENT_LENGTH = 50
    const val MIN_NATURAL_BLOCK_LENGTH = 48
    const val MAX_NATURAL_BLOCK_LENGTH = 160
    const val END_CHARS = "!?;:。！？；：\n"

    fun findFirstEndCharIndex(text: CharSequence): Int {
        for (index in 0 until text.length) {
            if (isSegmentEndingChar(text, index)) return index
        }
        return -1
    }

    fun nextSegmentEnd(buffer: CharSequence): Int {
        val endIndex = findFirstEndCharIndex(buffer)
        if (endIndex >= 0) {
            var boundary = endIndex + 1
            while (boundary < buffer.length && isTrailingEndingChar(buffer, boundary)) {
                boundary++
            }
            return boundary
        }
        if (buffer.length >= MAX_SEGMENT_LENGTH) return buffer.length
        return -1
    }

    fun split(text: String): List<String> {
        val buffer = StringBuilder(text)
        val segments = mutableListOf<String>()

        while (buffer.isNotEmpty()) {
            val endIndex = nextSegmentEnd(buffer)
            if (endIndex < 0) break

            val segment = buffer.substring(0, endIndex).trim()
            if (segment.isNotEmpty()) segments += segment
            buffer.delete(0, endIndex)
        }

        val remaining = buffer.toString().trim()
        if (remaining.isNotEmpty()) segments += remaining
        return segments
    }

    fun splitNaturalBlocks(text: String): List<String> {
        val buffer = TtsNaturalBlockBuffer()
        val blocks = mutableListOf<String>()
        split(text).forEach { segment -> blocks += buffer.append(segment) }
        buffer.flush()?.let(blocks::add)
        return blocks
    }

    private fun isSegmentEndingChar(text: CharSequence, index: Int): Boolean {
        val current = text[index]
        if (END_CHARS.indexOf(current) >= 0) {
            return true
        }

        if (current != '.') {
            return false
        }

        val next = text.getOrNull(index + 1)
        return next == null || (!next.isDigit() && next != '.')
    }

    private fun isTrailingEndingChar(text: CharSequence, index: Int): Boolean {
        val current = text[index]
        if (END_CHARS.indexOf(current) >= 0) {
            return true
        }

        if (current != '.') {
            return false
        }

        val previous = text.getOrNull(index - 1)
        return previous == '.'
    }

    private fun CharSequence.getOrNull(index: Int): Char? {
        if (index < 0 || index >= length) {
            return null
        }
        return this[index]
    }
}

class TtsNaturalBlockBuffer(
    private val minBlockLength: Int = TtsSegmenter.MIN_NATURAL_BLOCK_LENGTH,
    private val maxBlockLength: Int = TtsSegmenter.MAX_NATURAL_BLOCK_LENGTH,
    private val minSegmentCount: Int = 2,
) {
    private val buffer = StringBuilder()
    private var segmentCount = 0

    fun append(segment: String): List<String> {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return emptyList()

        val emitted = mutableListOf<String>()
        val separatorLength = if (buffer.isEmpty()) 0 else 1
        val projectedLength = buffer.length + separatorLength + trimmed.length
        if (buffer.isNotEmpty() && projectedLength > maxBlockLength) {
            flush()?.let(emitted::add)
        }

        if (buffer.isNotEmpty()) buffer.append(' ')
        buffer.append(trimmed)
        segmentCount += 1

        if (buffer.length >= minBlockLength || segmentCount >= minSegmentCount) {
            flush()?.let(emitted::add)
        }
        return emitted
    }

    fun flush(): String? {
        val block = buffer.toString().trim().takeIf { it.isNotEmpty() }
        buffer.clear()
        segmentCount = 0
        return block
    }

    fun clear() {
        buffer.clear()
        segmentCount = 0
    }
}
