package com.iceflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iceflow.IceFlowApp
import com.iceflow.data.ShareLinkParser
import com.iceflow.data.repository.SubscriptionFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SubscriptionUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as IceFlowApp).container.servers
                repository.store.value.subscriptions
                    .filter { it.id == intent?.getStringExtra(EXTRA_ID) }
                    .forEach { subscription ->
                        runCatching {
                            val servers = ShareLinkParser.parseMany(SubscriptionFetcher.fetch(subscription.url))
                            if (servers.isNotEmpty()) repository.upsertSubscription(subscription.url, subscription.name, servers, subscription.id)
                        }
                        repository.store.value.subscriptions.firstOrNull { it.id == subscription.id }?.let { SubscriptionScheduler.schedule(context, it) }
                    }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.iceflow.action.UPDATE_SUBSCRIPTION"
        const val EXTRA_ID = "subscription_id"
    }
}
