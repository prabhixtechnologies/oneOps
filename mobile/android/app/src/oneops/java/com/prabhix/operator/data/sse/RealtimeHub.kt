package com.prabhix.operator.data.sse

import com.prabhix.operator.BuildConfig
import com.prabhix.operator.data.api.ChatStreamPayload
import com.prabhix.operator.data.auth.TokenRefresher
import com.prabhix.operator.data.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

sealed interface RealtimeEvent {
    data class Chat(val payload: ChatStreamPayload) : RealtimeEvent
    data class Connection(val connected: Boolean) : RealtimeEvent
}

@Singleton
class RealtimeHub @Inject constructor(
    private val client: OkHttpClient,
    private val tokenStore: TokenStore,
    private val tokenRefresher: TokenRefresher,
    private val json: Json,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events

    private var chatSource: EventSource? = null
    private var reconnectJob: Job? = null
    private val attempt = AtomicInteger(0)

    fun start() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            tokenRefresher.refreshIfNeeded()
            connectChat()
            _events.emit(RealtimeEvent.Connection(true))
            attempt.set(0)
        }
    }

    fun stop() {
        reconnectJob?.cancel()
        chatSource?.cancel()
        chatSource = null
        scope.launch { _events.emit(RealtimeEvent.Connection(false)) }
    }

    /** Restarts the stream, after a sign-in or a change of organization. */
    fun restart() {
        stop()
        start()
    }

    private fun connectChat() {
        chatSource?.cancel()
        val session = tokenStore.session() ?: return
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/chat/stream")
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "text/event-stream")
            .header("X-Correlation-Id", UUID.randomUUID().toString())
            .header("X-Prabhix-Device", BuildConfig.DEVICE_HEADER)
            .apply {
                session.organizationId?.let { header("X-Prabhix-Org", it) }
            }
            .build()

        chatSource = EventSources.createFactory(client).newEventSource(request, sseListener { data ->
            if (data == "ping") return@sseListener
            runCatching {
                json.decodeFromString(ChatStreamPayload.serializer(), data)
            }.onSuccess { payload ->
                scope.launch { _events.emit(RealtimeEvent.Chat(payload)) }
            }
        })
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _events.emit(RealtimeEvent.Connection(false))
            val next = min(60_000L, 1_000L shl attempt.getAndIncrement())
            delay(next + Random.nextLong(500))
            start()
        }
    }

    private fun sseListener(onMessage: (String) -> Unit) = object : okhttp3.sse.EventSourceListener() {
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            onMessage(data)
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
            scheduleReconnect()
        }

        override fun onClosed(eventSource: EventSource) {
            scheduleReconnect()
        }
    }
}
