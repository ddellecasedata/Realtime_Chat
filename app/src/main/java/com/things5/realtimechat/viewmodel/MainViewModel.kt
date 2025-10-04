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
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "🚀 MainViewModel INIT - Starting up...")
        Log.d(TAG, "═══════════════════════════════════════════")
        
        // Initialize Things5 authentication at startup if enabled
        viewModelScope.launch {
            try {
                Log.d(TAG, "📥 Fetching settings from repository...")
                val settings = settingsRepository.settingsFlow.first()
                
                Log.d(TAG, "📥 Settings received:")
                Log.d(TAG, "   Things5 enabled: ${settings.things5Config.enabled}")
                Log.d(TAG, "   Things5 username: ${settings.things5Config.username}")
                Log.d(TAG, "   Things5 password: [${settings.things5Config.password.length} chars]")
                Log.d(TAG, "   Things5 status: ${settings.things5Config.connectionStatus}")
                
                initializeThings5Authentication(settings)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during Things5 startup authentication", e)
            }
        }
        
        // Observe settings changes and re-authenticate ONLY when credentials actually change
        viewModelScope.launch {
            var previousConfig: com.things5.realtimechat.data.Things5Config? = null
            settingsRepository.settingsFlow.collect { settings ->
                val currentConfig = settings.things5Config
                
                // Only re-authenticate if credentials or URL actually changed
                if (previousConfig != null && (
                    previousConfig!!.username != currentConfig.username ||
                    previousConfig!!.password != currentConfig.password ||
                    previousConfig!!.serverUrl != currentConfig.serverUrl ||
                    (previousConfig!!.enabled != currentConfig.enabled && currentConfig.enabled)
                )) {
                    Log.d(TAG, "🔄 Things5 credentials changed, re-authenticating...")
                    Log.d(TAG, "   Username changed: ${previousConfig!!.username != currentConfig.username}")
                    Log.d(TAG, "   Password changed: ${previousConfig!!.password != currentConfig.password}")
                    Log.d(TAG, "   URL changed: ${previousConfig!!.serverUrl != currentConfig.serverUrl}")
                    Log.d(TAG, "   Enabled toggled on: ${!previousConfig!!.enabled && currentConfig.enabled}")
                    initializeThings5Authentication(settings)
                } else if (previousConfig != null) {
                    Log.d(TAG, "⏭️  Things5 config updated but credentials unchanged, skipping re-auth")
                }
                
                previousConfig = settings.things5Config
            }
        }
    }
    
    /**
     * Initialize or re-initialize Things5 authentication
     */
    private suspend fun initializeThings5Authentication(settings: AppSettings) {
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "🔐 initializeThings5Authentication() called")
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "   Enabled: ${settings.things5Config.enabled}")
        Log.d(TAG, "   Username empty: ${settings.things5Config.username.isEmpty()}")
        Log.d(TAG, "   Password empty: ${settings.things5Config.password.isEmpty()}")
        
        if (settings.things5Config.enabled && 
            settings.things5Config.username.isNotEmpty() && 
            settings.things5Config.password.isNotEmpty()) {
            
            Log.d(TAG, "✅ Things5 credentials present, authenticating...")
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
            - Immediately after the user finishes speaking, say a very short preamble (max 3 words) and start the tool call.
              - Examples: "Controllo subito", "Un attimo, verifico", "Sto cercando"
              - Keep it ultra-short so it plays before the user speaks again.
            - Then immediately call the appropriate tool.
            - Use the tool description to understand when to use it.
            
            ## ⚠️ CRITICAL: Avoid ERROR -32602 "Invalid arguments"
            
            **Most common mistake**: Calling tools WITHOUT required parameters!
            - ❌ `device_firmware_detail()` without machine_id → ERROR -32602
            - ❌ `machine_command_execute()` without device_id or machine_command_id → ERROR -32602
            - ❌ `metrics_read()` without device_id → ERROR -32602
            
            **Solution**: ALWAYS get IDs first from `list_machines`, then pass them to other tools!
            
            ## Critical Tool Usage Patterns & Workflows
            
            ### 🔍 FINDING DEVICES (always do this first!)
            **Tool**: `list_machines`
            - Use `search` parameter to find by name or serial (e.g., search="frigo", search="ABC123")
            - Use `serial` for exact serial match
            - Use `machines_group_id` to filter by group
            - Returns device_id (UUID) needed for ALL other operations
            - **Example**: User says "dispositivo frigo" → `list_machines` with search="frigo"
            
            ### 📋 GETTING DEVICE INFO
            **Tool**: `device_details`
            - Requires: device_id from list_machines
            - Shows connection status, firmware version, model, group
            - Use `include_machine_model=true` for model details
            - Use `include_machines_group=true` for group details
            
            **Tool**: `device_firmware_detail` (⭐ MOST IMPORTANT!)
            - ⚠️ **REQUIRED PARAMETER**: `machine_id` (string, UUID) - NEVER call without it!
            - This machine_id is the device_id you got from `list_machines`
            - If you call without machine_id → ERROR -32602: "Required" 
            - Optional: `include_machine_commands=true` to see available commands
            - Optional: `include_machine_variables=true` to see metrics/parameters/states/events
            - **ALWAYS call this before**: executing commands, reading metrics, reading parameters
            - This tells you what's available on the device!
            
            ### ⚙️ EXECUTING COMMANDS
            **Workflow**:
            1. `list_machines` → get device_id
            2. `device_firmware_detail` with include_machine_commands=true → get available commands with their IDs
            3. `machine_command_execute` with device_id + machine_command_id
            
            **Tool**: `machine_command_execute`
            - ⚠️ **REQUIRED**: `device_id` (UUID from list_machines) AND `machine_command_id` (UUID from device_firmware_detail)
            - ⚠️ **IMPORTANT**: Check if command requires `parameters` array!
            - device_firmware_detail shows "X parameter(s)" for each command
            - If command has parameters, you MUST include them or use defaults
            - Parameters format: [{"name": "param_name", "value": param_value}]
            - **Common issue**: Command executed but device doesn't respond = missing required parameters!
            - If you call without required parameters → Command may silently fail on device
            - **Example**: User says "avvia cleaning" → find device → get commands WITH parameters → execute with command_id + parameters
            
            **Alternative Tool**: `perform_action`
            - Similar to machine_command_execute but might have different signature
            - Check which one is available on the firmware
            
            ### 📊 READING DEVICE DATA
            
            **For Metrics** (temperatures, counters, production data):
            - Tool: `metrics_read` or `aggregated_metrics` (for multiple devices)
            - ⚠️ **REQUIRED**: `device_id` (UUID from list_machines)
            - Optional: from/to (ISO8601 dates), metric_names array, last_value=true for latest only
            - **Must know metric names first**: call `device_firmware_detail` with machine_id + include_machine_variables=true
            - Without device_id → ERROR -32602
            
            **For Parameters** (device settings/configuration):
            - Tool: `read_parameters` for all parameters OR `read_single_parameter` for one by label
            - ⚠️ **REQUIRED**: `device_id` (UUID from list_machines)
            - Optional: parameter_name_list array to filter specific parameters
            - **Must know parameter names first**: call `device_firmware_detail` with machine_id + include_machine_variables=true
            - Without device_id → ERROR -32602
            
            **For States** (current device state):
            - Tool: `states_read` for time series OR `state_read_last_value` for current value
            - ⚠️ **REQUIRED**: `device_id` (UUID from list_machines)
            - Optional: from/to dates, states_names array
            - **Must know state names first**: call `device_firmware_detail` with machine_id + include_machine_variables=true
            - Without device_id → ERROR -32602
            
            **For Events/Alarms**:
            - Tool: `events_read` for specific device OR `overview_alarms`/`overview_events` for multiple devices
            - ⚠️ **REQUIRED**: `device_id` (UUID from list_machines)
            - Optional: from/to dates, event_names array
            - **Must know event names first**: call `device_firmware_detail` with machine_id + include_machine_variables=true
            - Without device_id → ERROR -32602
            
            ### 🍽️ RECIPES (device programs)
            **Tool**: `device_managed_recipes`
            - ⚠️ **REQUIRED**: `machine_id` (UUID - this is device_id from list_machines)
            - Returns list of recipes available on the device
            - Use before starting a recipe via command
            - Without machine_id → ERROR -32602
            
            ### 👥 USER MANAGEMENT
            **Listing users**: `users_list`
            - Optional: search string, machines_groups_ids to filter
            
            **User details**: `users_detail`
            - Requires: user_id
            
            **Create user**: `user_create`
            - Requires: email, first_name, last_name
            - Optional: phone, language
            
            ### 🏢 GROUP & ORGANIZATION
            **List groups**: `devices_groups_list`
            - Optional: parent_group_id to filter by parent
            
            **Group details**: `show_device_group`
            - Requires: group_id
            
            **Add user to group**: `create_device_group_user`
            - Requires: group_id, user_id
            - Optional: role
            
            **Organization details**: `organization_detail`
            - No parameters needed, returns current org info
            
            **List roles**: `roles_list`
            - Optional: organization_id
            
            ### 🔧 DEVICE MODELS & FIRMWARE
            **List models**: `device_models_list`
            - No parameters needed
            
            **Model details**: `device_model_detail`
            - Requires: machine_model_id
            - Use `include_machines_firmwares=true` to see available firmware versions
            
            **List firmwares**: `device_firmware_list`
            - Requires: machine_model_id
            
            **Firmware updates**:
            - Request update: `device_firmware_update_request` (device_id + firmware_id)
            - Check status: `device_firmware_update_status` (device_id)
            - Cancel update: `device_firmware_update_cancel` (device_id)
            
            ### ✏️ DEVICE MANAGEMENT
            **Create device**: `device_create`
            - Requires: serial, machine_model_id, machine_firmware_id
            - Optional: name, machines_group_id
            
            **Update device**: `device_update`
            - Requires: device_id + at least one field to update (name, serial)
            
            ### 🎯 COMMAND MANAGEMENT (admin)
            - Create: `machine_command_create` (machine_firmware_id, name, parameters)
            - Update: `machine_command_update` (machine_command_id, name, parameters)
            - Delete: `machine_command_delete` (machine_command_id)
            
            ### 📈 OVERVIEW/DASHBOARD
            - Recent alarms: `overview_alarms` (from/to dates, optional machine_ids)
            - Recent events: `overview_events` (from/to dates, optional machine_ids)
            
            ## 🚨 CRITICAL RULES
            1. **ALWAYS** call `list_machines` first when user mentions a device by name/serial
            2. **ALWAYS** call `device_firmware_detail` before reading data or executing commands
            3. **NEVER** guess device_id, machine_command_id, or variable names - get them from tools
            4. **Use ISO8601 format** for dates: "2024-10-04T10:00:00Z"
            5. **When user says "comando/command"** → workflow: find device → get commands → execute
            6. **When user asks for data** (temperature, stato, etc.) → workflow: find device → get variables → read data
            7. **MANDATORY: Immediate verbal confirmation after EVERY command execution**
               - ⚠️ **CRITICAL**: Say confirmation IMMEDIATELY when tool result arrives, before user speaks again!
               - Use ULTRA-SHORT responses (1-2 words max): "Fatto", "Eseguito", "OK", "Luce accesa", "Luce spenta"
               - **Respond within 0.5 seconds** of receiving tool result - don't wait or elaborate!
               - Example flow: Tool result arrives → INSTANTLY say "Fatto" → user can speak
               - ❌ WRONG: "Ho eseguito il comando e la luce è stata spenta con successo" (too long, user will interrupt)
               - ✅ CORRECT: "Fatto" (instant, user hears it)
               - If you don't respond immediately, user assumes command failed!
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
            - ALWAYS check tools before answering. Data for answering questions is provided by tools. 
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
                    // Flow ends when recording stops
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
                            // Pulisce il buffer di input lato server per evitare residui
                            realtimeClient?.clearInputBuffer()
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
    
    /**
     * End the realtime session immediately (user pressed Stop)
     * - Stops any ongoing assistant audio
     * - Cancels current response generation on the server
     * - Disconnects the websocket
     * - Resets UI state to Idle
     */
    fun endSession() {
        try {
            // Stop any local playback instantly
            audioManager.stopPlayback()
            isAssistantSpeaking = false
            
            // Clear any pending input buffer and cancel server-side response in progress
            realtimeClient?.clearInputBuffer()
            realtimeClient?.cancelResponse()
            
            // Disconnect websocket session
            realtimeClient?.disconnect()
            realtimeClient = null
            
            // Reset UI
            updateState(sessionState = SessionState.Idle)
            addDebugLog(DebugLogType.CONNECTION, "Sessione terminata dall'utente")
        } catch (e: Exception) {
            Log.e(TAG, "Error ending session", e)
            updateState(errorMessage = "Errore durante la chiusura sessione: ${e.message}")
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        audioManager.release()
        realtimeClient?.disconnect()
    }
}
