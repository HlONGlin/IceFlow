package com.iceflow.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.iceflow.data.model.Subscription

object SubscriptionScheduler {
    fun cancel(context: Context, id: String) {
        val intent = Intent(context, SubscriptionUpdateReceiver::class.java).setAction(SubscriptionUpdateReceiver.ACTION)
        val pending = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    fun schedule(context: Context, subscription: Subscription) {
<<<<<<< Updated upstream
        // 间隔为 0 表示不自动更新：清掉已注册的闹钟即可。
        if (subscription.updateIntervalHours <= 0) { cancel(context, subscription.id); return }
=======
        // 如果更新间隔为0，则取消自动更新
        if (subscription.updateIntervalHours == 0) {
            cancel(context, subscription.id)
            return
        }
        
>>>>>>> Stashed changes
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, SubscriptionUpdateReceiver::class.java)
            .setAction(SubscriptionUpdateReceiver.ACTION)
            .putExtra(SubscriptionUpdateReceiver.EXTRA_ID, subscription.id)
        val pending = PendingIntent.getBroadcast(
            context,
            subscription.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val interval = subscription.updateIntervalHours.coerceIn(1, 720) * 60L * 60L * 1000L
        alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + interval, interval, pending)
    }
}
