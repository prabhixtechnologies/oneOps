package com.prabhix.operator.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.prabhix.operator.data.api.MailApi
import com.prabhix.operator.data.api.ThreadSummary
import com.prabhix.operator.data.local.MailThreadDao
import com.prabhix.operator.data.local.MailThreadEntity

class MailThreadPagingSource(
    private val api: MailApi,
    private val dao: MailThreadDao,
) : PagingSource<String, ThreadSummary>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, ThreadSummary> = try {
        val page = api.listThreads(cursor = params.key, limit = params.loadSize.coerceAtMost(50))
        dao.upsertAll(
            page.items.map {
                MailThreadEntity(
                    id = it.id,
                    subject = it.subject,
                    status = it.status,
                    snippet = it.snippet,
                    unreadCount = it.unreadCount,
                    lastMessageAt = it.lastMessageAt,
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

    override fun getRefreshKey(state: PagingState<String, ThreadSummary>): String? = null
}
