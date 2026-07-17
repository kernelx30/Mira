package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.SpeechDelivery
import com.ai.assistance.operit.data.model.SpeechDirection
import com.ai.assistance.operit.data.model.SpeechEmotion
import com.ai.assistance.operit.data.model.SpeechPauseStyle

/** Cheap, deterministic fallback used when the model did not emit speech markup. */
object LocalSpeechDirectionResolver {
    fun resolve(
        text: String,
        delivery: SpeechDelivery = SpeechDelivery.CONVERSATION,
    ): SpeechDirection {
        val normalized = text.trim()
        val lower = normalized.lowercase()
        return when {
            containsAny(lower, "晚安", "睡吧", "早点休息", "小声", "轻一点") ->
                SpeechDirection(SpeechEmotion.SOFT, 0.40f, 0.90f, 0.97f, SpeechPauseStyle.DELIBERATE, delivery)
            containsAny(lower, "别怕", "没事", "我在", "抱抱", "慢慢来", "辛苦了") ->
                SpeechDirection(SpeechEmotion.WARM, 0.40f, 0.94f, 0.99f, delivery = delivery)
            containsAny(lower, "担心", "不舒服", "还好吗", "注意身体", "小心") ->
                SpeechDirection(SpeechEmotion.CONCERNED, 0.45f, 0.93f, 0.98f, SpeechPauseStyle.DELIBERATE, delivery)
            containsAny(lower, "嘴硬", "行啊", "被我抓到了", "逗你", "哈哈", "哼") ->
                SpeechDirection(SpeechEmotion.TEASING, 0.55f, 1.03f, 1.03f, SpeechPauseStyle.QUICK, delivery)
            containsAny(lower, "哈哈", "笑死", "太好了", "恭喜", "成功了") ||
                normalized.count { it == '!' || it == '！' } >= 2 ->
                SpeechDirection(SpeechEmotion.EXCITED, 0.65f, 1.08f, 1.06f, SpeechPauseStyle.QUICK, delivery)
            containsAny(lower, "难过", "遗憾", "对不起", "失望", "想哭") ->
                SpeechDirection(SpeechEmotion.SAD, 0.50f, 0.88f, 0.95f, SpeechPauseStyle.DELIBERATE, delivery)
            containsAny(lower, "先把", "必须", "结论", "步骤", "问题在", "直接看", "注意：") ->
                SpeechDirection(SpeechEmotion.FIRM, 0.30f, 0.97f, 0.98f, delivery = delivery)
            containsAny(lower, "玩一个", "试试看", "有意思", "猜猜", "真的假的") ->
                SpeechDirection(SpeechEmotion.PLAYFUL, 0.45f, 1.04f, 1.02f, delivery = delivery)
            else -> SpeechDirection(delivery = delivery)
        }.normalized()
    }

    private fun containsAny(text: String, vararg values: String): Boolean = values.any(text::contains)
}
