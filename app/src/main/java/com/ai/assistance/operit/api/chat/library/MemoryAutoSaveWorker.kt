package com.ai.assistance.operit.api.chat.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MemoryAutoSaveWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            val scheduler = MemoryAutoSaveScheduler(applicationContext, workerScope)
            val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
            val chatId = inputData.getString(KEY_CHAT_ID).orEmpty()
            if (profileId.isNotBlank() && chatId.isNotBlank()) {
                if (scheduler.flushBeforeCompaction(profileId, chatId)) Result.success() else Result.retry()
            } else {
                scheduler.runOnce()
                Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.e("MemoryAutoSaveWorker", "Background memory extraction failed", error)
            Result.retry()
        } finally {
            workerScope.cancel()
        }
    }

    companion object {
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_CHAT_ID = "chat_id"
    }
}

object MemoryAutoSaveWorkScheduler {
    private const val UNIQUE_WORK_NAME = "mira_companion_memory_extraction"

    fun ensureScheduled(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<MemoryAutoSaveWorker>(15, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }

    fun requestImmediate(context: Context, profileId: String, chatId: String) {
        if (profileId.isBlank() || chatId.isBlank()) return
        val request =
            OneTimeWorkRequestBuilder<MemoryAutoSaveWorker>()
                .setInputData(
                    workDataOf(
                        MemoryAutoSaveWorker.KEY_PROFILE_ID to profileId,
                        MemoryAutoSaveWorker.KEY_CHAT_ID to chatId,
                    )
                )
                .build()
        val workName =
            "mira_memory_flush_" +
                UUID.nameUUIDFromBytes("$profileId::$chatId".toByteArray(Charsets.UTF_8))
                    .toString()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
    }
}
