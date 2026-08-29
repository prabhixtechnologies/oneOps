package com.prabhix.operator.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.prabhix.operator.data.api.ChatApi
import com.prabhix.operator.data.api.ChatAssignRequest
import com.prabhix.operator.data.api.ConversationSummary
import com.prabhix.operator.data.api.MailApi
import com.prabhix.operator.data.api.ReplyRequest
import com.prabhix.operator.data.api.SendMessageRequest
import com.prabhix.operator.data.api.ThreadSummary
import com.prabhix.operator.data.api.UpdateConversationRequest
import com.prabhix.operator.data.api.VisitorApi
import com.prabhix.operator.data.local.ConversationDao
import com.prabhix.operator.data.local.MailThreadDao
import com.prabhix.operator.data.local.OutboundMessageEntity
import com.prabhix.operator.data.local.OutboundQueueDao
import com.prabhix.operator.data.paging.ConversationPagingSource
import com.prabhix.operator.data.paging.MailThreadPagingSource
import com.prabhix.operator.worker.OutboundFlushScheduler
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The product's repositories: conversations, mail threads, live visitors and the dashboard.
 *
 * <p>In `src/oneops/` rather than the shared source set because the admin app has no screen that
 * could use them, and a Hilt provider reachable from its graph would keep the Retrofit interfaces,
 * the Room DAOs and the paging sources in the staff APK.
 */

@Singleton
class ChatRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val conversationDao: ConversationDao,
    private val outboundQueueDao: OutboundQueueDao,
    private val outboundFlushScheduler: OutboundFlushScheduler,
) {
    fun conversations(queue: String): Flow<PagingData<ConversationSummary>> = Pager(
        config = PagingConfig(pageSize = 25, prefetchDistance = 10),
        pagingSourceFactory = { ConversationPagingSource(chatApi, queue, conversationDao) },
    ).flow

    suspend fun counts() = chatApi.counts()
    suspend fun detail(id: String) = chatApi.getConversation(id)
    suspend fun cannedReplies() = chatApi.cannedReplies()
    suspend fun assign(conversationId: String, agentId: String) =
        chatApi.assign(conversationId, ChatAssignRequest(agentId))

    suspend fun close(conversationId: String) =
        chatApi.update(conversationId, UpdateConversationRequest(status = "CLOSED"))

    suspend fun reopen(conversationId: String) =
        chatApi.update(conversationId, UpdateConversationRequest(status = "OPEN"))

    suspend fun sendMessage(conversationId: String, body: String, note: Boolean) {
        // Minted before the first attempt, not after it fails: a send that timed out may still
        // have reached the server, and only a key shared by the attempt and the queued retry
        // lets the server recognise the retry as a duplicate.
        val clientId = UUID.randomUUID().toString()
        try {
            chatApi.sendMessage(
                conversationId,
                SendMessageRequest(body, internal = note),
                note = note,
                idempotencyKey = clientId,
            )
        } catch (t: Throwable) {
            outboundQueueDao.enqueue(
                OutboundMessageEntity(
                    clientId = clientId,
                    conversationId = conversationId,
                    body = body,
                    isNote = note,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            outboundFlushScheduler.scheduleExpeditedFlush()
            throw t
        }
    }
}

@Singleton
class MailRepository @Inject constructor(
    private val mailApi: MailApi,
    private val mailThreadDao: MailThreadDao,
) {
    fun threads(): Flow<PagingData<ThreadSummary>> = Pager(
        config = PagingConfig(pageSize = 25),
        pagingSourceFactory = { MailThreadPagingSource(mailApi, mailThreadDao) },
    ).flow

    suspend fun detail(id: String) = mailApi.getThread(id)
    suspend fun reply(threadId: String, request: ReplyRequest) = mailApi.reply(threadId, request)
    suspend fun mailboxes() = mailApi.mailboxes()
    suspend fun cannedReplies() = mailApi.cannedReplies()
}

@Singleton
class VisitorRepository @Inject constructor(private val visitorApi: VisitorApi) {
    suspend fun live() = visitorApi.live()
}

@Singleton
class DashboardRepository @Inject constructor(
    private val dashboardApi: com.prabhix.operator.data.api.DashboardApi,
) {
    suspend fun load() = dashboardApi.dashboard()
}
