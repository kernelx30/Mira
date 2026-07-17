package com.ai.assistance.operit.core.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.ForegroundServiceCompat
import com.ai.assistance.operit.data.preferences.CompanionReminderPreferences
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MiraCompanionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = AtomicInteger(0)
    private lateinit var preferences: CompanionReminderPreferences
    @Volatile private var keepAliveEnabled = false

    override fun onCreate() {
        super.onCreate()
        isRunning.set(true)
        preferences = CompanionReminderPreferences.getInstance(applicationContext)
        createNotificationChannel()
        ForegroundServiceCompat.startForeground(
            service = this,
            notificationId = NOTIFICATION_ID,
            notification = createNotification(),
            types = ForegroundServiceCompat.buildTypes(dataSync = false, specialUse = true),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MiraCompanionContract.ACTION_DISABLE_KEEP_ALIVE -> {
                launchTracked {
                    preferences.saveKeepAliveEnabled(false)
                    keepAliveEnabled = false
                }
                return START_NOT_STICKY
            }

            MiraCompanionContract.ACTION_DELIVER -> {
                val profileId =
                    intent.getStringExtra(MiraCompanionContract.EXTRA_PROFILE_ID)
                        ?.takeIf { it.isNotBlank() }
                val memoryUuid =
                    intent.getStringExtra(MiraCompanionContract.EXTRA_MEMORY_UUID)
                        ?.takeIf { it.isNotBlank() }
                if (profileId != null && memoryUuid != null) {
                    launchTracked {
                        when (
                            MiraCompanionDelivery.deliver(
                                context = applicationContext,
                                profileId = profileId,
                                memoryUuid = memoryUuid,
                            )
                        ) {
                            MiraCompanionDeliveryResult.WaitingForNetwork ->
                                CompanionReminderScheduler.getInstance(applicationContext)
                                    .enqueueNetworkRecovery(profileId, memoryUuid)
                            MiraCompanionDeliveryResult.Retry ->
                                CompanionReminderScheduler.getInstance(applicationContext)
                                    .enqueueImmediateCompensation(profileId, memoryUuid)
                            else -> Unit
                        }
                    }
                }
            }

            MiraCompanionContract.ACTION_SYNC, null -> {
                if (intent?.action == MiraCompanionContract.ACTION_SYNC) {
                    keepAliveEnabled = true
                }
                launchTracked {
                    CompanionReminderScheduler.getInstance(applicationContext).syncAllProfiles()
                }
            }
        }
        return if (keepAliveEnabled || intent == null) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning.set(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun launchTracked(block: suspend () -> Unit) {
        activeJobs.incrementAndGet()
        serviceScope.launch {
            try {
                block()
            } catch (error: Exception) {
                AppLogger.e(TAG, "Local companion service task failed", error)
            } finally {
                activeJobs.decrementAndGet()
                stopIfIdle()
            }
        }
    }

    private suspend fun stopIfIdle() {
        keepAliveEnabled = preferences.keepAliveEnabledFlow.first()
        if (keepAliveEnabled || activeJobs.get() > 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_mira_monochrome)
            .setContentTitle(getString(R.string.mira_companion_service_title))
            .setContentText(getString(R.string.mira_companion_service_text))
            .setContentIntent(openAppIntent())
            .addAction(
                0,
                getString(R.string.mira_companion_service_stop),
                disableKeepAliveIntent(),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun disableKeepAliveIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            1,
            Intent(this, MiraCompanionService::class.java).apply {
                action = MiraCompanionContract.ACTION_DISABLE_KEEP_ALIVE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.mira_companion_service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.mira_companion_service_channel_description)
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val TAG = "MiraCompanionService"
        private const val CHANNEL_ID = "mira_companion_service"
        private const val NOTIFICATION_ID = 2401
        val isRunning = AtomicBoolean(false)

        fun startKeepAlive(context: Context): Boolean =
            startForegroundIntent(
                context,
                Intent(context, MiraCompanionService::class.java).apply {
                    action = MiraCompanionContract.ACTION_SYNC
                },
            )

        fun stopKeepAlive(context: Context) {
            if (!isRunning.get()) return
            context.applicationContext.startService(
                Intent(context.applicationContext, MiraCompanionService::class.java).apply {
                    action = MiraCompanionContract.ACTION_DISABLE_KEEP_ALIVE
                }
            )
        }

        fun startDelivery(context: Context, profileId: String, memoryUuid: String): Boolean =
            startForegroundIntent(
                context,
                Intent(context, MiraCompanionService::class.java).apply {
                    action = MiraCompanionContract.ACTION_DELIVER
                    putExtra(MiraCompanionContract.EXTRA_PROFILE_ID, profileId)
                    putExtra(MiraCompanionContract.EXTRA_MEMORY_UUID, memoryUuid)
                },
            )

        private fun startForegroundIntent(context: Context, intent: Intent): Boolean =
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
                true
            } catch (error: Exception) {
                AppLogger.e(TAG, "Unable to start local companion foreground service", error)
                false
            }
    }
}
