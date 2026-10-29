package com.nokat.chat.data.realtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.nokat.chat.data.model.Chat
import com.nokat.chat.data.model.Message
import com.nokat.chat.data.model.PresencePayload
import com.nokat.chat.data.model.ReadPayload
import com.nokat.chat.data.model.TypingPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Single app-wide WebSocket connection (`GET /ws?token=`).
 *
 * - [events] is a hot [SharedFlow] of parsed server events.
 * - Reconnects with exponential backoff 1 s → 30 s; a network-available callback skips the wait.
 * - Sends an application-level `ping` every 25 s.
 * - Emits [WsEvent.Connected] after every successful (re)connect so repositories can refetch.
 */
class RealtimeClient(
    context: Context,
    baseClient: OkHttpClient,
    private val json: Json,
    private val baseUrl: String,
    private val scope: CoroutineScope,
) {
    enum class State { Disconnected, Connecting, Connected }

    private val client: OkHttpClient = baseClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived socket
        .pingInterval(30, TimeUnit.SECONDS) // protocol-level keepalive to detect dead links
        .build()

    private val _events = MutableSharedFlow<WsEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(State.Disconnected)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var socket: WebSocket? = null
    private var loopJob: Job? = null
    private var currentToken: String? = null

    /** Wakes the backoff wait early (e.g. when the network comes back). */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val connectivity: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wake.trySend(Unit)
        }
    }

    @Synchronized
    fun start(token: String) {
        if (currentToken == token && loopJob?.isActive == true) return
        stopLocked()
        currentToken = token
        registerNetworkCallback()
        loopJob = scope.launch { runLoop(token) }
    }

    @Synchronized
    fun stop() {
        currentToken = null
        stopLocked()
        unregisterNetworkCallback()
    }

    /** Sends a client → server frame. Returns false when not connected. */
    fun send(type: String, data: JsonObject): Boolean {
        val ws = socket ?: return false
        if (_state.value != State.Connected) return false
        val frame = buildJsonObject {
            put("type", JsonPrimitive(type))
            put("data", data)
        }
        return ws.send(frame.toString())
    }

    private fun stopLocked() {
        loopJob?.cancel()
        loopJob = null
        socket?.close(NORMAL_CLOSURE, null)
        socket = null
        _state.value = State.Disconnected
    }

    private suspend fun runLoop(token: String) {
        var attempt = 0
        while (coroutineContext.isActive) {
            _state.value = State.Connecting
            val result = connectOnce(token)
            _state.value = State.Disconnected
            if (result.unauthorized) {
                _events.tryEmit(WsEvent.Unauthorized)
                return
            }
            if (result.opened) attempt = 0
            val backoff = minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl minOf(attempt, 5))
            attempt++
            wake.tryReceive() // drop stale wake-ups
            withTimeoutOrNull(backoff) { wake.receive() }
        }
    }

    private class ConnectResult(val opened: Boolean, val unauthorized: Boolean)

    /** Opens one socket and suspends until it closes or fails. */
    private suspend fun connectOnce(token: String): ConnectResult = coroutineScope {
        val opened = AtomicBoolean(false)
        val closed = CompletableDeferred<ConnectResult>()
        // OkHttp accepts http(s) URLs for WebSockets and upgrades them to ws(s).
        val url = "$baseUrl/ws?token=" + URLEncoder.encode(token, "UTF-8")
        val request = Request.Builder().url(url).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.set(true)
                _state.value = State.Connected
                _events.tryEmit(WsEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parse(text)?.let { _events.tryEmit(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(ConnectResult(opened.get(), unauthorized = false))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure: ${t.message}")
                closed.complete(ConnectResult(opened.get(), unauthorized = response?.code == 401))
            }
        }

        val ws = client.newWebSocket(request, listener)
        socket = ws
        val pingJob = launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                if (opened.get()) ws.send(PING_FRAME)
            }
        }
        try {
            closed.await()
        } finally {
            pingJob.cancel()
            if (socket === ws) socket = null
            ws.cancel()
        }
    }

    private fun parse(text: String): WsEvent? {
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            val type = root["type"]?.jsonPrimitive?.contentOrNull ?: return null
            val data: JsonElement = root["data"] ?: JsonNull
            when (type) {
                "message.new" -> WsEvent.MessageNew(json.decodeFromJsonElement(Message.serializer(), data))
                "message.read" -> json.decodeFromJsonElement(ReadPayload.serializer(), data)
                    .let { WsEvent.MessageRead(it.chatId, it.userId, it.readAt) }
                "chat.new" -> WsEvent.ChatNew(json.decodeFromJsonElement(Chat.serializer(), data))
                "typing" -> json.decodeFromJsonElement(TypingPayload.serializer(), data)
                    .let { WsEvent.Typing(it.chatId, it.userId, it.typing) }
                "presence" -> json.decodeFromJsonElement(PresencePayload.serializer(), data)
                    .let { WsEvent.Presence(it.userId, it.online, it.lastSeen) }
                else -> null // "pong" and unknown types
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unparseable frame: ${e.message}")
            null
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        try {
            connectivity?.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = connectivity != null
        } catch (e: Exception) {
            Log.w(TAG, "registerDefaultNetworkCallback failed", e)
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        try {
            connectivity?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "unregisterNetworkCallback failed", e)
        }
        networkCallbackRegistered = false
    }

    private companion object {
        const val TAG = "RealtimeClient"
        const val NORMAL_CLOSURE = 1000
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val PING_INTERVAL_MS = 25_000L
        const val PING_FRAME = """{"type":"ping","data":{}}"""
    }
}
