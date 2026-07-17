package com.ai.assistance.operit.core.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.util.AppLogger

class MiraAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MiraCompanionContract.ACTION_DELIVER) return
        val profileId =
            intent.getStringExtra(MiraCompanionContract.EXTRA_PROFILE_ID)?.takeIf { it.isNotBlank() }
                ?: return
        val memoryUuid =
            intent.getStringExtra(MiraCompanionContract.EXTRA_MEMORY_UUID)?.takeIf { it.isNotBlank() }
                ?: return

        if (!MiraCompanionService.startDelivery(context, profileId, memoryUuid)) {
            AppLogger.w(TAG, "Foreground delivery start was rejected; using WorkManager compensation")
            CompanionReminderScheduler.getInstance(context)
                .enqueueImmediateCompensation(profileId, memoryUuid)
        }
    }

    companion object {
        private const val TAG = "MiraAlarmReceiver"
    }
}
