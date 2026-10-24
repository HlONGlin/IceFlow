package com.iceflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iceflow.IceFlowApp

class SubscriptionUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Migrate alarms left by older versions without doing network work in a receiver.
        val repository = (context.applicationContext as IceFlowApp).container.servers
        repository.store.value.subscriptions.firstOrNull { it.id == intent?.getStringExtra(EXTRA_ID) }
            ?.let { SubscriptionScheduler.schedule(context, it) }
    }

    companion object {
        const val ACTION = "com.iceflow.action.UPDATE_SUBSCRIPTION"
        const val EXTRA_ID = "subscription_id"
    }
}
