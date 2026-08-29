package com.prabhix.operator.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.prabhix.operator.data.api.ChatApi
import com.prabhix.operator.data.api.ConversationSummary
import com.prabhix.operator.data.local.ConversationDao
import com.prabhix.operator.data.local.ConversationEntity

class ConversationPagingSource(
    private val api: ChatApi,
    private val queue: String,
    private val dao: ConversationDao,
) : PagingSource<String, ConversationSummary>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, ConversationSummary> = try {
        val page = api.listConversations(
            queue = queue,
            cursor = params.key,
            limit = params.loadSize.coerceAtMost(50),
        )
        dao.upsertAll(
            page.items.map {
                ConversationEntity(
                    id = it.id,
                    queue = queue,
                    status = it.status,
                    priority = it.priority,
                    subject = it.subject,
                    visitorName = it.visitorName,
                    visitorEmail = it.visitorEmail,
                    assignedAgentId = it.assignedAgentId,
                    unreadAgentCount = it.unreadAgentCount,
                    lastMessageAt = it.lastMessageAt,
                    lastMessagePreview = it.lastMessagePreview,
                    cachedAt = System.currentTimeMillis(),
                )
            },
        )
        LoadResult.Page(
            data = page.items,
            prevKey = null,
            nextKey = if (page.hasMore) page.nextCursor else null,
        )
    } catch (t: Throwable) {
        LoadResult.Error(t)
    }

    override fun getRefreshKey(state: PagingState<String, ConversationSummary>): String? = null
}
