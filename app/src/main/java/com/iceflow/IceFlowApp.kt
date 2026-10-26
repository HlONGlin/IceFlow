package com.iceflow

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.iceflow.data.repository.ServerRepository
import com.iceflow.data.repository.SettingsRepository
import com.iceflow.data.repository.UpdatePreferences
import com.iceflow.service.IceFlowVpnService
import com.iceflow.service.SubscriptionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** 简单的手动依赖容器。 */
class AppContainer(context: Context) {
    val servers = ServerRepository(File(context.filesDir, "servers.json"))
    val settings = SettingsRepository(context)
    val updates = UpdatePreferences(context)
    val routingResources = com.iceflow.data.repository.RoutingResources(context)
}

class IceFlowApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.routingResources.prepare() }
                .onFailure { android.util.Log.e("IceFlowApp", "Failed to prepare routing assets", it) }
        }
        container.servers.store.value.subscriptions.forEach { SubscriptionScheduler.schedule(this, it) }
        val channel = NotificationChannel(
            IceFlowVpnService.CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
