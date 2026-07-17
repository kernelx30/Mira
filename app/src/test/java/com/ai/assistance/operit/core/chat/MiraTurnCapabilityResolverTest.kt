package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiraTurnCapabilityResolverTest {
    @Test
    fun `ordinary conversation stays general while tools remain available`() {
        assertEquals(
            MiraTurnCapability.GENERAL,
            MiraTurnCapabilityResolver.resolve("今天心情有点乱，陪我聊聊", hasAttachments = false),
        )
        assertFalse(MiraTurnCapabilityResolver.requiresDeviceAssist("帮我打开思路讲讲这个问题"))
        assertFalse(MiraTurnCapabilityResolver.requiresDeviceAssist("怎么打开微信的小程序？"))
        assertFalse(MiraTurnCapabilityResolver.requiresDeviceAssist("How do I open Bluetooth settings?"))
    }

    @Test
    fun `attachments are inferred without a manual mode`() {
        assertEquals(
            MiraTurnCapability.ATTACHMENTS,
            MiraTurnCapabilityResolver.resolve("分析一下这张截图", hasAttachments = true),
        )
    }

    @Test
    fun `explicit device actions enable device assist for this turn`() {
        listOf(
            "帮我打开汽水音乐",
            "打开抖音",
            "点一下页面里的确认按钮",
            "把蓝牙打开",
            "音量调高一点",
            "take a screenshot of the screen",
        ).forEach { text ->
            assertTrue(text, MiraTurnCapabilityResolver.requiresDeviceAssist(text))
            assertEquals(
                text,
                MiraTurnCapability.DEVICE_ASSIST,
                MiraTurnCapabilityResolver.resolve(text, hasAttachments = false),
            )
        }
    }

    @Test
    fun `device intent takes precedence over an attachment`() {
        assertEquals(
            MiraTurnCapability.DEVICE_ASSIST,
            MiraTurnCapabilityResolver.resolve("照着这张图打开设置应用", hasAttachments = true),
        )
    }
}
