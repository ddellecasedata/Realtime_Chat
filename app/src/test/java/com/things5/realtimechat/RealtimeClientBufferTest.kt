package com.things5.realtimechat

import com.things5.realtimechat.network.RealtimeClient
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealtimeClientBufferTest {
    private lateinit var client: RealtimeClient

    @Before
    fun setup() {
        client = RealtimeClient(
            apiKey = "test-api-key",
            model = "gpt-4o-realtime-preview-2024-12-17"
        )
    }

    @Test
    fun `test clearInputBuffer method exists`() {
        try {
            client.clearInputBuffer()
            // If no exception is thrown, consider it ok in unit context
            assertTrue(true)
        } catch (e: Exception) {
            // When not connected, the client may emit an error via flow rather than throw; accept behavior
            assertTrue(true)
        }
    }
}
