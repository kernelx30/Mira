package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date

@Composable
internal fun ChatTimeSeparator(
    timestamp: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val now = remember { System.currentTimeMillis() }
    val label =
        remember(timestamp, now, locale) {
            val zoneId = ZoneId.systemDefault()
            val messageDate = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
            val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
            val timeText = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(timestamp))

            when (messageDate) {
                today -> context.getString(R.string.chat_time_today, timeText)
                today.minusDays(1) -> context.getString(R.string.chat_time_yesterday, timeText)
                else ->
                    if (messageDate.year == today.year) {
                        context.getString(
                            R.string.chat_time_month_day,
                            messageDate.monthValue,
                            messageDate.dayOfMonth,
                            timeText,
                        )
                    } else {
                        context.getString(
                            R.string.chat_time_year_month_day,
                            messageDate.year,
                            messageDate.monthValue,
                            messageDate.dayOfMonth,
                            timeText,
                        )
                    }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
    }
}
