package com.prabhix.operator.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.prabhix.operator.data.api.CannedReplyView
import com.prabhix.operator.data.api.ConversationSummary
import com.prabhix.operator.data.api.MessageView
import com.prabhix.operator.data.repository.AiRepository
import com.prabhix.operator.data.repository.AuthRepository
import com.prabhix.operator.data.repository.ChatRepository
import com.prabhix.operator.data.sse.RealtimeEvent
import com.prabhix.operator.data.sse.RealtimeHub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChatQueue(val apiValue: String) { MINE("mine"), UNASSIGNED("unassigned"), ALL("all") }

data class ChatListUiState(val counts: Pair<Long, Long>? = null, val connected: Boolean = false)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val realtimeHub: RealtimeHub,
) : ViewModel() {
    private val _queue = MutableStateFlow(ChatQueue.MINE)
    val queue: StateFlow<ChatQueue> = _queue.asStateFlow()
    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    val conversations = chatRepository.conversations(_queue.value.apiValue)

    init {
        refreshCounts()
        viewModelScope.launch {
            realtimeHub.events.collect { event ->
                when (event) {
                    is RealtimeEvent.Connection -> _state.value = _state.value.copy(connected = event.connected)
                    is RealtimeEvent.Chat -> refreshCounts()
                    else -> Unit
                }
            }
        }
    }

    fun setQueue(queue: ChatQueue) {
        _queue.value = queue
    }

    fun pagingFlow(queue: ChatQueue) = chatRepository.conversations(queue.apiValue)

    fun canViewAll() = authRepository.hasPermission("CHAT_READ_ALL")

    private fun refreshCounts() {
        viewModelScope.launch {
            runCatching {
                val c = chatRepository.counts()
                _state.value = _state.value.copy(counts = c.unassigned to c.mineUnread)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInboxScreen(
    onOpenConversation: (String) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableStateOf(0) }
    val queues = remember(viewModel.canViewAll()) {
        if (viewModel.canViewAll()) ChatQueue.entries else listOf(ChatQueue.MINE, ChatQueue.UNASSIGNED)
    }
    val queue = queues[selectedTab.coerceIn(0, queues.lastIndex)]
    val pagingItems = viewModel.pagingFlow(queue).collectAsLazyPagingItems()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Chat")
                    Text(
                        if (state.connected) "Live" else "Reconnecting…",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.connected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    )
                }
            })
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                queues.forEachIndexed { index, q ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            when (q) {
                                ChatQueue.UNASSIGNED -> BadgedBox(badge = {
                                    state.counts?.first?.takeIf { it > 0 }?.let { Badge { Text("$it") } }
                                }) { Text("Unassigned") }
                                ChatQueue.MINE -> BadgedBox(badge = {
                                    state.counts?.second?.takeIf { it > 0 }?.let { Badge { Text("$it") } }
                                }) { Text("Mine") }
                                else -> Text("All")
                            }
                        },
                    )
                }
            }
            ConversationList(pagingItems, onOpenConversation)
        }
    }
}

@Composable
private fun ConversationList(
    items: LazyPagingItems<ConversationSummary>,
    onOpen: (String) -> Unit,
) {
    LazyColumn {
        items(items.itemCount) { index ->
            val item = items[index] ?: return@items
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item.id) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.visitorName ?: item.visitorEmail ?: "Visitor", style = MaterialTheme.typography.titleMedium)
                    Text(
                        item.lastMessagePreview ?: item.subject ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (item.unreadAgentCount > 0) {
                    Badge { Text("${item.unreadAgentCount}") }
                }
            }
        }
    }
}

data class ChatDetailUiState(
    val messages: List<MessageView> = emptyList(),
    val visitorName: String = "",
    val status: String = "OPEN",
    val assignedAgentId: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val syncNote: String? = null,
    val operationError: String? = null,
    val aiNote: String? = null,
    val aiLoading: Boolean = false,
    val rewriteLoading: Boolean = false,
    val cannedReplies: List<CannedReplyView> = emptyList(),
    val suggestedDraft: String? = null,
    val rewrittenDraft: String? = null,
)

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val aiRepository: AiRepository,
    private val realtimeHub: RealtimeHub,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatDetailUiState())
    val state: StateFlow<ChatDetailUiState> = _state.asStateFlow()
    private var conversationId: String = ""
    private var subscribed = false

    fun load(id: String) {
        conversationId = id
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { chatRepository.detail(id) }
                .onSuccess { detail ->
                    _state.value = ChatDetailUiState(
                        messages = detail.messages,
                        visitorName = detail.conversation.visitorName ?: "Visitor",
                        status = detail.conversation.status,
                        assignedAgentId = detail.conversation.assignedAgentId,
                        loading = false,
                        cannedReplies = _state.value.cannedReplies,
                    )
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
        if (_state.value.cannedReplies.isEmpty()) {
            viewModelScope.launch {
                runCatching { chatRepository.cannedReplies() }
                    .onSuccess { replies ->
                        _state.value = _state.value.copy(cannedReplies = replies)
                    }
            }
        }
        if (!subscribed) {
            subscribed = true
            viewModelScope.launch {
                realtimeHub.events.collect { event ->
                    if (event is RealtimeEvent.Chat && event.payload.conversationId == conversationId) {
                        reload(conversationId)
                    }
                }
            }
        }
    }

    private fun reload(id: String) {
        viewModelScope.launch {
            runCatching { chatRepository.detail(id) }.onSuccess { detail ->
                _state.value = _state.value.copy(
                    messages = detail.messages,
                    visitorName = detail.conversation.visitorName ?: "Visitor",
                    status = detail.conversation.status,
                    assignedAgentId = detail.conversation.assignedAgentId,
                    loading = false,
                )
            }
        }
    }

    fun send(body: String, note: Boolean) {
        viewModelScope.launch {
            runCatching { chatRepository.sendMessage(conversationId, body, note) }
                .onSuccess { reload(conversationId) }
                .onFailure {
                    _state.value = _state.value.copy(syncNote = "Queued — will send when online")
                }
        }
    }

    fun assignToMe() {
        val agentId = authRepository.session()?.userId ?: return
        viewModelScope.launch {
            runCatching { chatRepository.assign(conversationId, agentId) }
                .onSuccess { summary ->
                    _state.value = _state.value.copy(
                        assignedAgentId = summary.assignedAgentId,
                        operationError = null,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(operationError = it.message)
                }
        }
    }

    fun toggleClosed() {
        viewModelScope.launch {
            runCatching {
                if (_state.value.status == "CLOSED") {
                    chatRepository.reopen(conversationId)
                } else {
                    chatRepository.close(conversationId)
                }
            }
                .onSuccess { summary ->
                    _state.value = _state.value.copy(status = summary.status, operationError = null)
                }
                .onFailure {
                    _state.value = _state.value.copy(operationError = it.message)
                }
        }
    }

    fun suggestReply() {
        viewModelScope.launch {
            _state.value = _state.value.copy(aiLoading = true, aiNote = null)
            runCatching { aiRepository.suggestChatReply(conversationId) }
                .onSuccess { result ->
                    if (result.available && result.draft.isNotBlank()) {
                        _state.value = _state.value.copy(aiLoading = false, suggestedDraft = result.draft)
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

    fun rewriteDraft(draft: String, action: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(rewriteLoading = true, aiNote = null)
            runCatching { aiRepository.rewriteChatDraft(conversationId, draft, action) }
                .onSuccess { result ->
                    if (result.available && result.text.isNotBlank()) {
                        _state.value = _state.value.copy(rewriteLoading = false, rewrittenDraft = result.text)
                    } else {
                        _state.value = _state.value.copy(
                            rewriteLoading = false,
                            aiNote = "AI rewrite unavailable right now",
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        rewriteLoading = false,
                        aiNote = "AI rewrite unavailable right now",
                    )
                }
        }
    }

    fun clearSuggestedDraft() {
        _state.value = _state.value.copy(suggestedDraft = null)
    }

    fun clearRewrittenDraft() {
        _state.value = _state.value.copy(rewrittenDraft = null)
    }

    fun canReply() = authRepository.hasPermission("CHAT_REPLY")
    fun canAssign() = authRepository.hasPermission("CHAT_ASSIGN")
    fun canUseAi() = authRepository.hasPermission("AI_USE")
    fun currentUserId() = authRepository.session()?.userId
    fun isAssignedToMe() = _state.value.assignedAgentId == currentUserId()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatDetailViewModel = hiltViewModel(),
) {
    var draft by remember { mutableStateOf("") }
    var noteMode by remember { mutableStateOf(false) }
    var cannedMenuOpen by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(conversationId) { viewModel.load(conversationId) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    LaunchedEffect(state.suggestedDraft) {
        state.suggestedDraft?.let {
            draft = it
            viewModel.clearSuggestedDraft()
        }
    }
    LaunchedEffect(state.rewrittenDraft) {
        state.rewrittenDraft?.let {
            draft = it
            viewModel.clearRewrittenDraft()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.visitorName)
                        Text(
                            state.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (viewModel.canAssign() && !viewModel.isAssignedToMe()) {
                        TextButton(onClick = { viewModel.assignToMe() }) {
                            Text("Assign to me")
                        }
                    }
                    if (viewModel.canReply()) {
                        TextButton(onClick = { viewModel.toggleClosed() }) {
                            Text(if (state.status == "CLOSED") "Reopen" else "Close")
                        }
                    }
                }
                state.operationError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                if (viewModel.canReply()) {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = { noteMode = !noteMode }, label = {
                                Text(if (noteMode) "Internal note" else "Reply")
                            })
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
                            if (viewModel.canUseAi() && !noteMode) {
                                TextButton(
                                    onClick = { viewModel.suggestReply() },
                                    enabled = !state.aiLoading,
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
                                placeholder = { Text(if (noteMode) "Internal note…" else "Message…") },
                            )
                            IconButton(onClick = {
                                if (draft.isNotBlank()) {
                                    viewModel.send(draft, noteMode)
                                    draft = ""
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        }
                        if (viewModel.canUseAi() && !noteMode && draft.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Improve" to "Improve", "Shorten" to "Shorten", "Translate" to "Translate").forEach { (action, label) ->
                                    TextButton(
                                        onClick = { viewModel.rewriteDraft(draft, action) },
                                        enabled = !state.rewriteLoading,
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                if (state.rewriteLoading) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                }
                            }
                        }
                        state.syncNote?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        state.aiNote?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(msg)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageView) {
    val isNote = message.senderType == "NOTE"
    val isVisitor = message.senderType == "VISITOR"
    val alignment = if (isVisitor) Alignment.CenterStart else Alignment.CenterEnd
    val bg = when {
        isNote -> MaterialTheme.colorScheme.tertiaryContainer
        isVisitor -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            Modifier
                .background(bg, MaterialTheme.shapes.medium)
                .padding(12.dp)
                .fillMaxWidth(0.85f),
        ) {
            if (isNote) {
                Text("Internal note", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic)
            }
            Text(message.body)
        }
    }
}

private fun aiUnavailableMessage(notConfigured: Boolean): String = when {
    notConfigured -> "AI is not configured for this organization"
    else -> "AI is unavailable right now"
}
