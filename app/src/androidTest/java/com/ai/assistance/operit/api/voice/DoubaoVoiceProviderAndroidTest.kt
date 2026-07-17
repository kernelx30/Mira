package com.ai.assistance.operit.api.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoubaoVoiceProviderAndroidTest {
    @Test
    fun v3SpeechRateUsesDoubaoIntegerScale() {
        assertEquals(-50, DoubaoVoiceProvider.rateRatioToV3SpeechRate(0.5f))
        assertEquals(0, DoubaoVoiceProvider.rateRatioToV3SpeechRate(1f))
        assertEquals(50, DoubaoVoiceProvider.rateRatioToV3SpeechRate(1.5f))
        assertEquals(100, DoubaoVoiceProvider.rateRatioToV3SpeechRate(2f))
    }

    @Test
    fun configuredV3ApiKeySynthesizesAndPlaysAudio() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = SpeechServicesPreferences(context).ttsHttpConfigFlow.first()
        val provider = DoubaoVoiceProvider(context, config)

        try {
            assertTrue(provider.initialize())
            assertTrue(provider.speak("你好", interrupt = true, rate = 1f, pitch = 1f))
        } finally {
            provider.shutdown()
        }
    }

    @Test
    fun jiaochuanVoiceSynthesizesAndPlaysAudio() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = SpeechServicesPreferences(context).ttsHttpConfigFlow.first()
        val provider = DoubaoVoiceProvider(context, config)

        try {
            assertTrue(provider.initialize())
            assertTrue(provider.setVoice("zh_female_jiaochuannv_uranus_bigtts"))
            assertTrue(provider.speak("这是音色试听", interrupt = true, rate = 1f, pitch = 1f))
        } finally {
            provider.shutdown()
        }
    }
}
