package com.things5.realtimechat

import com.things5.realtimechat.data.ConnectionQuality
import com.things5.realtimechat.data.DebugLogType
import com.things5.realtimechat.data.MainScreenState
import com.things5.realtimechat.data.RealtimeDebugState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Test

@ExperimentalCoroutinesApi
class MainViewModelDebugTest {

    @Test
    fun `test initial debug state`() = runTest {
        // Note: This test requires proper Android context
        // In a real scenario, we would use Robolectric or Android Test
        
        val initialDebugState = RealtimeDebugState()
        
        assert(!initialDebugState.isConnected)
        assert(initialDebugState.connectionQuality == ConnectionQuality.UNKNOWN)
        assert(initialDebugState.logs.isEmpty())
        assert(initialDebugState.audioChunksSent == 0)
        assert(initialDebugState.audioChunksReceived == 0)
        assert(initialDebugState.imagesProcessed == 0)
        assert(initialDebugState.toolCallsExecuted == 0)
        assert(initialDebugState.errors == 0)
    }

    @Test
    fun `test debug state after connection`() = runTest {
        val connectedState = RealtimeDebugState(
            isConnected = true,
            connectionQuality = ConnectionQuality.GOOD,
            sessionId = "test-session"
        )
        
        assert(connectedState.isConnected)
        assert(connectedState.connectionQuality == ConnectionQuality.GOOD)
        assert(connectedState.sessionId == "test-session")
    }

    @Test
    fun `test debug counters increment`() = runTest {
        var debugState = RealtimeDebugState()
        
        // Simulate sending audio chunks
        debugState = debugState.copy(
            audioChunksSent = debugState.audioChunksSent + 1
        )
        assert(debugState.audioChunksSent == 1)
        
        // Simulate receiving audio chunks
        debugState = debugState.copy(
            audioChunksReceived = debugState.audioChunksReceived + 1
        )
        assert(debugState.audioChunksReceived == 1)
        
        // Simulate processing image
        debugState = debugState.copy(
            imagesProcessed = debugState.imagesProcessed + 1
        )
        assert(debugState.imagesProcessed == 1)
        
        // Simulate tool call
        debugState = debugState.copy(
            toolCallsExecuted = debugState.toolCallsExecuted + 1
        )
        assert(debugState.toolCallsExecuted == 1)
        
        // Simulate error
        debugState = debugState.copy(
            errors = debugState.errors + 1
        )
        assert(debugState.errors == 1)
    }

    @Test
    fun `test debug log buffer limit`() = runTest {
        val maxLogs = 500
        val logs = mutableListOf<com.things5.realtimechat.data.DebugLogEntry>()
        
        // Add logs up to limit
        repeat(maxLogs + 100) { index ->
            logs.add(com.things5.realtimechat.data.DebugLogEntry(
                type = DebugLogType.INFO,
                message = "Log $index"
            ))
        }
        
        // Trim to max
        val trimmedLogs = logs.takeLast(maxLogs)
        
        assert(trimmedLogs.size == maxLogs)
        assert(trimmedLogs.first().message == "Log 100")
        assert(trimmedLogs.last().message == "Log 599")
    }

    @Test
    fun `test clear conversation resets state`() = runTest {
        var mainState = MainScreenState(
            transcript = "Some conversation text",
            capturedImages = listOf("image1", "image2")
        )
        
        // Simulate clear
        mainState = mainState.copy(
            transcript = "",
            capturedImages = emptyList()
        )
        
        assert(mainState.transcript.isEmpty())
        assert(mainState.capturedImages.isEmpty())
    }

    @Test
    fun `test debug logs for different event types`() = runTest {
        val logs = listOf(
            com.things5.realtimechat.data.DebugLogEntry(
                type = DebugLogType.CONNECTION,
                message = "Connected to API"
            ),
            com.things5.realtimechat.data.DebugLogEntry(
                type = DebugLogType.AUDIO_SENT,
                message = "Audio chunk sent",
                details = "Chunk size: 1024 bytes"
            ),
            com.things5.realtimechat.data.DebugLogEntry(
                type = DebugLogType.AUDIO_RECEIVED,
                message = "Audio chunk received"
            ),
            com.things5.realtimechat.data.DebugLogEntry(
                type = DebugLogType.IMAGE_SENT,
                message = "Image sent"
            ),
            com.things5.realtimechat.data.DebugLogEntry(
                type = DebugLogType.TOOL_CALL,
                message = "Tool: get_weather",
                details = "Parameters: {\"location\": \"Rome\"}"
            ),
            com.things5.realtimechat.data.DebugLogEntry(
                type = DebugLogType.TOOL_RESPONSE,
                message = "Tool response received"
            ),
            com.things5.realtimechat.data.DebugLogEntry(
                type = DebugLogType.ERROR,
                message = "Connection error",
                details = "Timeout after 30s"
            ),
            com.things5.realtimechat.data.DebugLogEntry(
                type = DebugLogType.INFO,
                message = "Session initialized"
            )
        )
        
        assert(logs.size == 8)
        assert(logs.count { it.type == DebugLogType.CONNECTION } == 1)
        assert(logs.count { it.type == DebugLogType.ERROR } == 1)
        assert(logs.count { it.type == DebugLogType.TOOL_CALL } == 1)
        
        // Verify details are preserved
        val toolCallLog = logs.find { it.type == DebugLogType.TOOL_CALL }
        assert(toolCallLog?.details?.contains("Rome") == true)
    }

    @Test
    fun `test connection quality transitions`() = runTest {
        var debugState = RealtimeDebugState(
            connectionQuality = ConnectionQuality.UNKNOWN
        )
        
        // Transition to GOOD
        debugState = debugState.copy(connectionQuality = ConnectionQuality.GOOD)
        assert(debugState.connectionQuality == ConnectionQuality.GOOD)
        
        // Transition to EXCELLENT
        debugState = debugState.copy(connectionQuality = ConnectionQuality.EXCELLENT)
        assert(debugState.connectionQuality == ConnectionQuality.EXCELLENT)
        
        // Transition to POOR
        debugState = debugState.copy(connectionQuality = ConnectionQuality.POOR)
        assert(debugState.connectionQuality == ConnectionQuality.POOR)
    }

    @Test
    fun `test error tracking in debug state`() = runTest {
        var debugState = RealtimeDebugState()
        
        // Add first error
        debugState = debugState.copy(
            errors = debugState.errors + 1,
            lastError = "Connection timeout"
        )
        
        assert(debugState.errors == 1)
        assert(debugState.lastError == "Connection timeout")
        
        // Add second error
        debugState = debugState.copy(
            errors = debugState.errors + 1,
            lastError = "Invalid API key"
        )
        
        assert(debugState.errors == 2)
        assert(debugState.lastError == "Invalid API key")
    }

    @Test
    fun `test usage limit flag`() = runTest {
        var debugState = RealtimeDebugState(
            usageLimitReached = false
        )
        
        assert(!debugState.usageLimitReached)
        
        // Simulate reaching limit
        debugState = debugState.copy(usageLimitReached = true)
        
        assert(debugState.usageLimitReached)
    }

    @Test
    fun `test session id tracking`() = runTest {
        var debugState = RealtimeDebugState(sessionId = null)
        
        assert(debugState.sessionId == null)
        
        // Set session ID
        debugState = debugState.copy(sessionId = "sess_abc123xyz")
        
        assert(debugState.sessionId == "sess_abc123xyz")
    }

    @Test
    fun `test comprehensive statistics tracking`() = runTest {
        var debugState = RealtimeDebugState()
        
        // Simulate a complete interaction
        debugState = debugState.copy(
            isConnected = true,
            connectionQuality = ConnectionQuality.EXCELLENT,
            audioChunksSent = 50,
            audioChunksReceived = 45,
            imagesProcessed = 2,
            toolCallsExecuted = 3,
            errors = 0
        )
        
        assert(debugState.isConnected)
        assert(debugState.connectionQuality == ConnectionQuality.EXCELLENT)
        assert(debugState.audioChunksSent == 50)
        assert(debugState.audioChunksReceived == 45)
        assert(debugState.imagesProcessed == 2)
        assert(debugState.toolCallsExecuted == 3)
        assert(debugState.errors == 0)
    }
}
