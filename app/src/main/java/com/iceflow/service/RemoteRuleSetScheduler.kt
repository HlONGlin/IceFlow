package com.iceflow.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.iceflow.data.model.RemoteRoutingRuleSet
import java.util.concurrent.TimeUnit

object RemoteRuleSetScheduler {
    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("routing-rules:" + id)
        WorkManager.getInstance(context).cancelUniqueWork("routing-rules-now:" + id)
    }

    fun schedule(context: Context, set: RemoteRoutingRuleSet) {
        if (!set.enabled || set.updateIntervalHours <= 0) { cancel(context, set.id); return }
        val request = PeriodicWorkRequestBuilder<RemoteRuleSetUpdateWorker>(set.updateIntervalHours.coerceIn(1, 720).toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(EXTRA_ID to set.id))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("routing-rules:" + set.id, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun updateNow(context: Context, id: String) {
        val request = OneTimeWorkRequestBuilder<RemoteRuleSetUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(EXTRA_ID to id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("routing-rules-now:" + id, ExistingWorkPolicy.REPLACE, request)
    }

    const val EXTRA_ID = "routing_rule_set_id"
}
