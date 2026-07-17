package com.ai.assistance.operit.api.voice

import android.content.Context
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class DoubaoVoiceProvider(
    context: Context,
    private val config: SpeechServicesPreferences.TtsHttpConfig,
) : HttpVoiceProvider(context) {

    override val capabilities: VoiceCapabilities
        get() =
            VoiceCapabilities(
                supportsRate = true,
                supportsPitch = config.modelName.isNotBlank(),
            )

    companion object {
        const val DEFAULT_ENDPOINT_URL = "https://openspeech.bytedance.com/api/v1/tts"
        const val V3_ENDPOINT_URL = "https://openspeech.bytedance.com/api/v3/tts/unidirectional"
        const val DEFAULT_CLUSTER = "volcano_tts"
        const val DEFAULT_VOICE_ID = "zh_female_vv_uranus_bigtts"

        val AVAILABLE_VOICES = DoubaoSeedTts2Voices.all

        private val RESPONSE_PIPELINE =
            listOf(
                HttpTtsResponsePipelineStep(type = HttpTtsResponsePipelineStep.TYPE_PARSE_JSON),
                HttpTtsResponsePipelineStep(
                    type = HttpTtsResponsePipelineStep.TYPE_PICK,
                    path = "data",
                ),
                HttpTtsResponsePipelineStep(type = HttpTtsResponsePipelineStep.TYPE_BASE64_DECODE),
            )

        private val V3_RESPONSE_PIPELINE = listOf(
            HttpTtsResponsePipelineStep(type = HttpTtsResponsePipelineStep.TYPE_JSON_LINES_BASE64_CONCAT),
        )

        internal fun rateRatioToV3SpeechRate(rate: Float): Int =
            ((rate.coerceIn(0.5f, 2f) - 1f) * 100f).roundToInt().coerceIn(-50, 100)
    }

    private val appContext = context.applicationContext
    private var selectedVoiceId: String = config.voiceId
        .takeIf { it.isNotBlank() && it != "BV700_V2_streaming" }
        ?: DEFAULT_VOICE_ID

    override suspend fun initialize(): Boolean {
        setConfiguration(buildHttpConfig())
        return super.initialize()
    }

    override suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float?,
        pitch: Float?,
        extraParams: Map<String, String>,
    ): Boolean {
        setConfiguration(buildHttpConfig())
        val requestVoice = extraParams["voice"]?.takeIf { it.isNotBlank() } ?: selectedVoiceId
        val cluster = extraParams["cluster"]?.takeIf { it.isNotBlank() } ?: DEFAULT_CLUSTER
        val effectiveRate =
            rate ?: SpeechServicesPreferences(appContext).ttsSpeechRateFlow.first()
        val providerParams =
            extraParams +
                mapOf(
                    "voice" to requestVoice,
                    "cluster" to cluster,
                    "doubao_speech_rate" to rateRatioToV3SpeechRate(effectiveRate).toString(),
                )
        return super.speak(
            text = text,
            interrupt = interrupt,
            rate = effectiveRate,
            pitch = pitch,
            extraParams = providerParams,
        )
    }

    override suspend fun getAvailableVoices(): List<VoiceService.Voice> = AVAILABLE_VOICES

    override suspend fun setVoice(voiceId: String): Boolean {
        selectedVoiceId = voiceId.ifBlank { DEFAULT_VOICE_ID }
        return true
    }

    private fun buildHttpConfig(): SpeechServicesPreferences.TtsHttpConfig {
        // New Doubao accounts use a single API Key. Keep App ID + Access Token
        // support for existing configurations created with the legacy console.
        if (config.modelName.isBlank()) {
            return SpeechServicesPreferences.TtsHttpConfig(
                urlTemplate = V3_ENDPOINT_URL,
                apiKey = config.apiKey.trim(),
                headers = config.headers + mapOf(
                    "X-Api-Key" to "{apiKey}",
                    "X-Api-Resource-Id" to "seed-tts-2.0",
                ),
                httpMethod = "POST",
                requestBody = """{"req_params":{"text":"{text}","speaker":"{voice}","audio_params":{"format":"mp3","sample_rate":24000,"speech_rate":{doubao_speech_rate}}}}""",
                contentType = "application/json",
                localeTag = config.localeTag.ifBlank { "zh-CN" },
                voiceId = selectedVoiceId,
                modelName = config.modelName,
                responsePipeline = V3_RESPONSE_PIPELINE,
            )
        }

        return SpeechServicesPreferences.TtsHttpConfig(
            urlTemplate = config.urlTemplate.ifBlank { DEFAULT_ENDPOINT_URL },
            apiKey = config.apiKey.trim(),
            headers = config.headers + mapOf("Authorization" to "Bearer;{apiKey}"),
            httpMethod = "POST",
            requestBody = """{"app":{"appid":"{model}","token":"{apiKey}","cluster":"{cluster}"},"user":{"uid":"operit"},"audio":{"voice_type":"{voice}","encoding":"mp3","speed_ratio":{rate},"pitch_ratio":{pitch}},"request":{"reqid":"{uuid}","text":"{text}","operation":"query"}}""",
            contentType = "application/json",
            localeTag = config.localeTag.ifBlank { "zh-CN" },
            voiceId = selectedVoiceId,
            modelName = config.modelName,
            responsePipeline = RESPONSE_PIPELINE,
        )
    }
}
