package com.ai.assistance.operit.core.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.data.preferences.CompanionReminderPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MiraScheduleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTORE_ACTIONS) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CompanionReminderScheduler.getInstance(appContext).syncAllProfiles()
                val keepAlive =
                    CompanionReminderPreferences.getInstance(appContext).keepAliveEnabledFlow.first()
                if (keepAlive) {
                    MiraCompanionService.startKeepAlive(appContext)
                }
            } catch (error: Exception) {
                AppLogger.e(TAG, "Failed to restore local companion scheduling", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MiraScheduleRestore"
        private val RESTORE_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
