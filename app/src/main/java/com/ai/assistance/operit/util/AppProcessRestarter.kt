package com.ai.assistance.operit.util

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import kotlin.system.exitProcess

object AppProcessRestarter {
    private const val TAG = "AppProcessRestarter"
    private const val RESTART_REQUEST_CODE = 0x4D495241

    fun scheduleAndExit(context: Context, delayMs: Long = 300L): Boolean {
        val appContext = context.applicationContext
        val launchIntent =
            appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        return scheduleIntentAndExit(context, launchIntent, delayMs)
    }

    fun scheduleIntentAndExit(
        context: Context,
        intent: Intent,
        delayMs: Long = 300L,
    ): Boolean {
        val appContext = context.applicationContext
        val launchIntent = Intent(intent).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        )
        return try {
            val flags =
                PendingIntent.FLAG_CANCEL_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_IMMUTABLE
                    } else {
                        0
                    }
            val pendingIntent =
                PendingIntent.getActivity(
                    appContext,
                    RESTART_REQUEST_CODE,
                    launchIntent,
                    flags,
                )
            val alarmManager =
                appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return false
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(100L),
                pendingIntent,
            )
            (context as? Activity)?.finishAffinity()
            Process.killProcess(Process.myPid())
            exitProcess(0)
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to schedule app restart", error)
            false
        }
    }
}
