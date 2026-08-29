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
data class ThreadSummary(
    val id: String,
    val mailboxId: String,
    val referenceKey: String? = null,
    val subject: String,
    val status: String,
    val priority: String,
    val assigneeUserId: String? = null,
    val customerEmail: String? = null,
    val snippet: String? = null,
    val unreadCount: Int = 0,
    val hasAttachments: Boolean = false,
    val lastMessageAt: String? = null,
    val slaDueAt: String? = null,
    val slaBreachedAt: String? = null,
)

@Serializable
data class MailMessageSummary(
    val id: String,
    val direction: String,
    val fromAddress: String? = null,
    val fromName: String? = null,
    val subject: String? = null,
    val snippet: String? = null,
    val bodyText: String? = null,
    val bodyHtml: String? = null,
    val occurredAt: String,
    val attachmentCount: Int = 0,
)

@Serializable
data class ThreadDetail(
    val thread: ThreadSummary,
    val messages: List<MailMessageSummary> = emptyList(),
)

@Serializable
data class ReplyRequest(
    val to: List<String>,
    val cc: List<String>? = null,
    val subject: String? = null,
    val bodyHtml: String,
    val attachmentIds: List<String>? = null,
)

@Serializable
data class MailAssignRequest(val userId: String? = null, val teamId: String? = null)

@Serializable
data class UpdateThreadRequest(val status: String? = null, val priority: String? = null)

@Serializable
data class TagResponse(val id: String, val slug: String, val name: String, val colour: String? = null)

@Serializable
data class ThreadTagRequest(val tagId: String)

@Serializable
data class MailboxResponse(val id: String, val name: String, val address: String)

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
    val openThreads: Long,
    val avgFirstResponseMinutes: Double,
    val slaBreaches: Long,
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
data class ChartPoint(val date: String, val value: Double)

@Serializable
data class DashboardResponse(
    val kpis: DashboardKpis,
    val recentActivity: List<DashboardActivityItem> = emptyList(),
    val threadsTrend: List<ChartPoint> = emptyList(),
    val responseTimeTrend: List<ChartPoint> = emptyList(),
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
data class MailStreamPayload(
    val type: String,
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

@Serializable
data class TextResult(
    val available: Boolean,
    val text: String,
    val provider: String? = null,
    val model: String? = null,
    val unavailableBecauseNotConfigured: Boolean = false,
)
