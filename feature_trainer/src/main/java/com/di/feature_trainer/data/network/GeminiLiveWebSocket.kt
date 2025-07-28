// GeminiLiveWebSocket.kt
// ------------------------------------------------------------
// 100 % self-contained, compile-ready version with the fixes
// discussed (audio-life-cycle, generationComplete, GoAway,
// sendRealtimeInputEnd, safer writes, etc.).
// ------------------------------------------------------------
package com.di.feature_trainer.data.network

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.di.feature_trainer.data.models.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiLiveWebSocket @Inject constructor(
    private val okHttp: OkHttpClient,
    private val json: Json
) : WebSocketListener() {

    /* ───────────────────── constants / state ───────────────────── */
    private companion object { const val TAG = "GeminiLiveWS" }

    private enum class State { IDLE, CONNECTING, CONFIGURING, READY, CLOSING, CLOSED }

    @Volatile private var state = State.IDLE
    private var ws: WebSocket? = null

    private val setupQueue = ConcurrentLinkedQueue<String>()
    private val outbox     = ConcurrentLinkedQueue<String>()

    private val _events = MutableSharedFlow<LiveApiEvent>(extraBufferCapacity = 64)
    val events : SharedFlow<LiveApiEvent> = _events

    /* ───────────────────── public API ───────────────────── */

    fun connect(apiKey: String, cfg: LiveSessionConfig) {
        if (state !in listOf(State.IDLE, State.CLOSED)) {
            Log.w(TAG, "connect() called while $state – ignored.")
            return
        }
        state = State.CONNECTING

        val url = "wss://generativelanguage.googleapis.com/ws/" +
                "google.ai.generativelanguage.v1beta.GenerativeService." +
                "BidiGenerateContent?key=$apiKey"

        setupQueue.clear()
        outbox.clear()

        val setupMessage = ClientSetupMessage(cfg)
        val setupJson = json.encodeToString(setupMessage)

        // VVVV  ENHANCED LOGGING VVVV
        Log.d(TAG, "===== FULL SETUP JSON (len: ${setupJson.length}) =====")
        // Log the JSON in chunks if it's too long for a single logcat line
        setupJson.chunked(4000).forEach { chunk ->
            Log.d(TAG, chunk)
        }
        Log.d(TAG, "==================================================")

        if (setupJson.length > 32768) {
            Log.w(TAG, "Setup message might be too large: ${setupJson.length} characters")
        }

        setupQueue += setupJson

        Log.d(TAG, "Connecting to $url")
        ws = okHttp.newWebSocket(Request.Builder().url(url).build(), this)
    }

    fun isConnected(): Boolean = state == State.READY

    /* --------------- client->server helpers --------------- */

    fun sendTextTurn(text: String, turnComplete: Boolean = true) =
        enqueue(
            ClientMessage(
                clientContent = ClientContent(
                    turns = listOf(Turn(role = "user", parts = listOf(Part(text = text)))),
                    turnComplete = turnComplete
                )
            )
        )

    fun sendAudioData(base64: String) =
        enqueue(
            ClientMessage(
                realtimeInput = RealtimeInput(
                    audio = AudioInput(
                        data = base64,
                        mimeType = "audio/pcm;rate=16000"
                    )
                )
            )
        )

    /** flushes cached mic data and tells backend user turn is finished */
    fun sendRealtimeInputEnd() =
        enqueue(
            ClientMessage(
                realtimeInput = RealtimeInput(audioStreamEnd = true)
            )
        )

    fun sendToolResponse(resp: ToolResponse) =
        enqueue(ClientMessage(toolResponse = resp))

    /* --------------- graceful shutdown --------------- */

    fun disconnect() {
        if (state in listOf(State.IDLE, State.CLOSED)) return
        state = State.CLOSING
        ws?.close(1000, "client bye")
        cleanup()
    }

    /* ───────────────── WebSocket listener overrides ───────────────── */

    override fun onOpen(ws: WebSocket, response: Response) {
        Log.d(TAG, "WS OPEN (${response.code})")
        state = State.CONFIGURING

        val setupMsg = setupQueue.poll()
        if (setupMsg != null) {
            Log.d(TAG, "Sending setup message: ${setupMsg.take(200)}...") // Log first 200 chars
            try {
                ws.send(setupMsg)
                Log.d(TAG, "Setup message sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send setup message", e)
                _events.tryEmit(LiveApiEvent.Error("Failed to send setup: ${e.message}"))
                disconnect()
                return
            }
        } else {
            Log.e(TAG, "Setup message missing from queue")
            _events.tryEmit(LiveApiEvent.Error("Setup message missing"))
            disconnect()
            return
        }

        // safety timer – 10 s to receive setupComplete
        Handler(Looper.getMainLooper()).postDelayed({
            if (state == State.CONFIGURING) {
                Log.e(TAG, "Setup timeout")
                _events.tryEmit(LiveApiEvent.Error("Setup timeout"))
                disconnect()
            }
        }, 10_000)
    }

    override fun onMessage(ws: WebSocket, text: String) {
        try {
            val msg = json.decodeFromString<ServerMessage>(text)

            if (msg.setupComplete != null) {
                state = State.READY
                _events.tryEmit(LiveApiEvent.ConnectionOpened)
                flushOutbox()
                return
            }

            if (state == State.READY) handleServerMessage(msg)
        } catch (t: Throwable) {
            Log.e(TAG, "JSON parse error", t)
            _events.tryEmit(LiveApiEvent.Error("Bad server JSON: ${t.message}"))
        }
    }

    override fun onMessage(ws: WebSocket, bytes: ByteString) {
        val isJson = bytes.size >= 1 && bytes[0].toChar() == '{'
        if (isJson) onMessage(ws, bytes.string(Charsets.UTF_8))
        else _events.tryEmit(
            LiveApiEvent.AudioReceived(
                Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            )
        )
    }

    override fun onClosing(ws: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WS CLOSING ($code) $reason")
        state = State.CLOSING
    }

    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "WS CLOSED ($code) $reason")
        cleanup()
        _events.tryEmit(LiveApiEvent.ConnectionClosed)
    }

    override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
        Log.e(TAG, "WS FAILURE", t)
        cleanup()
        _events.tryEmit(LiveApiEvent.Error("WebSocket failure: ${t.message ?: "unknown"}"))
    }

    /* ───────────────── internal helpers ───────────────── */

    private fun enqueue(msg: ClientMessage) = enqueue(json.encodeToString(msg))

    private fun enqueue(raw: String) {
        when (state) {
            State.READY -> ws?.send(raw) ?: run {
                Log.e(TAG, "WebSocket is null but state is READY")
                _events.tryEmit(LiveApiEvent.Error("WebSocket unexpectedly null"))
            }
            State.CONFIGURING -> {
                Log.d(TAG, "Queueing message while configuring: ${raw.take(100)}")
                outbox += raw
            }
            State.CONNECTING -> {
                Log.d(TAG, "Queueing message while connecting: ${raw.take(100)}")
                outbox += raw  // Also queue during connecting
            }
            else -> {
                Log.w(TAG, "Cannot send – socket state: $state")
                _events.tryEmit(LiveApiEvent.Error("Cannot send – socket not ready (state: $state)"))
            }
        }
    }

    private fun flushOutbox() {
        while (outbox.isNotEmpty()) ws?.send(outbox.poll())
    }

    private fun cleanup() {
        ws = null
        state = State.CLOSED
        outbox.clear()
        setupQueue.clear()
    }

    /* ───────────────── server message dispatcher ───────────────── */

    private fun handleServerMessage(msg: ServerMessage) {

        /* -------- single-field frames -------- */
        msg.data?.let {
            Log.d(TAG, "Received audio data (${it.length} chars)")
            _events.tryEmit(LiveApiEvent.AudioReceived(it))
            return
        }

        msg.toolCall?.let {
            _events.tryEmit(LiveApiEvent.ToolCallReceived(it)); return
        }

        msg.sessionResumptionUpdate?.newHandle?.let {
            _events.tryEmit(LiveApiEvent.SessionHandleUpdated(it)); return
        }

        /* -------- conversational traffic -------- */
        msg.serverContent?.let { sc ->
            val isTurnComplete = sc.turnComplete == true
            val isGenerationDone = sc.generationComplete == true

            // User transcription
            sc.inputTranscription?.text?.let {
                _events.tryEmit(
                    LiveApiEvent.UserTranscriptUpdated(it, isFinal = isTurnComplete)
                )
            }

            // MODIFIED: Only use outputTranscription for AI messages when in audio mode
            sc.outputTranscription?.text?.let {
                _events.tryEmit(
                    LiveApiEvent.AiTranscriptUpdated(it, isFinal = isTurnComplete)
                )
            }

            // MODIFIED: Comment out or remove the modelTurn text handling
            // We'll still process audio data from modelTurn
            sc.modelTurn?.parts?.forEach { part ->
                // Only handle audio data, not text
                part.inlineData?.takeIf { it.mimeType.startsWith("audio/") }?.let {
                    _events.tryEmit(LiveApiEvent.AudioReceived(it.data))
                }
            }

            if (sc.interrupted == true) {
                _events.tryEmit(LiveApiEvent.Interrupted("User interrupted model"))
            }

            if (isGenerationDone) {
                _events.tryEmit(LiveApiEvent.GenerationComplete())
            }

            return
        }

        /* -------- housekeeping -------- */

        msg.goAway?.let {
            Log.w(TAG, "GoAway – time left: ${it.timeLeft}")
            _events.tryEmit(LiveApiEvent.GoAway(it.timeLeft))
            return
        }

        msg.usageMetadata?.let {
            Log.d(TAG, "Usage: total tokens=${it.totalTokenCount ?: "unknown"}")
            return
        }

        Log.d(TAG, "Unhandled server msg: $msg")
    }
}
