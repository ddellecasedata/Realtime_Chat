package com.things5.realtimechat

import com.google.gson.JsonObject
import com.things5.realtimechat.data.McpServerConfig
import com.things5.realtimechat.network.McpBridge
import com.things5.realtimechat.network.McpConnectionState
import com.things5.realtimechat.network.McpServerStatus
import com.things5.realtimechat.network.McpTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Test per verificare il corretto funzionamento dello stato di debug MCP
 */
@OptIn(ExperimentalCoroutinesApi::class)
class McpBridgeStatusTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mcpBridge: McpBridge
    private val toolResults = mutableMapOf<String, String>()
    private val errors = mutableListOf<String>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::mcpBridge.isInitialized) {
            mcpBridge.disconnect()
        }
    }

    @Test
    fun `test McpServerStatus creation with all fields`() {
        // Given
        val serverName = "Test Server"
        val tools = listOf(
            McpTool(
                name = "test_tool",
                description = "A test tool",
                parameters = JsonObject()
            )
        )

        // When
        val status = McpServerStatus(
            serverName = serverName,
            connectionState = McpConnectionState.CONNECTED,
            protocol = "WebSocket",
            tools = tools,
            errorMessage = null,
            lastUpdate = System.currentTimeMillis()
        )

        // Then
        assertEquals(serverName, status.serverName)
        assertEquals(McpConnectionState.CONNECTED, status.connectionState)
        assertEquals("WebSocket", status.protocol)
        assertEquals(1, status.tools.size)
        assertEquals("test_tool", status.tools[0].name)
        assertNull(status.errorMessage)
    }

    @Test
    fun `test McpServerStatus with error state`() {
        // Given
        val errorMessage = "Connection timeout"

        // When
        val status = McpServerStatus(
            serverName = "Failed Server",
            connectionState = McpConnectionState.ERROR,
            protocol = "HTTP",
            tools = emptyList(),
            errorMessage = errorMessage
        )

        // Then
        assertEquals(McpConnectionState.ERROR, status.connectionState)
        assertEquals(errorMessage, status.errorMessage)
        assertTrue(status.tools.isEmpty())
    }

    @Test
    fun `test McpConnectionState enum values`() {
        // Verify all connection states exist
        val states = McpConnectionState.values()
        
        assertTrue(states.contains(McpConnectionState.DISCONNECTED))
        assertTrue(states.contains(McpConnectionState.CONNECTING))
        assertTrue(states.contains(McpConnectionState.CONNECTED))
        assertTrue(states.contains(McpConnectionState.ERROR))
        assertEquals(4, states.size)
    }

    @Test
    fun `test McpTool creation`() {
        // Given
        val params = JsonObject().apply {
            addProperty("query", "string")
        }

        // When
        val tool = McpTool(
            name = "execute_sql",
            description = "Execute SQL query",
            parameters = params
        )

        // Then
        assertEquals("execute_sql", tool.name)
        assertEquals("Execute SQL query", tool.description)
        assertNotNull(tool.parameters)
        assertTrue(tool.parameters!!.has("query"))
    }

    @Test
    fun `test McpBridge initial state is empty`() = runTest {
        // Given
        val servers = emptyList<McpServerConfig>()
        
        // When
        mcpBridge = McpBridge(
            servers = servers,
            onToolResult = { callId, result -> toolResults[callId] = result },
            onError = { error -> errors.add(error) }
        )

        // Then
        val status = mcpBridge.serverStatus.first()
        assertTrue(status.isEmpty())
    }

    @Test
    fun `test getAllTools returns empty list when no servers`() = runTest {
        // Given
        val servers = emptyList<McpServerConfig>()
        mcpBridge = McpBridge(
            servers = servers,
            onToolResult = { callId, result -> toolResults[callId] = result },
            onError = { error -> errors.add(error) }
        )

        // When
        val tools = mcpBridge.getAllTools()

        // Then
        assertTrue(tools.isEmpty())
    }

    @Test
    fun `test McpBridge disconnect clears status`() = runTest {
        // Given
        val servers = listOf(
            McpServerConfig(
                name = "Test Server",
                url = "wss://test.example.com",
                enabled = true
            )
        )
        mcpBridge = McpBridge(
            servers = servers,
            onToolResult = { callId, result -> toolResults[callId] = result },
            onError = { error -> errors.add(error) }
        )

        // When
        mcpBridge.disconnect()
        advanceUntilIdle()

        // Then
        val status = mcpBridge.serverStatus.first()
        assertTrue(status.isEmpty())
    }

    @Test
    fun `test McpServerStatus timestamp is recent`() {
        // Given
        val beforeTimestamp = System.currentTimeMillis()

        // When
        val status = McpServerStatus(
            serverName = "Test",
            connectionState = McpConnectionState.CONNECTED,
            protocol = "WebSocket",
            tools = emptyList()
        )
        val afterTimestamp = System.currentTimeMillis()

        // Then
        assertTrue(status.lastUpdate >= beforeTimestamp)
        assertTrue(status.lastUpdate <= afterTimestamp)
    }

    @Test
    fun `test McpServerStatus copy with different tools`() {
        // Given
        val originalStatus = McpServerStatus(
            serverName = "Test Server",
            connectionState = McpConnectionState.CONNECTED,
            protocol = "WebSocket",
            tools = emptyList()
        )

        val newTools = listOf(
            McpTool("tool1", "Description 1", null),
            McpTool("tool2", "Description 2", null)
        )

        // When
        val updatedStatus = originalStatus.copy(tools = newTools)

        // Then
        assertEquals(originalStatus.serverName, updatedStatus.serverName)
        assertEquals(originalStatus.connectionState, updatedStatus.connectionState)
        assertEquals(0, originalStatus.tools.size)
        assertEquals(2, updatedStatus.tools.size)
    }

    @Test
    fun `test McpServerStatus equals and hashCode`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val tools = listOf(McpTool("test", "desc", null))

        val status1 = McpServerStatus(
            serverName = "Server1",
            connectionState = McpConnectionState.CONNECTED,
            protocol = "WebSocket",
            tools = tools,
            lastUpdate = timestamp
        )

        val status2 = McpServerStatus(
            serverName = "Server1",
            connectionState = McpConnectionState.CONNECTED,
            protocol = "WebSocket",
            tools = tools,
            lastUpdate = timestamp
        )

        // Then
        assertEquals(status1, status2)
        assertEquals(status1.hashCode(), status2.hashCode())
    }

    @Test
    fun `test multiple connection states are distinct`() {
        // Given
        val baseStatus = McpServerStatus(
            serverName = "Test",
            connectionState = McpConnectionState.DISCONNECTED,
            protocol = "WebSocket",
            tools = emptyList()
        )

        // When
        val connecting = baseStatus.copy(connectionState = McpConnectionState.CONNECTING)
        val connected = baseStatus.copy(connectionState = McpConnectionState.CONNECTED)
        val error = baseStatus.copy(connectionState = McpConnectionState.ERROR)

        // Then
        assertNotEquals(baseStatus.connectionState, connecting.connectionState)
        assertNotEquals(baseStatus.connectionState, connected.connectionState)
        assertNotEquals(baseStatus.connectionState, error.connectionState)
        assertNotEquals(connecting.connectionState, connected.connectionState)
    }
}
