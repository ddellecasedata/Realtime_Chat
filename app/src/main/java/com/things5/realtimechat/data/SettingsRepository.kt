package com.things5.realtimechat.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    
    private val TAG = "SettingsRepository"
    private val gson = Gson()
    
    companion object {
        private val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        private val MCP_SERVERS = stringPreferencesKey("mcp_servers")
        private val THINGS5_CONFIG = stringPreferencesKey("things5_config")
        private val MCP_TOOLS_CONFIG = stringPreferencesKey("mcp_tools_config")
        private val IS_CONFIGURED = booleanPreferencesKey("is_configured")
    }
    
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val mcpServersJson = preferences[MCP_SERVERS] ?: "[]"
        val mcpServers = try {
            val type = object : TypeToken<List<McpServerConfig>>() {}.type
            gson.fromJson<List<McpServerConfig>>(mcpServersJson, type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MCP servers", e)
            emptyList()
        }
        
        val things5ConfigJson = preferences[THINGS5_CONFIG] ?: "{}"
        Log.d(TAG, "📖 Loading Things5 config from DataStore: $things5ConfigJson")
        
        val things5Config = try {
            gson.fromJson(things5ConfigJson, Things5Config::class.java) ?: Things5Config()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Things5 config", e)
            Things5Config()
        }
        
        Log.d(TAG, "📖 Loaded Things5 config:")
        Log.d(TAG, "   Enabled: ${things5Config.enabled}")
        Log.d(TAG, "   URL: ${things5Config.serverUrl}")
        Log.d(TAG, "   Username: ${things5Config.username}")
        Log.d(TAG, "   Password: [${things5Config.password.length} chars]")
        
        val toolsConfigJson = preferences[MCP_TOOLS_CONFIG] ?: "{}"
        val toolsConfig = try {
            gson.fromJson(toolsConfigJson, McpToolsConfiguration::class.java) ?: McpToolsConfiguration()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MCP tools config", e)
            McpToolsConfiguration()
        }
        
        AppSettings(
            openAiApiKey = preferences[OPENAI_API_KEY] ?: "",
            mcpServers = mcpServers,
            things5Config = things5Config,
            mcpToolsConfig = toolsConfig,
            isConfigured = preferences[IS_CONFIGURED] ?: false
        )
    }
    
    suspend fun saveSettings(settings: AppSettings) {
        val things5Json = gson.toJson(settings.things5Config)
        
        Log.d(TAG, "💾 Saving settings to DataStore:")
        Log.d(TAG, "   Things5 enabled: ${settings.things5Config.enabled}")
        Log.d(TAG, "   Things5 URL: ${settings.things5Config.serverUrl}")
        Log.d(TAG, "   Things5 username: ${settings.things5Config.username}")
        Log.d(TAG, "   Things5 password: [${settings.things5Config.password.length} chars]")
        Log.d(TAG, "   Things5 JSON: $things5Json")
        
        context.dataStore.edit { preferences ->
            preferences[OPENAI_API_KEY] = settings.openAiApiKey
            preferences[MCP_SERVERS] = gson.toJson(settings.mcpServers)
            preferences[THINGS5_CONFIG] = things5Json
            preferences[MCP_TOOLS_CONFIG] = gson.toJson(settings.mcpToolsConfig)
            preferences[IS_CONFIGURED] = settings.isConfigured
        }
        
        Log.d(TAG, "✅ Settings saved to DataStore")
    }
    
    suspend fun updateApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[OPENAI_API_KEY] = apiKey
        }
    }
    
    suspend fun addMcpServer(server: McpServerConfig) {
        context.dataStore.edit { preferences ->
            val currentServersJson = preferences[MCP_SERVERS] ?: "[]"
            val currentServers = try {
                val type = object : TypeToken<List<McpServerConfig>>() {}.type
                gson.fromJson<List<McpServerConfig>>(currentServersJson, type).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            
            currentServers.add(server)
            preferences[MCP_SERVERS] = gson.toJson(currentServers)
        }
    }
    
    suspend fun removeMcpServer(serverName: String) {
        context.dataStore.edit { preferences ->
            val currentServersJson = preferences[MCP_SERVERS] ?: "[]"
            val currentServers = try {
                val type = object : TypeToken<List<McpServerConfig>>() {}.type
                gson.fromJson<List<McpServerConfig>>(currentServersJson, type).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            
            currentServers.removeAll { it.name == serverName }
            preferences[MCP_SERVERS] = gson.toJson(currentServers)
        }
    }
    
    suspend fun updateThings5Config(config: Things5Config) {
        context.dataStore.edit { preferences ->
            preferences[THINGS5_CONFIG] = gson.toJson(config)
        }
    }
    
    suspend fun updateThings5Credentials(username: String, password: String) {
        context.dataStore.edit { preferences ->
            val currentConfigJson = preferences[THINGS5_CONFIG] ?: "{}"
            val currentConfig = try {
                gson.fromJson(currentConfigJson, Things5Config::class.java) ?: Things5Config()
            } catch (e: Exception) {
                Things5Config()
            }
            
            val updatedConfig = currentConfig.copy(
                username = username,
                password = password
            )
            preferences[THINGS5_CONFIG] = gson.toJson(updatedConfig)
        }
    }
    
    suspend fun updateThings5Status(config: Things5Config, status: Things5ConnectionStatus) {
        Log.d(TAG, "🔄 Updating Things5 status to: $status")
        Log.d(TAG, "   Preserving username: ${config.username}")
        Log.d(TAG, "   Preserving password: [${config.password.length} chars]")
        
        context.dataStore.edit { preferences ->
            val updatedConfig = config.copy(
                connectionStatus = status,
                lastSuccessfulConnection = if (status == Things5ConnectionStatus.CONNECTED) {
                    System.currentTimeMillis()
                } else {
                    config.lastSuccessfulConnection
                }
            )
            
            val jsonToSave = gson.toJson(updatedConfig)
            Log.d(TAG, "   Saving: $jsonToSave")
            
            preferences[THINGS5_CONFIG] = jsonToSave
        }
        
        Log.d(TAG, "✅ Things5 status updated in DataStore")
    }
    
    suspend fun setConfigured(configured: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_CONFIGURED] = configured
        }
    }
    
    /**
     * Update tool configuration for a specific server
     */
    suspend fun updateToolsConfig(toolsConfig: McpToolsConfiguration) {
        context.dataStore.edit { preferences ->
            preferences[MCP_TOOLS_CONFIG] = gson.toJson(toolsConfig)
        }
        Log.d(TAG, "✅ Tools configuration updated")
    }
    
    /**
     * Toggle tool enabled/disabled
     */
    suspend fun toggleTool(serverName: String, toolName: String) {
        context.dataStore.edit { preferences ->
            val currentConfigJson = preferences[MCP_TOOLS_CONFIG] ?: "{}"
            val currentConfig = try {
                gson.fromJson(currentConfigJson, McpToolsConfiguration::class.java) ?: McpToolsConfiguration()
            } catch (e: Exception) {
                McpToolsConfiguration()
            }
            
            val serverTools = currentConfig.tools[serverName]?.toMutableList() ?: mutableListOf()
            val toolIndex = serverTools.indexOfFirst { it.toolName == toolName }
            
            if (toolIndex >= 0) {
                // Toggle existing config
                serverTools[toolIndex] = serverTools[toolIndex].copy(enabled = !serverTools[toolIndex].enabled)
            } else {
                // Create new config (disabled by default when toggling first time)
                serverTools.add(McpToolConfig(serverName, toolName, enabled = false))
            }
            
            val updatedTools = currentConfig.tools.toMutableMap()
            updatedTools[serverName] = serverTools
            
            val updatedConfig = currentConfig.copy(tools = updatedTools)
            preferences[MCP_TOOLS_CONFIG] = gson.toJson(updatedConfig)
        }
    }
    
    /**
     * Update tool custom description
     */
    suspend fun updateToolDescription(serverName: String, toolName: String, description: String?) {
        context.dataStore.edit { preferences ->
            val currentConfigJson = preferences[MCP_TOOLS_CONFIG] ?: "{}"
            val currentConfig = try {
                gson.fromJson(currentConfigJson, McpToolsConfiguration::class.java) ?: McpToolsConfiguration()
            } catch (e: Exception) {
                McpToolsConfiguration()
            }
            
            val serverTools = currentConfig.tools[serverName]?.toMutableList() ?: mutableListOf()
            val toolIndex = serverTools.indexOfFirst { it.toolName == toolName }
            
            if (toolIndex >= 0) {
                // Update existing config
                serverTools[toolIndex] = serverTools[toolIndex].copy(customDescription = description)
            } else {
                // Create new config with custom description
                serverTools.add(McpToolConfig(serverName, toolName, enabled = true, customDescription = description))
            }
            
            val updatedTools = currentConfig.tools.toMutableMap()
            updatedTools[serverName] = serverTools
            
            val updatedConfig = currentConfig.copy(tools = updatedTools)
            preferences[MCP_TOOLS_CONFIG] = gson.toJson(updatedConfig)
        }
        Log.d(TAG, "✅ Tool description updated for $toolName")
    }
}
