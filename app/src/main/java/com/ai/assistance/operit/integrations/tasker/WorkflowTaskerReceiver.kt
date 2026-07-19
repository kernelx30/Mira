package com.ai.assistance.operit.integrations.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.repository.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for receiving workflow trigger requests from Tasker
 * 
 * This receiver allows Tasker to trigger Operit workflows via broadcasts.
 */
class WorkflowTaskerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WorkflowTaskerReceiver"
        const val ACTION_TRIGGER_WORKFLOW = "com.ai.assistance.operit.TRIGGER_WORKFLOW"
        const val EXTRA_CONFIRMED = "confirmed"
        private const val MAX_EXTRA_COUNT = 32
        private const val MAX_EXTRA_VALUE_LENGTH = 4096
        private val EXTRA_KEY_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_.-]{0,63}$")
        
        /**
         * Creates an intent to trigger workflows based on intent data.
         * This can be used by other parts of the app or external apps to trigger a check.
         */
        fun createTriggerIntent(context: Context, extras: Bundle? = null): Intent {
            return Intent(ACTION_TRIGGER_WORKFLOW).apply {
                setPackage(context.packageName)
                extras?.let { putExtras(it) }
                putExtra(EXTRA_CONFIRMED, true)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_WORKFLOW) {
            return
        }
        if (!intent.getBooleanExtra(EXTRA_CONFIRMED, false)) {
            AppLogger.w(TAG, "Rejected workflow trigger without explicit confirmation")
            return
        }
        val sanitizedIntent = sanitizeTriggerIntent(context, intent) ?: return

        AppLogger.d(TAG, "Received authenticated workflow trigger broadcast")

        // Use goAsync to allow async work
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = WorkflowRepository(context.applicationContext)
                // New method to find and trigger workflows based on the intent's content (action, extras, etc.)
                repository.triggerWorkflowsByIntentEvent(sanitizedIntent)
                AppLogger.d(TAG, "Finished processing intent trigger.")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error processing intent trigger for workflows", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sanitizeTriggerIntent(context: Context, source: Intent): Intent? {
        val sourceExtras = source.extras
        if (sourceExtras != null && sourceExtras.size() > MAX_EXTRA_COUNT) {
            AppLogger.w(TAG, "Rejected workflow trigger with too many extras")
            return null
        }
        val sanitized = Bundle()
        sourceExtras?.keySet().orEmpty().forEach { key ->
            if (key == EXTRA_CONFIRMED) return@forEach
            if (!EXTRA_KEY_PATTERN.matches(key)) {
                AppLogger.w(TAG, "Rejected workflow trigger extra key: $key")
                return null
            }
            val value = sourceExtras?.get(key)
            val normalized =
                when (value) {
                    null -> ""
                    is String, is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double ->
                        value.toString()
                    else -> {
                        AppLogger.w(TAG, "Rejected workflow trigger extra type for key=$key")
                        return null
                    }
                }
            if (normalized.length > MAX_EXTRA_VALUE_LENGTH) {
                AppLogger.w(TAG, "Rejected oversized workflow trigger extra: $key")
                return null
            }
            sanitized.putString(key, normalized)
        }
        return Intent(ACTION_TRIGGER_WORKFLOW).setPackage(context.packageName).putExtras(sanitized)
    }
}

/**
 * BroadcastReceiver for boot completed event
 * 
 * Re-schedules all enabled workflows after device reboot
 */
class WorkflowBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WorkflowBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        AppLogger.d(TAG, "Device booted, rescheduling workflows")

        // Use goAsync to allow async work
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = WorkflowRepository(context.applicationContext)
                val result = repository.getAllWorkflows()
                
                result.getOrNull()?.forEach { workflow ->
                    if (workflow.enabled) {
                        repository.scheduleWorkflow(workflow.id)
                        AppLogger.d(TAG, "Rescheduled workflow: ${workflow.name}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error rescheduling workflows after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
