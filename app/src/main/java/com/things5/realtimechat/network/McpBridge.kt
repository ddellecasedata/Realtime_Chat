package com.things5.realtimechat.network

import android.util.Log
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.things5.realtimechat.data.McpAuthType
import com.things5.realtimechat.data.McpServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Bridge per integrare server MCP esterni con le Realtime API
 * 
 * Gestisce:
 * - Connessione ai server MCP configurati
 * - Registrazione tools disponibili
 * - Esecuzione tool calls
 * - Ritorno risultati alle Realtime API
 */
class McpBridge(
    private val servers: List<McpServerConfig>,
    private val onToolResult: (toolCallId: String, result: String) -> Unit,
    private val onError: (error: String) -> Unit,
    private val onLog: ((type: com.things5.realtimechat.data.DebugLogType, message: String, details: String?) -> Unit)? = null
) {
    private val TAG = "McpBridge"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    
    // WebSocket connections per server
    private val mcpConnections = mutableMapOf<String, WebSocket>()
    
    // HTTP server base URLs
    private val httpServers = mutableMapOf<String, String>()
    
    // MCP Session IDs per server (for stateful HTTP servers)
    private val mcpSessionIds = mutableMapOf<String, String>()
    
    // Tools cache per server
    private val availableTools = mutableMapOf<String, List<McpTool>>()
    
    // Pending tool calls in attesa di risposta
    private val pendingCalls = mutableMapOf<String, String>()
    
    // JSON-RPC request ID counter
    private var jsonRpcIdCounter = 0
    
    // Connection status per server
    private val _serverStatus = MutableStateFlow<Map<String, McpServerStatus>>(emptyMap())
    // Exposed state flow
    val serverStatus: StateFlow<Map<String, McpServerStatus>> = _serverStatus.asStateFlow()
    
    /**
     * Build URL for initial HTTP requests, appending no_auth=true when auth is NONE
     */
    private fun initialHttpUrl(server: McpServerConfig): String {
        val base = server.url
        return if (server.authType == McpAuthType.NONE && !base.contains("no_auth=")) {
            val sep = if (base.contains('?')) '&' else '?'
            val withParam = "$base${sep}no_auth=true"
            onLog?.invoke(
                com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                "Opzione no_auth attiva per ${server.name}",
                withParam
            )
            withParam
        } else base
    }
    
    /**
     * Add authentication headers and session headers based on server config
     */
    private fun Request.Builder.addAuthHeaders(server: McpServerConfig): Request.Builder {
        // Add MCP Session ID if we have one for this server
        val sessionId = mcpSessionIds[server.name]
        if (sessionId != null) {
            this.header("mcp-session-id", sessionId)
            Log.e(TAG, "📎 Added mcp-session-id header for ${server.name}: $sessionId")
        }
        
        when (server.authType) {
            McpAuthType.NONE -> {
                // No authentication
                Log.e(TAG, "No authentication for ${server.name}")
            }
            McpAuthType.BEARER -> {
                if (server.apiKey.isNotEmpty()) {
                    this.header("Authorization", "Bearer ${server.apiKey}")
                    Log.e(TAG, "Added Bearer token for ${server.name} (token starts with: ${server.apiKey.substring(0, minOf(20, server.apiKey.length))}..., length: ${server.apiKey.length})")
                } else {
                    Log.e(TAG, "WARNING: BEARER selected but apiKey is empty!")
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                        "Token Bearer mancante per ${server.name}",
                        "Config auth=BEARER ma apiKey è vuota. Imposta un token valido oppure usa ?no_auth=true per test (se il server lo consente)."
                    )
                }
            }
            McpAuthType.API_KEY -> {
                if (server.apiKey.isNotEmpty()) {
                    this.header("X-API-Key", server.apiKey)
                    Log.d(TAG, "Added API Key header for ${server.name}")
                }
            }
            McpAuthType.BASIC -> {
                if (server.username.isNotEmpty() && server.password.isNotEmpty()) {
                    val credentials = "${server.username}:${server.password}"
                    val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                    this.header("Authorization", "Basic $encoded")
                    Log.d(TAG, "Added Basic auth for ${server.name}")
                }
            }
            McpAuthType.CUSTOM -> {
                if (server.customHeaderName.isNotEmpty() && server.apiKey.isNotEmpty()) {
                    this.header(server.customHeaderName, server.apiKey)
                    Log.d(TAG, "Added custom header '${server.customHeaderName}' for ${server.name}")
                }
            }
        }
        return this
    }
    
    /**
     * Connetti a tutti i server MCP abilitati
     */
    fun connectToServers() {
        servers.filter { it.enabled }.forEach { server ->
            connectToServer(server)
        }
    }
    
    /**
     * Connetti a un singolo server MCP
     * Supporta sia WebSocket (wss://) che HTTP (https://)
     */
    private fun connectToServer(server: McpServerConfig) {
        Log.d(TAG, "Connecting to MCP server: ${server.name} at ${server.url}")
        onLog?.invoke(
            com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
            "Connessione a ${server.name}",
            "URL: ${server.url}\nAuth: ${server.authType}"
        )
        
        // Rileva il protocollo
        when {
            server.url.startsWith("wss://") || server.url.startsWith("ws://") -> {
                Log.d(TAG, "Using WebSocket protocol for ${server.name}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                    "Protocollo WebSocket per ${server.name}",
                    null
                )
                connectViaWebSocket(server)
            }
            server.url.startsWith("https://") || server.url.startsWith("http://") -> {
                Log.d(TAG, "Using HTTP protocol for ${server.name}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                    "Protocollo HTTP per ${server.name}",
                    null
                )
                connectViaHttp(server)
            }
            else -> {
                Log.e(TAG, "Unknown protocol for ${server.url}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                    "Protocollo sconosciuto per ${server.name}",
                    "URL: ${server.url}\nDeve iniziare con wss://, ws://, https:// o http://"
                )
                onError("MCP Server ${server.name}: URL deve iniziare con wss:// ws:// https:// o http://")
            }
        }
    }
    
    /**
     * Connetti via WebSocket
     */
    private fun connectViaWebSocket(server: McpServerConfig) {
        val request = Request.Builder()
            .url(server.url)
            .build()
        
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to MCP server: ${server.name}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                    "✅ Connesso a ${server.name}",
                    "Protocollo: WebSocket\nStatus: ${response.code}"
                )
                mcpConnections[server.name] = webSocket
                
                // Update status
                updateServerStatus(server.name, McpConnectionState.CONNECTED, "WebSocket")
                
                // Richiedi la lista di tools disponibili
                requestTools(webSocket, server.name)
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message from ${server.name}: $text")
                handleMcpMessage(server.name, text)
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = if (response != null) {
                    "HTTP ${response.code} ${response.message} - ${response.body?.string() ?: "No body"}"
                } else {
                    t.message ?: "Unknown error"
                }
                Log.e(TAG, "Connection failed to ${server.name}: $errorMsg", t)
                Log.e(TAG, "URL: ${server.url}")
                Log.e(TAG, "Response headers: ${response?.headers}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                    "❌ Connessione fallita a ${server.name}",
                    "URL: ${server.url}\nErrore: $errorMsg\nException: ${t.javaClass.simpleName}\nHeaders: ${response?.headers}"
                )
                mcpConnections.remove(server.name)
                
                // Update status
                updateServerStatus(server.name, McpConnectionState.ERROR, "WebSocket", errorMsg)
                
                onError("MCP Server ${server.name} connection failed: $errorMsg. Verifica che l'URL sia corretto (deve iniziare con wss://) e che il server sia raggiungibile.")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Connection closed to ${server.name}: $reason")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                    "🔌 Connessione chiusa ${server.name}",
                    "Code: $code\nReason: $reason"
                )
                mcpConnections.remove(server.name)
                
                // Update status
                updateServerStatus(server.name, McpConnectionState.DISCONNECTED, "WebSocket", reason)
            }
        }
        
        client.newWebSocket(request, listener)
    }
    
    /**
     * Connetti via HTTP con protocollo MCP completo
     */
    private fun connectViaHttp(server: McpServerConfig) {
        scope.launch {
            try {
                // Salva l'URL per usi futuri
                httpServers[server.name] = server.url
                
                // Update status
                updateServerStatus(server.name, McpConnectionState.CONNECTING, "HTTP")
                
                // Step 1: Initialize handshake
                Log.e(TAG, "Step 1: Sending initialize to ${server.name}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                    "Inizializzazione HTTP ${server.name}",
                    "Step 1: Invio richiesta initialize"
                )
                val initResponse = initializeServerHttp(server)
                
                if (initResponse != null) {
                    Log.e(TAG, "Step 2: Server initialized, sending initialized notification")
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                        "Server ${server.name} inizializzato",
                        "Step 2: Invio notifica initialized"
                    )
                    // Step 2: Send initialized notification
                    sendInitializedNotification(server)
                    
                    // Wait for server to process the initialized notification
                    kotlinx.coroutines.delay(500)
                    
                    // Step 3: Request tools list
                    Log.e(TAG, "Step 3: Requesting tools list from ${server.name}")
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                        "Richiesta tools da ${server.name}",
                        "Step 3: Invio richiesta tools/list"
                    )
                    requestToolsHttp(server)
                    
                    // Update status
                    updateServerStatus(server.name, McpConnectionState.CONNECTED, "HTTP")
                    
                    Log.d(TAG, "✅ HTTP server fully connected: ${server.name}")
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                        "✅ Connesso a ${server.name}",
                        "Protocollo: HTTP\nHandshake completato"
                    )
                } else {
                    throw Exception("Failed to initialize server")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to HTTP server ${server.name}", e)
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                    "❌ Errore connessione HTTP ${server.name}",
                    "URL: ${server.url}\nErrore: ${e.message}\nException: ${e.javaClass.simpleName}\nStack: ${e.stackTraceToString().take(500)}"
                )
                
                // Update status
                updateServerStatus(server.name, McpConnectionState.ERROR, "HTTP", e.message)
                
                onError("MCP Server ${server.name} HTTP connection failed: ${e.message}")
            }
        }
    }
    
    /**
     * Step 1: Initialize MCP server (handshake) - Supports SSE streaming
     */
    private suspend fun initializeServerHttp(server: McpServerConfig): JsonObject? {
        try {
            val requestId = ++jsonRpcIdCounter
            val jsonRpcRequest = mapOf(
                "jsonrpc" to "2.0",
                "id" to requestId,
                "method" to "initialize",
                "params" to mapOf(
                    "protocolVersion" to "2024-11-05",
                    "capabilities" to mapOf(
                        "roots" to mapOf(
                            "listChanged" to true
                        ),
                        "sampling" to emptyMap<String, Any>()
                    ),
                    "clientInfo" to mapOf(
                        "name" to "Realtime Chat Android",
                        "version" to "1.0.0"
                    )
                )
            )
            
            val json = gson.toJson(jsonRpcRequest)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url(initialHttpUrl(server))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .addAuthHeaders(server)
                .post(requestBody)
                .build()
            
            Log.e(TAG, "Sending initialize request to ${server.name}: $json")
            Log.e(TAG, "Auth type: ${server.authType}")
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.e(TAG, "Initialize response headers: ${response.headers}")
                Log.e(TAG, "Set-Cookie headers: ${response.headers("Set-Cookie")}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                    "Initialize OK ${server.name}",
                    "HTTP ${response.code} • Headers: ${response.headers}"
                )
                
                // Save MCP Session ID if present
                val sessionId = response.header("mcp-session-id")
                if (sessionId != null) {
                    mcpSessionIds[server.name] = sessionId
                    Log.e(TAG, "✅ Saved MCP Session ID for ${server.name}: $sessionId")
                } else {
                    Log.e(TAG, "⚠️ No mcp-session-id header in response from ${server.name}")
                }
                
                val contentType = response.header("Content-Type") ?: ""
                
                if (contentType.contains("text/event-stream")) {
                    // Parse SSE response
                    Log.d(TAG, "Parsing SSE response from ${server.name}")
                    val sseData = parseSseResponse(response)
                    if (sseData != null) {
                        Log.d(TAG, "SSE data parsed: $sseData")
                        val jsonResponse = gson.fromJson(sseData, JsonObject::class.java)
                        
                        if (jsonResponse.has("error")) {
                            val error = jsonResponse.getAsJsonObject("error")
                            val errorMsg = error.get("message")?.asString ?: "Unknown error"
                            Log.e(TAG, "Initialize error from ${server.name}: $errorMsg")
                            onLog?.invoke(
                                com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                                "Initialize error ${server.name}",
                                error.toString().take(500)
                            )
                            return null
                        }
                        
                        val resultObj = jsonResponse.getAsJsonObject("result")
                        if (resultObj == null) {
                            onLog?.invoke(
                                com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                                "Initialize missing result ${server.name}",
                                sseData.take(500)
                            )
                        }
                        return resultObj
                    }
                    // SSE data was null
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                        "Initialize SSE vuota ${server.name}",
                        "Content-Type: $contentType"
                    )
                } else {
                    // Parse regular JSON response
                    val body = response.body?.string() ?: "{}"
                    Log.d(TAG, "Initialize response from ${server.name}: $body")
                    
                    val jsonResponse = gson.fromJson(body, JsonObject::class.java)
                    
                    if (jsonResponse.has("error")) {
                        val error = jsonResponse.getAsJsonObject("error")
                        val errorMsg = error.get("message")?.asString ?: "Unknown error"
                        Log.e(TAG, "Initialize error from ${server.name}: $errorMsg")
                        onLog?.invoke(
                            com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                            "Initialize error ${server.name}",
                            error.toString().take(500)
                        )
                        return null
                    }
                    val resultObj = jsonResponse.getAsJsonObject("result")
                    if (resultObj == null) {
                        onLog?.invoke(
                            com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                            "Initialize missing result ${server.name}",
                            body.take(500)
                        )
                    }
                    return resultObj
                }
            } else {
                Log.e(TAG, "Initialize HTTP request failed: ${response.code} ${response.message}")
                val body = try { response.body?.string() } catch (e: Exception) { null } ?: "<no body>"
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                    "Initialize HTTP failed ${server.name}",
                    "HTTP ${response.code} ${response.message}\nHeaders: ${response.headers}\nBody: ${body.take(500)}"
                )
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during initialize", e)
            onLog?.invoke(
                com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                "Initialize exception ${server.name}",
                "${e.javaClass.simpleName}: ${e.message}\nStack: ${e.stackTraceToString().take(500)}"
            )
            return null
        }
        return null
    }
    
    /**
     * Parse Server-Sent Events (SSE) response
     */
    private fun parseSseResponse(response: Response): String? {
        try {
            response.body?.use { responseBody ->
                val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                var dataLine: String? = null
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    
                    when {
                        currentLine.startsWith("data: ") -> {
                            dataLine = currentLine.substring(6) // Remove "data: " prefix
                            Log.d(TAG, "SSE data line: $dataLine")
                        }
                        currentLine.startsWith("event: ") -> {
                            Log.d(TAG, "SSE event: ${currentLine.substring(7)}")
                        }
                        currentLine.startsWith("id: ") -> {
                            Log.d(TAG, "SSE id: ${currentLine.substring(4)}")
                        }
                        currentLine.isEmpty() && dataLine != null -> {
                            // Empty line marks end of SSE message
                            return dataLine
                        }
                    }
                }
                
                return dataLine
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SSE response", e)
        }
        return null
    }
    
    /**
     * Step 2: Send initialized notification
     */
    private suspend fun sendInitializedNotification(server: McpServerConfig) {
        try {
            val jsonRpcNotification = mapOf(
                "jsonrpc" to "2.0",
                "method" to "notifications/initialized",
                "params" to emptyMap<String, Any>()
            )
            
            val json = gson.toJson(jsonRpcNotification)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toRequestBody(mediaType)
            
            val requestBuilder = Request.Builder()
                .url(server.url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .addAuthHeaders(server)
            
            // Include MCP Session ID if available
            val sessionId = mcpSessionIds[server.name]
            if (sessionId != null) {
                requestBuilder.header("mcp-session-id", sessionId)
                Log.d(TAG, "✅ Including session ID in initialized notification for ${server.name}")
            }
            
            val request = requestBuilder.post(requestBody).build()
            
            Log.d(TAG, "Sending initialized notification to ${server.name}")
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.d(TAG, "Initialized notification sent successfully to ${server.name}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                    "Initialized notification OK ${server.name}",
                    "HTTP ${response.code}"
                )
            } else {
                Log.w(TAG, "Initialized notification returned: ${response.code}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                    "Initialized notification failed ${server.name}",
                    "HTTP ${response.code} ${response.message}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending initialized notification", e)
            onLog?.invoke(
                com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                "Initialized notification exception ${server.name}",
                "${e.javaClass.simpleName}: ${e.message}\nStack: ${e.stackTraceToString().take(500)}"
            )
        }
    }
    
    /**
     * Richiedi lista tools da server HTTP MCP (JSON-RPC 2.0)
     */
    private suspend fun requestToolsHttp(server: McpServerConfig) {
        try {
            // Crea richiesta JSON-RPC 2.0
            val requestId = ++jsonRpcIdCounter
            val jsonRpcRequest = mapOf(
                "jsonrpc" to "2.0",
                "id" to requestId,
                "method" to "tools/list",
                "params" to emptyMap<String, Any>()
            )
            val json = gson.toJson(jsonRpcRequest)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toRequestBody(mediaType)
            
            val requestBuilder = Request.Builder()
                .url(server.url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .addAuthHeaders(server)
            
            // Include MCP Session ID if available
            val sessionId = mcpSessionIds[server.name]
            if (sessionId != null) {
                requestBuilder.header("mcp-session-id", sessionId)
                Log.d(TAG, "✅ Including session ID for ${server.name}: $sessionId")
            } else {
                Log.w(TAG, "⚠️ No session ID available for ${server.name}")
            }
            
            val request = requestBuilder.post(requestBody).build()
            
            Log.e(TAG, "Sending tools/list request to ${server.name}: $json")
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val contentType = response.header("Content-Type") ?: ""
                val responseData: String
                
                if (contentType.contains("text/event-stream")) {
                    // Parse SSE response
                    Log.d(TAG, "Parsing SSE response for tools/list from ${server.name}")
                    responseData = parseSseResponse(response) ?: "{}"
                } else {
                    // Parse regular JSON response
                    responseData = response.body?.string() ?: "{}"
                }
                
                Log.d(TAG, "Tools list response from ${server.name}: $responseData")
                
                val jsonResponse = gson.fromJson(responseData, JsonObject::class.java)
                
                // Check for JSON-RPC error
                if (jsonResponse.has("error")) {
                    val error = jsonResponse.getAsJsonObject("error")
                    val errorMsg = error.get("message")?.asString ?: "Unknown error"
                    Log.e(TAG, "JSON-RPC error from ${server.name}: $errorMsg")
                    onError("MCP Server ${server.name} error: $errorMsg")
                    return
                }
                
                // Parse result
                val result = jsonResponse.getAsJsonObject("result") ?: return
                val toolsArray = result.getAsJsonArray("tools") ?: return
                
                val tools = toolsArray.map { toolElement ->
                    val tool = toolElement.asJsonObject
                    val inputSchema = tool.getAsJsonObject("inputSchema")
                    
                    McpTool(
                        name = tool.get("name")?.asString ?: "",
                        description = tool.get("description")?.asString ?: "",
                        parameters = inputSchema
                    )
                }
                
                availableTools[server.name] = tools
                
                // Update tools in status
                updateServerTools(server.name, tools)
                
                Log.d(TAG, "✅ Registered ${tools.size} tools from HTTP server ${server.name}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_TOOL_REGISTERED,
                    "Registrati ${tools.size} tool da ${server.name}",
                    tools.joinToString(limit = 10) { it.name }
                )
            } else {
                val errorBody = response.body?.string() ?: "no body"
                Log.e(TAG, "HTTP request failed: ${response.code} ${response.message}")
                Log.e(TAG, "Error response body: $errorBody")
                Log.e(TAG, "Response headers: ${response.headers}")
                onError("Failed to get tools from ${server.name}: HTTP ${response.code}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                    "❌ tools/list fallita su ${server.name}",
                    "HTTP ${response.code} ${response.message}\nHeaders: ${response.headers}\nBody: ${errorBody.take(500)}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting tools from HTTP server", e)
            onError("Error requesting tools: ${e.message}")
            onLog?.invoke(
                com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                "❌ Errore richiesta tools/list ${server.name}",
                "${e.javaClass.simpleName}: ${e.message}\nStack: ${e.stackTraceToString().take(500)}"
            )
        }
    }
    
    /**
     * Richiedi lista tools da server MCP WebSocket
     */
    private fun requestTools(webSocket: WebSocket, serverName: String) {
        val message = mapOf(
            "type" to "list_tools"
        )
        webSocket.send(gson.toJson(message))
    }
    
    /**
     * Gestisci messaggi dal server MCP
     */
    private fun handleMcpMessage(serverName: String, message: String) {
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            val type = json.get("type")?.asString ?: return
            
            when (type) {
                "tools_list" -> {
                    // Server ha inviato lista tools
                    val toolsArray = json.getAsJsonArray("tools")
                    val tools = toolsArray.map { toolElement ->
                        val tool = toolElement.asJsonObject
                        McpTool(
                            name = tool.get("name")?.asString ?: "",
                            description = tool.get("description")?.asString ?: "",
                            parameters = tool.get("parameters")?.asJsonObject
                        )
                    }
                    
                    availableTools[serverName] = tools
                    
                    // Update tools in status
                    updateServerTools(serverName, tools)
                    
                    Log.d(TAG, "Registered ${tools.size} tools from $serverName")
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_TOOL_REGISTERED,
                        "Registrati ${tools.size} tool da $serverName",
                        tools.joinToString(limit = 10) { it.name }
                    )
                }
                
                "tool_result" -> {
                    // Risultato da tool call
                    val callId = json.get("id")?.asString ?: return
                    val result = json.get("result")?.toString() ?: ""
                    
                    pendingCalls.remove(callId)
                    
                    // Ritorna risultato alle Realtime API
                    onToolResult(callId, result)
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.TOOL_RESPONSE,
                        "Risultato tool da $serverName",
                        "Call ID: $callId • Output: ${result.take(200)}"
                    )
                }
                
                "error" -> {
                    val errorMsg = json.get("error")?.asString ?: "Unknown error"
                    Log.e(TAG, "MCP Error from $serverName: $errorMsg")
                    onError("MCP Server $serverName error: $errorMsg")
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                        "Errore MCP da $serverName",
                        errorMsg
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MCP message", e)
            onError("Error parsing MCP message: ${e.message}")
            onLog?.invoke(
                com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                "Errore parsing messaggio MCP $serverName",
                "${e.javaClass.simpleName}: ${e.message}\nPayload: ${message.take(500)}"
            )
        }
    }
    
    /**
     * Esegui una tool call su un server MCP
     * 
     * @param toolName Nome del tool da chiamare
     * @param parameters Parametri per il tool
     * @param callId ID univoco della chiamata
     */
    fun executeToolCall(toolName: String, parameters: Map<String, Any>, callId: String) {
        scope.launch {
            // Trova quale server ha questo tool
            val serverName = findServerForTool(toolName)
            
            if (serverName == null) {
                onError("Tool '$toolName' not found in any MCP server")
                return@launch
            }
            
            // Verifica se è WebSocket o HTTP
            val webSocket = mcpConnections[serverName]
            val httpUrl = httpServers[serverName]
            
            when {
                webSocket != null -> {
                    // Usa WebSocket
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                        "Esecuzione tool via WS",
                        "Server: $serverName • Tool: $toolName • Call ID: $callId"
                    )
                    executeToolCallWebSocket(webSocket, serverName, toolName, parameters, callId)
                }
                httpUrl != null -> {
                    // Usa HTTP
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                        "Esecuzione tool via HTTP",
                        "Server: $serverName • Tool: $toolName • Call ID: $callId"
                    )
                    executeToolCallHttp(httpUrl, serverName, toolName, parameters, callId)
                }
                else -> {
                    onError("MCP Server '$serverName' not connected")
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                        "Server MCP non connesso",
                        "Server: $serverName • Tool: $toolName • Call ID: $callId"
                    )
                }
            }
        }
    }
    
    /**
     * Esegui tool call via WebSocket
     */
    private fun executeToolCallWebSocket(
        webSocket: WebSocket,
        serverName: String,
        toolName: String,
        parameters: Map<String, Any>,
        callId: String
    ) {
        // Converti i parametri in un JsonObject per preservare i tipi corretti
        val parametersJson = JsonObject()
        parameters.forEach { (key, value) ->
            when (value) {
                is Boolean -> parametersJson.addProperty(key, value)
                is Number -> parametersJson.addProperty(key, value)
                is String -> parametersJson.addProperty(key, value)
                else -> {
                    // Per oggetti complessi, prova a parsarli come JSON
                    try {
                        val jsonElement = gson.toJsonTree(value)
                        parametersJson.add(key, jsonElement)
                    } catch (e: Exception) {
                        // Fallback: converti in stringa
                        parametersJson.addProperty(key, value.toString())
                    }
                }
            }
        }
        
        val message = JsonObject().apply {
            addProperty("type", "tool_call")
            addProperty("id", callId)
            addProperty("name", toolName)
            add("parameters", parametersJson)
        }
        
        pendingCalls[callId] = serverName
        val json = gson.toJson(message)
        webSocket.send(json)
        
        Log.d(TAG, "🔧 Executed tool call via WebSocket: $toolName on server $serverName")
        Log.d(TAG, "Parameters: $json")
    }
    
    /**
     * Esegui tool call via HTTP (JSON-RPC 2.0)
     */
    private suspend fun executeToolCallHttp(
        url: String,
        serverName: String,
        toolName: String,
        parameters: Map<String, Any>,
        callId: String
    ) {
        try {
            // Converti i parametri in un JsonObject per preservare i tipi corretti
            val argumentsJson = JsonObject()
            parameters.forEach { (key, value) ->
                when (value) {
                    is Boolean -> argumentsJson.addProperty(key, value)
                    is Number -> argumentsJson.addProperty(key, value)
                    is String -> argumentsJson.addProperty(key, value)
                    else -> {
                        // Per oggetti complessi, prova a parsarli come JSON
                        try {
                            val jsonElement = gson.toJsonTree(value)
                            argumentsJson.add(key, jsonElement)
                        } catch (e: Exception) {
                            // Fallback: converti in stringa
                            argumentsJson.addProperty(key, value.toString())
                        }
                    }
                }
            }
            
            // Crea richiesta JSON-RPC 2.0 per chiamata tool
            val requestId = ++jsonRpcIdCounter
            val jsonRpcRequest = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", requestId)
                addProperty("method", "tools/call")
                add("params", JsonObject().apply {
                    addProperty("name", toolName)
                    add("arguments", argumentsJson)
                })
            }
            
            val json = gson.toJson(jsonRpcRequest)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toRequestBody(mediaType)
            
            // Find server config to get auth info
            val serverConfig = servers.find { it.url == url }
            
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
            
            // Add authentication headers
            if (serverConfig != null) {
                requestBuilder.addAuthHeaders(serverConfig)
            }
            
            // Include MCP Session ID if available
            val sessionId = mcpSessionIds[serverName]
            if (sessionId != null) {
                requestBuilder.header("mcp-session-id", sessionId)
                Log.d(TAG, "✅ Including session ID for tool call on $serverName")
            } else {
                Log.w(TAG, "⚠️ No session ID for tool call on $serverName")
            }
            
            val request = requestBuilder.post(requestBody).build()
            
            Log.d(TAG, "🔧 Executing tool call via HTTP (JSON-RPC): $toolName on server $serverName")
            Log.d(TAG, "Parameters types: ${parameters.map { "${it.key}:${it.value::class.simpleName}" }}")
            Log.d(TAG, "Request: $json")
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val contentType = response.header("Content-Type") ?: ""
                val responseData: String
                
                if (contentType.contains("text/event-stream")) {
                    // Parse SSE response
                    Log.d(TAG, "Parsing SSE response for tool call from $serverName")
                    responseData = parseSseResponse(response) ?: "{}"
                } else {
                    // Parse regular JSON response
                    responseData = response.body?.string() ?: "{}"
                }
                
                Log.d(TAG, "✅ Tool result from $serverName: $responseData")
                
                val jsonResponse = gson.fromJson(responseData, JsonObject::class.java)
                
                // Check for JSON-RPC error
                if (jsonResponse.has("error")) {
                    val error = jsonResponse.getAsJsonObject("error")
                    val errorMsg = error.get("message")?.asString ?: "Unknown error"
                    Log.e(TAG, "JSON-RPC error from $serverName: $errorMsg")
                    onError("Tool call error: $errorMsg")
                    onLog?.invoke(
                        com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                        "Tool call JSON-RPC error da $serverName",
                        error.toString().take(500)
                    )
                    return
                }
                
                // Parse result and return
                val result = jsonResponse.get("result")
                val resultString = if (result != null) {
                    result.toString()
                } else {
                    "{}"
                }
                
                // Ritorna il risultato
                onToolResult(callId, resultString)
            } else {
                Log.e(TAG, "❌ HTTP tool call failed: ${response.code} ${response.message}")
                val errBody = try { response.body?.string() } catch (e: Exception) { null } ?: "<no body>"
                onError("Tool call failed: HTTP ${response.code}")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                    "Tool call HTTP fallita $serverName",
                    "HTTP ${response.code} ${response.message}\nHeaders: ${response.headers}\nBody: ${errBody.take(500)}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error executing HTTP tool call", e)
            onError("Tool call error: ${e.message}")
            onLog?.invoke(
                com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                "Eccezione tool call HTTP $serverName",
                "${e.javaClass.simpleName}: ${e.message}\nStack: ${e.stackTraceToString().take(500)}"
            )
        }
    }
    
    /**
     * Trova quale server ha un determinato tool
     */
    private fun findServerForTool(toolName: String): String? {
        for ((serverName, tools) in availableTools) {
            if (tools.any { it.name == toolName }) {
                return serverName
            }
        }
        return null
    }
    
    /**
     * Validate tool schema for OpenAI compatibility
     */
    private fun isValidToolSchema(tool: McpTool): Boolean {
        try {
            val params = tool.parameters ?: return true // No params is valid
            
            // Check if parameters is a valid JSON object
            if (!params.isJsonObject) {
                Log.w(TAG, "⚠️ Tool '${tool.name}' has invalid parameters (not an object)")
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                    "Invalid tool schema: ${tool.name}",
                    "Parameters is not a JSON object"
                )
                return false
            }
            
            val paramsObj = params.asJsonObject
            val properties = paramsObj.getAsJsonObject("properties") ?: return true
            
            val invalidProperties = mutableListOf<String>()
            
            // Validate each property
            for ((propName, propValue) in properties.entrySet()) {
                if (!propValue.isJsonObject) continue
                
                val propObj = propValue.asJsonObject
                val type = propObj.get("type")?.asString
                
                // Arrays must have 'items' defined
                if (type == "array") {
                    if (!propObj.has("items")) {
                        invalidProperties.add("$propName (array without items)")
                        Log.w(TAG, "⚠️ Tool '${tool.name}' property '$propName' is array without 'items'")
                        Log.w(TAG, "   Schema: $propObj")
                        Log.w(TAG, "   Fix: Add \"items\": {\"type\": \"string\"} (or appropriate type)")
                    }
                }
            }
            
            if (invalidProperties.isNotEmpty()) {
                val details = "Invalid properties:\n" + invalidProperties.joinToString("\n") { "  - $it" } +
                             "\n\nFull schema:\n${params.toString().take(500)}"
                onLog?.invoke(
                    com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                    "⚠️ Invalid tool schema: ${tool.name}",
                    details
                )
                return false
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating tool '${tool.name}' schema", e)
            onLog?.invoke(
                com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                "Error validating tool: ${tool.name}",
                e.message ?: "Unknown error"
            )
            return false
        }
    }
    
    /**
     * Ottieni tutti i tools disponibili da tutti i server
     * Formattato per OpenAI Realtime API
     * Filters out tools with invalid schemas
     */
    fun getAllTools(): List<Map<String, Any>> {
        val allTools = mutableListOf<Map<String, Any>>()
        var validCount = 0
        var invalidCount = 0
        
        for ((serverName, tools) in availableTools) {
            tools.forEach { tool ->
                if (isValidToolSchema(tool)) {
                    allTools.add(mapOf(
                        "type" to "function",
                        "name" to tool.name,
                        "description" to tool.description,
                        "parameters" to (tool.parameters ?: JsonObject())
                    ))
                    validCount++
                } else {
                    Log.w(TAG, "❌ Skipping tool '${tool.name}' from '$serverName' due to invalid schema")
                    invalidCount++
                }
            }
        }
        
        if (invalidCount > 0) {
            Log.w(TAG, "⚠️ Filtered out $invalidCount invalid tools, returning $validCount valid tools")
        }
        
        return allTools
    }
    
    /**
     * Disconnetti da tutti i server MCP
     */
    fun disconnect() {
        mcpConnections.values.forEach { ws ->
            ws.close(1000, "Client disconnect")
        }
        mcpConnections.clear()
        availableTools.clear()
        pendingCalls.clear()
        // Close HTTP MCP sessions if any
        if (httpServers.isNotEmpty()) {
            servers.forEach { server ->
                val url = httpServers[server.name]
                val sessionId = mcpSessionIds[server.name]
                if (url != null && sessionId != null) {
                    scope.launch {
                        try {
                            val requestBuilder = Request.Builder()
                                .url(url)
                                .header("Accept", "application/json, text/event-stream")
                                .delete()
                            requestBuilder.addAuthHeaders(server)
                            val request = requestBuilder.build()
                            val response = client.newCall(request).execute()
                            if (response.isSuccessful) {
                                Log.d(TAG, "HTTP MCP session closed for ${server.name}")
                                onLog?.invoke(
                                    com.things5.realtimechat.data.DebugLogType.MCP_CONNECTION,
                                    "Sessione MCP HTTP chiusa",
                                    "Server: ${server.name} • Session: $sessionId"
                                )
                            } else {
                                Log.w(TAG, "Failed to close MCP session for ${server.name}: ${response.code}")
                                onLog?.invoke(
                                    com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                                    "Chiusura sessione MCP fallita ${server.name}",
                                    "HTTP ${response.code} ${response.message}"
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error closing MCP HTTP session for ${server.name}", e)
                            onLog?.invoke(
                                com.things5.realtimechat.data.DebugLogType.MCP_ERROR,
                                "Errore chiusura sessione MCP ${server.name}",
                                "${e.javaClass.simpleName}: ${e.message}"
                            )
                        } finally {
                            mcpSessionIds.remove(server.name)
                            httpServers.remove(server.name)
                        }
                    }
                }
            }
        }
        _serverStatus.value = emptyMap()
    }
    
    /**
     * Update server connection status
     */
    private fun updateServerStatus(
        serverName: String,
        state: McpConnectionState,
        protocol: String,
        errorMessage: String? = null
    ) {
        val currentStatus = _serverStatus.value.toMutableMap()
        val existingStatus = currentStatus[serverName]
        
        currentStatus[serverName] = McpServerStatus(
            serverName = serverName,
            connectionState = state,
            protocol = protocol,
            tools = existingStatus?.tools ?: emptyList(),
            errorMessage = errorMessage,
            lastUpdate = System.currentTimeMillis()
        )
        
        _serverStatus.value = currentStatus
    }
    
    /**
     * Update server tools
     */
    private fun updateServerTools(serverName: String, tools: List<McpTool>) {
        val currentStatus = _serverStatus.value.toMutableMap()
        val existingStatus = currentStatus[serverName]
        
        if (existingStatus != null) {
            currentStatus[serverName] = existingStatus.copy(
                tools = tools,
                lastUpdate = System.currentTimeMillis()
            )
            _serverStatus.value = currentStatus
        }
    }
}

/**
 * Rappresenta un tool disponibile su un server MCP
 */
data class McpTool(
    val name: String,
    val description: String,
    val parameters: JsonObject?
)

/**
 * Connection state of an MCP server
 */
enum class McpConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Status of an MCP server
 */
data class McpServerStatus(
    val serverName: String,
    val connectionState: McpConnectionState,
    val protocol: String, // "WebSocket" or "HTTP"
    val tools: List<McpTool>,
    val errorMessage: String? = null,
    val lastUpdate: Long = System.currentTimeMillis()
)
