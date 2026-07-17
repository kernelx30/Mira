package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.companionReminderDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "companion_reminder_settings")

private val COMPANION_KEEP_ALIVE_ENABLED = booleanPreferencesKey("companion_keep_alive_enabled")

enum class CompanionReminderTargetType(val storageValue: String) {
    CHARACTER("character"),
    GROUP("group"),
}

data class CompanionReminderTarget(
    val type: CompanionReminderTargetType,
    val id: String,
)

enum class CompanionReminderIntensity(val storageValue: String) {
    EXPLICIT_ONLY("explicit_only"),
    OCCASIONAL("occasional"),
    DAILY("daily");

    companion object {
        fun fromStorageValue(value: String?): CompanionReminderIntensity =
            entries.firstOrNull { it.storageValue == value } ?: EXPLICIT_ONLY
    }
}

data class CompanionReminderSettings(
    val enabled: Boolean = true,
    val intensity: CompanionReminderIntensity = CompanionReminderIntensity.EXPLICIT_ONLY,
    val quietHoursEnabled: Boolean = true,
    val quietStartMinutes: Int = 23 * 60,
    val quietEndMinutes: Int = 8 * 60,
    val dailyLimit: Int = 3,
)

class CompanionReminderPreferences private constructor(private val context: Context) {

    val keepAliveEnabledFlow: Flow<Boolean> =
        context.companionReminderDataStore.data.map { values ->
            values[COMPANION_KEEP_ALIVE_ENABLED] ?: false
        }

    suspend fun saveKeepAliveEnabled(value: Boolean) {
        context.companionReminderDataStore.edit { it[COMPANION_KEEP_ALIVE_ENABLED] = value }
    }

    fun settingsFlow(target: CompanionReminderTarget): Flow<CompanionReminderSettings> {
        val keys = keysFor(target)
        return context.companionReminderDataStore.data.map { values ->
            CompanionReminderSettings(
                enabled = values[keys.enabled] ?: true,
                intensity = CompanionReminderIntensity.fromStorageValue(values[keys.intensity]),
                quietHoursEnabled = values[keys.quietHoursEnabled] ?: true,
                quietStartMinutes = (values[keys.quietStartMinutes] ?: 23 * 60).coerceIn(0, 1439),
                quietEndMinutes = (values[keys.quietEndMinutes] ?: 8 * 60).coerceIn(0, 1439),
                dailyLimit = (values[keys.dailyLimit] ?: 3).coerceIn(1, 8),
            )
        }
    }

    suspend fun getSettings(target: CompanionReminderTarget): CompanionReminderSettings =
        settingsFlow(target).first()

    suspend fun saveEnabled(target: CompanionReminderTarget, value: Boolean) {
        context.companionReminderDataStore.edit { it[keysFor(target).enabled] = value }
    }

    suspend fun saveIntensity(target: CompanionReminderTarget, value: CompanionReminderIntensity) {
        context.companionReminderDataStore.edit { it[keysFor(target).intensity] = value.storageValue }
    }

    suspend fun saveQuietHoursEnabled(target: CompanionReminderTarget, value: Boolean) {
        context.companionReminderDataStore.edit { it[keysFor(target).quietHoursEnabled] = value }
    }

    suspend fun saveQuietHours(
        target: CompanionReminderTarget,
        startMinutes: Int,
        endMinutes: Int,
    ) {
        val keys = keysFor(target)
        context.companionReminderDataStore.edit { values ->
            values[keys.quietStartMinutes] = startMinutes.coerceIn(0, 1439)
            values[keys.quietEndMinutes] = endMinutes.coerceIn(0, 1439)
        }
    }

    suspend fun saveDailyLimit(target: CompanionReminderTarget, value: Int) {
        context.companionReminderDataStore.edit { it[keysFor(target).dailyLimit] = value.coerceIn(1, 8) }
    }

    suspend fun getNotificationsSentToday(
        target: CompanionReminderTarget,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val keys = keysFor(target)
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toString()
        val values = context.companionReminderDataStore.data.first()
        return if (values[keys.budgetDate] == today) values[keys.budgetCount] ?: 0 else 0
    }

    suspend fun recordNotificationSent(
        target: CompanionReminderTarget,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        val keys = keysFor(target)
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toString()
        context.companionReminderDataStore.edit { values ->
            val currentCount = if (values[keys.budgetDate] == today) values[keys.budgetCount] ?: 0 else 0
            values[keys.budgetDate] = today
            values[keys.budgetCount] = currentCount + 1
        }
    }

    private fun keysFor(target: CompanionReminderTarget): ReminderKeys {
        val normalizedId = target.id.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        val prefix = "${target.type.storageValue}_${normalizedId}_"
        return ReminderKeys(
            enabled = booleanPreferencesKey("${prefix}enabled"),
            intensity = stringPreferencesKey("${prefix}intensity"),
            quietHoursEnabled = booleanPreferencesKey("${prefix}quiet_hours_enabled"),
            quietStartMinutes = intPreferencesKey("${prefix}quiet_start_minutes"),
            quietEndMinutes = intPreferencesKey("${prefix}quiet_end_minutes"),
            dailyLimit = intPreferencesKey("${prefix}daily_limit"),
            budgetDate = stringPreferencesKey("${prefix}budget_date"),
            budgetCount = intPreferencesKey("${prefix}budget_count"),
        )
    }

    private data class ReminderKeys(
        val enabled: Preferences.Key<Boolean>,
        val intensity: Preferences.Key<String>,
        val quietHoursEnabled: Preferences.Key<Boolean>,
        val quietStartMinutes: Preferences.Key<Int>,
        val quietEndMinutes: Preferences.Key<Int>,
        val dailyLimit: Preferences.Key<Int>,
        val budgetDate: Preferences.Key<String>,
        val budgetCount: Preferences.Key<Int>,
    )

    companion object {
        @Volatile private var instance: CompanionReminderPreferences? = null

        fun getInstance(context: Context): CompanionReminderPreferences =
            instance ?: synchronized(this) {
                instance ?: CompanionReminderPreferences(context.applicationContext).also { instance = it }
            }
    }
}
