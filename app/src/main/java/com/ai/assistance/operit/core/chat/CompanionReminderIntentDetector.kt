package com.ai.assistance.operit.core.chat

object CompanionReminderIntentDetector {
    private val explicitReminderPattern =
        Regex(
            pattern =
                "提醒我|记得提醒|到时候叫我|别让我忘|定个提醒|设个提醒|闹钟|\\bremind me\\b|\\bdon't let me forget\\b|\\bset (?:a )?reminder\\b|\\bwake me\\b",
            option = RegexOption.IGNORE_CASE,
        )
    private val timeReferencePattern =
        Regex(
            pattern =
                "今天|明天|后天|今晚|早上|上午|中午|下午|晚上|周[一二三四五六日天]|星期[一二三四五六日天]|\\d{1,2}(?::\\d{1,2}|点|时)|\\btoday\\b|\\btomorrow\\b|\\btonight\\b|\\bnext (?:week|month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b|\\b\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)\\b",
            option = RegexOption.IGNORE_CASE,
        )
    private val commitmentPattern =
        Regex(
            pattern =
                "要|得|准备|提交|交稿|开会|起床|复习|学习|吃药|喝水|完成|约定|答应|\\bneed to\\b|\\bhave to\\b|\\bmust\\b|\\bpromise\\b|\\bappointment\\b|\\bmeeting\\b|\\bsubmit\\b|\\bfinish\\b|\\btake (?:medicine|medication)\\b",
            option = RegexOption.IGNORE_CASE,
        )

    fun isTimeSensitive(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        return isExplicitReminder(normalized) ||
            (timeReferencePattern.containsMatchIn(normalized) && commitmentPattern.containsMatchIn(normalized))
    }

    fun isExplicitReminder(text: String): Boolean {
        val normalized = text.trim()
        return normalized.isNotEmpty() && explicitReminderPattern.containsMatchIn(normalized)
    }
}
