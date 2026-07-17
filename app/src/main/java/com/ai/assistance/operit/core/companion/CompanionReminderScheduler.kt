package com.ai.assistance.operit.core.companion

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ai.assistance.operit.data.model.CompanionEventStatus
import com.ai.assistance.operit.data.model.CompanionMemoryMetadata
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.companionMetadata
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CompanionReminderPreferences
import com.ai.assistance.operit.data.preferences.CompanionReminderTarget
import com.ai.assistance.operit.data.preferences.CompanionReminderTargetType
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.util.AppLogger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class CompanionReminderScheduler private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val workManager = WorkManager.getInstance(appContext)

    suspend fun sync(profileId: String, memory: Memory) {
        val metadata = memory.companionMetadata()
        if (
            metadata == null ||
                metadata.status != CompanionEventStatus.PENDING ||
                metadata.eventAtMs == null ||
                metadata.notifiedAtMs != null
        ) {
            cancel(profileId, memory.uuid)
            return
        }
        val settings =
            CompanionReminderPreferences.getInstance(appContext)
                .getSettings(metadata.toReminderTarget())
        if (
            !settings.enabled ||
                !CompanionReminderPolicy.isKindEnabled(metadata.kind, settings.intensity)
        ) {
            cancel(profileId, memory.uuid)
            return
        }
        scheduleAt(
            profileId = profileId,
            memoryUuid = memory.uuid,
            deliveryAtMs = maxOf(metadata.eventAtMs, metadata.nextAttemptAtMs ?: 0L),
        )
    }

    fun scheduleAt(profileId: String, memoryUuid: String, deliveryAtMs: Long) {
        val normalizedAtMs = deliveryAtMs.coerceAtLeast(System.currentTimeMillis() + MIN_TRIGGER_DELAY_MS)
        scheduleAlarm(profileId, memoryUuid, normalizedAtMs)
        scheduleCompensation(profileId, memoryUuid, normalizedAtMs + COMPENSATION_DELAY_MS)
    }

    fun enqueueImmediateCompensation(profileId: String, memoryUuid: String) {
        scheduleCompensation(
            profileId = profileId,
            memoryUuid = memoryUuid,
            runAtMs = System.currentTimeMillis(),
        )
    }

    fun enqueueNetworkRecovery(profileId: String, memoryUuid: String) {
        val request =
            OneTimeWorkRequestBuilder<CompanionReminderWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        MiraCompanionContract.EXTRA_PROFILE_ID to profileId,
                        MiraCompanionContract.EXTRA_MEMORY_UUID to memoryUuid,
                    )
                )
                .addTag(GLOBAL_WORK_TAG)
                .addTag(profileTag(profileId))
                .build()
        workManager.enqueueUniqueWork(
            networkWorkName(profileId, memoryUuid),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(profileId: String, memoryUuid: String) {
        alarmManager.cancel(alarmPendingIntent(profileId, memoryUuid))
        workManager.cancelUniqueWork(workName(profileId, memoryUuid))
        workManager.cancelUniqueWork(networkWorkName(profileId, memoryUuid))
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun syncProfile(profileId: String) {
        MemoryRepository(appContext, profileId).getCompanionMemories().forEach { sync(profileId, it) }
    }

    suspend fun syncAllProfiles() {
        preferencesManager.profileListFlow.first().forEach { syncProfile(it) }
    }

    private fun scheduleAlarm(profileId: String, memoryUuid: String, deliveryAtMs: Long) {
        val pendingIntent = alarmPendingIntent(profileId, memoryUuid)
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    deliveryAtMs,
                    pendingIntent,
                )
                return
            } catch (error: SecurityException) {
                AppLogger.w(TAG, "Exact alarm permission changed; falling back to inexact alarm", error)
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deliveryAtMs, pendingIntent)
    }

    private fun scheduleCompensation(profileId: String, memoryUuid: String, runAtMs: Long) {
        val delayMs = (runAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val request =
            OneTimeWorkRequestBuilder<CompanionReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        MiraCompanionContract.EXTRA_PROFILE_ID to profileId,
                        MiraCompanionContract.EXTRA_MEMORY_UUID to memoryUuid,
                    )
                )
                .addTag(GLOBAL_WORK_TAG)
                .addTag(profileTag(profileId))
                .build()
        workManager.enqueueUniqueWork(
            workName(profileId, memoryUuid),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun alarmPendingIntent(profileId: String, memoryUuid: String): PendingIntent {
        val data =
            Uri.Builder()
                .scheme("mira")
                .authority("companion-event")
                .appendPath(profileId)
                .appendPath(memoryUuid)
                .build()
        val intent =
            Intent(appContext, MiraAlarmReceiver::class.java).apply {
                action = MiraCompanionContract.ACTION_DELIVER
                this.data = data
                putExtra(MiraCompanionContract.EXTRA_PROFILE_ID, profileId)
                putExtra(MiraCompanionContract.EXTRA_MEMORY_UUID, memoryUuid)
            }
        return PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun workName(profileId: String, memoryUuid: String): String =
        "mira_companion_reminder_${stableKey(profileId)}_${stableKey(memoryUuid)}"

    private fun networkWorkName(profileId: String, memoryUuid: String): String =
        "mira_companion_network_${stableKey(profileId)}_${stableKey(memoryUuid)}"

    private fun profileTag(profileId: String): String =
        "mira_companion_profile_${stableKey(profileId)}"

    private fun CompanionMemoryMetadata.toReminderTarget(): CompanionReminderTarget =
        if (characterGroupId.isNotBlank()) {
            CompanionReminderTarget(CompanionReminderTargetType.GROUP, characterGroupId)
        } else {
            CompanionReminderTarget(
                CompanionReminderTargetType.CHARACTER,
                characterId.ifBlank { CharacterCardManager.DEFAULT_CHARACTER_CARD_ID },
            )
        }

    private fun stableKey(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        private const val TAG = "CompanionReminderScheduler"
        private const val GLOBAL_WORK_TAG = "mira_companion_reminders"
        private const val MIN_TRIGGER_DELAY_MS = 1_000L
        private const val COMPENSATION_DELAY_MS = 15 * 60 * 1_000L

        @Volatile private var instance: CompanionReminderScheduler? = null

        fun getInstance(context: Context): CompanionReminderScheduler =
            instance ?: synchronized(this) {
                instance ?: CompanionReminderScheduler(context).also { instance = it }
            }
    }
}
