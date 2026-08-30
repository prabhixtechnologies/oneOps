package com.prabhix.operator.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String>? = null,
    val traceId: String? = null,
    val path: String? = null,
)

@Serializable
data class LogoutRequest(val refreshToken: String? = null)

@Serializable
data class AuthMeResponse(
    val userId: String,
    val email: String,
    val displayName: String,
    val organizationId: String? = null,
    val sessionId: String,
    val permissions: Set<String> = emptySet(),
    val platformAdmin: Boolean = false,
)

@Serializable
data class OrganizationView(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
)

@Serializable
data class ChatInboxCounts(val unassigned: Long, @SerialName("mineUnread") val mineUnread: Long)

@Serializable
data class ConversationSummary(
    val id: String,
    val status: String,
    val priority: String,
    val subject: String? = null,
    val visitorName: String? = null,
    val visitorEmail: String? = null,
    val assignedAgentId: String? = null,
    val tags: List<String> = emptyList(),
    val unreadAgentCount: Int = 0,
    val lastMessageAt: String? = null,
    val lastMessagePreview: String? = null,
    val visitorId: String? = null,
)

@Serializable
data class MessageView(
    val id: String,
    val senderType: String,
    val senderUserId: String? = null,
    val body: String,
    val fileId: String? = null,
    val occurredAt: String,
)

@Serializable
data class ConversationDetail(
    val conversation: ConversationSummary,
    val messages: List<MessageView> = emptyList(),
)

@Serializable
data class SendMessageRequest(
    val body: String,
    val fileId: String? = null,
    val internal: Boolean? = null,
)

@Serializable
data class ChatAssignRequest(val agentId: String)

@Serializable
data class UpdateConversationRequest(
    val status: String? = null,
    val priority: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class CannedReplyView(
    val id: String,
    val shortcut: String? = null,
    val title: String,
    val body: String,
)

@Serializable
data class LiveVisitor(
    val visitorId: String,
    val externalKey: String? = null,
    val currentPath: String? = null,
    val currentTitle: String? = null,
    val since: String? = null,
    val email: String? = null,
    val displayName: String? = null,
)

@Serializable
data class DashboardKpis(
    val openConversations: Long = 0,
    val unassignedConversations: Long = 0,
    val visitorsToday: Long = 0,
    val ordersLast30Days: Long = 0,
    val revenueLast30Days: Long = 0,
    val seatsUsed: Int,
    val seatsLimit: Int,
    val mrr: Long,
    val currency: String,
)

@Serializable
data class DashboardActivityItem(
    val id: String,
    val type: String,
    val description: String,
    val actor: String? = null,
    val createdAt: String,
)

@Serializable
data class DashboardResponse(
    val kpis: DashboardKpis,
    val recentActivity: List<DashboardActivityItem> = emptyList(),
)

@Serializable
data class PushTokenRequest(
    val token: String,
    val platform: String,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
)

@Serializable
data class ChatStreamPayload(
    val type: String,
    val conversationId: String? = null,
    val payload: Map<String, String>? = null,
)

@Serializable
data class DraftSuggestion(
    val available: Boolean,
    val draft: String,
    val provider: String? = null,
    val model: String? = null,
    val piiRedacted: Boolean = false,
    val unavailableBecauseNotConfigured: Boolean = false,
)

@Serializable
data class RewriteRequest(
    val draft: String,
    val action: String,
)

@Serializable
data class RewriteResult(
    val available: Boolean,
    val text: String,
    val provider: String? = null,
    val model: String? = null,
)
