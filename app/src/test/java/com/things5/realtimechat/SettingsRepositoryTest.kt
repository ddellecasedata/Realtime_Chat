package com.things5.realtimechat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.things5.realtimechat.data.AppSettings
import com.things5.realtimechat.data.McpServerConfig
import com.things5.realtimechat.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test per SettingsRepository
 * 
 * Questi test verificano:
 * - Salvataggio e caricamento delle impostazioni
 * - Gestione dei server MCP
 * - Persistenza dei dati
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SettingsRepositoryTest {
    
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = SettingsRepository(context)
    }
    
    @Test
    fun `test save and load settings`() = runBlocking {
        // Given
        val testSettings = AppSettings(
            openAiApiKey = "test-api-key",
            mcpServers = listOf(
                McpServerConfig("Server1", "ws://localhost:3000", true)
            ),
            isConfigured = true
        )
        
        // When
        repository.saveSettings(testSettings)
        val loadedSettings = withTimeout(5000) { repository.settingsFlow.first() }
        
        // Then
        assertEquals(testSettings.openAiApiKey, loadedSettings.openAiApiKey)
        assertEquals(testSettings.mcpServers.size, loadedSettings.mcpServers.size)
        assertEquals(testSettings.isConfigured, loadedSettings.isConfigured)
    }
    
    @Test
    fun `test add MCP server`() = runBlocking {
        // Given
        val newServer = McpServerConfig("TestServer", "ws://test:8080", true)
        
        // When
        repository.addMcpServer(newServer)
        val settings = withTimeout(5000) { repository.settingsFlow.first() }
        
        // Then
        assertTrue(settings.mcpServers.any { it.name == "TestServer" })
    }
    
    @Test
    fun `test remove MCP server`() = runBlocking {
        // Given
        val server = McpServerConfig("ToRemove", "ws://test:8080", true)
        repository.addMcpServer(server)
        
        // When
        repository.removeMcpServer("ToRemove")
        val settings = withTimeout(5000) { repository.settingsFlow.first() }
        
        // Then
        assertFalse(settings.mcpServers.any { it.name == "ToRemove" })
    }
    
    @Test
    fun `test update API key`() = runBlocking {
        // Given
        val newApiKey = "new-test-key"
        
        // When
        repository.updateApiKey(newApiKey)
        val settings = withTimeout(5000) { repository.settingsFlow.first() }
        
        // Then
        assertEquals(newApiKey, settings.openAiApiKey)
    }
    
    @Test
    fun `test set configured flag`() = runBlocking {
        // When
        repository.setConfigured(true)
        val settings = withTimeout(5000) { repository.settingsFlow.first() }
        
        // Then
        assertTrue(settings.isConfigured)
    }
}
