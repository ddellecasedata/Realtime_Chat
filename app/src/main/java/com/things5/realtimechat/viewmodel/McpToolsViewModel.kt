package com.things5.realtimechat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.things5.realtimechat.data.McpToolConfig
import com.things5.realtimechat.data.McpToolsConfiguration
import com.things5.realtimechat.data.SettingsRepository
import com.things5.realtimechat.network.McpBridge
import com.things5.realtimechat.network.McpTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for managing MCP tools configuration
 */
class McpToolsViewModel(
    application: Application,
    private val serverName: String,
    private val mcpBridge: McpBridge?
) : AndroidViewModel(application) {
    
    private val TAG = "McpToolsViewModel"
    private val settingsRepository = SettingsRepository(application)
    
    // UI State
    data class ToolUiState(
        val name: String,
        val originalDescription: String,
        val customDescription: String?,
        val enabled: Boolean,
        val parameters: String // JSON schema as string for display
    )
    
    private val _toolsState = MutableStateFlow<List<ToolUiState>>(emptyList())
    val toolsState: StateFlow<List<ToolUiState>> = _toolsState.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadTools()
    }
    
    /**
     * Load tools from MCP Bridge
     */
    private fun loadTools() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Get tools from MCP Bridge
                val serverTools = mcpBridge?.getServerTools(serverName) ?: emptyList()
                
                // Get current tool configuration
                val settings = settingsRepository.settingsFlow.first()
                val toolConfigs = settings.mcpToolsConfig.tools[serverName] ?: emptyList()
                
                // Build UI state
                val toolsUi = serverTools.map { tool ->
                    val config = toolConfigs.firstOrNull { it.toolName == tool.name }
                    ToolUiState(
                        name = tool.name,
                        originalDescription = tool.description,
                        customDescription = config?.customDescription,
                        enabled = config?.enabled ?: true,
                        parameters = tool.parameters?.toString() ?: "{}"
                    )
                }
                
                _toolsState.value = toolsUi
                Log.d(TAG, "Loaded ${toolsUi.size} tools for server: $serverName")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading tools", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Toggle tool enabled/disabled
     */
    fun toggleTool(toolName: String) {
        viewModelScope.launch {
            try {
                settingsRepository.toggleTool(serverName, toolName)
                
                // Update local state
                _toolsState.value = _toolsState.value.map { tool ->
                    if (tool.name == toolName) {
                        tool.copy(enabled = !tool.enabled)
                    } else {
                        tool
                    }
                }
                
                Log.d(TAG, "Toggled tool: $toolName")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling tool", e)
            }
        }
    }
    
    /**
     * Update tool custom description
     */
    fun updateToolDescription(toolName: String, description: String?) {
        viewModelScope.launch {
            try {
                val cleanDescription = description?.takeIf { it.isNotBlank() }
                settingsRepository.updateToolDescription(serverName, toolName, cleanDescription)
                
                // Update local state
                _toolsState.value = _toolsState.value.map { tool ->
                    if (tool.name == toolName) {
                        tool.copy(customDescription = cleanDescription)
                    } else {
                        tool
                    }
                }
                
                Log.d(TAG, "Updated description for tool: $toolName")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating tool description", e)
            }
        }
    }
    
    /**
     * Reset tool description to original
     */
    fun resetToolDescription(toolName: String) {
        updateToolDescription(toolName, null)
    }
}
