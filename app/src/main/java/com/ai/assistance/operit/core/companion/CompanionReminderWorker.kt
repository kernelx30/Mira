package com.ai.assistance.operit.core.companion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CompanionReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val profileId =
            inputData.getString(MiraCompanionContract.EXTRA_PROFILE_ID)?.takeIf { it.isNotBlank() }
                ?: return Result.failure()
        val memoryUuid =
            inputData.getString(MiraCompanionContract.EXTRA_MEMORY_UUID)?.takeIf { it.isNotBlank() }
                ?: return Result.failure()

        return when (
            val result = MiraCompanionDelivery.deliver(
                context = applicationContext,
                profileId = profileId,
                memoryUuid = memoryUuid,
            )
        ) {
            MiraCompanionDeliveryResult.Retry -> Result.retry()
            MiraCompanionDeliveryResult.WaitingForNetwork -> {
                CompanionReminderScheduler.getInstance(applicationContext)
                    .enqueueNetworkRecovery(profileId, memoryUuid)
                Result.success()
            }
            else -> Result.success()
        }
    }
}
