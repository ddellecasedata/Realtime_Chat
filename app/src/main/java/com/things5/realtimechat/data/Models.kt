package com.things5.realtimechat.data

import androidx.compose.runtime.Immutable

/**
 * Authentication type for MCP servers
 */
enum class McpAuthType {
    NONE,           // No authentication
    BEARER,         // Authorization: Bearer <token>
    API_KEY,        // X-API-Key: <key>
    BASIC,          // Authorization: Basic <base64(user:pass)>
    CUSTOM          // Custom header name and value
}

/**
 * Configuration for an MCP server
 */
@Immutable
data class McpServerConfig(
    val name: String = "",
    val url: String = "",
    val enabled: Boolean = true,
    val authType: McpAuthType = McpAuthType.NONE,
    val apiKey: String = "",           // For BEARER, API_KEY, or CUSTOM value
    val username: String = "",         // For BASIC auth
    val password: String = "",         // For BASIC auth
    val customHeaderName: String = ""  // For CUSTOM auth
)

/**
 * Things5 authentication configuration
 */
@Immutable
data class Things5Config(
    val enabled: Boolean = false,
    val serverUrl: String = "https://things5-mcp-server.onrender.com/mcp",
    val username: String = "",
    val password: String = "",
    val connectionStatus: Things5ConnectionStatus = Things5ConnectionStatus.DISCONNECTED,
    val lastSuccessfulConnection: Long? = null,
    val autoConnect: Boolean = true
)

/**
 * Things5 connection status
 */
enum class Things5ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
    AUTHENTICATION_FAILED
}

/**
 * App settings stored persistently
 */
@Immutable
data class AppSettings(
    val openAiApiKey: String = "",
    val mcpServers: List<McpServerConfig> = emptyList(),
    val things5Config: Things5Config = Things5Config(),
    val isConfigured: Boolean = false
)

/**
 * Session state for realtime chat
 */
sealed class SessionState {
    object Idle : SessionState()
    object Connecting : SessionState()
    object Connected : SessionState()
    data class Error(val message: String) : SessionState()
}

/**
 * Realtime API message types
 */
sealed class RealtimeMessage {
    data class SessionUpdate(val session: Map<String, Any>) : RealtimeMessage()
    data class InputAudioBuffer(val audio: String) : RealtimeMessage()
    data class ResponseAudioDelta(val delta: String) : RealtimeMessage()
    data class ResponseAudioDone(val itemId: String) : RealtimeMessage()
    data class ConversationItem(
        val id: String,
        val type: String,
        val content: List<ContentItem>
    ) : RealtimeMessage()
    data class Error(val error: String) : RealtimeMessage()
}

/**
 * Content item for conversation
 */
@Immutable
data class ContentItem(
    val type: String, // "text", "audio", "image"
    val text: String? = null,
    val audio: String? = null,
    val imageUrl: String? = null,
    val imageData: String? = null // Base64 encoded image
)

/**
 * UI state for main screen
 */
@Immutable
data class MainScreenState(
    val sessionState: SessionState = SessionState.Idle,
    val isRecording: Boolean = false,
    val transcript: String = "",
    val capturedImages: List<String> = emptyList(), // Base64 encoded images
    val errorMessage: String? = null,
    val isSendingAudio: Boolean = false, // Feedback invio audio
    val isSendingImage: Boolean = false, // Feedback invio immagine
    val lastDataSent: String? = null // "Audio", "Immagine", "Tool call", etc.
)

/**
 * Debug log entry for operations
 */
@Immutable
data class DebugLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val type: DebugLogType,
    val message: String,
    val details: String? = null
)

enum class DebugLogType {
    CONNECTION,
    AUDIO_SENT,
    AUDIO_RECEIVED,
    IMAGE_SENT,
    TOOL_CALL,
    TOOL_RESPONSE,
    MCP_CONNECTION,
    MCP_ERROR,
    MCP_TOOL_REGISTERED,
    ERROR,
    INFO
}

/**
 * Realtime API debug state
 */
@Immutable
data class RealtimeDebugState(
    val isConnected: Boolean = false,
    val connectionQuality: ConnectionQuality = ConnectionQuality.UNKNOWN,
    val sessionId: String? = null,
    val model: String = "gpt-realtime",
    val tokensUsed: Int = 0,
    val audioChunksSent: Int = 0,
    val audioChunksReceived: Int = 0,
    val imagesProcessed: Int = 0,
    val toolCallsExecuted: Int = 0,
    val errors: Int = 0,
    val logs: List<DebugLogEntry> = emptyList(),
    val lastError: String? = null,
    val usageLimitReached: Boolean = false
)

enum class ConnectionQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    UNKNOWN
}
