package com.ai.assistance.operit.util

import android.content.Context
import android.content.Intent
import android.os.Process
import com.ai.assistance.operit.ui.error.CrashReportActivity
import kotlin.system.exitProcess

class GlobalExceptionHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        val stackTrace = ThrowableTextFormatter.format(ex)

        val intent =
                Intent(context, CrashReportActivity::class.java).apply {
                    putExtra(CrashReportActivity.EXTRA_STACK_TRACE, stackTrace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
        if (AppProcessRestarter.scheduleIntentAndExit(context, intent, delayMs = 350L)) {
            return
        }

        // 闹钟服务不可用时仍尽量展示崩溃页；只有启动失败才强制结束进程。
        try {
            context.startActivity(intent)
        } catch (startError: Throwable) {
            AppLogger.e("GlobalExceptionHandler", "Failed to launch crash report", startError)
        } finally {
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }
}
