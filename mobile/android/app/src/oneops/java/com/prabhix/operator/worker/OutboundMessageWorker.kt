package com.prabhix.operator.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prabhix.operator.data.api.ChatApi
import com.prabhix.operator.data.api.SendMessageRequest
import com.prabhix.operator.data.local.OutboundQueueDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OutboundMessageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val queueDao: OutboundQueueDao,
    private val chatApi: ChatApi,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = queueDao.pending()
        if (pending.isEmpty()) return Result.success()

        var failures = 0
        for (item in pending) {
            try {
                chatApi.sendMessage(
                    item.conversationId,
                    SendMessageRequest(item.body, internal = item.isNote),
                    note = item.isNote,
                    // The queue row's own id doubles as the idempotency key, so a retry after a
                    // partial success (server accepted, response lost) is collapsed server-side.
                    idempotencyKey = item.clientId,
                )
                queueDao.remove(item.clientId)
            } catch (_: Throwable) {
                failures++
            }
        }
        return if (failures == 0) Result.success() else Result.retry()
    }
}
