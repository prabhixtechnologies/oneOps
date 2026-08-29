package com.prabhix.operator.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.prabhix.operator.BuildConfig
import com.prabhix.operator.data.api.ApiException
import com.prabhix.operator.data.api.AuthApi
import com.prabhix.operator.data.api.ChatApi
import com.prabhix.operator.data.api.ChatAssignRequest
import com.prabhix.operator.data.api.ConversationSummary
import com.prabhix.operator.data.api.EmailRequest
import com.prabhix.operator.data.api.LoginRequest
import com.prabhix.operator.data.api.LogoutRequest
import com.prabhix.operator.data.api.MagicLinkVerifyRequest
import com.prabhix.operator.data.api.MailApi
import com.prabhix.operator.data.api.OrganizationApi
import com.prabhix.operator.data.api.OtpVerifyRequest
import com.prabhix.operator.data.api.ReplyRequest
import com.prabhix.operator.data.api.SendMessageRequest
import com.prabhix.operator.data.api.ThreadSummary
import com.prabhix.operator.data.api.TokenResponse
import com.prabhix.operator.data.api.UpdateConversationRequest
import com.prabhix.operator.data.api.VisitorApi
import com.prabhix.operator.data.auth.ApiErrorParser
import com.prabhix.operator.data.auth.TokenStore
import com.prabhix.operator.data.local.ConversationDao
import com.prabhix.operator.data.local.MailThreadDao
import com.prabhix.operator.data.local.OutboundMessageEntity
import com.prabhix.operator.data.local.OutboundQueueDao
import com.prabhix.operator.data.paging.ConversationPagingSource
import com.prabhix.operator.data.paging.MailThreadPagingSource
import com.prabhix.operator.data.push.PushTokenManager
import com.prabhix.operator.data.sse.RealtimeHub
import com.prabhix.operator.worker.OutboundFlushScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val organizationApi: OrganizationApi,
    private val tokenStore: TokenStore,
    private val pushTokenManager: PushTokenManager,
    private val realtimeHub: RealtimeHub,
    private val json: Json,
) {
    fun session() = tokenStore.session()
    fun hasPermission(p: String) = tokenStore.hasPermission(p)

    suspend fun login(email: String, password: String): Result<Unit> = safeCall {
        val response = authApi.login(
            LoginRequest(
                email = email.trim(),
                password = password,
                deviceId = tokenStore.deviceId(),
                // Names the app as well as the phone. Both apps can be installed at once, and two
                // sessions both labelled "Pixel 7" cannot be told apart when revoking one of them.
                deviceName = "${android.os.Build.MODEL} · ${BuildConfig.APP_LABEL}",
            ),
        )
        completeLogin(response)
    }

    suspend fun requestOtp(email: String) = authApi.requestOtp(EmailRequest(email.trim()))
    suspend fun verifyOtp(email: String, code: String) = safeCall {
        completeLogin(authApi.verifyOtp(OtpVerifyRequest(email.trim(), code.trim())))
    }

    suspend fun requestMagicLink(email: String) = authApi.requestMagicLink(EmailRequest(email.trim()))
    suspend fun verifyMagicLink(token: String) = safeCall {
        completeLogin(authApi.verifyMagicLink(MagicLinkVerifyRequest(token)))
    }

    private suspend fun completeLogin(response: TokenResponse) {
        tokenStore.saveTokens(response)
        val me = authApi.me()
        tokenStore.saveProfile(me.userId, me.email, me.displayName, me.platformAdmin)
        realtimeHub.start()
        pushTokenManager.registerIfPossible()
    }

    suspend fun selectOrganization(orgId: String): Result<Unit> = safeCall {
        val tokens = organizationApi.select(orgId)
        tokenStore.saveTokens(tokens)
        tokenStore.setOrganizationId(orgId)
        realtimeHub.stop()
        realtimeHub.start()
    }

    suspend fun organizations() = organizationApi.list()

    suspend fun logout() {
        runCatching {
            val refresh = tokenStore.session()?.refreshToken
            authApi.logout(LogoutRequest(refresh))
        }
        pushTokenManager.unregisterIfRegistered()
        realtimeHub.stop()
        tokenStore.clear()
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (t: Throwable) {
        Result.failure(wrap(t))
    }

    private fun wrap(t: Throwable): Throwable = when (t) {
        is ApiException -> t
        is HttpException -> {
            ApiErrorParser.parse(json, t.response()?.errorBody()?.string())
                ?: ApiException.Network(t)
        }
        else -> ApiException.Network(t)
    }
}

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
