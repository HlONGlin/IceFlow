package com.iceflow.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.iceflow.MainActivity
import com.iceflow.R

object VpnErrorNotifier {
    private const val NOTIFICATION_ID = 2

    @SuppressLint("MissingPermission")
    fun show(context: Context, presentation: VpnErrorPresentation) {
        val connectAction = presentation.action == VpnErrorAction.CONNECT || presentation.action == VpnErrorAction.REAUTHORIZE
        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply {
                if (connectAction) putExtra(MainActivity.EXTRA_CONNECT_FROM_TILE, true)
                if (presentation.action == VpnErrorAction.OPEN_LOGS) putExtra(MainActivity.EXTRA_OPEN_LOGS, true)
            }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "${presentation.message}\n${presentation.nextAction}"
        val notification = NotificationCompat.Builder(context, IceFlowVpnService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(presentation.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, presentation.actionLabel, contentIntent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onFailure { AppLog.write(context, "WARN", "VPN failure notification unavailable: ${it.javaClass.simpleName}") }
    }

    fun dismiss(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}
