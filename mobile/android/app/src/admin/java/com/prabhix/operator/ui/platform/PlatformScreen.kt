package com.prabhix.operator.ui.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhix.operator.data.api.PlatformOverview
import com.prabhix.operator.data.api.TenantSummary
import com.prabhix.operator.data.repository.PlatformRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Statuses the tenant directory can be filtered by, matching the server's enum. */
private val STATUSES = listOf("ACTIVE", "TRIAL", "SUSPENDED", "CANCELLED")

data class PlatformUiState(
    val overview: PlatformOverview? = null,
    val tenants: List<TenantSummary> = emptyList(),
    val status: String? = null,
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PlatformViewModel @Inject constructor(
    private val platformRepository: PlatformRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlatformUiState())
    val state: StateFlow<PlatformUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            // Independent failures. A broken overview should not hide the tenant list, because the
            // list is the part you act on.
            val overview = runCatching { platformRepository.overview() }
            val page = runCatching { platformRepository.tenants(status = _state.value.status) }
            _state.value = _state.value.copy(
                overview = overview.getOrNull() ?: _state.value.overview,
                tenants = page.getOrNull()?.items ?: emptyList(),
                nextCursor = page.getOrNull()?.nextCursor,
                isLoading = false,
                error = listOfNotNull(
                    overview.exceptionOrNull()?.let { "Overview unavailable" },
                    page.exceptionOrNull()?.let { "Tenants unavailable" },
                ).joinToString(" · ").ifBlank { null },
            )
        }
    }

    fun setStatus(status: String?) {
        if (_state.value.status == status) return
        _state.value = _state.value.copy(status = status)
        refresh()
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return
        _state.value = _state.value.copy(isLoadingMore = true)
        viewModelScope.launch {
            runCatching { platformRepository.tenants(status = _state.value.status, cursor = cursor) }
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        tenants = _state.value.tenants + page.items,
                        nextCursor = page.nextCursor,
                        isLoadingMore = false,
                    )
                }
                .onFailure { _state.value = _state.value.copy(isLoadingMore = false) }
        }
    }
}

/**
 * Platform overview and the tenant directory — the whole of the admin app.
 *
 * <p>The smallest useful slice of the web Ops Hub: the counts that say whether anything is wrong,
 * and the directory that says with whom. Leads, subscribers and applications are deliberately left
 * on the web, where reading a form submission is not a phone-sized task.
 *
 * <p>The tenant rows are not tappable, and this is not an omission. Opening a customer means
 * opening the customer's screens, which are the OneOps product's — on the web, staff are handed off
 * to that console with the organization carried across and the access announced. There is nothing
 * for a tap here to lead to, and a phone-sized reimplementation of somebody else's inbox would be a
 * second copy of the product to keep in step with the first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformScreen(viewModel: PlatformViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Platform") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            state.overview?.let { overview ->
                item { SectionHeading("Tenants") }
                item {
                    CountRow(
                        "${overview.tenants.total} total",
                        "${overview.tenants.active} active",
                        "${overview.tenants.trial} trial",
                    )
                }
                item {
                    CountRow(
                        "${overview.tenants.suspended} suspended",
                        "${overview.tenants.cancelled} cancelled",
                        "+${overview.tenants.createdLast30Days} in 30d",
                    )
                }

                item { SectionHeading("Accounts") }
                item {
                    CountRow(
                        "${overview.accounts.total} total",
                        "${overview.accounts.lockedOut} locked out",
                        "${overview.accounts.platformAdmins} staff",
                    )
                }

                item { SectionHeading("Backlogs") }
                item {
                    CountRow(
                        "${overview.queues.mailPending} mail pending",
                        "${overview.queues.mailFailed} mail failed",
                        "${overview.queues.activeSessions} sessions",
                    )
                }

                item { SectionHeading("Last 24 hours") }
                item {
                    CountRow(
                        "${overview.activity.errorsLast24h} errors",
                        "${overview.activity.securityEventsLast24h} security events",
                    )
                }
            } ?: if (state.isLoading) {
                item { CircularProgressIndicator() }
            } else {
                null
            }

            item { SectionHeading("Directory") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.status == null,
                        onClick = { viewModel.setStatus(null) },
                        label = { Text("All") },
                    )
                    STATUSES.forEach { status ->
                        FilterChip(
                            selected = state.status == status,
                            onClick = { viewModel.setStatus(status) },
                            label = { Text(status.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }

            items(state.tenants, key = { it.id }) { tenant ->
                TenantRow(tenant)
            }

            if (state.tenants.isEmpty() && !state.isLoading) {
                item { Text("No organization matches this filter.") }
            }

            state.nextCursor?.let {
                item {
                    TextButton(onClick = { viewModel.loadMore() }, enabled = !state.isLoadingMore) {
                        Text(if (state.isLoadingMore) "Loading…" else "Load more")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun CountRow(vararg values: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            AssistChip(onClick = {}, label = { Text(value) })
        }
    }
}

@Composable
private fun TenantRow(tenant: TenantSummary) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    tenant.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${tenant.status.lowercase()} · ${tenant.memberCount}/${tenant.seatLimit} seats",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
