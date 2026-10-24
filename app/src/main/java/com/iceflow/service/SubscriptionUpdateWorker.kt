package com.iceflow.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iceflow.IceFlowApp
import com.iceflow.data.ShareLinkParser
import com.iceflow.data.repository.SubscriptionFetcher
import kotlinx.coroutines.CancellationException
import java.io.IOException

class SubscriptionUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(SubscriptionUpdateReceiver.EXTRA_ID) ?: return Result.failure()
        val repository = (applicationContext as IceFlowApp).container.servers
        val subscription = repository.store.value.subscriptions.firstOrNull { it.id == id } ?: return Result.success()
        if (subscription.updateIntervalHours <= 0) return Result.success()
        return try {
            val download = SubscriptionFetcher.fetch(subscription.url)
            val servers = ShareLinkParser.parseMany(download.body)
            check(servers.isNotEmpty()) { "订阅中没有可识别的节点" }
            repository.upsertSubscription(subscription.url, subscription.name, servers, subscription.id, download.userInfo, download.downloadedBytes)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.write(applicationContext, "WARN", "订阅 ${subscription.name} 自动更新失败：${error.message}")
            if (error is IOException && runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
