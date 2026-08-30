package com.prabhix.operator.data.repository

import com.prabhix.operator.data.api.ChatAiApi
import com.prabhix.operator.data.api.DraftSuggestion
import com.prabhix.operator.data.api.RewriteRequest
import com.prabhix.operator.data.api.RewriteResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val chatAiApi: ChatAiApi,
) {
    suspend fun suggestChatReply(conversationId: String): DraftSuggestion =
        chatAiApi.suggestReply(conversationId)

    suspend fun rewriteChatDraft(conversationId: String, draft: String, action: String): RewriteResult =
        chatAiApi.rewrite(conversationId, RewriteRequest(draft = draft, action = action))
}
