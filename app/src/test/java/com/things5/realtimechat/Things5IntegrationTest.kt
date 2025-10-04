package com.things5.realtimechat

import com.things5.realtimechat.data.Things5Config
import com.things5.realtimechat.data.Things5ConnectionStatus
import com.things5.realtimechat.network.Things5AuthService
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Comprehensive tests for Things5 integration
 * 
 * These tests verify:
 * - Authentication service functionality
 * - MCP server URL building
 * - Credential validation
 * - Connection status handling
 * - Error scenarios
 */
class Things5IntegrationTest {
    
    private lateinit var authService: Things5AuthService
    
    @Before
    fun setup() {
        authService = Things5AuthService()
    }
    
    @Test
    fun `test credential validation - valid credentials`() {
        // Valid email and password
        assertTrue(authService.validateCredentials("user@example.com", "password123"))
        assertTrue(authService.validateCredentials("test.user@things5.digital", "mySecurePass"))
        assertTrue(authService.validateCredentials("admin@company.org", "123456"))
    }
    
    @Test
    fun `test credential validation - invalid credentials`() {
        // Invalid email format
        assertFalse(authService.validateCredentials("invalid-email", "password123"))
        assertFalse(authService.validateCredentials("user@", "password123"))
        assertFalse(authService.validateCredentials("@example.com", "password123"))
        
        // Empty credentials
        assertFalse(authService.validateCredentials("", "password123"))
        assertFalse(authService.validateCredentials("user@example.com", ""))
        assertFalse(authService.validateCredentials("", ""))
        
        // Password too short
        assertFalse(authService.validateCredentials("user@example.com", "12345"))
        assertFalse(authService.validateCredentials("user@example.com", "abc"))
    }
    
    @Test
    fun `test MCP server URL building`() {
        val config = Things5Config(
            enabled = true,
            serverUrl = "https://things5-mcp-server.onrender.com/sse",
            username = "test@example.com",
            password = "testpass"
        )
        
        val url = authService.buildMcpServerUrl(config)
        
        assertTrue(url.contains("username=test%40example.com"))
        assertTrue(url.contains("password=testpass"))
        assertTrue(url.startsWith("https://things5-mcp-server.onrender.com/sse?"))
    }
    
    @Test
    fun `test MCP server URL building with special characters`() {
        val config = Things5Config(
            enabled = true,
            serverUrl = "https://things5-mcp-server.onrender.com/sse",
            username = "user+test@example.com",
            password = "pass@word#123"
        )
        
        val url = authService.buildMcpServerUrl(config)
        
        // Check that special characters are properly encoded
        assertTrue(url.contains("username=user%2Btest%40example.com"))
        assertTrue(url.contains("password=pass%40word%23123"))
    }
    
    @Test
    fun `test MCP server URL building with existing query parameters`() {
        val config = Things5Config(
            enabled = true,
            serverUrl = "https://things5-mcp-server.onrender.com/sse?debug=true",
            username = "test@example.com",
            password = "testpass"
        )
        
        val url = authService.buildMcpServerUrl(config)
        
        // Should use & separator since ? already exists
        assertTrue(url.contains("debug=true&username="))
        assertTrue(url.contains("&password=testpass"))
    }
    
    @Test
    fun `test connection status descriptions`() {
        assertEquals("Non connesso", authService.getStatusDescription(Things5ConnectionStatus.DISCONNECTED))
        assertEquals("Connessione in corso...", authService.getStatusDescription(Things5ConnectionStatus.CONNECTING))
        assertEquals("Connesso", authService.getStatusDescription(Things5ConnectionStatus.CONNECTED))
        assertEquals("Errore di connessione", authService.getStatusDescription(Things5ConnectionStatus.ERROR))
        assertEquals("Autenticazione fallita", authService.getStatusDescription(Things5ConnectionStatus.AUTHENTICATION_FAILED))
    }
    
    @Test
    fun `test clear auth functionality`() {
        // Clear auth should reset connection status
        authService.clearAuth()
        
        runBlocking {
            assertEquals(Things5ConnectionStatus.DISCONNECTED, authService.connectionStatus.value)
        }
    }
    
    @Test
    fun `test Things5Config default values`() {
        val config = Things5Config()
        
        assertFalse(config.enabled)
        assertEquals("https://things5-mcp-server.onrender.com/sse", config.serverUrl)
        assertEquals("", config.username)
        assertEquals("", config.password)
        assertEquals(Things5ConnectionStatus.DISCONNECTED, config.connectionStatus)
        assertNull(config.lastSuccessfulConnection)
        assertTrue(config.autoConnect)
    }
    
    @Test
    fun `test Things5Config with custom values`() {
        val timestamp = System.currentTimeMillis()
        val config = Things5Config(
            enabled = true,
            serverUrl = "https://custom-server.com/mcp",
            username = "custom@user.com",
            password = "custompass",
            connectionStatus = Things5ConnectionStatus.CONNECTED,
            lastSuccessfulConnection = timestamp,
            autoConnect = false
        )
        
        assertTrue(config.enabled)
        assertEquals("https://custom-server.com/mcp", config.serverUrl)
        assertEquals("custom@user.com", config.username)
        assertEquals("custompass", config.password)
        assertEquals(Things5ConnectionStatus.CONNECTED, config.connectionStatus)
        assertEquals(timestamp, config.lastSuccessfulConnection)
        assertFalse(config.autoConnect)
    }
    
    @Test
    fun `test connection test with invalid credentials should fail`() = runBlocking {
        val config = Things5Config(
            enabled = true,
            username = "invalid-email",
            password = "short"
        )
        
        // This should fail due to invalid credentials format
        // Note: In a real test environment, you might want to mock the HTTP calls
        // For now, we test the validation logic
        assertFalse(authService.validateCredentials(config.username, config.password))
    }
    
    @Test
    fun `test connection test with empty credentials should fail`() = runBlocking {
        val config = Things5Config(
            enabled = true,
            username = "",
            password = ""
        )
        
        assertFalse(authService.validateCredentials(config.username, config.password))
    }
    
    @Test
    fun `test MCP server URL encoding edge cases`() {
        // Test with various special characters that need encoding
        val specialChars = mapOf(
            "user@domain.com" to "user%40domain.com",
            "pass word" to "pass%20word",
            "test+user" to "test%2Buser",
            "pass&word" to "pass%26word",
            "user=test" to "user%3Dtest"
        )
        
        specialChars.forEach { (input, expected) ->
            val config = Things5Config(
                serverUrl = "https://server.com/sse",
                username = if (input.contains("@")) input else "test@example.com",
                password = if (!input.contains("@")) input else "testpass"
            )
            
            val url = authService.buildMcpServerUrl(config)
            assertTrue("URL should contain encoded value: $expected", url.contains(expected))
        }
    }
    
    @Test
    fun `test configuration state transitions`() {
        // Test that configuration changes are handled properly
        val initialConfig = Things5Config()
        assertFalse(initialConfig.enabled)
        assertEquals(Things5ConnectionStatus.DISCONNECTED, initialConfig.connectionStatus)
        
        val enabledConfig = initialConfig.copy(enabled = true)
        assertTrue(enabledConfig.enabled)
        
        val connectedConfig = enabledConfig.copy(
            connectionStatus = Things5ConnectionStatus.CONNECTED,
            lastSuccessfulConnection = System.currentTimeMillis()
        )
        assertEquals(Things5ConnectionStatus.CONNECTED, connectedConfig.connectionStatus)
        assertNotNull(connectedConfig.lastSuccessfulConnection)
    }
    
    @Test
    fun `test server URL variations`() {
        val baseUrls = listOf(
            "https://things5-mcp-server.onrender.com/sse",
            "https://things5-mcp-server-staging.onrender.com/sse",
            "http://localhost:3000/sse",
            "https://custom.domain.com/mcp/endpoint"
        )
        
        baseUrls.forEach { baseUrl ->
            val config = Things5Config(
                serverUrl = baseUrl,
                username = "test@example.com",
                password = "testpass"
            )
            
            val url = authService.buildMcpServerUrl(config)
            assertTrue("URL should start with base: $baseUrl", url.startsWith(baseUrl))
            assertTrue("URL should contain credentials", url.contains("username=") && url.contains("password="))
        }
    }
}
