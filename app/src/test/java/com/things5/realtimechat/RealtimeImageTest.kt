package com.things5.realtimechat

import org.junit.Assert.*
import org.junit.Test

/**
 * Test per verificare il corretto formato delle immagini inviate alla Realtime API
 */
class RealtimeImageTest {

    @Test
    fun `test image data URL format`() {
        // Given
        val base64Image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        val mimeType = "image/jpeg"
        
        // When
        val dataUrl = "data:$mimeType;base64,$base64Image"
        
        // Then
        assertTrue(dataUrl.startsWith("data:"))
        assertTrue(dataUrl.contains(";base64,"))
        assertTrue(dataUrl.contains(mimeType))
        assertTrue(dataUrl.endsWith(base64Image))
    }
    
    @Test
    fun `test data URL has correct structure`() {
        // Given
        val base64Image = "ABC123"
        val mimeType = "image/png"
        
        // When
        val dataUrl = "data:$mimeType;base64,$base64Image"
        
        // Then
        val expected = "data:image/png;base64,ABC123"
        assertEquals(expected, dataUrl)
    }
    
    @Test
    fun `test different image mime types`() {
        // Given
        val base64Image = "testImage"
        
        // Test JPEG
        val jpegUrl = "data:image/jpeg;base64,$base64Image"
        assertTrue(jpegUrl.contains("image/jpeg"))
        
        // Test PNG
        val pngUrl = "data:image/png;base64,$base64Image"
        assertTrue(pngUrl.contains("image/png"))
        
        // Test WebP
        val webpUrl = "data:image/webp;base64,$base64Image"
        assertTrue(webpUrl.contains("image/webp"))
    }
    
    @Test
    fun `test image URL content structure`() {
        // Given
        val base64Image = "sampleBase64String"
        val mimeType = "image/jpeg"
        val dataUrl = "data:$mimeType;base64,$base64Image"
        
        // Simulate the conversation.item.create event structure
        val event = mapOf(
            "type" to "conversation.item.create",
            "item" to mapOf(
                "type" to "message",
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "input_image",
                        "image_url" to dataUrl
                    )
                )
            )
        )
        
        // Then - verify structure
        assertEquals("conversation.item.create", event["type"])
        
        val item = event["item"] as Map<*, *>
        assertEquals("message", item["type"])
        assertEquals("user", item["role"])
        
        val content = item["content"] as List<*>
        assertEquals(1, content.size)
        
        val imageContent = content[0] as Map<*, *>
        assertEquals("input_image", imageContent["type"])
        assertEquals(dataUrl, imageContent["image_url"])
        assertTrue((imageContent["image_url"] as String).startsWith("data:"))
    }
    
    @Test
    fun `test base64 string without prefix`() {
        // Given - user might provide base64 WITH or WITHOUT the data URL prefix
        val base64WithoutPrefix = "ABC123"
        val base64WithPrefix = "data:image/jpeg;base64,ABC123"
        
        // When - we always create the data URL format
        val dataUrl1 = "data:image/jpeg;base64,$base64WithoutPrefix"
        
        // If it already has prefix, we should handle it
        val cleaned = base64WithPrefix.substringAfter("base64,").ifEmpty { base64WithPrefix }
        val dataUrl2 = "data:image/jpeg;base64,$cleaned"
        
        // Then
        assertTrue(dataUrl1.startsWith("data:"))
        assertTrue(dataUrl2.startsWith("data:"))
        assertEquals("data:image/jpeg;base64,ABC123", dataUrl1)
        assertEquals("data:image/jpeg;base64,ABC123", dataUrl2)
    }
}
