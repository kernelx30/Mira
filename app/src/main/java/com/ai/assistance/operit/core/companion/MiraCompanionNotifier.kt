package com.ai.assistance.operit.core.companion

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.util.AppLogger

object MiraCompanionNotifier {
    const val REMINDER_CHANNEL_ID = "mira_companion_reminders_v2"

    fun canNotify(context: Context): Boolean {
        val runtimePermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        memoryUuid: String,
        chatId: String,
        roleName: String,
        body: String,
    ) {
        if (!canNotify(context) || ActivityLifecycleManager.getCurrentActivity() != null) return
        createChannel(context)
        try {
            NotificationManagerCompat.from(context).notify(
                notificationTag(memoryUuid),
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_mira_monochrome)
                    .setContentTitle(roleName.ifBlank { context.getString(R.string.mate_default_companion) })
                    .setContentText(body.take(140))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(contentIntent(context, chatId, memoryUuid))
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build(),
            )
        } catch (error: SecurityException) {
            // Permission can change between the check and notify(); keep the local message intact.
            AppLogger.w("MiraCompanionNotifier", "Notification permission changed before delivery", error)
        }
    }

    private fun contentIntent(context: Context, chatId: String, memoryUuid: String): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = MiraCompanionContract.ACTION_OPEN_REMINDER
                data =
                    Uri.Builder()
                        .scheme("mira")
                        .authority("companion-notification")
                        .appendPath(memoryUuid)
                        .build()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MiraCompanionContract.EXTRA_CHAT_ID, chatId)
                putExtra(MiraCompanionContract.EXTRA_MEMORY_UUID, memoryUuid)
            }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationTag(memoryUuid: String): String = "mira_companion_$memoryUuid"

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.companion_reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.companion_reminder_channel_description)
                setShowBadge(false)
            }
        )
    }

    private const val NOTIFICATION_ID = 2402
}
