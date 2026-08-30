package com.prabhix.operator.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The product's HTTP surface: conversations, visitors, the dashboard and the AI helpers.
 *
 * In `src/oneops/` so the admin APK does not contain it. These paths are tenant-scoped, and the
 * staff app calls only the two cross-tenant endpoints under `/admin/platform`.
 */
interface ChatApi {
    @GET("chat/conversations")
    suspend fun listConversations(
        @Query("queue") queue: String? = null,
        @Query("status") status: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): CursorPage<ConversationSummary>

    @GET("chat/conversations/counts")
    suspend fun counts(): ChatInboxCounts

    @GET("chat/conversations/{id}")
    suspend fun getConversation(@Path("id") id: String): ConversationDetail

    @GET("chat/conversations/{id}/messages")
    suspend fun listMessages(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): CursorPage<MessageView>

    /**
     * [idempotencyKey] must be stable across retries of the same composed message, otherwise a
     * queued send that is retried after a flaky network posts the message twice.
     */
    @POST("chat/conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") id: String,
        @Body body: SendMessageRequest,
        @Query("note") note: Boolean = false,
        @Header("Idempotency-Key") idempotencyKey: String? = null,
    ): MessageView

    @POST("chat/conversations/{id}/assign")
    suspend fun assign(@Path("id") id: String, @Body body: ChatAssignRequest): ConversationSummary

    @PATCH("chat/conversations/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UpdateConversationRequest,
    ): ConversationSummary

    @GET("chat/canned-replies")
    suspend fun cannedReplies(): List<CannedReplyView>
}

interface VisitorApi {
    @GET("visitors/live")
    suspend fun live(): List<LiveVisitor>
}

interface DashboardApi {
    @GET("dashboard")
    suspend fun dashboard(): DashboardResponse
}

interface PushApi {
    @POST("devices/push-tokens")
    suspend fun registerPushToken(@Body body: PushTokenRequest)

    @DELETE("devices/push-tokens/{token}")
    suspend fun unregisterPushToken(@Path("token") token: String)
}

interface ChatAiApi {
    @POST("chat/conversations/{id}/ai/reply/suggest")
    suspend fun suggestReply(@Path("id") id: String): DraftSuggestion

    @POST("chat/conversations/{id}/ai/rewrite")
    suspend fun rewrite(@Path("id") id: String, @Body body: RewriteRequest): RewriteResult
}
