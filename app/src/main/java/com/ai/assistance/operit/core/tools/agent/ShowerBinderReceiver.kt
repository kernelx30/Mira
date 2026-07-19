package com.ai.assistance.operit.core.tools.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.shower.IShowerService
import com.ai.assistance.shower.ShowerBinderContainer
import com.ai.assistance.showerclient.ShowerServerManager

class ShowerBinderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOWER_BINDER_READY) {
            return
        }
        val unknownExtras = intent.extras?.keySet().orEmpty() - ALLOWED_EXTRA_KEYS
        if (unknownExtras.isNotEmpty()) {
            AppLogger.w(TAG, "Rejected Shower Binder handoff with unknown extras: $unknownExtras")
            return
        }
        val container = intent.getParcelableExtra<ShowerBinderContainer>(EXTRA_BINDER_CONTAINER)
        val binder = container?.binder
        val hasExpectedDescriptor =
            runCatching { binder?.interfaceDescriptor == SHOWER_INTERFACE_DESCRIPTOR }.getOrDefault(false)
        if (!hasExpectedDescriptor) {
            AppLogger.w(TAG, "Rejected Shower Binder handoff with invalid interface descriptor")
            return
        }
        if (!ShowerServerManager.consumeExpectedHandshakeToken(intent.getStringExtra(EXTRA_HANDSHAKE_TOKEN))) {
            AppLogger.w(TAG, "Rejected Shower Binder handoff with invalid or replayed challenge")
            return
        }
        val service = binder?.let { IShowerService.Stub.asInterface(it) }
        val alive = service?.asBinder()?.isBinderAlive == true
        AppLogger.d(TAG, "onReceive: service=$service alive=$alive")
        if (alive) {
            ShowerBinderRegistry.setService(service)
        }
    }

    companion object {
        private const val TAG = "ShowerBinderReceiver"
        const val ACTION_SHOWER_BINDER_READY = "com.ai.assistance.operit.action.SHOWER_BINDER_READY"
        const val EXTRA_BINDER_CONTAINER = "binder_container"
        const val EXTRA_HANDSHAKE_TOKEN = "handshake_token"
        private const val SHOWER_INTERFACE_DESCRIPTOR = "com.ai.assistance.shower.IShowerService"
        private val ALLOWED_EXTRA_KEYS = setOf(EXTRA_BINDER_CONTAINER, EXTRA_HANDSHAKE_TOKEN)
    }
}
