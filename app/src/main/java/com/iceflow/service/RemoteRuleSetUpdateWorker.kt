package com.iceflow.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.iceflow.IceFlowApp
import kotlinx.coroutines.CancellationException
import java.io.IOException

class RemoteRuleSetUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(RemoteRuleSetScheduler.EXTRA_ID) ?: return Result.failure()
        val container = (applicationContext as IceFlowApp).container
        val settings = container.settings.settings.value
        val set = settings.remoteRuleSets.firstOrNull { it.id == id } ?: return Result.success()
        return try {
            container.routingRuleSets.update(set, settings.remoteRuleSets)
            container.settings.update { current ->
                current.copy(expandedRemoteRules = container.routingRuleSets.loadEnabled(current.remoteRuleSets))
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLog.write(applicationContext, "WARN", "路由规则集 " + set.name + " 更新失败：" + error.message)
            if (error is IOException && runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
