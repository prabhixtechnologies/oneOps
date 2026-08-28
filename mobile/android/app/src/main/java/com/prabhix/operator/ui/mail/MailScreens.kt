package com.prabhix.operator.ui.mail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.compose.collectAsLazyPagingItems
import com.prabhix.operator.data.api.CannedReplyView
import com.prabhix.operator.data.api.MailMessageSummary
import com.prabhix.operator.data.api.ReplyRequest
import com.prabhix.operator.data.api.ThreadSummary
import com.prabhix.operator.data.repository.AiRepository
import com.prabhix.operator.data.repository.AuthRepository
import com.prabhix.operator.data.repository.MailRepository
import com.prabhix.operator.data.sse.RealtimeEvent
import com.prabhix.operator.data.sse.RealtimeHub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MailInboxViewModel @Inject constructor(
    private val mailRepository: MailRepository,
    private val realtimeHub: RealtimeHub,
) : ViewModel() {
    val threads = mailRepository.threads()

    private val _refreshSignal = MutableStateFlow(0)
    val refreshSignal: StateFlow<Int> = _refreshSignal.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    init {
        viewModelScope.launch {
            realtimeHub.events.collect { event ->
                when (event) {
                    is RealtimeEvent.Connection -> _connected.value = event.connected
                    is RealtimeEvent.Mail -> _refreshSignal.value++
                    else -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailInboxScreen(onOpenThread: (String) -> Unit, viewModel: MailInboxViewModel = hiltViewModel()) {
    val items = viewModel.threads.collectAsLazyPagingItems()
    val refreshSignal by viewModel.refreshSignal.collectAsState()
    val connected by viewModel.connected.collectAsState()

    LaunchedEffect(refreshSignal) {
        if (refreshSignal > 0) items.refresh()
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text("Mail")
                Text(
                    if (connected) "Live" else "Reconnecting…",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                )
            }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(items.itemCount) { index ->
                val thread = items[index] ?: return@items
                ThreadRow(thread) { onOpenThread(thread.id) }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: ThreadSummary, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(thread.subject, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(thread.snippet ?: thread.customerEmail ?: "", maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (thread.slaBreachedAt != null) {
            Text("SLA breached", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

data class MailDetailUiState(
    val subject: String = "",
    val messages: List<MailMessageSummary> = emptyList(),
    val customerEmail: String? = null,
    val loading: Boolean = true,
    val sending: Boolean = false,
    val error: String? = null,
    val syncNote: String? = null,
    val aiNote: String? = null,
    val summary: String? = null,
    val summaryLoading: Boolean = false,
    val aiLoading: Boolean = false,
    val cannedReplies: List<CannedReplyView> = emptyList(),
    val suggestedDraft: String? = null,
)

@HiltViewModel
class MailDetailViewModel @Inject constructor(
    private val mailRepository: MailRepository,
    private val authRepository: AuthRepository,
    private val aiRepository: AiRepository,
    private val realtimeHub: RealtimeHub,
) : ViewModel() {
    private val _state = MutableStateFlow(MailDetailUiState())
    val state: StateFlow<MailDetailUiState> = _state.asStateFlow()
    private var threadId: String = ""
    private var subscribed = false

    fun load(id: String) {
        threadId = id
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { mailRepository.detail(id) }
                .onSuccess { detail ->
                    _state.value = _state.value.copy(
                        subject = detail.thread.subject,
                        messages = detail.messages,
                        customerEmail = detail.thread.customerEmail,
                        loading = false,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                }
        }
        if (_state.value.cannedReplies.isEmpty()) {
            viewModelScope.launch {
                runCatching { mailRepository.cannedReplies() }
                    .onSuccess { replies ->
                        _state.value = _state.value.copy(cannedReplies = replies)
                    }
            }
        }
        if (!subscribed) {
            subscribed = true
            viewModelScope.launch {
                realtimeHub.events.collect { event ->
                    if (event is RealtimeEvent.Mail) {
                        val eventThreadId = event.payload.payload?.get("threadId")
                        if (eventThreadId == null || eventThreadId == threadId) {
                            reload(threadId)
                        }
                    }
                }
            }
        }
    }

    private fun reload(id: String) {
        viewModelScope.launch {
            runCatching { mailRepository.detail(id) }.onSuccess { detail ->
                _state.value = _state.value.copy(
                    subject = detail.thread.subject,
                    messages = detail.messages,
                    customerEmail = detail.thread.customerEmail,
                    loading = false,
                )
            }
        }
    }

    fun reply(body: String) {
        val to = listOfNotNull(_state.value.customerEmail)
        if (to.isEmpty()) {
            _state.value = _state.value.copy(error = "No recipient address on this thread")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, error = null, syncNote = null)
            runCatching {
                mailRepository.reply(
                    threadId,
                    ReplyRequest(to = to, bodyHtml = plainTextToHtml(body)),
                )
            }.onSuccess {
                _state.value = _state.value.copy(sending = false)
                reload(threadId)
            }.onFailure {
                _state.value = _state.value.copy(
                    sending = false,
                    error = it.message ?: "Could not send reply",
                )
            }
        }
    }

    fun suggestReply() {
        viewModelScope.launch {
            _state.value = _state.value.copy(aiLoading = true, aiNote = null)
            runCatching { aiRepository.suggestMailReply(threadId) }
                .onSuccess { result ->
                    if (result.available && result.draft.isNotBlank()) {
                        _state.value = _state.value.copy(
                            aiLoading = false,
                            suggestedDraft = result.draft,
                        )
                    } else {
                        _state.value = _state.value.copy(
                            aiLoading = false,
                            aiNote = aiUnavailableMessage(result.unavailableBecauseNotConfigured),
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        aiLoading = false,
                        aiNote = "AI suggestion unavailable right now",
                    )
                }
        }
    }

    fun clearSuggestedDraft() {
        _state.value = _state.value.copy(suggestedDraft = null)
    }

    fun summarize() {
        viewModelScope.launch {
            _state.value = _state.value.copy(summaryLoading = true, aiNote = null)
            runCatching { aiRepository.summarizeMailThread(threadId) }
                .onSuccess { result ->
                    _state.value = if (result.available && result.text.isNotBlank()) {
                        _state.value.copy(summary = result.text, summaryLoading = false)
                    } else {
                        _state.value.copy(
                            summaryLoading = false,
                            aiNote = aiUnavailableMessage(result.unavailableBecauseNotConfigured),
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        summaryLoading = false,
                        aiNote = "Summary unavailable right now",
                    )
                }
        }
    }

    fun canSend() = authRepository.hasPermission("MAIL_SEND")
    fun canUseAi() = authRepository.hasPermission("AI_USE")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailDetailScreen(threadId: String, viewModel: MailDetailViewModel = hiltViewModel()) {
    var draft by remember { mutableStateOf("") }
    var cannedMenuOpen by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(threadId) { viewModel.load(threadId) }
    LaunchedEffect(state.suggestedDraft) {
        state.suggestedDraft?.let {
            draft = it
            viewModel.clearSuggestedDraft()
        }
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(state.subject, maxLines = 1, overflow = TextOverflow.Ellipsis) }) },
        bottomBar = {
            if (viewModel.canSend()) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.cannedReplies.isNotEmpty()) {
                            AssistChip(
                                onClick = { cannedMenuOpen = true },
                                label = { Text("Canned") },
                            )
                            DropdownMenu(
                                expanded = cannedMenuOpen,
                                onDismissRequest = { cannedMenuOpen = false },
                            ) {
                                state.cannedReplies.forEach { reply ->
                                    DropdownMenuItem(
                                        text = { Text(reply.title) },
                                        onClick = {
                                            draft = if (draft.isBlank()) reply.body else "${draft.trimEnd()}\n\n${reply.body}"
                                            cannedMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        if (viewModel.canUseAi()) {
                            TextButton(
                                onClick = { viewModel.suggestReply() },
                                enabled = !state.aiLoading && !state.sending,
                            ) {
                                if (state.aiLoading) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 4.dp))
                                }
                                Text("Suggest")
                            }
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            placeholder = { Text("Reply…") },
                            minLines = 2,
                            enabled = !state.sending,
                        )
                        IconButton(
                            onClick = {
                                if (draft.isNotBlank()) {
                                    viewModel.reply(draft)
                                    draft = ""
                                }
                            },
                            enabled = !state.sending && draft.isNotBlank(),
                        ) {
                            if (state.sending) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        }
                    }
                    state.syncNote?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    state.aiNote?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (viewModel.canUseAi()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { viewModel.summarize() },
                            enabled = !state.summaryLoading,
                        ) {
                            if (state.summaryLoading) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 4.dp))
                            }
                            Text("Summarize thread")
                        }
                    }
                    state.summary?.let { summary ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("AI summary", style = MaterialTheme.typography.labelMedium)
                                Text(summary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            items(state.messages, key = { it.id }) { msg ->
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        "${msg.fromName ?: msg.fromAddress ?: "Unknown"} · ${msg.direction}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    val body = msg.bodyText ?: stripHtml(msg.bodyHtml)
                    Text(body)
                }
            }
        }
    }
}

private fun plainTextToHtml(text: String): String {
    val escaped = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    return "<p>${escaped.replace("\n", "<br>")}</p>"
}

private fun aiUnavailableMessage(notConfigured: Boolean): String = when {
    notConfigured -> "AI is not configured for this organization"
    else -> "AI is unavailable right now"
}

private fun stripHtml(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return html.replace(Regex("<[^>]+>"), "\n").trim()
}
