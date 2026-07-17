package com.ai.assistance.operit.util

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaifuEmojiAndroidTest {
    @Before
    fun setUp() {
        WaifuMessageProcessor.initialize(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun emotionTagBecomesStableStandaloneEmoji() {
        val source = "今天见到你很开心。<emotion>happy</emotion>"
        val session = WaifuMessageProcessor.StreamingSession()

        val segments =
            session.collectStableSegments(source) + session.collectFinalSegments(source)
        val emojiSegments = segments.filter { it.startsWith("![happy](file:") }

        assertTrue(segments.contains("今天见到你很开心。"))
        assertEquals(1, emojiSegments.size)
        val imageUrl = emojiSegments.single().substringAfter('(').substringBeforeLast(')')
        assertTrue(File(Uri.parse(imageUrl).path.orEmpty()).isFile)
        assertEquals("", WaifuMessageProcessor.cleanContentForWaifu(emojiSegments.single()))
        assertTrue(session.collectFinalSegments(source).isEmpty())
    }

    @Test
    fun onlyOneEmojiIsRenderedPerReply() {
        val segments =
            WaifuMessageProcessor.splitMessageBySentences(
                "第一句。<emotion>happy</emotion><emotion>sad</emotion>"
            )

        assertEquals(1, segments.count { it.startsWith("![") })
    }
}
