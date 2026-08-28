package com.prabhix.operator.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): TokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest)

    @GET("auth/me")
    suspend fun me(): AuthMeResponse

    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: EmailRequest): AckResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequest): TokenResponse

    @POST("auth/magic-link/request")
    suspend fun requestMagicLink(@Body body: EmailRequest): AckResponse

    @POST("auth/magic-link/verify")
    suspend fun verifyMagicLink(@Body body: MagicLinkVerifyRequest): TokenResponse
}

interface OrganizationApi {
    @GET("organizations")
    suspend fun list(): List<OrganizationView>

    @POST("organizations/{id}/select")
    suspend fun select(@Path("id") id: String): TokenResponse
}

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

interface MailApi {
    @GET("mail/threads")
    suspend fun listThreads(
        @Query("mailboxId") mailboxId: String? = null,
        @Query("status") status: String? = null,
        @Query("unreadOnly") unreadOnly: Boolean? = null,
        @Query("q") q: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): CursorPage<ThreadSummary>

    @GET("mail/threads/{id}")
    suspend fun getThread(@Path("id") id: String): ThreadDetail

    @PATCH("mail/threads/{id}")
    suspend fun updateThread(@Path("id") id: String, @Body body: UpdateThreadRequest): ThreadSummary

    @POST("mail/threads/{id}/reply")
    suspend fun reply(@Path("id") id: String, @Body body: ReplyRequest): MailMessageSummary

    @POST("mail/threads/{id}/assign")
    suspend fun assign(@Path("id") id: String, @Body body: MailAssignRequest)

    @POST("mail/threads/{id}/tags")
    suspend fun addTag(@Path("id") id: String, @Body body: ThreadTagRequest)

    @GET("mail/mailboxes")
    suspend fun mailboxes(): List<MailboxResponse>

    @GET("mail/canned-replies")
    suspend fun cannedReplies(): List<CannedReplyView>

    @GET("mail/tags")
    suspend fun tags(): List<TagResponse>
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

interface MailAiApi {
    @POST("mail/threads/{id}/ai/reply/suggest")
    suspend fun suggestReply(@Path("id") id: String): DraftSuggestion

    @POST("mail/threads/{id}/ai/summarize")
    suspend fun summarize(@Path("id") id: String): TextResult
}
