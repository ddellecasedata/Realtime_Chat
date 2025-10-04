package com.things5.realtimechat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.things5.realtimechat.audio.AudioManager
import com.things5.realtimechat.data.AppSettings
import com.things5.realtimechat.data.ConnectionQuality
import com.things5.realtimechat.data.DebugLogEntry
import com.things5.realtimechat.data.DebugLogType
import com.things5.realtimechat.data.MainScreenState
import com.things5.realtimechat.data.McpAuthType
import com.things5.realtimechat.data.McpServerConfig
import com.things5.realtimechat.data.RealtimeDebugState
import com.things5.realtimechat.data.SessionState
import com.things5.realtimechat.data.SettingsRepository
import com.things5.realtimechat.data.Things5ConnectionStatus
import com.things5.realtimechat.network.Things5AuthService
import com.things5.realtimechat.network.RealtimeClient
import com.things5.realtimechat.network.RealtimeEvent
import com.things5.realtimechat.network.McpBridge
import com.things5.realtimechat.network.McpServerStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    
    private val settingsRepository = SettingsRepository(application)
    private var realtimeClient: RealtimeClient? = null
    private val audioManager = AudioManager()
    private var mcpBridge: McpBridge? = null
    private var isAssistantSpeaking = false
    private val things5AuthService = Things5AuthService()
    
    init {
        // Initialize Things5 authentication at startup if enabled
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settingsFlow.first()
                initializeThings5Authentication(settings)
            } catch (e: Exception) {
                Log.e(TAG, "Error during Things5 startup authentication", e)
            }
        }
        
        // Observe settings changes and re-authenticate when Things5 config changes
        viewModelScope.launch {
            var previousConfig: com.things5.realtimechat.data.Things5Config? = null
            settingsRepository.settingsFlow.collect { settings ->
                if (previousConfig != null && previousConfig != settings.things5Config) {
                    Log.d(TAG, "🔄 Things5 config changed, re-authenticating...")
                    initializeThings5Authentication(settings)
                }
                previousConfig = settings.things5Config
            }
        }
    }
    
    /**
     * Initialize or re-initialize Things5 authentication
     */
    private suspend fun initializeThings5Authentication(settings: AppSettings) {
        if (settings.things5Config.enabled && 
            settings.things5Config.username.isNotEmpty() && 
            settings.things5Config.password.isNotEmpty()) {
            
            Log.d(TAG, "🚀 Initializing Things5 authentication...")
            Log.d(TAG, "   URL: ${settings.things5Config.serverUrl}")
            Log.d(TAG, "   Username: ${settings.things5Config.username}")
            
            val authResult = things5AuthService.testConnection(settings.things5Config)
            Log.d(TAG, "Things5 authentication result: $authResult")
            
            if (authResult == Things5ConnectionStatus.CONNECTED) {
                Log.d(TAG, "✅ Things5 ready with valid token")
            } else {
                Log.w(TAG, "⚠️ Things5 authentication failed: $authResult")
            }
        } else {
            Log.d(TAG, "⚠️ Things5 not enabled or credentials missing")
        }
    }
    
    /**
     * Build structured prompt following realtime model best practices
     */
    private fun buildRealtimePrompt(): String {
        // Get available tools from MCP Bridge
        val tools = mcpBridge?.getAllTools() ?: emptyList()
        
        val toolsSection = if (tools.isNotEmpty()) {
            val toolsList = tools.joinToString("\n") { tool ->
                val name = tool["name"] ?: "unknown"
                val description = tool["description"] ?: "No description"
                "  - $name: $description"
            }
            
            """
            ## Available Tools
            You have access to the following tools:
            $toolsList
            
            ## Using Tools
            - When you need to use a tool, briefly tell the user what you're doing.
            - Sample preambles (vary):
              - "Controllo subito."
              - "Verifico per te."
              - "Un attimo, cerco l'informazione."
            - Then immediately call the appropriate tool.
            - Use the tool description to understand when to use it.
            """.trimIndent()
        } else {
            """
            # Tools
            - When you need to use a tool, briefly tell the user what you're doing.
            - Sample preambles (vary):
              - "Controllo subito."
              - "Verifico per te."
              - "Un attimo, cerco l'informazione."
            - Then immediately call the tool.
            """.trimIndent()
        }
        
        return """
            # Role & Objective
            You are a helpful, intelligent voice assistant for a mobile app.
            Your goal is to assist users naturally through voice conversation.
            
            # Personality & Tone
            ## Personality
            Friendly, approachable, and efficient.
            
            ## Tone
            Warm, concise, confident. Never robotic or fawning.
            
            ## Length
            Keep responses to 2-3 sentences per turn.
            
            ## Pacing
            Deliver audio responses at a natural, slightly quick pace.
            Do not sound rushed, but keep things moving.
            
            # Language
            - Respond in Italian by default.
            - Match the user's language if they switch.
            - For non-Italian, use standard dialect.
            
            # Unclear Audio
            - Only respond to clear audio or text.
            - If audio is unclear/partial/noisy/silent, ask for clarification in Italian.
            - Sample phrases (vary, don't repeat):
              - "Scusa, non ho capito. Puoi ripetere?"
              - "C'è del rumore di fondo. Ripeti l'ultima parte."
              - "Ho sentito solo una parte. Cosa hai detto dopo ___?"
            
            $toolsSection
            
            # Instructions
            - Be natural and conversational.
            - If interrupted, stop speaking immediately.
            - Use tools when appropriate to help the user.
            - Confirm before taking any destructive actions.
            - Keep conversations efficient and helpful.
            
            # Variety
            DO NOT repeat the same sentence twice. Vary your responses to sound natural.
        """.trimIndent()
    }
    
    // UI State
    private val _uiState = MutableStateFlow(MainScreenState())
    val uiState: StateFlow<MainScreenState> = _uiState.asStateFlow()
    
    // Settings
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    // MCP Server Status
    private val _mcpServerStatus = MutableStateFlow<Map<String, McpServerStatus>>(emptyMap())
    val mcpServerStatus: StateFlow<Map<String, McpServerStatus>> = _mcpServerStatus.asStateFlow()
    
    // Debug State
    private val _debugState = MutableStateFlow(RealtimeDebugState())
    val debugState: StateFlow<RealtimeDebugState> = _debugState.asStateFlow()
    
    // Debug log buffer (max 500 entries)
    private val maxLogEntries = 500
    
    // Transcript accumulator
    private val transcriptBuilder = StringBuilder()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _settings.value = settings
            }
        }
    }
    
    /**
     * Initialize the Realtime session
     */
    fun initializeSession() {
        viewModelScope.launch {
            try {
                val currentSettings = _settings.first()
                
                if (currentSettings.openAiApiKey.isEmpty()) {
                    updateState(errorMessage = "API Key non configurata. Vai nelle impostazioni.")
                    return@launch
                }
                
                updateState(sessionState = SessionState.Connecting)
                
                // Create client
                realtimeClient = RealtimeClient(currentSettings.openAiApiKey)
                
                // Observe events
                observeRealtimeEvents()
                
                // Debug: Log current configuration
                Log.d(TAG, "🔍 Current Settings (BEFORE cleanup):")
                Log.d(TAG, "   MCP Servers count: ${currentSettings.mcpServers.size}")
                currentSettings.mcpServers.forEachIndexed { index, server ->
                    Log.d(TAG, "   Server $index: ${server.name} -> ${server.url} (${server.authType})")
                }
                
                // Clean up old Things5 servers that might conflict (SYNCHRONOUS)
                cleanupOldThings5Servers(currentSettings)
                
                // IMPORTANT: Reload settings after cleanup
                delay(500) // Wait for cleanup to complete
                val updatedSettings = settingsRepository.settingsFlow.first()
                
                Log.d(TAG, "🔍 Current Settings (AFTER cleanup):")
                Log.d(TAG, "   MCP Servers count: ${updatedSettings.mcpServers.size}")
                updatedSettings.mcpServers.forEachIndexed { index, server ->
                    Log.d(TAG, "   Server $index: ${server.name} -> ${server.url} (${server.authType})")
                }
                
                Log.d(TAG, "🔍 Current Things5 Config:")
                Log.d(TAG, "   Enabled: ${updatedSettings.things5Config.enabled}")
                Log.d(TAG, "   URL: ${updatedSettings.things5Config.serverUrl}")
                Log.d(TAG, "   Username: ${updatedSettings.things5Config.username}")
                Log.d(TAG, "   Password: [${updatedSettings.things5Config.password.length} chars]")
                
                // Re-authenticate Things5 if enabled and no valid token
                if (updatedSettings.things5Config.enabled && 
                    updatedSettings.things5Config.username.isNotEmpty() && 
                    updatedSettings.things5Config.password.isNotEmpty()) {
                    
                    val currentToken = things5AuthService.getCurrentAccessToken()
                    if (currentToken == null) {
                        Log.d(TAG, "🔐 No valid token, re-authenticating Things5...")
                        val authResult = things5AuthService.testConnection(updatedSettings.things5Config)
                        Log.d(TAG, "Things5 re-authentication result: $authResult")
                    } else {
                        Log.d(TAG, "✅ Things5 token already available (from startup)")
                    }
                } else {
                    Log.d(TAG, "⚠️ Things5 not enabled or credentials missing")
                }
                
                // Build complete MCP server list including Things5 if enabled (use UPDATED settings)
                val allMcpServers = buildMcpServerList(updatedSettings)
                
                Log.d(TAG, "═══════════════════════════════════════════")
                Log.d(TAG, "Total MCP Servers: ${allMcpServers.size}")
                allMcpServers.forEachIndexed { index, server ->
                    Log.d(TAG, "  [$index] ${server.name}")
                    Log.d(TAG, "      URL: ${server.url}")
                    Log.d(TAG, "      Enabled: ${server.enabled}")
                    Log.d(TAG, "      Auth: ${server.authType}")
                    if (server.authType == McpAuthType.BEARER) {
                        Log.d(TAG, "      Token: ${server.apiKey.take(20)}...")
                    }
                }
                Log.d(TAG, "═══════════════════════════════════════════")
                
                if (allMcpServers.isNotEmpty()) {
                    Log.d(TAG, "Initializing MCP Bridge...")
                    initializeMcpBridge(allMcpServers)
                } else {
                    Log.w(TAG, "No MCP servers configured, skipping MCP Bridge initialization")
                }
                
                // Connect
                realtimeClient?.connect()
                
                // Wait for connection
                delay(2000)
                
                // Wait for MCP tools to be available (with timeout)
                Log.d(TAG, "⏳ Waiting for MCP tools to be registered...")
                var tools: List<Map<String, Any>>? = null
                var attempts = 0
                val maxAttempts = 15 // 15 seconds max
                
                while (attempts < maxAttempts) {
                    tools = mcpBridge?.getAllTools()
                    val toolCount = tools?.size ?: 0
                    
                    if (toolCount > 0) {
                        Log.d(TAG, "✅ Got $toolCount MCP tools!")
                        tools?.forEach { tool ->
                            Log.d(TAG, "   - ${tool["name"]}")
                        }
                        break
                    }
                    
                    Log.d(TAG, "   Attempt ${attempts + 1}/$maxAttempts: No tools yet, waiting...")
                    delay(1000)
                    attempts++
                }
                
                if (tools.isNullOrEmpty()) {
                    Log.w(TAG, "⚠️ No MCP tools available after ${maxAttempts}s")
                    Log.w(TAG, "⚠️ Realtime API will have NO TOOLS")
                } else {
                    Log.d(TAG, "✅ Total tools available: ${tools.size}")
                    Log.d(TAG, "=".repeat(60))
                    Log.d(TAG, "📤 SENDING TOOLS TO REALTIME API:")
                    Log.d(TAG, "=".repeat(60))
                    tools.forEachIndexed { index, tool ->
                        Log.d(TAG, "  [$index] ${tool["name"]}")
                        Log.d(TAG, "      Type: ${tool["type"]}")
                        Log.d(TAG, "      Description: ${tool["description"]}")
                        val params = tool["parameters"]
                        if (params != null) {
                            Log.d(TAG, "      Parameters: ${params.toString().take(200)}")
                        }
                    }
                    Log.d(TAG, "=".repeat(60))
                }
                
                Log.d(TAG, "🔄 Calling realtimeClient.updateSession()...")
                Log.d(TAG, "   Tools count: ${tools?.size ?: 0}")
                Log.d(TAG, "   Tools is null: ${tools == null}")
                
                // Configure session usando semantic_vad per interruzioni naturali
                // Seguendo le best practices per prompting realtime models
                realtimeClient?.updateSession(
                    instructions = buildRealtimePrompt(),
                    voice = "alloy",
                    turnDetection = mapOf(
                        "type" to "semantic_vad"  // Semantic VAD per interruzioni naturali
                    ),
                    tools = tools
                )
                
                Log.d(TAG, "✅ updateSession() called with ${tools?.size ?: 0} tools")
                
                // Initialize audio playback
                val playbackInit = audioManager.initPlayback()
                Log.d(TAG, "Audio playback initialized: $playbackInit")
                
                updateState(sessionState = SessionState.Connected)
                
                // Update debug state
                addDebugLog(DebugLogType.CONNECTION, "Sessione connessa con successo")
                updateDebugState(
                    isConnected = true,
                    connectionQuality = ConnectionQuality.GOOD,
                    model = "gpt-realtime",
                    usageLimitReached = false
                )
                
                Log.d(TAG, "==================================")
                Log.d(TAG, "✅ SESSION READY - FULL-DUPLEX MODE")
                Log.d(TAG, "==================================")
                Log.d(TAG, "📊 Connection status:")
                Log.d(TAG, "  ✅ WebSocket: ${realtimeClient?.isConnected?.value}")
                Log.d(TAG, "  ✅ Audio playback: $playbackInit")
                Log.d(TAG, "  ✅ Server VAD: enabled (500ms silence)")
                Log.d(TAG, "  ✅ MCP tools: ${tools?.size ?: 0} available")
                Log.d(TAG, "==================================")
                Log.d(TAG, "👉 Premi 'Parla' e inizia a parlare")
                Log.d(TAG, "👉 L'assistente risponderà automaticamente")
                Log.d(TAG, "==================================")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing session", e)
                addDebugLog(DebugLogType.ERROR, "Errore inizializzazione", e.message)
                updateDebugState(isConnected = false, lastError = e.message)
                updateState(
                    sessionState = SessionState.Error(e.message ?: "Unknown error"),
                    errorMessage = "Errore durante l'inizializzazione: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Clean up old Things5 servers that might conflict with the integration
     */
    private fun cleanupOldThings5Servers(settings: AppSettings) {
        val conflictingServers = settings.mcpServers.filter { server ->
            server.name.lowercase().contains("things5") || 
            server.url.contains("things5.digital") ||
            server.name == "things5"
        }
        
        if (conflictingServers.isNotEmpty()) {
            Log.w(TAG, "🧹 Found ${conflictingServers.size} conflicting Things5 servers, removing them:")
            conflictingServers.forEach { server ->
                Log.w(TAG, "   Removing: ${server.name} -> ${server.url}")
            }
            
            viewModelScope.launch {
                conflictingServers.forEach { server ->
                    settingsRepository.removeMcpServer(server.name)
                }
            }
        }
    }
    
    /**
     * Build complete MCP server list including Things5 if enabled
     */
    private fun buildMcpServerList(settings: AppSettings): List<McpServerConfig> {
        val servers = settings.mcpServers.toMutableList()
        
        // Add Things5 MCP server if enabled
        if (settings.things5Config.enabled && 
            settings.things5Config.username.isNotEmpty() && 
            settings.things5Config.password.isNotEmpty()) {
            
            // Get current access token (should be available after authentication)
            val accessToken = things5AuthService.getCurrentAccessToken()
            
            if (accessToken != null) {
                val things5Server = McpServerConfig(
                    name = "Things5 Integration",
                    url = settings.things5Config.serverUrl,
                    enabled = true,
                    authType = McpAuthType.BEARER,
                    apiKey = accessToken
                )
                
                // Remove any existing Things5 servers to avoid duplicates
                servers.removeAll { it.name == "Things5 IoT" || it.name == "Things5 Integration" || it.name == "things5" }
                servers.add(things5Server)
                
                Log.d(TAG, "✅ Added Things5 MCP server with Bearer token")
                Log.d(TAG, "   Name: ${things5Server.name}")
                Log.d(TAG, "   URL: ${things5Server.url}")
                Log.d(TAG, "   Auth: ${things5Server.authType}")
                Log.d(TAG, "   Enabled: ${things5Server.enabled}")
                Log.d(TAG, "   Token: ${accessToken.take(20)}...")
            } else {
                Log.w(TAG, "⚠️ Things5 enabled but no valid token available after authentication")
                Log.w(TAG, "   This means the token was not obtained during authentication")
            }
        } else {
            Log.d(TAG, "⚠️ Things5 NOT added to server list")
            Log.d(TAG, "   Enabled: ${settings.things5Config.enabled}")
            Log.d(TAG, "   Username empty: ${settings.things5Config.username.isEmpty()}")
            Log.d(TAG, "   Password empty: ${settings.things5Config.password.isEmpty()}")
        }
        
        return servers.filter { it.enabled }
    }
    
    /**
     * Initialize MCP Bridge and connect to servers
     */
    private fun initializeMcpBridge(servers: List<McpServerConfig>) {
        try {
            mcpBridge = McpBridge(
                servers = servers,
                onToolResult = { callId, result ->
                    // Invia il risultato del tool alle Realtime API
                    realtimeClient?.sendToolResult(callId, result)
                    addDebugLog(
                        DebugLogType.TOOL_RESPONSE,
                        "Risposta tool ricevuta",
                        "Call ID: $callId • Result: ${result.take(200)}"
                    )
                    Log.d(TAG, "Tool result sent for call $callId")
                },
                onError = { error ->
                    Log.e(TAG, "MCP Bridge error: $error")
                    addDebugLog(DebugLogType.MCP_ERROR, "Errore MCP Bridge", error)
                    updateState(errorMessage = "MCP Error: $error")
                },
                onLog = { type, message, details ->
                    addDebugLog(type, message, details)
                }
            )
            
            // Passa la configurazione tool al bridge
            viewModelScope.launch {
                val settings = settingsRepository.settingsFlow.first()
                mcpBridge?.setToolsConfiguration(settings.mcpToolsConfig)
                Log.d(TAG, "Tools configuration applied to MCP Bridge")
            }
            
            // Connetti ai server MCP
            mcpBridge?.connectToServers()
            
            // Observe MCP server status
            observeMcpStatus()
            
            // Observe tool configuration changes
            observeToolConfigChanges()
            
            Log.d(TAG, "MCP Bridge initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MCP Bridge", e)
            updateState(errorMessage = "Errore MCP: ${e.message}")
        }
    }
    
    /**
     * Observe MCP server status changes
     */
    private fun observeMcpStatus() {
        viewModelScope.launch {
            mcpBridge?.serverStatus?.collect { status ->
                _mcpServerStatus.value = status
                Log.d(TAG, "MCP Status updated: ${status.keys}")
            }
        }
    }
    
    /**
     * Observe tool configuration changes and update MCP Bridge
     */
    private fun observeToolConfigChanges() {
        viewModelScope.launch {
            settingsRepository.settingsFlow
                .map { it.mcpToolsConfig }
                .distinctUntilChanged()
                .collect { toolsConfig ->
                    Log.d(TAG, "🔧 Tool configuration changed, updating MCP Bridge...")
                    mcpBridge?.setToolsConfiguration(toolsConfig)
                }
        }
    }
    
    /**
     * Close the Realtime session
     */
    fun closeSession() {
        viewModelScope.launch {
            try {
                stopAudioRecording()
                audioManager.stopPlayback()
                realtimeClient?.disconnect()
                realtimeClient = null
                mcpBridge?.disconnect()
                mcpBridge = null
                
                updateState(sessionState = SessionState.Idle)
                transcriptBuilder.clear()
                
                addDebugLog(DebugLogType.CONNECTION, "Sessione chiusa")
                updateDebugState(isConnected = false)
                
                Log.d(TAG, "Session closed")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing session", e)
            }
        }
    }
    
    /**
     * Start audio recording and streaming
     */
    fun startAudioRecording() {
        viewModelScope.launch {
            if (realtimeClient?.isConnected?.value != true) {
                updateState(errorMessage = "Sessione non connessa")
                return@launch
            }
            
            val started = audioManager.startRecording()
            if (started) {
                // Se l'assistente sta parlando, interrompilo
                if (isAssistantSpeaking) {
                    audioManager.stopPlayback()
                    realtimeClient?.cancelResponse()
                    isAssistantSpeaking = false
                    Log.d(TAG, "⏯️ User interrupting - cancelling active response")
                }
                
                updateState(isRecording = true)
                Log.d(TAG, "🎤 Audio recording started - semantic_vad will handle turn detection")
                
                // Stream audio chunks to the API in real-time
                var chunkCount = 0
                viewModelScope.launch {
                    audioManager.audioChunks.collect { audioBase64 ->
                        chunkCount++
                        Log.v(TAG, "📤 Sending audio chunk #$chunkCount (${audioBase64.length} chars)")
                        
                        // Mostra feedback invio audio
                        updateState(isSendingAudio = true, lastDataSent = "Audio")
                        
                        realtimeClient?.sendAudio(audioBase64)
                        
                        // Update debug stats
                        incrementDebugCounter(audioChunksSent = 1)
                        if (chunkCount % 10 == 0) {
                            addDebugLog(DebugLogType.AUDIO_SENT, "Inviati $chunkCount chunk audio")
                        }
                        
                        // Nascondi feedback dopo breve delay
                        kotlinx.coroutines.delay(100)
                        updateState(isSendingAudio = false)
                    }
                    Log.d(TAG, "✅ Total audio chunks sent: $chunkCount")
                }
            } else {
                updateState(errorMessage = "Impossibile avviare la registrazione audio")
            }
        }
    }
    
    /**
     * Stop audio recording
     * Semantic VAD gestisce automaticamente il commit quando rileva la fine del parlato
     */
    fun stopAudioRecording() {
        viewModelScope.launch {
            audioManager.stopRecording()
            updateState(isRecording = false)
            
            Log.d(TAG, "⏹️ Recording stopped - semantic VAD will finalize any pending speech")
        }
    }
    
    /**
     * Send an image to the conversation
     */
    fun sendImage(imageBase64: String) {
        viewModelScope.launch {
            try {
                if (realtimeClient?.isConnected?.value != true) {
                    updateState(errorMessage = "Sessione non connessa")
                    return@launch
                }
                
                // Add to captured images
                val currentImages = _uiState.value.capturedImages.toMutableList()
                currentImages.add(imageBase64)
                updateState(capturedImages = currentImages)
                
                // Mostra feedback invio
                updateState(isSendingImage = true, lastDataSent = "Immagine")
                
                // Send to API
                realtimeClient?.sendImage(imageBase64)
                
                // Update debug stats
                incrementDebugCounter(imagesProcessed = 1)
                addDebugLog(DebugLogType.IMAGE_SENT, "Immagine inviata")
                
                // Trigger a response
                realtimeClient?.createResponse()
                
                // Nascondi feedback dopo 1 secondo
                kotlinx.coroutines.delay(1000)
                updateState(isSendingImage = false)
                
                Log.d(TAG, "Image sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending image", e)
                updateState(errorMessage = "Errore durante l'invio dell'immagine: ${e.message}")
            }
        }
    }
    
    /**
     * Clear captured images
     */
    fun clearImages() {
        updateState(capturedImages = emptyList())
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        updateState(errorMessage = null)
    }
    
    /**
     * Observe events from the Realtime API
     */
    private fun observeRealtimeEvents() {
        viewModelScope.launch {
            realtimeClient?.events?.collect { event ->
                Log.d(TAG, "📨 Event received: ${event::class.simpleName}")
                when (event) {
                    is RealtimeEvent.Connected -> {
                        Log.d(TAG, "✅ Connected to Realtime API")
                    }
                    is RealtimeEvent.Disconnected -> {
                        Log.w(TAG, "❌ Disconnected from Realtime API")
                        updateState(sessionState = SessionState.Idle)
                    }
                    is RealtimeEvent.InputAudioBufferSpeechStarted -> {
                        Log.d(TAG, "🗣️ User started speaking (detected by semantic VAD)")
                        
                        // Se l'assistente sta parlando, interrompilo automaticamente
                        if (isAssistantSpeaking) {
                            Log.d(TAG, "⏯️ Auto-interrupting assistant - user is speaking")
                            audioManager.stopPlayback()
                            realtimeClient?.cancelResponse()
                            isAssistantSpeaking = false
                        }
                    }
                    is RealtimeEvent.InputAudioBufferSpeechStopped -> {
                        Log.d(TAG, "🤐 User stopped speaking - semantic VAD will commit")
                    }
                    is RealtimeEvent.InputAudioBufferCommitted -> {
                        Log.d(TAG, "✅ Semantic VAD committed audio - response will follow")
                    }
                    is RealtimeEvent.AudioDelta -> {
                        Log.v(TAG, "🔊 Audio delta received: ${event.delta.length} bytes")
                        isAssistantSpeaking = true
                        audioManager.playAudio(event.delta)
                        incrementDebugCounter(audioChunksReceived = 1)
                    }
                    is RealtimeEvent.AudioDone -> {
                        Log.d(TAG, "✅ Audio response completed")
                        isAssistantSpeaking = false
                    }
                    is RealtimeEvent.TextDelta -> {
                        Log.d(TAG, "📝 Text delta: ${event.delta}")
                        // Append text delta to transcript
                        transcriptBuilder.append(event.delta)
                        updateState(transcript = transcriptBuilder.toString())
                    }
                    is RealtimeEvent.TextDone -> {
                        Log.d(TAG, "✅ Text complete")
                        // Final text received
                        transcriptBuilder.append("\n\n")
                        updateState(transcript = transcriptBuilder.toString())
                    }
                    is RealtimeEvent.ResponseDone -> {
                        Log.d(TAG, "✅ Response completed")
                    }
                    is RealtimeEvent.ToolCall -> {
                        // L'assistente vuole usare un tool
                        Log.d(TAG, "🔧 Tool call: ${event.toolName} with params: ${event.parameters}")
                        
                        // Mostra feedback tool call
                        updateState(lastDataSent = "Tool: ${event.toolName}")
                        
                        // Update debug stats
                        incrementDebugCounter(toolCallsExecuted = 1)
                        addDebugLog(
                            DebugLogType.TOOL_CALL,
                            "Tool: ${event.toolName}",
                            "Parametri: ${event.parameters}"
                        )
                        
                        mcpBridge?.executeToolCall(
                            toolName = event.toolName,
                            parameters = event.parameters,
                            callId = event.callId
                        )
                    }
                    is RealtimeEvent.Error -> {
                        // Ignora errori benigni di cancellazione (risposta già completata)
                        val isBenignCancellationError = event.message.contains("Cancellation failed", ignoreCase = true) &&
                                event.message.contains("no active response", ignoreCase = true)
                        
                        if (isBenignCancellationError) {
                            Log.d(TAG, "⚠️ Cancellation error (response already completed): ${event.message}")
                            // Non incrementare il contatore errori per questo
                        } else {
                            Log.e(TAG, "❌ Realtime API error: ${event.message}")
                            incrementDebugCounter(errors = 1)
                            addDebugLog(DebugLogType.ERROR, "Errore API", event.message)
                        }
                        
                        // Non mostrare errore nell'UI per cancellazioni benigne
                        if (!isBenignCancellationError) {
                            val limit = event.message.contains("rate limit", ignoreCase = true) ||
                                    event.message.contains("quota", ignoreCase = true) ||
                                    event.message.contains("limit", ignoreCase = true)
                            updateDebugState(lastError = event.message, usageLimitReached = limit)
                            updateState(errorMessage = "API Error: ${event.message}")
                        }
                    }
                    else -> {
                        // Handle other events
                        Log.d(TAG, "Received event: $event")
                    }
                }
            }
        }
        
        // Also observe errors
        viewModelScope.launch {
            realtimeClient?.errors?.collect { error ->
                Log.e(TAG, "Realtime client error: $error")
                updateState(errorMessage = error)
            }
        }
    }
    
    /**
     * Update UI state
     */
    private fun updateState(
        sessionState: SessionState = _uiState.value.sessionState,
        isRecording: Boolean = _uiState.value.isRecording,
        transcript: String = _uiState.value.transcript,
        capturedImages: List<String> = _uiState.value.capturedImages,
        errorMessage: String? = _uiState.value.errorMessage,
        isSendingAudio: Boolean = _uiState.value.isSendingAudio,
        isSendingImage: Boolean = _uiState.value.isSendingImage,
        lastDataSent: String? = _uiState.value.lastDataSent
    ) {
        _uiState.value = MainScreenState(
            sessionState = sessionState,
            isRecording = isRecording,
            transcript = transcript,
            capturedImages = capturedImages,
            errorMessage = errorMessage,
            isSendingAudio = isSendingAudio,
            isSendingImage = isSendingImage,
            lastDataSent = lastDataSent
        )
    }
    
    /**
     * Clear conversation and context
     */
    fun clearConversation() {
        viewModelScope.launch {
            try {
                // Clear transcript
                transcriptBuilder.clear()
                updateState(transcript = "", capturedImages = emptyList())
                
                // Clear conversation on server
                realtimeClient?.clearConversation()
                
                addDebugLog(DebugLogType.INFO, "Conversazione e contesto cancellati")
                
                Log.d(TAG, "Conversation cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing conversation", e)
                updateState(errorMessage = "Errore durante la cancellazione: ${e.message}")
            }
        }
    }
    
    /**
     * Clear debug logs
     */
    fun clearDebugLogs() {
        updateDebugState(logs = emptyList())
        addDebugLog(DebugLogType.INFO, "Log cancellati")
    }
    
    /**
     * Get MCP Bridge instance (for external access)
     */
    fun getMcpBridge(): McpBridge? {
        return mcpBridge
    }
    
    /**
     * Add debug log entry
     */
    private fun addDebugLog(type: DebugLogType, message: String, details: String? = null) {
        val currentLogs = _debugState.value.logs.toMutableList()
        currentLogs.add(DebugLogEntry(type = type, message = message, details = details))
        
        // Keep only last maxLogEntries
        val trimmedLogs = if (currentLogs.size > maxLogEntries) {
            currentLogs.takeLast(maxLogEntries)
        } else {
            currentLogs
        }
        
        _debugState.value = _debugState.value.copy(logs = trimmedLogs)
    }
    
    /**
     * Update debug state
     */
    private fun updateDebugState(
        isConnected: Boolean = _debugState.value.isConnected,
        connectionQuality: ConnectionQuality = _debugState.value.connectionQuality,
        sessionId: String? = _debugState.value.sessionId,
        model: String = _debugState.value.model,
        lastError: String? = _debugState.value.lastError,
        usageLimitReached: Boolean = _debugState.value.usageLimitReached,
        logs: List<DebugLogEntry> = _debugState.value.logs
    ) {
        _debugState.value = _debugState.value.copy(
            isConnected = isConnected,
            connectionQuality = connectionQuality,
            sessionId = sessionId,
            model = model,
            lastError = lastError,
            usageLimitReached = usageLimitReached,
            logs = logs
        )
    }
    
    /**
     * Increment debug counters
     */
    private fun incrementDebugCounter(
        audioChunksSent: Int = 0,
        audioChunksReceived: Int = 0,
        imagesProcessed: Int = 0,
        toolCallsExecuted: Int = 0,
        errors: Int = 0
    ) {
        _debugState.value = _debugState.value.copy(
            audioChunksSent = _debugState.value.audioChunksSent + audioChunksSent,
            audioChunksReceived = _debugState.value.audioChunksReceived + audioChunksReceived,
            imagesProcessed = _debugState.value.imagesProcessed + imagesProcessed,
            toolCallsExecuted = _debugState.value.toolCallsExecuted + toolCallsExecuted,
            errors = _debugState.value.errors + errors
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        audioManager.release()
        realtimeClient?.disconnect()
    }
}
