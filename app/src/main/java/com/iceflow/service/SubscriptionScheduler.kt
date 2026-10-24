package com.iceflow.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.iceflow.data.model.Subscription
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object SubscriptionScheduler {
    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("subscription:$id")
        cancelLegacyAlarm(context, id)
    }

    private fun cancelLegacyAlarm(context: Context, id: String) {
        val intent = Intent(context, SubscriptionUpdateReceiver::class.java).setAction(SubscriptionUpdateReceiver.ACTION)
        val pending = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    fun schedule(context: Context, subscription: Subscription) {
        // 间隔为 0 表示不自动更新：清掉已注册的闹钟即可。
        if (subscription.updateIntervalHours <= 0) { cancel(context, subscription.id); return }
        cancelLegacyAlarm(context, subscription.id)
        val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(subscription.updateIntervalHours.coerceIn(1, 720).toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(SubscriptionUpdateReceiver.EXTRA_ID to subscription.id))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("subscription:${subscription.id}", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
