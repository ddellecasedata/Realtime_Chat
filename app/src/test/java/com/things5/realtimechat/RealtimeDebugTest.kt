package com.things5.realtimechat

import com.things5.realtimechat.data.ConnectionQuality
import com.things5.realtimechat.data.DebugLogEntry
import com.things5.realtimechat.data.DebugLogType
import com.things5.realtimechat.data.RealtimeDebugState
import org.junit.Assert.*
import org.junit.Test

class RealtimeDebugTest {

    @Test
    fun `test debug state initialization`() {
        val debugState = RealtimeDebugState()
        
        assertFalse(debugState.isConnected)
        assertEquals(ConnectionQuality.UNKNOWN, debugState.connectionQuality)
        assertNull(debugState.sessionId)
        assertEquals("gpt-realtime", debugState.model)
        assertEquals(0, debugState.tokensUsed)
        assertEquals(0, debugState.audioChunksSent)
        assertEquals(0, debugState.audioChunksReceived)
        assertEquals(0, debugState.imagesProcessed)
        assertEquals(0, debugState.toolCallsExecuted)
        assertEquals(0, debugState.errors)
        assertTrue(debugState.logs.isEmpty())
        assertNull(debugState.lastError)
        assertFalse(debugState.usageLimitReached)
    }

    @Test
    fun `test debug state with connection`() {
        val debugState = RealtimeDebugState(
            isConnected = true,
            connectionQuality = ConnectionQuality.EXCELLENT,
            sessionId = "test-session-123"
        )
        
        assertTrue(debugState.isConnected)
        assertEquals(ConnectionQuality.EXCELLENT, debugState.connectionQuality)
        assertEquals("test-session-123", debugState.sessionId)
    }

    @Test
    fun `test debug state with statistics`() {
        val debugState = RealtimeDebugState(
            audioChunksSent = 100,
            audioChunksReceived = 50,
            imagesProcessed = 5,
            toolCallsExecuted = 3,
            errors = 2
        )
        
        assertEquals(100, debugState.audioChunksSent)
        assertEquals(50, debugState.audioChunksReceived)
        assertEquals(5, debugState.imagesProcessed)
        assertEquals(3, debugState.toolCallsExecuted)
        assertEquals(2, debugState.errors)
    }

    @Test
    fun `test debug log entry creation`() {
        val timestamp = System.currentTimeMillis()
        val log = DebugLogEntry(
            timestamp = timestamp,
            type = DebugLogType.CONNECTION,
            message = "Connected to server",
            details = "Session ID: abc123"
        )
        
        assertEquals(timestamp, log.timestamp)
        assertEquals(DebugLogType.CONNECTION, log.type)
        assertEquals("Connected to server", log.message)
        assertEquals("Session ID: abc123", log.details)
    }

    @Test
    fun `test debug log types`() {
        val types = DebugLogType.values()
        
        assertTrue(types.contains(DebugLogType.CONNECTION))
        assertTrue(types.contains(DebugLogType.AUDIO_SENT))
        assertTrue(types.contains(DebugLogType.AUDIO_RECEIVED))
        assertTrue(types.contains(DebugLogType.IMAGE_SENT))
        assertTrue(types.contains(DebugLogType.TOOL_CALL))
        assertTrue(types.contains(DebugLogType.TOOL_RESPONSE))
        assertTrue(types.contains(DebugLogType.ERROR))
        assertTrue(types.contains(DebugLogType.INFO))
    }

    @Test
    fun `test connection quality levels`() {
        val qualities = ConnectionQuality.values()
        
        assertTrue(qualities.contains(ConnectionQuality.EXCELLENT))
        assertTrue(qualities.contains(ConnectionQuality.GOOD))
        assertTrue(qualities.contains(ConnectionQuality.FAIR))
        assertTrue(qualities.contains(ConnectionQuality.POOR))
        assertTrue(qualities.contains(ConnectionQuality.UNKNOWN))
    }

    @Test
    fun `test debug state with logs`() {
        val logs = listOf(
            DebugLogEntry(
                type = DebugLogType.CONNECTION,
                message = "Connected"
            ),
            DebugLogEntry(
                type = DebugLogType.AUDIO_SENT,
                message = "Audio sent"
            ),
            DebugLogEntry(
                type = DebugLogType.ERROR,
                message = "Error occurred",
                details = "Connection timeout"
            )
        )
        
        val debugState = RealtimeDebugState(logs = logs)
        
        assertEquals(3, debugState.logs.size)
        assertEquals(DebugLogType.CONNECTION, debugState.logs[0].type)
        assertEquals(DebugLogType.AUDIO_SENT, debugState.logs[1].type)
        assertEquals(DebugLogType.ERROR, debugState.logs[2].type)
        assertEquals("Connection timeout", debugState.logs[2].details)
    }

    @Test
    fun `test debug state with error`() {
        val debugState = RealtimeDebugState(
            lastError = "Connection failed",
            errors = 1
        )
        
        assertEquals("Connection failed", debugState.lastError)
        assertEquals(1, debugState.errors)
    }

    @Test
    fun `test debug state with usage limit reached`() {
        val debugState = RealtimeDebugState(
            usageLimitReached = true
        )
        
        assertTrue(debugState.usageLimitReached)
    }

    @Test
    fun `test debug state copy with updated values`() {
        val original = RealtimeDebugState(
            isConnected = false,
            audioChunksSent = 10
        )
        
        val updated = original.copy(
            isConnected = true,
            audioChunksSent = 20
        )
        
        assertFalse(original.isConnected)
        assertEquals(10, original.audioChunksSent)
        
        assertTrue(updated.isConnected)
        assertEquals(20, updated.audioChunksSent)
    }

    @Test
    fun `test debug log entry without details`() {
        val log = DebugLogEntry(
            type = DebugLogType.INFO,
            message = "Simple info message"
        )
        
        assertEquals(DebugLogType.INFO, log.type)
        assertEquals("Simple info message", log.message)
        assertNull(log.details)
    }

    @Test
    fun `test multiple log entries with different types`() {
        val logs = mutableListOf<DebugLogEntry>()
        
        // Add different types of logs
        logs.add(DebugLogEntry(type = DebugLogType.CONNECTION, message = "Connected"))
        logs.add(DebugLogEntry(type = DebugLogType.AUDIO_SENT, message = "Audio chunk sent"))
        logs.add(DebugLogEntry(type = DebugLogType.AUDIO_RECEIVED, message = "Audio chunk received"))
        logs.add(DebugLogEntry(type = DebugLogType.IMAGE_SENT, message = "Image sent"))
        logs.add(DebugLogEntry(type = DebugLogType.TOOL_CALL, message = "Tool called"))
        logs.add(DebugLogEntry(type = DebugLogType.TOOL_RESPONSE, message = "Tool response"))
        logs.add(DebugLogEntry(type = DebugLogType.ERROR, message = "Error"))
        logs.add(DebugLogEntry(type = DebugLogType.INFO, message = "Info"))
        
        assertEquals(8, logs.size)
        assertEquals(DebugLogType.values().size, logs.size)
    }

    @Test
    fun `test debug state incremental updates`() {
        var debugState = RealtimeDebugState()
        
        // Simulate incremental updates
        debugState = debugState.copy(audioChunksSent = debugState.audioChunksSent + 1)
        assertEquals(1, debugState.audioChunksSent)
        
        debugState = debugState.copy(audioChunksSent = debugState.audioChunksSent + 1)
        assertEquals(2, debugState.audioChunksSent)
        
        debugState = debugState.copy(audioChunksReceived = debugState.audioChunksReceived + 1)
        assertEquals(1, debugState.audioChunksReceived)
        assertEquals(2, debugState.audioChunksSent)
    }

    @Test
    fun `test log buffer management`() {
        val maxLogs = 500
        val logs = mutableListOf<DebugLogEntry>()
        
        // Add more than max logs
        repeat(600) { index ->
            logs.add(DebugLogEntry(
                type = DebugLogType.INFO,
                message = "Log entry $index"
            ))
        }
        
        // Simulate trimming to last 500
        val trimmedLogs = logs.takeLast(maxLogs)
        
        assertEquals(maxLogs, trimmedLogs.size)
        assertEquals("Log entry 100", trimmedLogs.first().message)
        assertEquals("Log entry 599", trimmedLogs.last().message)
    }
}
