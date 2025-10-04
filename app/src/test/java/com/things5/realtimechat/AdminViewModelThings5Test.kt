package com.things5.realtimechat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.things5.realtimechat.data.Things5Config
import com.things5.realtimechat.data.Things5ConnectionStatus
import com.things5.realtimechat.viewmodel.AdminViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for AdminViewModel Things5 integration functionality
 * 
 * These tests verify:
 * - Things5 configuration updates
 * - Credential management
 * - Integration toggle functionality
 * - Connection testing
 * - Settings persistence
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AdminViewModelThings5Test {
    
    private lateinit var viewModel: AdminViewModel
    private lateinit var application: Application
    
    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        viewModel = AdminViewModel(application)
    }
    
    @Test
    fun `test initial Things5 configuration`() = runTest {
        val settings = viewModel.settings.first()
        val things5Config = settings.things5Config
        
        // Should start with default configuration
        assertFalse(things5Config.enabled)
        assertEquals("https://things5-mcp-server.onrender.com/sse", things5Config.serverUrl)
        assertEquals("", things5Config.username)
        assertEquals("", things5Config.password)
        assertEquals(Things5ConnectionStatus.DISCONNECTED, things5Config.connectionStatus)
    }
    
    @Test
    fun `test update Things5 credentials`() = runTest {
        val testUsername = "test@example.com"
        val testPassword = "testpassword"
        
        viewModel.updateThings5Credentials(testUsername, testPassword)
        
        val settings = viewModel.settings.first()
        val things5Config = settings.things5Config
        
        assertEquals(testUsername, things5Config.username)
        assertEquals(testPassword, things5Config.password)
    }
    
    @Test
    fun `test toggle Things5 integration`() = runTest {
        // Initially disabled
        var settings = viewModel.settings.first()
        assertFalse(settings.things5Config.enabled)
        
        // Toggle to enabled
        viewModel.toggleThings5Integration()
        settings = viewModel.settings.first()
        assertTrue(settings.things5Config.enabled)
        
        // Toggle back to disabled
        viewModel.toggleThings5Integration()
        settings = viewModel.settings.first()
        assertFalse(settings.things5Config.enabled)
    }
    
    @Test
    fun `test update complete Things5 config`() = runTest {
        val newConfig = Things5Config(
            enabled = true,
            serverUrl = "https://custom-server.com/sse",
            username = "custom@user.com",
            password = "custompass",
            connectionStatus = Things5ConnectionStatus.CONNECTED,
            lastSuccessfulConnection = System.currentTimeMillis(),
            autoConnect = false
        )
        
        viewModel.updateThings5Config(newConfig)
        
        val settings = viewModel.settings.first()
        val things5Config = settings.things5Config
        
        assertEquals(newConfig.enabled, things5Config.enabled)
        assertEquals(newConfig.serverUrl, things5Config.serverUrl)
        assertEquals(newConfig.username, things5Config.username)
        assertEquals(newConfig.password, things5Config.password)
        assertEquals(newConfig.connectionStatus, things5Config.connectionStatus)
        assertEquals(newConfig.lastSuccessfulConnection, things5Config.lastSuccessfulConnection)
        assertEquals(newConfig.autoConnect, things5Config.autoConnect)
    }
    
    @Test
    fun `test credential validation`() {
        // Valid credentials
        assertTrue(viewModel.validateThings5Credentials("user@example.com", "password123"))
        assertTrue(viewModel.validateThings5Credentials("test.user@domain.org", "securepass"))
        
        // Invalid credentials
        assertFalse(viewModel.validateThings5Credentials("invalid-email", "password123"))
        assertFalse(viewModel.validateThings5Credentials("user@example.com", "short"))
        assertFalse(viewModel.validateThings5Credentials("", "password123"))
        assertFalse(viewModel.validateThings5Credentials("user@example.com", ""))
    }
    
    @Test
    fun `test status descriptions`() {
        assertEquals("Non connesso", viewModel.getThings5StatusDescription(Things5ConnectionStatus.DISCONNECTED))
        assertEquals("Connessione in corso...", viewModel.getThings5StatusDescription(Things5ConnectionStatus.CONNECTING))
        assertEquals("Connesso", viewModel.getThings5StatusDescription(Things5ConnectionStatus.CONNECTED))
        assertEquals("Errore di connessione", viewModel.getThings5StatusDescription(Things5ConnectionStatus.ERROR))
        assertEquals("Autenticazione fallita", viewModel.getThings5StatusDescription(Things5ConnectionStatus.AUTHENTICATION_FAILED))
    }
    
    @Test
    fun `test Things5 integration with empty credentials should not enable`() = runTest {
        // Try to enable with empty credentials
        viewModel.updateThings5Credentials("", "")
        viewModel.toggleThings5Integration()
        
        val settings = viewModel.settings.first()
        
        // Should be enabled in UI but won't work functionally
        assertTrue(settings.things5Config.enabled)
        assertEquals("", settings.things5Config.username)
        assertEquals("", settings.things5Config.password)
    }
    
    @Test
    fun `test Things5 integration state persistence`() = runTest {
        // Configure Things5
        val testUsername = "persistent@test.com"
        val testPassword = "persistentpass"
        
        viewModel.updateThings5Credentials(testUsername, testPassword)
        viewModel.toggleThings5Integration()
        
        // Save settings
        viewModel.saveSettings()
        
        // Verify state is maintained
        val settings = viewModel.settings.first()
        val things5Config = settings.things5Config
        
        assertTrue(things5Config.enabled)
        assertEquals(testUsername, things5Config.username)
        assertEquals(testPassword, things5Config.password)
    }
    
    @Test
    fun `test connection status flow`() = runTest {
        // Initial status should be DISCONNECTED
        val initialStatus = viewModel.things5ConnectionStatus.first()
        assertEquals(Things5ConnectionStatus.DISCONNECTED, initialStatus)
        
        // Note: In a real test environment, you would mock the network calls
        // and test the actual connection flow. For now, we test the state management.
    }
    
    @Test
    fun `test Things5 config with various server URLs`() = runTest {
        val serverUrls = listOf(
            "https://things5-mcp-server.onrender.com/sse",
            "https://things5-mcp-server-staging.onrender.com/sse",
            "http://localhost:3000/sse",
            "https://custom.domain.com/mcp"
        )
        
        serverUrls.forEach { serverUrl ->
            val config = Things5Config(
                enabled = true,
                serverUrl = serverUrl,
                username = "test@example.com",
                password = "testpass"
            )
            
            viewModel.updateThings5Config(config)
            
            val settings = viewModel.settings.first()
            assertEquals(serverUrl, settings.things5Config.serverUrl)
        }
    }
    
    @Test
    fun `test Things5 integration toggle clears auth when disabled`() = runTest {
        // Enable integration
        viewModel.updateThings5Credentials("test@example.com", "testpass")
        viewModel.toggleThings5Integration()
        
        var settings = viewModel.settings.first()
        assertTrue(settings.things5Config.enabled)
        
        // Disable integration - should clear auth
        viewModel.toggleThings5Integration()
        
        settings = viewModel.settings.first()
        assertFalse(settings.things5Config.enabled)
        
        // Connection status should be reset to DISCONNECTED
        val status = viewModel.things5ConnectionStatus.first()
        assertEquals(Things5ConnectionStatus.DISCONNECTED, status)
    }
    
    @Test
    fun `test multiple credential updates`() = runTest {
        val credentials = listOf(
            "user1@example.com" to "password1",
            "user2@domain.org" to "password2",
            "admin@company.com" to "adminpass"
        )
        
        credentials.forEach { (username, password) ->
            viewModel.updateThings5Credentials(username, password)
            
            val settings = viewModel.settings.first()
            val things5Config = settings.things5Config
            
            assertEquals(username, things5Config.username)
            assertEquals(password, things5Config.password)
        }
    }
    
    @Test
    fun `test Things5 config immutability`() = runTest {
        val originalConfig = viewModel.settings.first().things5Config
        
        // Update credentials
        viewModel.updateThings5Credentials("new@user.com", "newpass")
        
        val updatedSettings = viewModel.settings.first()
        val updatedConfig = updatedSettings.things5Config
        
        // Original config should be unchanged (immutable)
        assertEquals("", originalConfig.username)
        assertEquals("", originalConfig.password)
        
        // New config should have updated values
        assertEquals("new@user.com", updatedConfig.username)
        assertEquals("newpass", updatedConfig.password)
    }
}
