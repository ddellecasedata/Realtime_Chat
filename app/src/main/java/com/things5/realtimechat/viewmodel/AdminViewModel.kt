package com.things5.realtimechat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.things5.realtimechat.data.AppSettings
import com.things5.realtimechat.data.McpServerConfig
import com.things5.realtimechat.data.SettingsRepository
import com.things5.realtimechat.data.Things5Config
import com.things5.realtimechat.data.Things5ConnectionStatus
import com.things5.realtimechat.network.Things5AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "AdminViewModel"
    
    private val settingsRepository = SettingsRepository(application)
    private val things5AuthService = Things5AuthService()
    
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()
    
    private val _isTestingThings5 = MutableStateFlow(false)
    val isTestingThings5: StateFlow<Boolean> = _isTestingThings5.asStateFlow()
    
    // Things5 connection status - combines service status and persisted status
    private val _things5ConnectionStatus = MutableStateFlow(Things5ConnectionStatus.DISCONNECTED)
    val things5ConnectionStatus: StateFlow<Things5ConnectionStatus> = _things5ConnectionStatus.asStateFlow()
    
    init {
        loadSettings()
        
        // Observe service connection status changes (from test connection, etc.)
        viewModelScope.launch {
            things5AuthService.connectionStatus.collect { serviceStatus ->
                // Update our status, but only if it's not DISCONNECTED 
                // (to avoid resetting the persisted CONNECTED status on init)
                if (serviceStatus != Things5ConnectionStatus.DISCONNECTED) {
                    _things5ConnectionStatus.value = serviceStatus
                }
            }
        }
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                Log.d(TAG, "📥 Settings loaded from repository:")
                Log.d(TAG, "   Things5 enabled: ${settings.things5Config.enabled}")
                Log.d(TAG, "   Things5 URL: ${settings.things5Config.serverUrl}")
                Log.d(TAG, "   Things5 username: ${settings.things5Config.username}")
                Log.d(TAG, "   Things5 password: [${settings.things5Config.password.length} chars]")
                Log.d(TAG, "   Things5 status from DataStore: ${settings.things5Config.connectionStatus}")
                
                _settings.value = settings
                
                // Sync connection status from persisted settings
                _things5ConnectionStatus.value = settings.things5Config.connectionStatus
            }
        }
    }
    
    fun updateApiKey(apiKey: String) {
        _settings.value = _settings.value.copy(openAiApiKey = apiKey)
    }
    
    fun addMcpServer(server: McpServerConfig) {
        val currentServers = _settings.value.mcpServers.toMutableList()
        currentServers.add(server)
        _settings.value = _settings.value.copy(mcpServers = currentServers)
    }
    
    fun removeMcpServer(serverName: String) {
        val currentServers = _settings.value.mcpServers.toMutableList()
        currentServers.removeAll { it.name == serverName }
        _settings.value = _settings.value.copy(mcpServers = currentServers)
    }
    
    fun toggleMcpServer(serverName: String) {
        val currentServers = _settings.value.mcpServers.map {
            if (it.name == serverName) {
                it.copy(enabled = !it.enabled)
            } else {
                it
            }
        }
        _settings.value = _settings.value.copy(mcpServers = currentServers)
    }
    
    fun saveSettings() {
        viewModelScope.launch {
            _isSaving.value = true
            
            try {
                val settingsToSave = _settings.value.copy(
                    isConfigured = _settings.value.openAiApiKey.isNotEmpty()
                )
                
                Log.d(TAG, "💾 Saving settings:")
                Log.d(TAG, "   Things5 enabled: ${settingsToSave.things5Config.enabled}")
                Log.d(TAG, "   Things5 URL: ${settingsToSave.things5Config.serverUrl}")
                Log.d(TAG, "   Things5 username: ${settingsToSave.things5Config.username}")
                Log.d(TAG, "   Things5 password: [${settingsToSave.things5Config.password.length} chars]")
                
                settingsRepository.saveSettings(settingsToSave)
                
                Log.d(TAG, "✅ Settings saved successfully")
                _saveSuccess.value = true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to save settings", e)
                _saveSuccess.value = false
            } finally {
                _isSaving.value = false
            }
        }
    }
    
    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }
    
    // Things5 specific methods
    fun updateThings5Config(config: Things5Config) {
        _settings.value = _settings.value.copy(things5Config = config)
    }
    
    fun updateThings5Credentials(username: String, password: String) {
        // Only update local state, don't save to DataStore yet
        // This prevents triggering settings flow on every keystroke
        val currentConfig = _settings.value.things5Config
        val updatedConfig = currentConfig.copy(
            username = username,
            password = password
        )
        _settings.value = _settings.value.copy(things5Config = updatedConfig)
        
        // Credentials will be saved when user clicks "Salva" button
    }
    
    fun updateThings5ServerUrl(serverUrl: String) {
        val currentConfig = _settings.value.things5Config
        val updatedConfig = currentConfig.copy(serverUrl = serverUrl)
        _settings.value = _settings.value.copy(things5Config = updatedConfig)
    }
    
    fun toggleThings5Integration() {
        val currentConfig = _settings.value.things5Config
        val updatedConfig = currentConfig.copy(enabled = !currentConfig.enabled)
        _settings.value = _settings.value.copy(things5Config = updatedConfig)
        
        // Clear auth if disabling
        if (!updatedConfig.enabled) {
            things5AuthService.clearAuth()
        }
    }
    
    fun testThings5Connection() {
        viewModelScope.launch {
            _isTestingThings5.value = true
            
            try {
                val config = _settings.value.things5Config
                
                Log.d(TAG, "🧪 Testing Things5 connection...")
                Log.d(TAG, "   URL: ${config.serverUrl}")
                Log.d(TAG, "   Username: ${config.username}")
                Log.d(TAG, "   Password: [${config.password.length} chars]")
                
                val status = things5AuthService.testConnection(config)
                
                Log.d(TAG, "🧪 Test result: $status")
                
                // Update the config with the new status (preserving credentials!)
                val updatedConfig = config.copy(connectionStatus = status)
                _settings.value = _settings.value.copy(things5Config = updatedConfig)
                
                // Save the updated status WITH credentials
                settingsRepository.updateThings5Status(config, status)
                
                if (status == Things5ConnectionStatus.CONNECTED) {
                    Log.d(TAG, "✅ Things5 connection test SUCCESSFUL")
                    
                    // Auto-save ALL settings to ensure Things5 config is persisted
                    // This prevents losing configuration if app closes before manual save
                    try {
                        Log.d(TAG, "💾 Auto-saving all settings after successful Things5 connection...")
                        settingsRepository.saveSettings(_settings.value)
                        Log.d(TAG, "✅ Auto-save completed")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Auto-save failed", e)
                    }
                } else {
                    Log.w(TAG, "⚠️ Things5 connection test FAILED: $status")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Things5 connection test EXCEPTION", e)
                
                // Handle error (preserving credentials!)
                val config = _settings.value.things5Config
                val updatedConfig = config.copy(
                    connectionStatus = Things5ConnectionStatus.ERROR
                )
                _settings.value = _settings.value.copy(things5Config = updatedConfig)
                settingsRepository.updateThings5Status(config, Things5ConnectionStatus.ERROR)
            } finally {
                _isTestingThings5.value = false
            }
        }
    }
    
    fun validateThings5Credentials(username: String, password: String): Boolean {
        return things5AuthService.validateCredentials(username, password)
    }
    
    fun getThings5StatusDescription(status: Things5ConnectionStatus): String {
        return things5AuthService.getStatusDescription(status)
    }
}
