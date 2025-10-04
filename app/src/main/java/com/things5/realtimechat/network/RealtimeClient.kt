package com.things5.realtimechat.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.things5.realtimechat.data.ContentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Client for OpenAI Realtime API
 * Based on https://platform.openai.com/docs/guides/voice-agents
 */
class RealtimeClient(
    private val apiKey: String,
    private val model: String = "gpt-realtime"
) {
    private val TAG = "RealtimeClient"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)  // Ping automatico ogni 20s per keep-alive
        .build()
    
    // State flows
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _events = MutableSharedFlow<RealtimeEvent>(replay = 0)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()
    
    private val _errors = MutableSharedFlow<String>(replay = 0)
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    
    private val wsListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "✅ WebSocket connected - Keep-alive enabled")
            _isConnected.value = true
            scope.launch {
                _events.emit(RealtimeEvent.Connected)
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received message: $text")
            scope.launch {
                try {
                    val jsonObject = gson.fromJson(text, JsonObject::class.java)
                    val type = jsonObject.get("type")?.asString ?: ""
                    
                    when (type) {
                        "session.created" -> {
                            _events.emit(RealtimeEvent.SessionCreated(text))
                        }
                        "session.updated" -> {
                            _events.emit(RealtimeEvent.SessionUpdated(text))
                        }
                        "conversation.item.created" -> {
                            _events.emit(RealtimeEvent.ConversationItemCreated(text))
                        }
                        "input_audio_buffer.committed" -> {
                            Log.d(TAG, "✅ Server VAD: Audio buffer committed")
                            _events.emit(RealtimeEvent.InputAudioBufferCommitted)
                        }
                        "input_audio_buffer.speech_started" -> {
                            Log.d(TAG, "🗣️ Server VAD: Speech detected!")
                            _events.emit(RealtimeEvent.InputAudioBufferSpeechStarted)
                        }
                        "input_audio_buffer.speech_stopped" -> {
                            Log.d(TAG, "🤐 Server VAD: Speech stopped (silence detected)")
                            _events.emit(RealtimeEvent.InputAudioBufferSpeechStopped)
                        }
                        "response.audio.delta" -> {
                            val delta = jsonObject.get("delta")?.asString ?: ""
                            _events.emit(RealtimeEvent.AudioDelta(delta))
                        }
                        "response.audio.done" -> {
                            _events.emit(RealtimeEvent.AudioDone)
                        }
                        "response.text.delta" -> {
                            val delta = jsonObject.get("delta")?.asString ?: ""
                            _events.emit(RealtimeEvent.TextDelta(delta))
                        }
                        "response.text.done" -> {
                            _events.emit(RealtimeEvent.TextDone(text))
                        }
                        "response.done" -> {
                            _events.emit(RealtimeEvent.ResponseDone)
                        }
                        "response.function_call_arguments.done" -> {
                            // Tool call dall'assistente
                            val callId = jsonObject.get("call_id")?.asString ?: ""
                            val name = jsonObject.get("name")?.asString ?: ""
                            val argumentsStr = jsonObject.get("arguments")?.asString ?: "{}"
                            
                            try {
                                val argsJson = gson.fromJson(argumentsStr, JsonObject::class.java)
                                val params = argsJson.entrySet().associate { entry ->
                                    val value: Any = when {
                                        entry.value.isJsonPrimitive -> {
                                            val primitive = entry.value.asJsonPrimitive
                                            when {
                                                primitive.isBoolean -> primitive.asBoolean
                                                primitive.isNumber -> {
                                                    val num = primitive.asNumber
                                                    // Try to preserve int vs double
                                                    if (num.toDouble() == num.toInt().toDouble()) {
                                                        num.toInt()
                                                    } else {
                                                        num.toDouble()
                                                    }
                                                }
                                                else -> primitive.asString
                                            }
                                        }
                                        entry.value.isJsonArray -> entry.value.asJsonArray.toString()
                                        entry.value.isJsonObject -> entry.value.asJsonObject.toString()
                                        else -> entry.value.toString()
                                    }
                                    entry.key to value
                                }
                                _events.emit(RealtimeEvent.ToolCall(callId, name, params))
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing tool call arguments", e)
                            }
                        }
                        "error" -> {
                            val error = jsonObject.get("error")?.asJsonObject
                            val message = error?.get("message")?.asString ?: "Unknown error"
                            _events.emit(RealtimeEvent.Error(message))
                            _errors.emit(message)
                        }
                        else -> {
                            Log.d(TAG, "Unhandled event type: $type")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message", e)
                    _errors.emit("Error parsing message: ${e.message}")
                }
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "❌ WebSocket failure: ${t.javaClass.simpleName} - ${t.message}", t)
            _isConnected.value = false
            scope.launch {
                _events.emit(RealtimeEvent.Disconnected)
                _errors.emit("Connessione persa: ${t.message}. Premi 'Chiudi Sessione' e riavvia.")
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code - $reason")
            webSocket.close(1000, null)
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "🔌 WebSocket closed: code=$code, reason=$reason")
            _isConnected.value = false
            scope.launch {
                _events.emit(RealtimeEvent.Disconnected)
            }
        }
    }
    
    /**
     * Connect to the Realtime API
     */
    fun connect() {
        if (_isConnected.value) {
            Log.w(TAG, "Already connected")
            return
        }
        
        val url = "wss://api.openai.com/v1/realtime?model=$model"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()
        
        webSocket = client.newWebSocket(request, wsListener)
    }
    
    /**
     * Disconnect from the Realtime API
     */
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected.value = false
    }
    
    /**
     * Update session configuration
     */
    fun updateSession(
        instructions: String? = null,
        voice: String = "alloy",
        inputAudioFormat: String = "pcm16",
        outputAudioFormat: String = "pcm16",
        turnDetection: Map<String, Any>? = null,
        tools: List<Map<String, Any>>? = null
    ) {
        val event = mutableMapOf<String, Any>(
            "type" to "session.update",
            "session" to mutableMapOf<String, Any>()
        )
        
        val session = event["session"] as MutableMap<String, Any>
        
        instructions?.let { session["instructions"] = it }
        session["voice"] = voice
        session["input_audio_format"] = inputAudioFormat
        session["output_audio_format"] = outputAudioFormat
        
        turnDetection?.let { 
            session["turn_detection"] = it 
            Log.d(TAG, "🎯 Turn detection configured: $it")
        }
        tools?.let { 
            session["tools"] = it 
            Log.d(TAG, "🔧 Tools configured: ${it.size} tools")
        }
        
        Log.d(TAG, "⚙️ Sending session.update event")
        sendEvent(event)
    }
    
    /**
     * Send audio data to the API
     */
    fun sendAudio(audioBase64: String) {
        val event = mapOf(
            "type" to "input_audio_buffer.append",
            "audio" to audioBase64
        )
        sendEvent(event)
    }
    
    /**
     * Commit the audio buffer (signal end of user input)
     */
    fun commitAudio() {
        Log.d(TAG, "📤 Committing audio buffer to API...")
        val event = mapOf(
            "type" to "input_audio_buffer.commit"
        )
        sendEvent(event)
        
        // Trigger response generation
        Log.d(TAG, "🤖 Requesting response from API...")
        createResponse()
    }
    
    /**
     * Send an image in the conversation
     * Format: data URL with base64 encoded image
     */
    fun sendImage(imageBase64: String, mimeType: String = "image/jpeg") {
        // Create data URL format: data:image/jpeg;base64,<base64_string>
        val dataUrl = "data:$mimeType;base64,$imageBase64"
        
        val event = mapOf(
            "type" to "conversation.item.create",
            "item" to mapOf(
                "type" to "message",
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "input_image",
                        "image_url" to dataUrl
                    )
                )
            )
        )
        
        Log.d(TAG, "📸 Sending image: ${mimeType}, size: ${imageBase64.length} chars")
        sendEvent(event)
    }
    
    /**
     * Send a text message
     */
    fun sendText(text: String) {
        val event = mapOf(
            "type" to "conversation.item.create",
            "item" to mapOf(
                "type" to "message",
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "input_text",
                        "text" to text
                    )
                )
            )
        )
        sendEvent(event)
    }
    
    /**
     * Request a response from the assistant
     */
    fun createResponse() {
        val event = mapOf(
            "type" to "response.create"
        )
        sendEvent(event)
    }
    
    /**
     * Cancel the current response
     */
    fun cancelResponse() {
        Log.d(TAG, "⏹️ Cancelling current response (interrupt)")
        val event = mapOf(
            "type" to "response.cancel"
        )
        sendEvent(event)
    }
    
    /**
     * Send tool call result back to the API
     */
    fun sendToolResult(callId: String, result: String) {
        val event = mapOf(
            "type" to "conversation.item.create",
            "item" to mapOf(
                "type" to "function_call_output",
                "call_id" to callId,
                "output" to result
            )
        )
        sendEvent(event)
        
        // Trigger response generation dopo il tool result
        createResponse()
    }
    
    /**
     * Clear the conversation history
     */
    fun clearConversation() {
        val event = mapOf(
            "type" to "conversation.clear"
        )
        Log.d(TAG, "🗑️ Clearing conversation history")
        sendEvent(event)
    }
    
    /**
     * Send a custom event to the API
     */
    private fun sendEvent(event: Map<String, Any>) {
        if (!_isConnected.value) {
            Log.w(TAG, "Cannot send event: not connected")
            scope.launch {
                _errors.emit("Cannot send event: not connected")
            }
            return
        }
        
        try {
            val eventType = event["type"] ?: "unknown"
            val json = gson.toJson(event)
            Log.d(TAG, "📤 Sending event type: $eventType")
            Log.v(TAG, "Event JSON: $json")
            webSocket?.send(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending event", e)
            scope.launch {
                _errors.emit("Error sending event: ${e.message}")
            }
        }
    }
}

/**
 * Events from the Realtime API
 */
sealed class RealtimeEvent {
    object Connected : RealtimeEvent()
    object Disconnected : RealtimeEvent()
    data class SessionCreated(val data: String) : RealtimeEvent()
    data class SessionUpdated(val data: String) : RealtimeEvent()
    data class ConversationItemCreated(val data: String) : RealtimeEvent()
    object InputAudioBufferCommitted : RealtimeEvent()
    object InputAudioBufferSpeechStarted : RealtimeEvent()
    object InputAudioBufferSpeechStopped : RealtimeEvent()
    data class AudioDelta(val delta: String) : RealtimeEvent()
    object AudioDone : RealtimeEvent()
    data class TextDelta(val delta: String) : RealtimeEvent()
    data class TextDone(val text: String) : RealtimeEvent()
    object ResponseDone : RealtimeEvent()
    data class ToolCall(val callId: String, val toolName: String, val parameters: Map<String, Any>) : RealtimeEvent()
    data class Error(val message: String) : RealtimeEvent()
}
