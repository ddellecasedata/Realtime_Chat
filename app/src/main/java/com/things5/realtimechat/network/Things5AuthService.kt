package com.things5.realtimechat.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.things5.realtimechat.data.Things5Config
import com.things5.realtimechat.data.Things5ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Service for managing Things5 authentication and MCP server integration
 * 
 * This service handles:
 * - Authentication with Things5 Keycloak
 * - MCP server connection management
 * - Token refresh and session management
 * - Connection status monitoring
 */
class Things5AuthService {
    
    private val TAG = "Things5AuthService"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    // Connection status state
    private val _connectionStatus = MutableStateFlow(Things5ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<Things5ConnectionStatus> = _connectionStatus.asStateFlow()
    
    // Current access token
    private var currentAccessToken: String? = null
    private var tokenExpiryTime: Long = 0
    
    // Keycloak configuration
    private val keycloakBaseUrl = "https://auth.things5.digital"
    private val realm = "demo10"
    private val clientId = "api"
    
    /**
     * Test connection to Things5 MCP server with provided credentials
     * First authenticate with Keycloak, then test MCP server with Bearer token
     */
    suspend fun testConnection(config: Things5Config): Things5ConnectionStatus {
        return try {
            Log.d(TAG, "Testing connection to Things5 MCP server")
            _connectionStatus.value = Things5ConnectionStatus.CONNECTING
            
            // First, authenticate with Keycloak to get Bearer token
            val authResult = authenticateWithKeycloak(config.username, config.password)
            if (!authResult) {
                Log.e(TAG, "❌ Keycloak authentication failed")
                _connectionStatus.value = Things5ConnectionStatus.AUTHENTICATION_FAILED
                return Things5ConnectionStatus.AUTHENTICATION_FAILED
            }
            
            // Then test MCP server connection with Bearer token
            val mcpResult = testMcpServerConnection(config)
            _connectionStatus.value = mcpResult
            
            Log.d(TAG, "Connection test completed with status: $mcpResult")
            mcpResult
            
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            Things5ConnectionStatus.ERROR
        }
    }
    
    /**
     * Authenticate with Keycloak and get access token
     */
    private suspend fun authenticateWithKeycloak(username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val tokenUrl = "$keycloakBaseUrl/auth/realms/$realm/protocol/openid-connect/token"
                
                Log.d(TAG, "Requesting OAuth token from: $tokenUrl")
                
                val requestBody = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "password")
                    .add("scope", "openid")
                    .add("username", username)
                    .add("password", password)
                    .build()
                
                val request = Request.Builder()
                    .url(tokenUrl)
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                    
                    currentAccessToken = jsonResponse.get("access_token")?.asString
                    val expiresIn = jsonResponse.get("expires_in")?.asLong ?: 3600
                    tokenExpiryTime = System.currentTimeMillis() + (expiresIn * 1000)
                    
                    Log.d(TAG, "✅ OAuth authentication successful")
                    Log.d(TAG, "Token expires in: ${expiresIn}s")
                    
                    true
                } else {
                    val errorBody = response.body?.string() ?: "No error details"
                    Log.e(TAG, "❌ OAuth authentication failed: ${response.code} ${response.message}")
                    Log.e(TAG, "Error response: $errorBody")
                    false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Keycloak authentication", e)
                false
            }
        }
    }
    
    /**
     * Test MCP server connection with MCP initialize call using Bearer token
     */
    private suspend fun testMcpServerConnection(config: Things5Config): Things5ConnectionStatus {
        return withContext(Dispatchers.IO) {
            try {
                // Use the server URL directly (no credentials in URL)
                val mcpUrl = config.serverUrl
                
                // Get current access token
                if (currentAccessToken == null) {
                    Log.e(TAG, "❌ No access token available for MCP server test")
                    return@withContext Things5ConnectionStatus.AUTHENTICATION_FAILED
                }
            
            // Test with actual MCP initialize call
            val initRequest = mapOf(
                "jsonrpc" to "2.0",
                "id" to 1,
                "method" to "initialize",
                "params" to mapOf(
                    "protocolVersion" to "2024-11-05",
                    "capabilities" to mapOf(
                        "roots" to mapOf("listChanged" to true),
                        "sampling" to emptyMap<String, Any>()
                    ),
                    "clientInfo" to mapOf(
                        "name" to "Realtime Chat Android",
                        "version" to "1.0.0"
                    )
                )
            )
            
            val json = gson.toJson(initRequest)
            val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url(mcpUrl)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer $currentAccessToken")
                .post(requestBody)
                .build()
            
            Log.d(TAG, "Testing MCP server with initialize call")
            Log.d(TAG, "URL: $mcpUrl")
            Log.d(TAG, "Request body: $json")
            Log.d(TAG, "Headers: ${request.headers}")
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "✅ MCP server responded: ${response.code}")
                Log.d(TAG, "Response: ${responseBody.take(200)}")
                
                // Check if response contains valid MCP response
                if (responseBody.contains("\"result\"") || responseBody.contains("capabilities")) {
                    Things5ConnectionStatus.CONNECTED
                } else if (responseBody.contains("authentication") || responseBody.contains("unauthorized")) {
                    Things5ConnectionStatus.AUTHENTICATION_FAILED
                } else {
                    Things5ConnectionStatus.ERROR
                }
            } else {
                Log.w(TAG, "⚠️ MCP server test failed: ${response.code} ${response.message}")
                val errorBody = response.body?.string() ?: ""
                Log.w(TAG, "Error response body: $errorBody")
                Log.w(TAG, "Response headers: ${response.headers}")
                
                when (response.code) {
                    401, 403 -> {
                        Log.e(TAG, "❌ Authentication failed - check credentials")
                        Things5ConnectionStatus.AUTHENTICATION_FAILED
                    }
                    404 -> {
                        Log.e(TAG, "❌ MCP endpoint not found - check URL")
                        Things5ConnectionStatus.ERROR
                    }
                    in 400..499 -> {
                        Log.e(TAG, "❌ Client error: ${response.code}")
                        Things5ConnectionStatus.ERROR
                    }
                    in 500..599 -> {
                        Log.e(TAG, "❌ Server error: ${response.code}")
                        Things5ConnectionStatus.ERROR
                    }
                    else -> {
                        Log.e(TAG, "❌ Unknown error: ${response.code}")
                        Things5ConnectionStatus.ERROR
                    }
                }
            }
            
            } catch (e: Exception) {
                Log.e(TAG, "MCP server connection test failed", e)
                Things5ConnectionStatus.ERROR
            }
        }
    }
    
    /**
     * Build MCP server URL with authentication parameters
     */
    fun buildMcpServerUrl(config: Things5Config): String {
        val baseUrl = config.serverUrl
        val separator = if (baseUrl.contains('?')) '&' else '?'
        
        fun enc(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")
        val finalUrl = "${baseUrl}${separator}username=${enc(config.username)}" +
                "&password=${enc(config.password)}"
        
        Log.d(TAG, "Built MCP URL: $finalUrl")
        Log.d(TAG, "Base URL: $baseUrl")
        Log.d(TAG, "Username: ${config.username}")
        Log.d(TAG, "Password: [${config.password.length} chars]")
        
        return finalUrl
    }
    
    /**
     * Get current access token (non-suspend version for immediate access)
     */
    fun getCurrentAccessToken(): String? {
        // Check if token is still valid (with 5 minute buffer)
        if (currentAccessToken != null && System.currentTimeMillis() < (tokenExpiryTime - 300000)) {
            return currentAccessToken
        }
        
        // Token expired or doesn't exist
        return null
    }
    
    /**
     * Get current access token, refreshing if necessary
     */
    suspend fun getValidAccessToken(config: Things5Config): String? {
        // Check if token is still valid (with 5 minute buffer)
        if (currentAccessToken != null && System.currentTimeMillis() < (tokenExpiryTime - 300000)) {
            return currentAccessToken
        }
        
        // Token expired or doesn't exist, try to re-authenticate
        if (currentAccessToken == null) {
            Log.d(TAG, "🔄 No valid token, re-authenticating...")
            if (authenticateWithKeycloak(config.username, config.password)) {
                return currentAccessToken
            }
        }
        
        return null
    }
    
    /**
     * Check if credentials are valid format
     */
    fun validateCredentials(username: String, password: String): Boolean {
        // Simple but effective email regex, case-insensitive
        val emailRegex = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
        return emailRegex.matches(username) && password.length >= 6
    }
    
    /**
     * Get connection status description
     */
    fun getStatusDescription(status: Things5ConnectionStatus): String {
        return when (status) {
            Things5ConnectionStatus.DISCONNECTED -> "Non connesso"
            Things5ConnectionStatus.CONNECTING -> "Connessione in corso..."
            Things5ConnectionStatus.CONNECTED -> "Connesso"
            Things5ConnectionStatus.ERROR -> "Errore di connessione"
            Things5ConnectionStatus.AUTHENTICATION_FAILED -> "Autenticazione fallita"
        }
    }
    
    /**
     * Clear authentication state
     */
    fun clearAuth() {
        currentAccessToken = null
        tokenExpiryTime = 0
        _connectionStatus.value = Things5ConnectionStatus.DISCONNECTED
    }
}
