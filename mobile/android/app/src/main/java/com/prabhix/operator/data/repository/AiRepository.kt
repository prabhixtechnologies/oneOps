package com.prabhix.operator.data.repository

import com.prabhix.operator.data.api.ChatAiApi
import com.prabhix.operator.data.api.DraftSuggestion
import com.prabhix.operator.data.api.MailAiApi
import com.prabhix.operator.data.api.RewriteRequest
import com.prabhix.operator.data.api.RewriteResult
import com.prabhix.operator.data.api.TextResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val chatAiApi: ChatAiApi,
    private val mailAiApi: MailAiApi,
) {
    suspend fun suggestChatReply(conversationId: String): DraftSuggestion =
        chatAiApi.suggestReply(conversationId)

    suspend fun rewriteChatDraft(conversationId: String, draft: String, action: String): RewriteResult =
        chatAiApi.rewrite(conversationId, RewriteRequest(draft = draft, action = action))

    suspend fun suggestMailReply(threadId: String): DraftSuggestion =
        mailAiApi.suggestReply(threadId)

    suspend fun summarizeMailThread(threadId: String): TextResult =
        mailAiApi.summarize(threadId)
}
